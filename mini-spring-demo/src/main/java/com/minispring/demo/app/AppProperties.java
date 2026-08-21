package com.minispring.demo.app;

import com.minispring.config.annotation.Value;
import com.minispring.context.annotation.Component;

/** 承载从 Environment 注入的配置值；处理器由 ValueAutoConfiguration 自动装配，无需手写 @Bean。 */
@Component
public class AppProperties {

    @Value("${app.name}")
    private String name;

    @Value("${server.port}")
    private int port;

    @Value("${app.instance-id:local}")
    private String instanceId;

    public String getName() {
        return name;
    }

    public int getPort() {
        return port;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
