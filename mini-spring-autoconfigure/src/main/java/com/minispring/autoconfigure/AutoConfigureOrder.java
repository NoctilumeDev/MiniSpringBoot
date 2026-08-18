package com.minispring.autoconfigure;

import com.minispring.core.Ordered;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明自动配置类的装配优先级（数值越小越先执行）。
 *
 * <p>标在自动配置类上，供 {@link AutoConfigurationImportSelector} 在装载后统一排序；
 * 通常不用，只有当两个自动配置之间存在先后依赖时（如 AOP 代理器需要先于 MVC 就位）才显式指定。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoConfigureOrder {

    int value() default Ordered.LOWEST_PRECEDENCE;
}