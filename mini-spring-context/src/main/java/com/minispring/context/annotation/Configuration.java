package com.minispring.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「配置类」：它也是 {@link Component}，但多了一层职责——
 * 其中被 {@link Bean} 标注的方法，会被容器识别为「生产 Bean 的工厂方法」。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Configuration {

    String value() default "";
}