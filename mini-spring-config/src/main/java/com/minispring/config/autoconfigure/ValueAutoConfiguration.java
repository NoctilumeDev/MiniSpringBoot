package com.minispring.config.autoconfigure;

import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.config.annotation.ValueAnnotationBeanPostProcessor;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;

/**
 * 外部化配置自动配置：注入依赖了 {@code mini-spring-config} 时，自动把 {@code @Value} 注入处理器装配进容器。
 */
@Configuration
@ConditionalOnClass(ValueAnnotationBeanPostProcessor.class)
public class ValueAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ValueAnnotationBeanPostProcessor valueAnnotationBeanPostProcessor() {
        return new ValueAnnotationBeanPostProcessor();
    }
}