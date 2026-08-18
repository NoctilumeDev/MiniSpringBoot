package com.minispring.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务层组件。它本身不携带任何逻辑，只是一个「表意更清晰」的 {@link Component} 别名。
 * 扫描器看到它标注了 {@link Component}（元注解），就把它当组件收编。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Service {

    String value() default "";
}