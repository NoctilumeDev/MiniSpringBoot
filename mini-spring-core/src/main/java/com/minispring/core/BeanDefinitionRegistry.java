package com.minispring.core;

/**
 * Bean 图纸的注册中心：负责登记与查找 {@link BeanDefinition}。
 */
public interface BeanDefinitionRegistry {

    void registerBeanDefinition(String beanName, BeanDefinition beanDefinition);

    BeanDefinition getBeanDefinition(String beanName) throws BeansException;

    boolean containsBeanDefinition(String beanName);
}