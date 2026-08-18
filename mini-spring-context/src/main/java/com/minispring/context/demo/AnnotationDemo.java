package com.minispring.context.demo;

import com.minispring.context.ApplicationContext;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;

/**
 * M2 落地演示：用注解真实扫描出 Bean 并完成依赖注入。
 *
 * <p>验收标准：本 main 真启动、exit 0，且下列行为在运行期真实发生——
 * 组件被真实扫描、@Autowired 真实注入、循环依赖真实解开、@Primary/@Qualifier 真实裁决。
 */
public class AnnotationDemo {

    public static void main(String[] args) {
        System.out.println("=== M2 注解与类扫描落地演示 ===");

        ApplicationContext ctx = new AnnotationConfigApplicationContext(AppConfig.class);

        // 1. @Autowired 字段注入 + 循环依赖（OrderService ↔ UserService）
        OrderService orderService = ctx.getBean("orderService", OrderService.class);
        UserService userService = ctx.getBean("userService", UserService.class);
        check(orderService.getUserService() != null, "OrderService.userService 未被注入");
        check(userService.getOrderService() != null, "UserService.orderService 未被注入");
        check(orderService.getUserService() == userService, "循环依赖未正确解开");
        check(userService.getOrderService() == orderService, "循环依赖未正确解开（反向）");
        System.out.println("    [OK] @Autowired 字段注入 + 循环依赖解开成功");

        // 2. @Primary：多候选里择优 FrenchGreeter
        GreetingService greetingService = ctx.getBean("greetingService", GreetingService.class);
        check(greetingService.greeter() instanceof FrenchGreeter, "@Primary 未生效，应注入 FrenchGreeter");
        System.out.println("    [OK] @Primary 生效 -> " + greetingService.sayHello("Alice"));

        // 3. @Qualifier：显式点名 englishGreeter
        EnglishOnlyService englishOnly = ctx.getBean("englishOnlyService", EnglishOnlyService.class);
        check(englishOnly.sayHello("Bob").startsWith("Hello"), "@Qualifier 未生效，应注入 EnglishGreeter");
        System.out.println("    [OK] @Qualifier 生效 -> " + englishOnly.sayHello("Bob"));

        // 4. @Bean 方法 + 单例一致性
        String appName = ctx.getBean("appName", String.class);
        check("MiniSpringBoot-Demo".equals(appName), "@Bean 方法未生产出 appName");
        check(ctx.getBean("appName") == ctx.getBean("appName"), "单例被重复创建");
        System.out.println("    [OK] @Bean 方法生效 -> appName = " + appName);

        // 5. @Scope("prototype")
        Counter c1 = ctx.getBean("counter", Counter.class);
        Counter c2 = ctx.getBean("counter", Counter.class);
        check(c1 != c2, "@Scope(\"prototype\") 未生效");
        System.out.println("    [OK] @Scope prototype 生效：每次获取都是新实例");

        // 6. 单例复查
        check(ctx.getBean("orderService") == orderService, "orderService 单例被重复创建");

        ctx.close();
        System.out.println("=== M2 落地验证通过 ===");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("M2 落地校验失败: " + message);
        }
    }
}