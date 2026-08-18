package com.minispring.aop;

/**
 * {@link ProceedingJoinPoint} 的默认实现：把「放行」委托回真正的 {@link MethodInvocation}。
 */
public class MethodInvocationProceedingJoinPoint implements ProceedingJoinPoint {

    private final MethodInvocation invocation;

    public MethodInvocationProceedingJoinPoint(MethodInvocation invocation) {
        this.invocation = invocation;
    }

    @Override
    public Object proceed() throws Throwable {
        return invocation.proceed();
    }

    @Override
    public String getMethodName() {
        return invocation.getMethod().getName();
    }
}