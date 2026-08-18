package com.minispring.core;

/**
 * 一个待注入的属性（依赖项）。
 *
 * <p>{@code name} 是字段名；{@code value} 既可能是直接值，也可能是另一个 Bean 的名称（引用）。
 */
public class PropertyValue {

    private final String name;
    private final Object value;
    private final boolean isRef;

    /** 直接值（非引用）。 */
    public PropertyValue(String name, Object value) {
        this(name, value, false);
    }

    /** 引用另一个 Bean：value 传目标 Bean 的名称。 */
    public static PropertyValue ref(String name, String refBeanName) {
        return new PropertyValue(name, refBeanName, true);
    }

    private PropertyValue(String name, Object value, boolean isRef) {
        this.name = name;
        this.value = value;
        this.isRef = isRef;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    public boolean isRef() {
        return isRef;
    }
}