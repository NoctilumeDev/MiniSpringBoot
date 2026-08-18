package com.minispring.core;

import com.minispring.core.env.Environment;

/**
 * 需要访问 {@link Environment} 的 Bean 可实现此接口，容器在初始化阶段把环境注入进去。
 *
 * <p>{@code @Value} 的注入处理器正是靠这个回调拿到环境，再去查占位符对应的值。
 */
public interface EnvironmentAware {

    void setEnvironment(Environment environment);
}