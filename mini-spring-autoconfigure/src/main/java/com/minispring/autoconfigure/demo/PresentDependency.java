package com.minispring.autoconfigure.demo;

/**
 * 「依赖已就位」的占位类：仅作为 classpath 上存在的标记，
 * 供 {@code @ConditionalOnClass(PresentDependency.class)} 命中判断。
 */
public class PresentDependency {
}