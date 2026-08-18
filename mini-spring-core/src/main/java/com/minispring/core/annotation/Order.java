package com.minispring.core.annotation;

import com.minispring.core.Ordered;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明优先级：可与 {@link Ordered} 配合，让 AOP 通知、自动配置类等按「谁先谁后」被确定性地排序。
 *
 * <p>{@link #value()} 越小越靠前；缺省为 {@link Ordered#LOWEST_PRECEDENCE}。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Order {

    int value() default Ordered.LOWEST_PRECEDENCE;
}