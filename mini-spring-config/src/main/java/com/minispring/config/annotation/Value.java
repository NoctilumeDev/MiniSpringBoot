package com.minispring.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 把 {@code Environment} 中的某个配置值注入字段。
 *
 * <p>支持 {@code ${key}}（找不到则报错）与 {@code ${key:default}}（找不到用默认值）。
 * SpEL（{@code #{...}}）不在本阶段主线，暂不支持。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Value {

    String value();
}