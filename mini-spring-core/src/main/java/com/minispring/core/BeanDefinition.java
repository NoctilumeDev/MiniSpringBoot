package com.minispring.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Bean 的「图纸」：描述一个 Bean 的类、作用域、依赖与生命周期回调。
 *
 * <p>容器里流转的从来不是对象，而是对象的「图纸」——有了图纸，容器才能在合适的时机「施工」（实例化）。
 */
public class BeanDefinition {

    public static final String SCOPE_SINGLETON = "singleton";
    public static final String SCOPE_PROTOTYPE = "prototype";

    private final Class<?> beanClass;
    private String scope = SCOPE_SINGLETON;
    private String initMethodName;
    private String destroyMethodName;
    private String factoryBeanName;
    private String factoryMethodName;
    private boolean primary;
    private String qualifier;
    private final List<PropertyValue> propertyValues = new ArrayList<>();

    public BeanDefinition(Class<?> beanClass) {
        this.beanClass = beanClass;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    /** 该 Bean 由哪个工厂 Bean（如 @Configuration 类）生产；为空表示直接 new。 */
    public String getFactoryBeanName() {
        return factoryBeanName;
    }

    public void setFactoryBeanName(String factoryBeanName) {
        this.factoryBeanName = factoryBeanName;
    }

    /** 该 Bean 由工厂方法的哪个方法生产（对应 @Bean 方法名）。 */
    public String getFactoryMethodName() {
        return factoryMethodName;
    }

    public void setFactoryMethodName(String factoryMethodName) {
        this.factoryMethodName = factoryMethodName;
    }

    public boolean isFactoryMethod() {
        return factoryMethodName != null;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public boolean isSingleton() {
        return SCOPE_SINGLETON.equals(scope);
    }

    public String getInitMethodName() {
        return initMethodName;
    }

    public void setInitMethodName(String initMethodName) {
        this.initMethodName = initMethodName;
    }

    public String getDestroyMethodName() {
        return destroyMethodName;
    }

    public void setDestroyMethodName(String destroyMethodName) {
        this.destroyMethodName = destroyMethodName;
    }

    /** 是否为「多候选」场景下的优先 Bean（对应 {@code @Primary}，可来自类或 {@code @Bean} 方法）。 */
    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    /** 该 Bean 的限定名（对应 {@code @Qualifier} 的 value），用于按名裁决多候选注入。 */
    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    public List<PropertyValue> getPropertyValues() {
        return propertyValues;
    }

    public void addPropertyValue(PropertyValue pv) {
        this.propertyValues.add(pv);
    }
}