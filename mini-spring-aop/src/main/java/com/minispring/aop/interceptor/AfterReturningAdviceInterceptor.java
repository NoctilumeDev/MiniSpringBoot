package com.minispring.aop.interceptor;

import com.minispring.aop.MethodInterceptor;
import com.minispring.aop.MethodInvocation;

import java.lang.reflect.InvocationTargetException;
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
        Throwable failure = null;
        Object result = null;
        try {
            result = invocation.proceed();
        } catch (Throwable t) {
            failure = t;
        }
        // @After = finally 语义：目标无论正常/异常，后置通知都执行
        try {
            afterMethod.invoke(aspectInstance);
        } catch (InvocationTargetException e) {
            Throwable afterFailure = e.getTargetException();
            // P5：后置通知自身异常不得覆盖目标异常——目标异常为主，后置异常作为 suppressed 附加
            if (failure != null) {
                failure.addSuppressed(afterFailure);
            } else {
                failure = afterFailure;
            }
        }
        if (failure != null) {
            throw failure;
        }
        return result;
    }
}