package com.minispring.core;

/**
 * 最底层的 Bean 工厂：只负责「生产 Bean」。
 *
 * <p>这是「接口契约」的第一块基石：上层只依赖此接口，绝不触碰具体实现。
 */
public interface BeanFactory {

    Object getBean(String name) throws BeansException;

    <T> T getBean(String name, Class<T> requiredType) throws BeansException;

    boolean containsBean(String name);
}