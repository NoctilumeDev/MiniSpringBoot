package com.minispring.autoconfigure.condition;

import com.minispring.context.annotation.AnnotatedTypeMetadata;
import com.minispring.context.annotation.Condition;
import com.minispring.context.annotation.ConditionContext;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Bean 存在性判定：同时服务 {@link ConditionalOnBean}（存在才装配）与
 * {@link ConditionalOnMissingBean}（不存在才装配，语义取反）。
 */
public class OnBeanCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean missing = metadata.isAnnotated(ConditionalOnMissingBean.class);
        Class<? extends Annotation> annotationType = missing ? ConditionalOnMissingBean.class : ConditionalOnBean.class;

        Map<String, Object> attrs = metadata.getAnnotationAttributes(annotationType);
        String[] names = (String[]) attrs.get("name");
        Class<?>[] types = (Class<?>[]) attrs.get("value");

        boolean anyPresent = isAnyPresent(context, names, types, metadata);
        return missing ? !anyPresent : anyPresent;
    }

    private boolean isAnyPresent(ConditionContext context, String[] names, Class<?>[] types, AnnotatedTypeMetadata metadata) {
        for (String name : names) {
            if (context.getRegistry().containsBeanDefinition(name)) {
                return true;
            }
        }
        for (Class<?> type : types) {
            if (context.getBeanFactory().getBeanNamesForType(type).length > 0) {
                return true;
            }
        }
        // 未显式指定名字/类型：落到 @Bean 方法的返回类型上（类级元素则无期望类型，视为「无物可判」）
        if (names.length == 0 && types.length == 0) {
            Class<?> expected = metadata.getExpectedType();
            return expected != null && context.getBeanFactory().getBeanNamesForType(expected).length > 0;
        }
        return false;
    }
}