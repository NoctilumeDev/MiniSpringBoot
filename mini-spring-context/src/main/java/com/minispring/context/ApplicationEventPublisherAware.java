package com.minispring.context;

/**
 * 需要「事件发布器」的 Bean 实现此接口：容器会在初始化回调之前注入 {@link ApplicationEventPublisher}，
 * 之后即可在 {@code afterPropertiesSet} / initMethod 等任何时机发布事件（A-6 场景的正式入口）。
 */
public interface ApplicationEventPublisherAware {

    void setApplicationEventPublisher(ApplicationEventPublisher publisher);
}
