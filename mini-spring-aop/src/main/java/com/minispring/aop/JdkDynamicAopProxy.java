package com.minispring.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
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
                AopProxyUtils.completeProxiedInterfaces(target.getClass()),
                this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object[] arguments = (args != null) ? args : new Object[0];
        // equals/hashCode 必须遵守代理自身的身份契约：若委托给目标，
        // target.equals(proxy) 通常为 false，连 proxy.equals(proxy) 也会被破坏。
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                // toString 不参与身份判定，保留目标的可读表示。
                default -> invokeTarget(method, arguments);
            };
        }
        // 收集所有「切点命中该方法」的拦截器，组成链
        List<MethodInterceptor> chain = new ArrayList<>();
        for (Advisor advisor : advisors) {
            if (advisor.getPointcut().matches(method, target.getClass())) {
                chain.add(advisor.getMethodInterceptor());
            }
        }
        if (chain.isEmpty()) {
            return invokeTarget(method, arguments);
        }
        return new ReflectiveMethodInvocation(target, method, arguments, chain).proceed();
    }

    /**
     * 反射调用目标方法并拆包 {@link InvocationTargetException}，使所有代理调用路径
     * 都保留目标异常的类型与消息。
     */
    private Object invokeTarget(Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
