package com.minispring.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 条件装配入口：标在「配置类」或「{@code @Bean} 方法」上，声明一批 {@link Condition}。
 * 容器在登记前逐个求值，任一条件为 {@code false} 即跳过该组件。
 *
 * <p>Spring Boot 派生的 {@code @ConditionalOnClass}/{@code @ConditionalOnMissingBean} 等，
 * 本质都是「元注解本注解」——即它们的定义上写着 {@code @Conditional(某个 Condition.class)}。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Conditional {

    /** 全部满足（AND 语义）才注册。 */
    Class<? extends Condition>[] value();
}