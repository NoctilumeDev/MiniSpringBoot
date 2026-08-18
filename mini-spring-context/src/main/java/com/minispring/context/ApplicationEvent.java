package com.minispring.context;

/**
 * 应用事件的基类：携带「事件源」与「发生时间」。
 * 框架内所有生命周期事件（刷新 / 启动 / 关闭）都继承它。
 *
 * <p>本身是抽象类——实际广播的总是某个具体事件（如 {@code ContextRefreshedEvent}），
 * 监听器通过泛型声明自己关心哪种事件。
 */
public abstract class ApplicationEvent {

    private final Object source;
    private final long timestamp;

    public ApplicationEvent(Object source) {
        this.source = source;
        this.timestamp = System.currentTimeMillis();
    }

    public Object getSource() {
        return source;
    }

    public long getTimestamp() {
        return timestamp;
    }
}