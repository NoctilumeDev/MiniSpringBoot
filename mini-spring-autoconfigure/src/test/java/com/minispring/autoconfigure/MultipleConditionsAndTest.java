package com.minispring.autoconfigure;

import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.autoconfigure.condition.ConditionalOnProperty;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M8 修复回归：一个元素上<b>多个</b>条件注解（各自携带 @Conditional）必须全部求值（AND）。
 * 修复前：ConditionEvaluator 只取第一个命中的 @Conditional——本用例的
 * 「类在（OnClass 命中）+ 配置缺失（OnProperty 不命中）」会被误装配。
 */
class MultipleConditionsAndTest {

    /** 两个条件：类存在（必命中）+ 配置开关（本测试未配置 → 不命中）。 */
    @Configuration
    @ConditionalOnClass(name = "java.lang.String")
    @ConditionalOnProperty(name = "minispring.multiple.enabled")
    static class TwoConditionConfig {
        @Bean
        public String andSemanticsBean() {
            return "bad";
        }
    }

    /** 对照组：两个条件都命中（类存在 + 配置存在）。 */
    @Configuration
    @ConditionalOnClass(name = "java.lang.String")
    @ConditionalOnProperty(name = "minispring.multiple.control", havingValue = "true", matchIfMissing = true)
    static class BothHitConfig {
        @Bean
        public String bothHitBean() {
            return "good";
        }
    }

    @Test
    void secondConditionMustAlsoBeEvaluated() {
        // matchIfMissing 的 control 键从系统属性注入（Environment 系统属性层），保证对照组命中
        System.setProperty("minispring.multiple.control", "true");
        try {
            AnnotationConfigApplicationContext ctx =
                    new AnnotationConfigApplicationContext(TwoConditionConfig.class, BothHitConfig.class);
            try {
                assertFalse(ctx.containsBean("andSemanticsBean"),
                        "OnProperty 不命中时必须跳过——修复前第二个条件被静默忽略、误装配");
                assertTrue(ctx.containsBean("bothHitBean"), "两个条件都命中时正常装配（对照组）");
            } finally {
                ctx.close();
            }
        } finally {
            System.clearProperty("minispring.multiple.control");
        }
    }
}
