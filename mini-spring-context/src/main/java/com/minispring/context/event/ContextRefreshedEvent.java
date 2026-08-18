package com.minispring.context.event;

import com.minispring.context.ApplicationEvent;

/**
 * 容器「刷新完成」后广播：此刻所有单例已实例化、事件监听器已就位。
 */
public class ContextRefreshedEvent extends ApplicationEvent {

    public ContextRefreshedEvent(Object source) {
        super(source);
    }
}