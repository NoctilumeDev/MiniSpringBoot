package com.minispring.aop;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * 极简切点：支持两种表达式——
 * <ul>
 *   <li>{@code execution(返回类型 包.类.方法(..))}：类名/方法名可用 {@code *} 通配；</li>
 *   <li>{@code @annotation(注解全限定名)}：标注了指定注解的方法命中（M8 为 {@code @Transactional} 引入）。
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

    public AspectJExpressionPointcut(String expression) {
        this.expression = expression;
        parse(expression);
    }

    private void parse(String expression) {
        String trimmed = expression.trim();
        // @annotation(全限定名)：注解级切点（M8）
        if (trimmed.startsWith("@annotation(") && trimmed.endsWith(")")) {
            this.annotationName = trimmed
                    .substring("@annotation(".length(), trimmed.length() - 1).trim();
            if (annotationName.isEmpty() || !annotationName.contains(".")) {
                throw new IllegalArgumentException("@annotation 切点需要注解全限定名: " + expression);
            }
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

    /** 注解切点：方法本身或实现类的对应方法上标了指定注解即命中。 */
    private boolean annotationMatches(Method method, Class<?> targetClass) {
        if (isAnnotationPresent(method)) {
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
        return method.isAnnotationPresent(loadAnnotation());
    }

    @SuppressWarnings("unchecked")
    private Class<? extends java.lang.annotation.Annotation> loadAnnotation() {
        try {
            return (Class<? extends java.lang.annotation.Annotation>)
                    Class.forName(annotationName, false, Thread.currentThread().getContextClassLoader());
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
