package com.minispring.context.annotation;

import java.beans.Introspector;

/**
 * beanName 生成器：优先取注解上显式声明的 {@code value}，否则取「类名首字母小写」。
 */
public class AnnotationBeanNameGenerator {

    public String generateBeanName(Class<?> beanClass) {
        String explicit = explicitName(beanClass);
        return explicit != null ? explicit : Introspector.decapitalize(beanClass.getSimpleName());
    }

    /** 从组件注解族里读显式 beanName（@Component/@Service/@Repository/@Configuration 的 value）。 */
    private String explicitName(Class<?> beanClass) {
        Component c = beanClass.getAnnotation(Component.class);
        if (c != null && !c.value().isEmpty()) {
            return c.value();
        }
        Service s = beanClass.getAnnotation(Service.class);
        if (s != null && !s.value().isEmpty()) {
            return s.value();
        }
        Repository r = beanClass.getAnnotation(Repository.class);
        if (r != null && !r.value().isEmpty()) {
            return r.value();
        }
        Configuration cfg = beanClass.getAnnotation(Configuration.class);
        if (cfg != null && !cfg.value().isEmpty()) {
            return cfg.value();
        }
        return null;
    }
}