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
                target.getClass().getInterfaces(),
                this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object[] arguments = (args != null) ? args : new Object[0];
        // Object 的 equals/hashCode/toString 属于代理自身语义，不落入业务切点，直接透传给目标
        if (method.getDeclaringClass() == Object.class) {
            return invokeTarget(method, arguments);
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
     * 反射调用目标方法并拆包 {@link InvocationTargetException}（M8 修复）。
     *
     * <p>不命中切点的方法此前直接 {@code method.invoke} 上抛——目标异常被包成
     * InvocationTargetException（其 message 为 null），到 DispatcherServlet 变成
     * 「500 Internal Server Error: null」，原始异常类型与消息全部丢失。M3 只修了
     * 命中切点的链路（ReflectiveMethodInvocation），此处为对称遗漏；M8 接口化
     * Service + 仅部分方法 @Transactional 使「经代理但不命中切点」成为常态调用
     * 路径，该缺陷被激活。
     */
    private Object invokeTarget(Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}