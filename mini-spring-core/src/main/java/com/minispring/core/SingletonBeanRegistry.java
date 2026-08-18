package com.minispring.core;

/**
 * 单例注册中心：除了「读单例」之外，还允许「替换 / 登记一个已就绪的单例」。
 *
 * <p>这个能力单独拆成一个接口，是因为个别后处理器（如 AOP 自动代理器）需要在 Bean 已经完全创建后、
 * 把「补代理后的对象」放回一级缓存，从而让提前抛给别人的引用与容器最终持有的引用保持一致（D30）。
 */
public interface SingletonBeanRegistry {

    /** 以 {@code singletonObject} 覆盖（或新增）beanName 对应的单例。 */
    void registerSingleton(String beanName, Object singletonObject);
}