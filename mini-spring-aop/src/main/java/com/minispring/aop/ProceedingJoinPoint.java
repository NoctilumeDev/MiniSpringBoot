package com.minispring.aop;

/**
 * 环绕通知拿到的「连接点」：既能把执行权放行给目标方法，也能拿到方法名等信息。
 */
public interface ProceedingJoinPoint {

    Object proceed() throws Throwable;

    String getMethodName();
}