package com.minispring.web.mvc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 路径变量：从 URL 模板（如 {@code /users/{id}}）里取值的参数。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PathVariable {

    /** 变量名，对应 URL 模板里 {@code {name}} 中的 name。 */
    String value() default "";
}