package com.minispring.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 指定要扫描的包。放在入口配置类上，告诉容器「去哪些包下面找组件」。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ComponentScan {

    /** 要扫描的包列表；为空则默认扫描「标注了本注解的类」所在包。 */
    String[] basePackages() default {};
}