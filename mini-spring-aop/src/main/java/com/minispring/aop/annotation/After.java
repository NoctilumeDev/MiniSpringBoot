package com.minispring.aop.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 后置通知：与 Spring 的 {@code @After} 一致，为 finally 语义——
 * 无论目标方法正常返回还是抛出异常，本通知都会执行（D4 修正注释）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface After {

    /** 切点表达式。 */
    String value();
}