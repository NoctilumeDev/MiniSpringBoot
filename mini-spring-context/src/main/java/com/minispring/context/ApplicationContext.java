package com.minispring.context;

import com.minispring.core.ListableBeanFactory;

/**
 * 应用上下文：在「Bean 工厂」之上，叠加了「启动入口 / 生命周期 / 事件广播」的语义。
 *
 * <p>框架使用方只面向这个接口操作容器，而不是直接抓 {@code DefaultListableBeanFactory}。
 */
public interface ApplicationContext extends ListableBeanFactory, ApplicationEventPublisher {

    /** 关闭容器，触发所有单例的销毁回调（并在销毁前广播 {@code ContextClosedEvent}）。 */
    void close();
}