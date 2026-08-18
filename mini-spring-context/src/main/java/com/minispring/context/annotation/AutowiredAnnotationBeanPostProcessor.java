package com.minispring.context.annotation;

import com.minispring.core.BeanDefinition;
import com.minispring.core.BeanDefinitionRegistry;
import com.minispring.core.BeansException;
import com.minispring.core.InstantiationAwareBeanPostProcessor;
import com.minispring.core.ListableBeanFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Autowired} 注入处理器：在「属性填充」阶段，把被 {@link Autowired} 标注的字段真正的依赖塞进去。
 *
 * <p>解析优先级：{@link Qualifier}（按名）→ 唯一类型匹配 → {@link Primary}（多候选裁决）。
 */
public class AutowiredAnnotationBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

    private final ListableBeanFactory beanFactory;
    private final BeanDefinitionRegistry registry;

    public AutowiredAnnotationBeanPostProcessor(ListableBeanFactory beanFactory, BeanDefinitionRegistry registry) {
        this.beanFactory = beanFactory;
        this.registry = registry;
    }

    @Override
    public void postProcessProperties(Object bean, String beanName) {
        injectFields(bean, beanName);
    }

    private void injectFields(Object bean, String beanName) {
        for (Field field : findAllFields(bean.getClass())) {
            if (!field.isAnnotationPresent(Autowired.class)) {
                continue;
            }
            Object value = resolveValue(field, field.getType(), beanName);
            field.setAccessible(true);
            try {
                field.set(bean, value);
            } catch (IllegalAccessException e) {
                throw new BeansException("注入字段[" + beanName + "." + field.getName() + "]失败", e);
            }
        }
    }

    private Object resolveValue(Field field, Class<?> requiredType, String beanName) {
        // 1. @Qualifier 指名道姓，直接按名拿
        if (field.isAnnotationPresent(Qualifier.class)) {
            String name = field.getAnnotation(Qualifier.class).value();
            if (!name.isEmpty()) {
                return beanFactory.getBean(name);
            }
        }
        // 2. 按类型匹配
        String[] candidates = beanFactory.getBeanNamesForType(requiredType);
        if (candidates.length == 1) {
            return beanFactory.getBean(candidates[0]);
        }
        if (candidates.length > 1) {
            return resolveByPrimary(candidates, requiredType, beanName, field.getName());
        }
        // 3. 没有候选
        if (field.getAnnotation(Autowired.class).required()) {
            throw new BeansException("注入[" + beanName + "." + field.getName() + "]失败：找不到类型 " + requiredType.getName() + " 的 Bean");
        }
        return null;
    }

    private Object resolveByPrimary(String[] candidates, Class<?> requiredType, String beanName, String fieldName) {
        String primary = null;
        for (String name : candidates) {
            BeanDefinition bd = registry.getBeanDefinition(name);
            // 统一看 BeanDefinition.isPrimary()——既覆盖类级 @Primary，也覆盖 @Bean 方法级 @Primary（D23）
            if (bd.isPrimary()) {
                if (primary != null) {
                    throw new BeansException("注入[" + beanName + "." + fieldName + "]失败：存在多个 @Primary 候选");
                }
                primary = name;
            }
        }
        if (primary != null) {
            return beanFactory.getBean(primary);
        }
        throw new BeansException("注入[" + beanName + "." + fieldName + "]失败：" + requiredType.getName()
                + " 有多个候选，请用 @Qualifier 或 @Primary 拍板");
    }

    /** 收集类及其父类的所有字段（继承注入也需要照顾）。 */
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