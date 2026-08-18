package com.minispring.context.annotation;

/**
 * 延迟导入选择器：不立即执行，而是等<b>用户自己的配置 + 组件扫描全部落地之后</b>再执行。
 *
 * <p>这是 Spring Boot 自动配置的排序保证：用户 Bean 先注册，自动配置后注册，
 * 于是 {@code @ConditionalOnMissingBean} 才能正确判断「用户是否已提供」，实现「用户优先、自动兜底」。
 */
public interface DeferredImportSelector extends ImportSelector {
}