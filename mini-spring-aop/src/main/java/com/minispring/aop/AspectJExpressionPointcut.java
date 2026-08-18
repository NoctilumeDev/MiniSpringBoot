package com.minispring.aop;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * 极简切点：支持 {@code execution(返回类型 包.类.方法(..))} 形式的方法级匹配，
 * 其中类名 / 方法名可用 {@code *} 通配。
 *
 * <p>注意：这不是完整 AspectJ 语法（roadmap 明确不做），只覆盖演示所需的子集。
 */
public class AspectJExpressionPointcut implements Pointcut {

    private final String expression;
    private String classPattern;
    private String methodPattern;

    public AspectJExpressionPointcut(String expression) {
        this.expression = expression;
        parse(expression);
    }

    private void parse(String expression) {
        String trimmed = expression.trim();
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
        return classMatches(targetClass.getName()) && methodMatches(method.getName());
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