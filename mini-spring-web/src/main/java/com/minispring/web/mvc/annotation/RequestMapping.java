package com.minispring.web.mvc.annotation;

import com.minispring.web.http.HttpMethod;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求映射：把「路径 + HTTP 方法」绑定到一个处理器方法（类级则作为方法级路径的前缀）。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestMapping {

    /** 路径；与 {@link #path()} 互为别名。 */
    String value() default "";

    /** 路径；与 {@link #value()} 互为别名。 */
    String path() default "";

    /** 限制的 HTTP 方法；为空表示不限制。 */
    HttpMethod[] method() default {};
}