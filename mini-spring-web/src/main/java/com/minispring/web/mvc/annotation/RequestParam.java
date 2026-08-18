package com.minispring.web.mvc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 查询参数：从 URL 的 query 部分（{@code ?name=xxx}）取值的参数。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestParam {

    String value() default "";

    /** 缺省且无默认值时是否报错。 */
    boolean required() default true;

    /** 缺省时使用的默认值（空字符串表示「无默认值」）。 */
    String defaultValue() default "";
}