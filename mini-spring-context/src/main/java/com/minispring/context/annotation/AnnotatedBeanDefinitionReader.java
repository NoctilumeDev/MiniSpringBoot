package com.minispring.context.annotation;

import com.minispring.core.BeanDefinition;
import com.minispring.core.BeanDefinitionRegistry;

import java.lang.reflect.Method;

/**
 * 配置类读取器：把 {@link Configuration} 类里的 {@link Bean} 方法，
 * 逐一翻译成「由工厂方法生产的 BeanDefinition」并登记进注册中心。
 */
public class AnnotatedBeanDefinitionReader {

    private final BeanDefinitionRegistry registry;

    public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry) {
        this.registry = registry;
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