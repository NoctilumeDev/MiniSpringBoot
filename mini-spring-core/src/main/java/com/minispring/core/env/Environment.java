package com.minispring.core.env;

/**
 * 对外统一的配置访问门面：聚合多个 {@link PropertySource}，按优先级查值，并支持占位符解析。
 */
public interface Environment {

    /** 按优先级查找；找不到返回 null。 */
    String getProperty(String key);

    String getProperty(String key, String defaultValue);

    boolean containsProperty(String key);

    /**
     * 解析文本里的 {@code ${key}} 占位符，支持 {@code ${key:default}} 与嵌套；
     * 找不到且无默认值时抛异常。
     */
    String resolvePlaceholders(String text);

    String[] getActiveProfiles();
}