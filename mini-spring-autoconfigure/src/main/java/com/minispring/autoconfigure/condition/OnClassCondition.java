package com.minispring.autoconfigure.condition;

import com.minispring.context.annotation.AnnotatedTypeMetadata;
import com.minispring.context.annotation.Condition;
import com.minispring.context.annotation.ConditionContext;

/**
 * 「类是否在 classpath」的判定。用 {@code Class.forName(name, false, cl)} 仅做存在性检查，
 * 不触发类的静态初始化，避免为了一次判断而执行第三方库的重量级初始化。
 */
public class OnClassCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        java.util.Map<String, Object> attrs = metadata.getAnnotationAttributes(ConditionalOnClass.class);
        ClassLoader classLoader = context.getClassLoader();

        String[] names = (String[]) attrs.get("name");
        for (String name : names) {
            if (!isPresent(name, classLoader)) {
                return false;
            }
        }
        Class<?>[] types = (Class<?>[]) attrs.get("value");
        for (Class<?> type : types) {
            if (!isPresent(type.getName(), classLoader)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPresent(String className, ClassLoader classLoader) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}