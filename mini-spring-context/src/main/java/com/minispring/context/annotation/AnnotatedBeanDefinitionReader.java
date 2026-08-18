package com.minispring.context.annotation;

import com.minispring.core.BeanDefinition;
import com.minispring.core.BeanDefinitionRegistry;

import java.lang.reflect.Method;

/**
 * 配置类读取器：把 {@link Configuration} 类里的 {@link Bean} 方法，
 * 逐一翻译成「由工厂方法生产的 BeanDefinition」并登记进注册中心。
 *
 * <p>登记前会先过一遍方法级 {@link Conditional}：不命中的 {@code @Bean} 方法直接跳过——
 * 这正是 Spring Boot 自动配置「按需产生 Bean」的门禁之一。
 */
public class AnnotatedBeanDefinitionReader {

    private final BeanDefinitionRegistry registry;
    private final ConditionEvaluator conditionEvaluator;

    public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, ConditionEvaluator conditionEvaluator) {
        this.registry = registry;
        this.conditionEvaluator = conditionEvaluator;
    }

    /**
     * 注册 {@code configClass} 上所有 {@link Bean} 方法。
     *
     * @param configBeanName 配置类在容器中的 beanName（工厂 Bean 的名字）
     */
    public void registerBeanMethods(Class<?> configClass, String configBeanName) {
        for (Method method : configClass.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Bean.class)) {
                continue;
            }
            // 方法级条件：不命中则跳过该 @Bean
            if (conditionEvaluator != null && conditionEvaluator.shouldSkip(SimpleAnnotationMetadata.of(method))) {
                continue;
            }
            Bean bean = method.getAnnotation(Bean.class);
            String beanName = (bean.value() == null || bean.value().isEmpty()) ? method.getName() : bean.value();

            BeanDefinition bd = new BeanDefinition(method.getReturnType());
            bd.setFactoryBeanName(configBeanName);
            bd.setFactoryMethodName(method.getName());
            if (method.isAnnotationPresent(Scope.class)) {
                bd.setScope(method.getAnnotation(Scope.class).value());
            }
            registry.registerBeanDefinition(beanName, bd);
        }
    }
}