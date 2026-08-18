package com.minispring.aop;

import java.lang.reflect.Method;

/**
 * 切点：回答「哪些方法的调用要被拦截」。
 */
public interface Pointcut {

    boolean matches(Method method, Class<?> targetClass);
}