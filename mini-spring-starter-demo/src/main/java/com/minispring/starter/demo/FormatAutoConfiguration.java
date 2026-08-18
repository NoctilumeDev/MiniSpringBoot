package com.minispring.starter.demo;

import com.minispring.autoconfigure.condition.ConditionalOnMissingBean;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;

/**
 * starter 自带的自动配置：用户未定义 {@link FormatService} 时，兜底装配 {@link UpperCaseFormatService}。
 *
 * <p>它被登记在该 starter 的 {@code META-INF/minispring/EnableAutoConfiguration.imports} 里，
 * 任何「引入本 starter 且开启 @EnableAutoConfiguration」的应用都会自动获得它，无需显式注册。
 */
@Configuration
public class FormatAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FormatService formatService() {
        return new UpperCaseFormatService();
    }
}