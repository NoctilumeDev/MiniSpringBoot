package com.minispring.context.annotation;

/**
 * 条件：决定一个「配置类 / Bean 方法」当前环境下是否应被注册。
 *
 * <p>配合 {@link Conditional} 使用：由 {@link Conditional#value()} 指定实现类，
 * 容器在登记前逐个求值，任一条件不满足即整体跳过。这是 Spring Boot 自动配置「按需装配」的基石。
 */
@FunctionalInterface
public interface Condition {

    /**
     * 返回 {@code true} 表示条件命中（该组件应当注册）。
     *
     * @param context  条件上下文，可访问注册中心 / Bean 工厂 / 环境 / 类加载器
     * @param metadata 被判定元素的标注信息（类或方法，含元注解与 {@code @Bean} 返回类型）
     */
    boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);
}