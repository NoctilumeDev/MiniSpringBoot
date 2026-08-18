package com.minispring.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 把类标记为「候选组件」，交给容器扫描管理。是所有「派生注解」的根：
 * {@code @Service}、{@code @Repository}、{@code @Configuration} 都标注了它，
 * 扫描器会顺着元注解一路往上找，把它们识别成组件。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Component {

    /** 显式指定 beanName；为空则由容器按类名首字母小写生成。 */
    String value() default "";
}