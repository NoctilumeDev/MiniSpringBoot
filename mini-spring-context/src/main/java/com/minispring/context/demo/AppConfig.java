package com.minispring.context.demo;

import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.ComponentScan;
import com.minispring.context.annotation.Configuration;
import com.minispring.context.annotation.Scope;

/** 入口配置类：声明 @Bean 工厂方法，并用 @ComponentScan 扫描本包组件。 */
@Configuration
@ComponentScan
public class AppConfig {

    @Bean
    public String appName() {
        return "MiniSpringBoot-Demo";
    }

    @Bean
    @Scope("prototype")
    public Counter counter() {
        return new Counter();
    }
}