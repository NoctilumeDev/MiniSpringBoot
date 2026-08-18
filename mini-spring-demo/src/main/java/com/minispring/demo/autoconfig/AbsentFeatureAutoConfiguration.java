package com.minispring.demo.autoconfig;

import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;

/**
 * 「依赖缺失」自动配置：因为 classpath 上不存在 {@code DoesNotExist}，此配置整棵被跳过。
 */
@Configuration
@ConditionalOnClass(name = "com.minispring.demo.autoconfig.DoesNotExist")
public class AbsentFeatureAutoConfiguration {

    @Bean
    public String absentFeature() {
        return "本不该出现（依赖缺失 → 跳过）";
    }
}