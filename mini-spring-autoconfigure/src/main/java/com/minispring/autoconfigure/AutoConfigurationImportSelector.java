package com.minispring.autoconfigure;

import com.minispring.context.annotation.AnnotatedTypeMetadata;
import com.minispring.context.annotation.DeferredImportSelector;

/**
 * 自动配置导入选择器：从 classpath 的所有 SPI 文件里读出候选自动配置类名。
 *
 * <p>实现为 {@link DeferredImportSelector}：它被<b>延迟</b>到用户自己的配置与组件扫描全部落地之后执行，
 * 从而保证 {@code @ConditionalOnMissingBean} 能判断「用户是否已提供同类型 Bean」，实现「用户优先、自动兜底」。
 */
public class AutoConfigurationImportSelector implements DeferredImportSelector {

    @Override
    public String[] selectImports(AnnotatedTypeMetadata importingClassMetadata) {
        // 自动配置候选只取决于 classpath，与触发它的入口类无关，故忽略 metadata
        return AutoConfigurationLoader.load(AutoConfigurationImportSelector.class.getClassLoader());
    }
}