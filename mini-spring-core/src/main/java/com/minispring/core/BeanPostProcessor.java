package com.minispring.core;

/**
 * 质检员：在 Bean 初始化前后各给一次机会，可以改动「成品」。
 *
 * <p>AOP 的代理生成，未来就发生在 {@link #postProcessAfterInitialization} 里。
 */
public interface BeanPostProcessor {

    default Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    default Object postProcessAfterInitialization(Object bean, String beanName) {
        return bean;
    }
}