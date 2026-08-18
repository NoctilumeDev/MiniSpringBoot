package com.minispring.aop.interceptor;

import com.minispring.aop.MethodInterceptor;
import com.minispring.aop.MethodInvocation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 前置通知拦截器：先执行切面方法，再放行目标方法。
 */
public class MethodBeforeAdviceInterceptor implements MethodInterceptor {

    private final Object aspectInstance;
    private final Method beforeMethod;

    public MethodBeforeAdviceInterceptor(Object aspectInstance, Method beforeMethod) {
        this.aspectInstance = aspectInstance;
        this.beforeMethod = beforeMethod;
        this.beforeMethod.setAccessible(true);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        try {
            beforeMethod.invoke(aspectInstance);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
        return invocation.proceed();
    }
}