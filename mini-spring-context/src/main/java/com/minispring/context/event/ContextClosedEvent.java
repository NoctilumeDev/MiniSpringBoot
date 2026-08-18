package com.minispring.context.event;

import com.minispring.context.ApplicationEvent;

/**
 * 容器「关闭、单例销毁前」广播。
 */
public class ContextClosedEvent extends ApplicationEvent {

    public ContextClosedEvent(Object source) {
        super(source);
    }
}