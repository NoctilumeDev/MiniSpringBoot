package com.minispring.starter.demo;

import com.minispring.context.annotation.AnnotationConfigApplicationContext;

/**
 * M6 演示 Starter 落地：真实启动，证明「引入 starter 后自动装配 FormatService」。
 *
 * <p>关键点：{@link StarterApplication} 没写任何 {@code @Bean}，{@code FormatService} 完全由
 * 本 starter 的 SPI 文件里的 {@link FormatAutoConfiguration} 经 @EnableAutoConfiguration 装配而来。
 */
public class StarterDemo {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(StarterApplication.class);

        System.out.println("=== M6 演示 Starter 落地 ===");
        FormatService formatService = context.getBean("formatService", FormatService.class);
        System.out.println("formatService 实现: " + formatService.getClass().getSimpleName());
        System.out.println("format(\"hello\") -> " + formatService.format("hello"));

        assertTrue("formatService 应由 starter 自动装配（非显式注册）", formatService instanceof UpperCaseFormatService);
        assertTrue("format 行为应正确（转大写）", "HELLO".equals(formatService.format("hello")));
        System.out.println("Starter 自动装配断言通过。");
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new IllegalStateException("断言失败: " + label);
        }
        System.out.println("  [PASS] " + label);
    }
}