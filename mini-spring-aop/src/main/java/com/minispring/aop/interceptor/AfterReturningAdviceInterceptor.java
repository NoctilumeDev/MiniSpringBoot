package com.minispring.aop.interceptor;

import com.minispring.aop.MethodInterceptor;
import com.minispring.aop.MethodInvocation;

import java.lang.reflect.Method;

/**
 * 后置通知拦截器：先放行目标方法，正常返回后再执行切面方法。
 */
public class AfterReturningAdviceInterceptor implements MethodInterceptor {

    private final Object aspectInstance;
    private final Method afterMethod;

    public AfterReturningAdviceInterceptor(Object aspectInstance, Method afterMethod) {
        this.aspectInstance = aspectInstance;
        this.afterMethod = afterMethod;
        this.afterMethod.setAccessible(true);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        try {
            return invocation.proceed();
        } finally {
            afterMethod.invoke(aspectInstance);
        }
    }
}