package com.minispring.autoconfigure.condition;

import com.minispring.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当指定类<b>在 classpath 上存在</b>时才装配。
 *
 * <p>{@code value()} 用类字面量（编译期即需存在）；{@code name()} 用字符串（可判断「可能不存在」的类，
 * 且不会触发类加载 / 静态初始化）。这是 Spring Boot 自动配置的「看菜下饭」：引入依赖 → 相关配置自动生效。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnClassCondition.class)
public @interface ConditionalOnClass {

    Class<?>[] value() default {};

    String[] name() default {};
}