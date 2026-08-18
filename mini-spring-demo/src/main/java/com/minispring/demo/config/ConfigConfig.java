package com.minispring.demo.config;

import com.minispring.config.annotation.ValueAnnotationBeanPostProcessor;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.ComponentScan;
import com.minispring.context.annotation.Configuration;

/** M4 配置入口：扫描本包组件，并显式把「@Value 注入处理器」作为基础设施 Bean 注册进容器。 */
@Configuration
@ComponentScan
public class ConfigConfig {

    @Bean
    public ValueAnnotationBeanPostProcessor valueAnnotationBeanPostProcessor() {
        return new ValueAnnotationBeanPostProcessor();
    }
}