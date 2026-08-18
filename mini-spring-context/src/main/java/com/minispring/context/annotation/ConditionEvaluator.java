package com.minispring.context.annotation;

import com.minispring.core.BeansException;

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
     * @return {@code true} 表示该组件应跳过（不注册）；只要有一个条件不命中即跳过（AND 语义）。
     */
    boolean shouldSkip(AnnotatedTypeMetadata metadata) {
        if (!metadata.isAnnotated(Conditional.class)) {
            return false;
        }
        Class<?>[] conditionTypes = (Class<?>[]) metadata.getAnnotationAttributes(Conditional.class).get("value");
        for (Class<?> conditionType : conditionTypes) {
            Condition condition = instantiate(conditionType);
            if (!condition.matches(context, metadata)) {
                return true;
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