package com.minispring.core.env;

/**
 * 配置的「源头」：无论来自文件、系统属性还是环境变量，都抽象成同一种问法。
 *
 * <p>这就是 {@code application.yml} 里一行配置能流到 {@code @Value} 字段上的第一块基石。
 */
public interface PropertySource {

    String getName();

    /** 返回该源头里 key 对应的值；没有则返回 null。 */
    Object getProperty(String key);
}