package com.minispring.aop.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 前置通知：在目标方法执行「之前」运行。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Before {

    /** 切点表达式，如 {@code execution(* com.x.Y.*(..))}。 */
    String value();
}