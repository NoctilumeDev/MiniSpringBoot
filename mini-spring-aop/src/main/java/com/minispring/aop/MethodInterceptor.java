package com.minispring.aop;

/**
 * 方法拦截器：AOP 里所有通知（前置 / 后置 / 环绕）最终都统一成它，
 * 串成一条「拦截器链」套在目标方法上。
 */
public interface MethodInterceptor {

    Object invoke(MethodInvocation invocation) throws Throwable;
}