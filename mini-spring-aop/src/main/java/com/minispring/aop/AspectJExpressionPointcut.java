package com.minispring.aop;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * 极简切点：支持两种表达式——
 * <ul>
 *   <li>{@code execution(返回类型 包.类.方法(..))}：类名/方法名可用 {@code *} 通配；</li>
 *   <li>{@code @annotation(注解全限定名)}：标注了指定注解的方法命中。
 *       接口方法本身没标、但<b>实现类对应方法</b>标了也算命中——与 Spring 的
 *       AnnotationMatchingPointcut 的 specific-method 回查语义一致。</li>
 * </ul>
 * 这不是完整 AspectJ 语法（roadmap 明确不做），只覆盖教学所需的子集。
 */
public class AspectJExpressionPointcut implements Pointcut {

    private final String expression;
    private String classPattern;
    private String methodPattern;
    private String annotationName;
    /** @annotation 切点的注解类（解析期加载一次缓存——matches 是每次代理调用的热路径，不做重复 Class.forName）。 */
    private Class<? extends java.lang.annotation.Annotation> annotationClass;

    public AspectJExpressionPointcut(String expression) {
        this.expression = expression;
        parse(expression);
    }

    private void parse(String expression) {
        String trimmed = expression.trim();
        // @annotation(全限定名)：注解级切点
        if (trimmed.startsWith("@annotation(") && trimmed.endsWith(")")) {
            this.annotationName = trimmed
                    .substring("@annotation(".length(), trimmed.length() - 1).trim();
            if (annotationName.isEmpty() || !annotationName.contains(".")) {
                throw new IllegalArgumentException("@annotation 切点需要注解全限定名: " + expression);
            }
            // 构造切点时解析并缓存注解类型，使非法类型在启动期失败，并避免热路径重复 Class.forName。
            this.annotationClass = loadAnnotation();
            return;
        }
        if (!trimmed.startsWith("execution(") || !trimmed.endsWith(")")) {
            throw new IllegalArgumentException("不支持的切点表达式: " + expression);
        }
        // 去掉 execution( 与结尾 )，得到 "* com.x.Y.m(..)"
        String body = trimmed.substring("execution(".length(), trimmed.length() - 1).trim();
        int paren = body.indexOf('(');
        if (paren < 0) {
            throw new IllegalArgumentException("切点表达式缺少参数列表: " + expression);
        }
        // "* com.x.Y.m" -> 最后一段是「类.方法」
        String signature = body.substring(0, paren).trim();
        String[] parts = signature.split("\\s+");
        String target = parts[parts.length - 1];
        int lastDot = target.lastIndexOf('.');
        if (lastDot < 0) {
            throw new IllegalArgumentException("切点表达式缺少方法名: " + expression);
        }
        this.classPattern = target.substring(0, lastDot);
        this.methodPattern = target.substring(lastDot + 1);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (annotationName != null) {
            return annotationMatches(method, targetClass);
        }
        return classMatches(targetClass.getName()) && methodMatches(method.getName());
    }

    /** 注解切点：方法本身、类级标注（该类全部方法命中）、或实现类的对应方法上标了指定注解即命中。 */
    private boolean annotationMatches(Method method, Class<?> targetClass) {
        if (isAnnotationPresent(method)) {
            return true;
        }
        // 类级标注（如 @Transactional 标在实现类上）命中该类全部方法，与 Spring 类级语义对齐。
        if (targetClass.isAnnotationPresent(annotationClass)) {
            return true;
        }
        // method 可能来自接口（JDK 代理）：回查实现类的对应方法（specific method）
        if (method.getDeclaringClass() != targetClass) {
            try {
                Method specific = targetClass.getMethod(method.getName(), method.getParameterTypes());
                return isAnnotationPresent(specific);
            } catch (NoSuchMethodException ignored) {
                return false;
            }
        }
        return false;
    }

    private boolean isAnnotationPresent(Method method) {
        return method.isAnnotationPresent(annotationClass);
    }

    private Class<? extends java.lang.annotation.Annotation> loadAnnotation() {
        // TCCL 为 null 的启动器场景兜底用本类加载器（否则落到 bootstrap loader，
        // 应用注解必然找不到，报出误导性的「注解不存在」）
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = AspectJExpressionPointcut.class.getClassLoader();
        }
        try {
            Class<?> candidate = Class.forName(annotationName, false, loader);
            if (!candidate.isAnnotation()) {
                throw new IllegalArgumentException("@annotation 切点引用的类型不是注解: " + annotationName);
            }
            return candidate.asSubclass(java.lang.annotation.Annotation.class);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("@annotation 切点引用的注解不存在: " + annotationName, e);
        }
    }

    private boolean classMatches(String className) {
        return globMatch(classPattern, className);
    }

    private boolean methodMatches(String methodName) {
        return globMatch(methodPattern, methodName);
    }

    /** 极简 glob：把 {@code *} 当任意序列，其余字符按字面量，最终用正则匹配。 */
    private boolean globMatch(String pattern, String value) {
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return value.matches(regex.toString());
    }

    @Override
    public String toString() {
        return expression;
    }
}
