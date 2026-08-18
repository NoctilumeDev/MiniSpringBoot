package com.minispring.aop;

/**
 * 顾问：一个「切点 + 一段拦截逻辑」的组合。容器里有多少切面方法，就有多少个 Advisor。
 */
public interface Advisor {

    Pointcut getPointcut();

    MethodInterceptor getMethodInterceptor();
}