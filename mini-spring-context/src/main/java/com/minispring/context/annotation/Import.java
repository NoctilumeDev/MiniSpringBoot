package com.minispring.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 把外部的「配置类」或「导入选择器」拉进当前应用上下文。
 *
 * <p>Spring Boot 的 {@code @EnableAutoConfiguration} 就是靠 {@code @Import(AutoConfigurationImportSelector.class)}
 * 触发自动配置的。这里支持两种值：
 * <ul>
 *   <li>{@link ImportSelector}：运行时返回一批类名（{@link DeferredImportSelector} 会被延迟到用户配置之后处理）；</li>
 *   <li>普通 {@link Configuration} 类：直接作为配置类注册。</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Import {

    Class<?>[] value();
}