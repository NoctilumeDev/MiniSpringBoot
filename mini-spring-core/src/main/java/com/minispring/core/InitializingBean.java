package com.minispring.core;

/**
 * 初始化回调：容器在「属性填充完成后」调用一次 {@link #afterPropertiesSet()}。
 */
public interface InitializingBean {

    void afterPropertiesSet() throws Exception;
}