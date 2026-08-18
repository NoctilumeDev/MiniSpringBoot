package com.minispring.autoconfigure.config;

import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.config.annotation.ValueAnnotationBeanPostProcessor;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;

/**
 * 外部化配置自动配置：classpath 上有 {@code mini-spring-config} 时，自动把 {@code @Value} 注入处理器装配进容器。
 *
 * <p>A-1 归位 + D45 收口：类级条件用 {@code name} 字符串形式（理由见 {@link AopAutoConfiguration}），
 * config jar 缺失时注解解析不碰类字面量，条件安全返回 false → 整个配置类跳过。
 */
@Configuration
@ConditionalOnClass(name = "com.minispring.config.annotation.ValueAnnotationBeanPostProcessor")
public class ValueAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ValueAnnotationBeanPostProcessor valueAnnotationBeanPostProcessor() {
        return new ValueAnnotationBeanPostProcessor();
    }
}
