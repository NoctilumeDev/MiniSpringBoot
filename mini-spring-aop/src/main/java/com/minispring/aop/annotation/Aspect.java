package com.minispring.aop.annotation;

import com.minispring.context.annotation.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「切面类」：里面被 {@link Before}/{@link After}/{@link Around} 标注的方法即横切逻辑。
 *
 * <p>同时它也是一个 {@link Component}（元注解），所以切面类同样会被组件扫描收编进容器，
 * AOP 代理创建器再从容器里把它们挑出来解析成一个个 Advisor。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Aspect {
}