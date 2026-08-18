package com.minispring.autoconfigure;

import com.minispring.autoconfigure.EnableAutoConfiguration;
import com.minispring.config.annotation.ValueAnnotationBeanPostProcessor;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;
import com.minispring.web.mvc.RequestMappingHandlerMapping;
import com.minispring.web.servlet.DispatcherServlet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动配置装配回归（A-1 归位后）：SPI 声明的三个框架自动配置真实生效，
 * 且 @ConditionalOnMissingBean 的「用户优先、自动兜底」语义保持。
 */
class AutoConfigurationAssemblyTest {

    @Configuration
    @EnableAutoConfiguration
    static class SpiApp {
    }

    /** 用户自定义了 RequestMappingHandlerMapping：自动配置必须回退，不得重复注册。 */
    @Configuration
    @EnableAutoConfiguration
    static class UserOverrideApp {
        @Bean
        public RequestMappingHandlerMapping customHandlerMapping() {
            return new CustomHandlerMapping();
        }
    }

    static class CustomHandlerMapping extends RequestMappingHandlerMapping {
    }

    @Test
    void spiAutoConfigurationsAreLoaded() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SpiApp.class);
        try {
            assertTrue(ctx.getBeanNamesForType(ValueAnnotationBeanPostProcessor.class).length >= 1,
                    "ValueAutoConfiguration 应从 autoconfigure 模块的 SPI 装配 @Value 处理器");
            assertTrue(ctx.getBeanNamesForType(DispatcherServlet.class).length == 1,
                    "WebMvcAutoConfiguration 应装配 DispatcherServlet");
        } finally {
            ctx.close();
        }
    }

    @Test
    void userBeanTakesPrecedenceOverAutoConfiguration() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(UserOverrideApp.class);
        try {
            assertEquals(1, ctx.getBeanNamesForType(RequestMappingHandlerMapping.class).length,
                    "用户已定义同类型 Bean 时，@ConditionalOnMissingBean 应回退（不重复注册）");
            Object mapping = ctx.getBean(ctx.getBeanNamesForType(RequestMappingHandlerMapping.class)[0]);
            assertInstanceOf(CustomHandlerMapping.class, mapping, "生效的必须是用户自定义的那个");
        } finally {
            ctx.close();
        }
    }
}
