package com.minispring.aop;

/**
 * 最朴素的 Advisor 实现：直接组装一个 {@link Pointcut} 和一个 {@link MethodInterceptor}。
 */
public class PointcutAdvisor implements Advisor {

    private final Pointcut pointcut;
    private final MethodInterceptor methodInterceptor;

    public PointcutAdvisor(Pointcut pointcut, MethodInterceptor methodInterceptor) {
        this.pointcut = pointcut;
        this.methodInterceptor = methodInterceptor;
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public MethodInterceptor getMethodInterceptor() {
        return methodInterceptor;
    }
}