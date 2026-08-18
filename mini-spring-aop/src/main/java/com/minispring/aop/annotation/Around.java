package com.minispring.aop.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 环绕通知：能完全掌控目标方法的执行——执行前 / 执行中 / 执行后都插得上手。
 * 方法签名约定为 {@code Object around(ProceedingJoinPoint pjp)}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Around {

    /** 切点表达式。 */
    String value();
}