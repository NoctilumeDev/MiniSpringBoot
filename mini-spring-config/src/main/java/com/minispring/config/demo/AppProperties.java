package com.minispring.config.demo;

import com.minispring.config.annotation.Value;
import com.minispring.context.annotation.Component;

/** 承载从 Environment 注入的配置值：既含 yaml 拍平、列表、默认值，也含 properties 与类型转换。 */
@Component
public class AppProperties {

    @Value("${app.name}")
    private String name;

    @Value("${server.port}")
    private int port;

    @Value("${app.timeout}")
    private int timeout;

    @Value("${app.missing:3000}")
    private int fallbackTimeout;

    @Value("${app.features[0]}")
    private String firstFeature;

    @Value("${app.features[1]}")
    private String secondFeature;

    @Value("${app.version}")
    private String version;

    @Value("${smtp.host}")
    private String smtpHost;

    // 嵌套占位符：先解析 ${app.pointer} -> name，再解析 ${app.name} -> MiniSpringBoot
    @Value("${app.${app.pointer}}")
    private String nestedPlaceholder;

    public String getName() {
        return name;
    }

    public int getPort() {
        return port;
    }

    public int getTimeout() {
        return timeout;
    }

    public int getFallbackTimeout() {
        return fallbackTimeout;
    }

    public String getFirstFeature() {
        return firstFeature;
    }

    public String getSecondFeature() {
        return secondFeature;
    }

    public String getVersion() {
        return version;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public String getNestedPlaceholder() {
        return nestedPlaceholder;
    }
}