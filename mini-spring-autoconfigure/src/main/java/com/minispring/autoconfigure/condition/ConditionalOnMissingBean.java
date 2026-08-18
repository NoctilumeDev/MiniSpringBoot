package com.minispring.autoconfigure.condition;

import com.minispring.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当容器中<b>不存在</b>指定 Bean（按类型或名字）时才装配。
 *
 * <p>标在 {@code @Bean} 方法上且不写 value/name 时，默认以<b>方法返回类型</b>为判定类型——
 * 「用户没定义，我才提供默认实现」，是自动配置「可被用户覆盖」的关键。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnBeanCondition.class)
public @interface ConditionalOnMissingBean {

    Class<?>[] value() default {};

    String[] name() default {};
}