package com.minispring.demo.autoconfig;

import com.minispring.config.support.ConfigFilePropertySourceLoader;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;
import com.minispring.core.env.StandardEnvironment;

/**
 * M6 自动配置演示（迁移到 mini-spring-demo）：真实启动一个带自动配置的应用上下文，逐一断言条件装配结果。
 */
public class AutoConfigDemo {

    public static void main(String[] args) {
        StandardEnvironment environment = new StandardEnvironment();
        new ConfigFilePropertySourceLoader().load(environment);

        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(environment, AutoConfigApplication.class);

        System.out.println("=== M6 自动配置落地演示（已迁至 demo 模块）===");
        System.out.println("presentFeature 存在: " + context.containsBean("presentFeature"));
        System.out.println("absentFeature  存在: " + context.containsBean("absentFeature"));
        System.out.println("optionalFeature 存在: " + context.containsBean("optionalFeature"));

        GreetingService greeting = context.getBean("greetingService", GreetingService.class);
        System.out.println("greetingService 实现: " + greeting.getClass().getSimpleName() + " -> " + greeting.greet());

        NamingService naming = context.getBean("namingService", NamingService.class);
        System.out.println("namingService   实现: " + naming.getClass().getSimpleName() + " -> " + naming.name());

        assertTrue("presentFeature 应因依赖存在而装配", context.containsBean("presentFeature"));
        assertTrue("absentFeature 应因依赖缺失而跳过", !context.containsBean("absentFeature"));
        assertTrue("optionalFeature 应因开关命中而装配", context.containsBean("optionalFeature"));
        assertTrue("greetingService 应为自动配置默认实现", greeting instanceof DefaultGreetingService);
        assertTrue("namingService 应为用户实现（自动配置回退）", naming instanceof UserNamingService);

        System.out.println("M6 自动配置全部断言通过。");
        context.close();
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new IllegalStateException("断言失败: " + label);
        }
        System.out.println("  [PASS] " + label);
    }
}