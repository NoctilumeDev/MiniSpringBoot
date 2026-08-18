package com.minispring.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「这里需要注入一个依赖」。可标注在字段、构造器、方法或参数上（A-4/D3 收口：四种位置均已落地）。
 *
 * <ul>
 *   <li>字段 / 方法：populateBean 阶段注入；{@code required=false} 时依赖缺失则注入 null / 跳过整个方法；</li>
 *   <li>构造器：实例化前选出唯一 {@code @Autowired} 构造器并解析参数（构造器循环依赖会得到可读错误）；</li>
 *   <li>参数：配合容器解析 {@code required}（缺省注入 null 需显式标 {@code required=false}）。</li>
 * </ul>
 * 默认按类型注入；配合 {@link Qualifier} 可改为按名字；多个候选时用 {@link Primary} 拍板。
 */
@Target({ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Autowired {

    /** 依赖是否必须。为 true 时找不到候选会直接抛异常。 */
    boolean required() default true;
}