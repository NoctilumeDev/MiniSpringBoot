package com.minispring.context.annotation;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

/**
 * 被「条件 / 导入选择器」判定的元素上的标注信息快照。
 *
 * <p>屏蔽「是类还是方法」的差异：条件只需关心「有没有某个注解、它的属性是什么」。
 * 查找范围含<b>元注解</b>——例如 {@code @ConditionalOnClass} 上标着 {@code @Conditional}，
 * 对 {@code @Conditional} 的查询也能命中。
 */
public interface AnnotatedTypeMetadata {

    /** 当前元素（或其元注解）上是否标注了 {@code annotationType}。 */
    boolean isAnnotated(Class<? extends Annotation> annotationType);

    /**
     * 返回 {@code annotationType} 的属性（属性名 → 值）；未找到返回空 Map。
     * 属性值通过注解实例反射取值（{@code String[]} / {@code Class[]} / {@code boolean} 等原样保留）。
     */
    Map<String, Object> getAnnotationAttributes(Class<? extends Annotation> annotationType);

    /**
     * 返回元素（含元注解，递归）上标注的<b>全部</b> {@code annotationType} 实例。
     *
     * <p>与 {@link #getAnnotationAttributes}（只取第一个命中）的分工：条件求值需要
     * 「一个元素上多个派生注解各自携带的 {@code @Conditional}」全部参与 AND——
     * 例如 {@code @ConditionalOnClass} + {@code @ConditionalOnBean} 同标一个类时，
     * 两个条件都必须参与求值。
     */
    List<Annotation> findAnnotations(Class<? extends Annotation> annotationType);

    /**
     * 被判定元素所属的类：类级元素即该类自身，方法级元素即其声明类。
     */
    Class<?> getIntrospectedClass();

    /**
     * {@code @Bean} 方法的返回类型，用作 {@code @ConditionalOnMissingBean} 缺省类型；
     * 类级元素恒为 {@code null}。
     */
    default Class<?> getExpectedType() {
        return null;
    }
}
