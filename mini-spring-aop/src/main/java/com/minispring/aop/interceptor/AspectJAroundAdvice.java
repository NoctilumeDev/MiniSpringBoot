package com.minispring.aop.interceptor;

import com.minispring.aop.MethodInterceptor;
import com.minispring.aop.MethodInvocation;
import com.minispring.aop.MethodInvocationProceedingJoinPoint;
import com.minispring.aop.ProceedingJoinPoint;

import java.lang.reflect.Method;

/**
 * 环绕通知拦截器：把执行权以 {@link ProceedingJoinPoint} 的形式交给切面方法，
 * 切面方法可自行决定何时（或是否）调用 {@code proceed()}。
 */
public class AspectJAroundAdvice implements MethodInterceptor {

    private final Object aspectInstance;
    private final Method aroundMethod;

    public AspectJAroundAdvice(Object aspectInstance, Method aroundMethod) {
        this.aspectInstance = aspectInstance;
        this.aroundMethod = aroundMethod;
        this.aroundMethod.setAccessible(true);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        ProceedingJoinPoint pjp = new MethodInvocationProceedingJoinPoint(invocation);
        return aroundMethod.invoke(aspectInstance, pjp);
    }
}