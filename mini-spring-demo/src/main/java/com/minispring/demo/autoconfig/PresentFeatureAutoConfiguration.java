package com.minispring.demo.autoconfig;

import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;

/**
 * 「依赖存在」自动配置：只有当 {@link PresentDependency} 在 classpath 上才装配。
 */
@Configuration
@ConditionalOnClass(PresentDependency.class)
public class PresentFeatureAutoConfiguration {

    @Bean
    public String presentFeature() {
        return "presence-feature 已启用（依赖存在 → 装配）";
    }
}