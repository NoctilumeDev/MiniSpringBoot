package com.minispring.autoconfigure.condition;

import com.minispring.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当某个配置项满足指定值时才装配。
 *
 * <p>语义：{@code name()} 为配置键；{@code havingValue()} 非空则要求值完全相等，
 * 为空则「键存在即命中」；键不存在时按 {@code matchIfMissing()} 决定。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnPropertyCondition.class)
public @interface ConditionalOnProperty {

    String name();

    String havingValue() default "";

    boolean matchIfMissing() default false;
}