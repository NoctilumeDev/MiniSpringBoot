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
    private final List<PropertyValue> propertyValues = new ArrayList<>();

    public BeanDefinition(Class<?> beanClass) {
        this.beanClass = beanClass;
    }

    public Class<?> getBeanClass() {
        return beanClass;
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

    public List<PropertyValue> getPropertyValues() {
        return propertyValues;
    }

    public void addPropertyValue(PropertyValue pv) {
        this.propertyValues.add(pv);
    }
}