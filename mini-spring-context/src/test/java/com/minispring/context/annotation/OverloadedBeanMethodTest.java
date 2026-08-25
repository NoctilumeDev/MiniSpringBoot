package com.minispring.context.annotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 同名重载的 @Bean 方法必须以精确 Method 身份实例化，不能按反射枚举顺序取第一个。 */
class OverloadedBeanMethodTest {

    static class Dependency {
    }

    @Configuration
    static class OverloadedConfig {
        @Bean
        Dependency dependency() {
            return new Dependency();
        }

        @Bean("plainValue")
        String value() {
            return "plain";
        }

        @Bean("dependentValue")
        Integer value(Dependency dependency) {
            return dependency == null ? -1 : 42;
        }
    }

    @Test
    void overloadedBeanMethodsKeepTheirExactIdentity() {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(OverloadedConfig.class);
        try {
            assertEquals("plain", context.getBean("plainValue", String.class));
            assertEquals(42, context.getBean("dependentValue", Integer.class));
        } finally {
            context.close();
        }
    }
}
