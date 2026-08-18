package com.minispring.demo.autoconfig;

import com.minispring.autoconfigure.condition.ConditionalOnProperty;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;

/**
 * 「配置开关」自动配置：仅当 {@code feature.optional.enabled=true} 时装配。
 */
@Configuration
@ConditionalOnProperty(name = "feature.optional.enabled", havingValue = "true")
public class OptionalFeatureAutoConfiguration {

    @Bean
    public String optionalFeature() {
        return "optional-feature 已启用（配置开关命中 → 装配）";
    }
}