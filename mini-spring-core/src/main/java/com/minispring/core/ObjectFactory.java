package com.minispring.core;

/**
 * 能「产出」对象的工厂，用于三级缓存里提前暴露半成品（将来可能用于生成代理）。
 */
@FunctionalInterface
public interface ObjectFactory<T> {

    T getObject();
}