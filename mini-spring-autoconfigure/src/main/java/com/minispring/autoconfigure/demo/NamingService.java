package com.minispring.autoconfigure.demo;

/**
 * 命名服务契约：用于演示「{@code @ConditionalOnMissingBean} 用户覆盖则回退」。
 */
public interface NamingService {

    String name();
}