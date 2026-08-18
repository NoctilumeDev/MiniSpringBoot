package com.minispring.boot;

import com.minispring.context.ApplicationEvent;

/**
 * 应用「启动完成」事件：在 {@code run()} 把上下文刷新、Banner 打印之后广播。
 */
public class StartedEvent extends ApplicationEvent {

    public StartedEvent(Object source) {
        super(source);
    }
}