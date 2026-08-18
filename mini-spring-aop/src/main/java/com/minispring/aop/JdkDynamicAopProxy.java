package com.minispring.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 JDK 动态代理的 AOP 代理：只代理接口方法，是无侵入织入的核心。
 */
public class JdkDynamicAopProxy implements AopProxy, InvocationHandler {

    private final Object target;
    private final List<Advisor> advisors;

    public JdkDynamicAopProxy(Object target, List<Advisor> advisors) {
        this.target = target;
        this.advisors = advisors;
    }

    @Override
    public Object getProxy() {
        return Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object[] arguments = (args != null) ? args : new Object[0];
        // Object 的 equals/hashCode/toString 属于代理自身语义，不落入业务切点，直接透传给目标
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(target, arguments);
        }
        // 收集所有「切点命中该方法」的拦截器，组成链
        List<MethodInterceptor> chain = new ArrayList<>();
        for (Advisor advisor : advisors) {
            if (advisor.getPointcut().matches(method, target.getClass())) {
                chain.add(advisor.getMethodInterceptor());
            }
        }
        if (chain.isEmpty()) {
            return method.invoke(target, arguments);
        }
        return new ReflectiveMethodInvocation(target, method, arguments, chain).proceed();
    }
}