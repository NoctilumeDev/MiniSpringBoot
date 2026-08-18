package com.minispring.demo.autoconfig;

import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;

/**
 * 命名自动配置：用户已定义 {@link NamingService} 时会回退（本 demo 里 AutoConfigApplication 已自定，故应被跳过）。
 */
@Configuration
public class NamingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NamingService namingService() {
        return new AutoNamingService();
    }
}