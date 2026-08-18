package com.minispring.context.annotation;

import com.minispring.core.BeansException;

import java.util.List;

/**
 * 条件求值器：把一个 {@link AnnotatedTypeMetadata} 上的 {@link Conditional} 声明跑一遍，
 * 得出「该不该跳过」的结论。所有 {@code @ConditionalOnXXX} 派生注解最终都汇到这一处。
 */
class ConditionEvaluator {

    private final ConditionContext context;

    ConditionEvaluator(ConditionContext context) {
        this.context = context;
    }

    /**
     * @return {@code true} 表示该组件应跳过（不注册）。
     *
     * <p>M8 修复：元素上可能有<b>多个</b>派生注解各自携带 {@code @Conditional}
     * （如 {@code @ConditionalOnClass} + {@code @ConditionalOnBean} 同标一个类）——
     * 此前只取第一个命中的 {@code @Conditional} 求值，后续条件被静默忽略；
     * 现在收集全部实例逐一 AND，任一不命中即跳过。
     */
    boolean shouldSkip(AnnotatedTypeMetadata metadata) {
        List<java.lang.annotation.Annotation> conditionals = metadata.findAnnotations(Conditional.class);
        if (conditionals.isEmpty()) {
            return false;
        }
        for (java.lang.annotation.Annotation conditional : conditionals) {
            Class<?>[] conditionTypes;
            try {
                conditionTypes = (Class<?>[]) Conditional.class.getMethod("value").invoke(conditional);
            } catch (ReflectiveOperationException e) {
                throw new BeansException("读取 @Conditional value 失败", e);
            }
            for (Class<?> conditionType : conditionTypes) {
                Condition condition = instantiate(conditionType);
                if (!condition.matches(context, metadata)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Condition instantiate(Class<?> conditionType) {
        if (!Condition.class.isAssignableFrom(conditionType)) {
            throw new BeansException("[" + conditionType.getName() + "] 未实现 " + Condition.class.getName());
        }
        try {
            return (Condition) conditionType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new BeansException("实例化条件[" + conditionType.getName() + "]失败", e);
        }
    }
}