package com.minispring.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「这里需要注入一个依赖」。可标注在字段、构造器、方法或参数上。
 * 默认按类型注入；配合 {@link Qualifier} 可改为按名字；多个候选时用 {@link Primary} 拍板。
 */
@Target({ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Autowired {

    /** 依赖是否必须。为 true 时找不到候选会直接抛异常。 */
    boolean required() default true;
}