package com.minispring.autoconfigure;

import com.minispring.autoconfigure.condition.ConditionalOnClass;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * D45 收口回归：类级条件用 {@code name} 字符串探测「可能不存在」的类。
 *
 * <p>这是 optional 依赖结构的安全前提：框架 jar 缺失时（单测里以「类名不存在」等价模拟），
 * 注解解析不碰任何类字面量 → 条件安全返回 false → 整个配置类（含其 @Bean 方法）跳过，不抛
 * {@code NoClassDefFoundError}。
 *
 * <p>修复前若注解写了 {@code value = Missing.class}，编译都无法构造该场景；本用例同时锁住
 * 「条件 skip 必须发生在 registerBeanMethods 枚举方法之前」的求值顺序。
 */
class ConditionalOnClassNameTest {

    /** 条件类名不存在：配置类必须被整体跳过，且 @Bean 方法 ghost() 不注册。 */
    @Configuration
    @ConditionalOnClass(name = "com.minispring.not.exist.Ghost")
    static class GhostConfig {
        @Bean
        public String ghost() {
            return "ghost";
        }
    }

    /** 对照组：条件类名存在（本测试类自己）→ 正常装配。 */
    @Configuration
    @ConditionalOnClass(name = "com.minispring.autoconfigure.ConditionalOnClassNameTest")
    static class PresentConfig {
        @Bean
        public String present() {
            return "present";
        }
    }

    @Test
    void absentClassNameSkipsWholeConfigurationWithoutError() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(GhostConfig.class, PresentConfig.class);
        try {
            assertFalse(ctx.containsBean("ghost"), "类名不存在时 GhostConfig 整个跳过，ghost Bean 不得注册");
            assertFalse(ctx.containsBean("ghostConfig"), "配置类本身也不得注册");
        } finally {
            ctx.close();
        }
    }
}
