package com.minispring.aop;

import java.lang.reflect.Method;

/**
 * 一次方法调用的上下文：携带着目标对象、方法、参数和拦截器链。
 * 拦截器在 {@link #proceed()} 里决定「是否继续往下走、走到最后就是真调目标方法」。
 */
public interface MethodInvocation {

    Method getMethod();

    Object[] getArguments();

    Object getTarget();

    Object proceed() throws Throwable;
}