package com.minispring.autoconfigure.condition;

import com.minispring.context.annotation.AnnotatedTypeMetadata;
import com.minispring.context.annotation.Condition;
import com.minispring.context.annotation.ConditionContext;

import java.util.Map;

/**
 * 配置项判定：按 {@link ConditionalOnProperty} 的 name / havingValue / matchIfMissing 裁决。
 */
public class OnPropertyCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attrs = metadata.getAnnotationAttributes(ConditionalOnProperty.class);
        String name = (String) attrs.get("name");
        String havingValue = (String) attrs.get("havingValue");
        boolean matchIfMissing = (Boolean) attrs.get("matchIfMissing");

        String value = context.getEnvironment().getProperty(name);
        if (value == null) {
            return matchIfMissing;
        }
        if (havingValue.isEmpty()) {
            return true;
        }
        return havingValue.equals(value);
    }
}