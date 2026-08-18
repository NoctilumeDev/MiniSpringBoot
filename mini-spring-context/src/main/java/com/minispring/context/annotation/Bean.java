package com.minispring.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 把「某个方法的返回值」注册成容器里的一个 Bean。只用在 {@link Configuration} 类的方法上。
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Bean {

    /** 显式指定 beanName；为空则用方法名作为 beanName。 */
    String value() default "";

    /**
     * 初始化回调方法名（无参）：Bean 属性填充完成后调用（在 {@code InitializingBean} 之后）。
     * D2 收口于 M8——DataSource 这类需显式生命周期控制的 Bean 依赖它。
     */
    String initMethod() default "";

    /**
     * 销毁回调方法名（无参）：容器关闭时调用。典型用途：{@code HikariDataSource.close()}
     * 释放连接池（不配则池线程泄漏、JVM 挂着退不干净）。
     */
    String destroyMethod() default "";
}