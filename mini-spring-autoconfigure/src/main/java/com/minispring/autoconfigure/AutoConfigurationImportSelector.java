package com.minispring.autoconfigure;

import com.minispring.context.annotation.AnnotatedTypeMetadata;
import com.minispring.context.annotation.DeferredImportSelector;
import com.minispring.core.Ordered;
import com.minispring.core.annotation.Order;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自动配置导入选择器：从 classpath 的所有 SPI 文件里读出候选自动配置类名，去重并按
 * {@link AutoConfigureOrder} / {@link Order} 排序后返回（D20）。
 *
 * <p>实现为 {@link DeferredImportSelector}：它被<b>延迟</b>到用户自己的配置与组件扫描全部落地之后执行，
 * 从而保证 {@code @ConditionalOnMissingBean} 能判断「用户是否已提供同类型 Bean」，实现「用户优先、自动兜底」。
 */
public class AutoConfigurationImportSelector implements DeferredImportSelector {

    @Override
    public String[] selectImports(AnnotatedTypeMetadata importingClassMetadata) {
        // 自动配置候选只取决于 classpath，与触发它的入口类无关，故忽略 metadata
        String[] classNames = AutoConfigurationLoader.load(AutoConfigurationImportSelector.class.getClassLoader());
        return deduplicateAndSort(classNames);
    }

    /** 去重（保序）后按「@AutoConfigureOrder → @Order → 最低优先级」升序排列。 */
    private static String[] deduplicateAndSort(String[] classNames) {
        Map<String, Integer> orderByClass = new LinkedHashMap<>();
        for (String className : classNames) {
            orderByClass.putIfAbsent(className, resolveOrder(className));
        }
        return orderByClass.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toArray(String[]::new);
    }

    private static int resolveOrder(String className) {
        try {
            Class<?> clazz = Class.forName(className, false, AutoConfigurationImportSelector.class.getClassLoader());
            AutoConfigureOrder autoOrder = clazz.getAnnotation(AutoConfigureOrder.class);
            if (autoOrder != null) {
                return autoOrder.value();
            }
            Order order = clazz.getAnnotation(Order.class);
            if (order != null) {
                return order.value();
            }
        } catch (Throwable ignored) {
            // 类或它的注解引用了缺失类型：排序阶段不阻断，交由条件装配/注册阶段按需裁决
        }
        return Ordered.LOWEST_PRECEDENCE;
    }
}