package com.minispring.core;

import java.lang.reflect.Constructor;

/**
 * 实例化感知的质检员：在 {@link BeanPostProcessor} 基础上，增加「实例化前选构造器」与「属性填充」两个回调。
 *
 * <p>{@code @Autowired} 字段/方法注入发生在 {@link #postProcessProperties}；构造器注入则由
 * {@link #determineCandidateConstructors} 先选出候选构造器，容器负责解析参数并调用。
 * 为什么注入必须拆出这个时机？因为注入必须发生在「半成品已被提前暴露（三级缓存）之后、初始化回调之前」，
 * 才能和三级缓存一起正确解开循环依赖。
 */
public interface InstantiationAwareBeanPostProcessor extends BeanPostProcessor {

    /**
     * 实例化前调用：返回标注了注入注解（如 {@code @Autowired}）的候选构造器；无则返回 {@code null} 走默认构造。
     * 返回多个时容器只取唯一一个（多个 {@code @Autowired(required=true)} 构造器是歧义配置，直接报错）。
     */
    default Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName) throws BeansException {
        return null;
    }

    /**
     * 在 populateBean 阶段（PropertyValue 注入之后、初始化之前）被调用，
     * 用于完成按注解的字段 / 方法注入。
     */
    default void postProcessProperties(Object bean, String beanName) throws BeansException {
    }
}