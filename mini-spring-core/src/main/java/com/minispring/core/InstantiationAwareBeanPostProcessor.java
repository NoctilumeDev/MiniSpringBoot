package com.minispring.core;

/**
 * 实例化感知的质检员：在 {@link BeanPostProcessor} 基础上，增加「属性填充」阶段的回调。
 *
 * <p>{@code @Autowired} 字段注入就发生在这里。为什么必须拆出这个时机？
 * 因为字段注入必须发生在「半成品已被提前暴露（三级缓存）之后、初始化回调之前」，
 * 才能和三级缓存一起正确解开循环依赖。
 */
public interface InstantiationAwareBeanPostProcessor extends BeanPostProcessor {

    /**
     * 在 populateBean 阶段（PropertyValue 注入之后、初始化之前）被调用，
     * 用于完成按注解的字段 / 构造注入。
     */
    default void postProcessProperties(Object bean, String beanName) throws BeansException {
    }
}