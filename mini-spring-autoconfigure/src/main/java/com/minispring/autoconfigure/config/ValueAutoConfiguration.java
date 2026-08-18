package com.minispring.autoconfigure.config;

import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.config.annotation.ValueAnnotationBeanPostProcessor;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;

/**
 * 外部化配置自动配置：classpath 上有 {@code mini-spring-config} 时，自动把 {@code @Value} 注入处理器装配进容器。
 *
 * <p>A-1 归位：自动配置类统一放在 autoconfigure 模块，config 框架模块不再反向依赖 autoconfigure/context。
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
