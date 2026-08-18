package com.minispring.context;

/**
 * 事件发布器：把事件交给已注册的监听器，由广播器同步、按序分发。
 */
@FunctionalInterface
public interface ApplicationEventPublisher {

    void publishEvent(ApplicationEvent event);
}