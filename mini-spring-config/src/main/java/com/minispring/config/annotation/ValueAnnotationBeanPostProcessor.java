package com.minispring.config.annotation;

import com.minispring.config.support.SimpleTypeConverter;
import com.minispring.core.BeansException;
import com.minispring.core.EnvironmentAware;
import com.minispring.core.InstantiationAwareBeanPostProcessor;
import com.minispring.core.env.Environment;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Value} 注入处理器：在「属性填充」阶段，把 {@code ${key}} 占位符解析成环境里的值，
 * 再做类型转换注入字段。处理器本身通过 {@link EnvironmentAware} 拿到 {@link Environment}。
 */
public class ValueAnnotationBeanPostProcessor implements InstantiationAwareBeanPostProcessor, EnvironmentAware {

    private Environment environment;
    private final SimpleTypeConverter converter = new SimpleTypeConverter();

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessProperties(Object bean, String beanName) {
        for (Field field : findAllFields(bean.getClass())) {
            Value value = field.getAnnotation(Value.class);
            if (value == null) {
                continue;
            }
            String resolved = environment.resolvePlaceholders(value.value());
            Object converted = converter.convert(resolved, field.getType());
            field.setAccessible(true);
            try {
                field.set(bean, converted);
            } catch (IllegalAccessException e) {
                throw new BeansException("注入 @Value 字段[" + beanName + "." + field.getName() + "]失败", e);
            }
        }
    }

    private List<Field> findAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }
}