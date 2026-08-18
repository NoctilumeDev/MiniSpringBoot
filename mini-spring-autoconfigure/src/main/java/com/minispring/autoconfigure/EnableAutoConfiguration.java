package com.minispring.autoconfigure;

import com.minispring.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 开启自动配置。标注在应用入口（或任意配置类）上即可。
 *
 * <p>本质上是 {@link Import}{@code (AutoConfigurationImportSelector.class)} 的语义别名：
 * 容器看到它，会去 classpath 上收集所有 SPI 文件里声明的候选配置类，再交给 @Conditional 机制逐个裁决。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AutoConfigurationImportSelector.class)
public @interface EnableAutoConfiguration {
}