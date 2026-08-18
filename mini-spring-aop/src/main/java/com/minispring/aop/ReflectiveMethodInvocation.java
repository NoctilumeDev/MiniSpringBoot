package com.minispring.aop;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 拦截器链的实际执行器：维护一个游标，{@link #proceed()} 逐个放行拦截器，
 * 走到最后一个拦截器之后，才真正反射调用目标方法。
 */
public class ReflectiveMethodInvocation implements MethodInvocation {

    private final Object target;
    private final Method method;
    private final Object[] arguments;
    private final List<MethodInterceptor> interceptors;
    private int currentIndex = 0;

    public ReflectiveMethodInvocation(Object target, Method method, Object[] arguments,
                                      List<MethodInterceptor> interceptors) {
        this.target = target;
        this.method = method;
        this.arguments = arguments;
        this.interceptors = interceptors;
        this.method.setAccessible(true);
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public Object[] getArguments() {
        return arguments;
    }

    @Override
    public Object getTarget() {
        return target;
    }

    @Override
    public Object proceed() throws Throwable {
        // 拦截器全部走完 -> 直捣目标方法
        if (currentIndex >= interceptors.size()) {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException e) {
                // 拆掉反射多包的一层，把原始异常原样抛出；否则 JDK 代理会再包成 UndeclaredThrowableException
                throw e.getTargetException();
            }
        }
        MethodInterceptor interceptor = interceptors.get(currentIndex++);
        return interceptor.invoke(this);
    }
}