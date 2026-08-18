package com.minispring.context;

/**
 * 事件监听器：只关心某一类 {@link ApplicationEvent}（由泛型参数 {@code E} 声明）。
 *
 * <p>实现此接口的 Bean 会被容器自动注册进事件广播器，无需手动订阅。
 */
@FunctionalInterface
public interface ApplicationListener<E extends ApplicationEvent> {

    void onApplicationEvent(E event);
}