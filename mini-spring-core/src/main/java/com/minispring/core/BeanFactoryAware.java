package com.minispring.core;

/**
 * 让 Bean 能感知到它所在的 BeanFactory。
 *
 * <p>AOP 的代理创建器需要「按名/按类型查找 Bean」来收集切面，就靠容器把它自己注入进来。
 */
public interface BeanFactoryAware {

    void setBeanFactory(BeanFactory beanFactory);
}