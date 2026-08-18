package com.minispring.aop;

import com.minispring.core.Ordered;

/**
 * 最朴素的 Advisor 实现：直接组装一个 {@link Pointcut} 和一个 {@link MethodInterceptor}。
 *
 * <p>额外持有排序优先级 {@code order}，供多切面组合时确定执行顺序（D5）。
 */
public class PointcutAdvisor implements Advisor {

    private final Pointcut pointcut;
    private final MethodInterceptor methodInterceptor;
    private final int order;

    public PointcutAdvisor(Pointcut pointcut, MethodInterceptor methodInterceptor) {
        this(pointcut, methodInterceptor, Ordered.LOWEST_PRECEDENCE);
    }

    public PointcutAdvisor(Pointcut pointcut, MethodInterceptor methodInterceptor, int order) {
        this.pointcut = pointcut;
        this.methodInterceptor = methodInterceptor;
        this.order = order;
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public MethodInterceptor getMethodInterceptor() {
        return methodInterceptor;
    }

    @Override
    public int getOrder() {
        return order;
    }
}