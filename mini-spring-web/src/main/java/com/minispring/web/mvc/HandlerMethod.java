package com.minispring.web.mvc;

import java.lang.reflect.Method;

/**
 * 处理器方法：把「哪个 Controller 的哪个方法」封装成统一的可调用单元。
 *
 * <p>它是 HandlerMapping 的产出、HandlerAdapter 的输入，MVC 里承上启下的核心价值对象
 * （对应 Spring 的 {@code HandlerMethod}）。
 */
public final class HandlerMethod {

    private final Object bean;
    private final Method method;

    public HandlerMethod(Object bean, Method method) {
        this.bean = bean;
        this.method = method;
    }

    public Object getBean() {
        return bean;
    }

    public Method getMethod() {
        return method;
    }

    public Class<?> getBeanType() {
        return bean.getClass();
    }

    @Override
    public String toString() {
        return getBeanType().getSimpleName() + "#" + method.getName();
    }
}