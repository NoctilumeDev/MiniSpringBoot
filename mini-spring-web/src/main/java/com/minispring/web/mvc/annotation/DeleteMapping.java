package com.minispring.web.mvc.annotation;

import com.minispring.web.http.HttpMethod;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * DELETE 请求映射，等价于 {@code @RequestMapping(method = HttpMethod.DELETE)} 的简写（M8 CRUD 补齐）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequestMapping(method = HttpMethod.DELETE)
public @interface DeleteMapping {

    String value() default "";
}
