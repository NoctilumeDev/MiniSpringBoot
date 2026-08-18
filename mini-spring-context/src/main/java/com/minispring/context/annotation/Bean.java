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
}