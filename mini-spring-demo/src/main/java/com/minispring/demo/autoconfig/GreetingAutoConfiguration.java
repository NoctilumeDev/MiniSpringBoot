package com.minispring.demo.autoconfig;

import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;

/**
 * 问候自动配置：用户未定义 {@link GreetingService} 时，兜底提供 {@link DefaultGreetingService}。
 */
@Configuration
public class GreetingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GreetingService greetingService() {
        return new DefaultGreetingService();
    }
}