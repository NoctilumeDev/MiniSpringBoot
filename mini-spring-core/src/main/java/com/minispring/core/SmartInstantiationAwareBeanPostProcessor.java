package com.minispring.core;

/**
 * 智能实例化感知的质检员：在 {@link InstantiationAwareBeanPostProcessor} 之上，增加「提前暴露引用」回调。
 *
 * <p>用于解决「被代理 Bean 同时参与循环依赖」：当某个 Bean 在半成品阶段就被他人引用时，
 * 需要在这一刻就给出「代理后的引用」而非原始裸对象，否则循环依赖的一方注入的是裸对象、
 * 容器最终持有的是代理，两者不一致（B2）。
 */
public interface SmartInstantiationAwareBeanPostProcessor extends InstantiationAwareBeanPostProcessor {

    /**
     * 在 Bean 尚未完成初始化就被他人引用时被调用。默认原样返回，
     * AOP 代理器可在此提前生成代理，保证提前暴露与最终持有的是同一个引用。
     */
    default Object getEarlyBeanReference(Object bean, String beanName) throws BeansException {
        return bean;
    }
}