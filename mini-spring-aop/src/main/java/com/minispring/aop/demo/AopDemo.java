package com.minispring.aop.demo;

import com.minispring.context.ApplicationContext;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Proxy;

/**
 * M3 落地演示：@Aspect 切面真实拦截 OrderService 的下单方法。
 *
 * <p>验收标准：容器返回的是代理、@Before/@After/@Around 真实触发、计时真实生效、目标方法真实执行并返回正确结果。
 */
public class AopDemo {

    public static void main(String[] args) {
        System.out.println("=== M3 AOP 落地演示 ===");

        ApplicationContext ctx = new AnnotationConfigApplicationContext(AopConfig.class);

        // 1. 容器返回的必须是 JDK 动态代理（而非原始对象），证明已织入切面
        OrderService orderService = ctx.getBean("orderServiceImpl", OrderService.class);
        check(Proxy.isProxyClass(orderService.getClass()), "OrderService 未被代理，AOP 未生效");
        System.out.println("    [OK] OrderService 已被 JDK 动态代理");

        // 2. 真实调用被拦截的方法
        String result = orderService.placeOrder("Apple");

        // 3. 用切面内部计数器断言：三种通知都真实触发、计时真实发生
        LoggingAspect aspect = ctx.getBean("loggingAspect", LoggingAspect.class);
        check(aspect.beforeCount() >= 1, "@Before 未触发");
        check(aspect.afterCount() >= 1, "@After 未触发");
        check(aspect.aroundCount() >= 1, "@Around 未触发");
        check(aspect.aroundCostNanos() > 0, "@Around 计时未生效");
        check("已下单[Apple]".equals(result), "目标方法返回值不正确");

        System.out.println("    [OK] @Before 触发 " + aspect.beforeCount() + " 次");
        System.out.println("    [OK] @After 触发 " + aspect.afterCount() + " 次");
        System.out.println("    [OK] @Around 触发 " + aspect.aroundCount() + " 次，累计耗时 " + aspect.aroundCostNanos() + " ns");
        System.out.println("    [OK] 目标方法真实执行，返回值 = " + result);

        // 4. B1 回归：被代理方法抛异常时，拿到的是原始异常，而非被包装成 UndeclaredThrowableException
        try {
            orderService.failOrder();
            check(false, "failOrder 应抛出业务异常");
        } catch (IllegalStateException e) {
            check("下单失败(模拟业务异常)".equals(e.getMessage()),
                    "异常被包装(B1)，实际拿到 " + e.getClass().getName());
        }
        System.out.println("    [OK] B1 异常原样透传：failOrder 抛出原始 IllegalStateException");

        // 5. B3 回归：Object 方法(toString/hashCode/equals)不应触发切面
        int beforeObjectCalls = aspect.beforeCount() + aspect.afterCount() + aspect.aroundCount();
        orderService.toString();
        orderService.hashCode();
        orderService.equals(orderService);
        int afterObjectCalls = aspect.beforeCount() + aspect.afterCount() + aspect.aroundCount();
        check(beforeObjectCalls == afterObjectCalls, "Object 方法不应触发切面(B3)");
        System.out.println("    [OK] B3 Object 方法(toString/hashCode/equals)未触发切面");

        ctx.close();
        System.out.println("=== M3 落地验证通过 ===");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("M3 落地校验失败: " + message);
        }
    }
}