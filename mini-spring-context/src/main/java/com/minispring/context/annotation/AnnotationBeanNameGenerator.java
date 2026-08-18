package com.minispring.context.annotation;

import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

/**
 * beanName 生成器：优先取组件注解上显式声明的 {@code value}，否则取「类名首字母小写」。
 *
 * <p>D24：不写死 {@code @Component/@Service/@Repository/@Configuration}，而是反扫类上所有
 * 「元注解带 {@link Component}」的组件注解、读其 {@code value()}。这样 web 层的
 * {@code @Controller("name")/@RestController("name")} 即便不在本模块、也无需反向依赖就能被识别。
 */
public class AnnotationBeanNameGenerator {

    public String generateBeanName(Class<?> beanClass) {
        String explicit = explicitName(beanClass);
        return explicit != null ? explicit : Introspector.decapitalize(beanClass.getSimpleName());
    }

    private String explicitName(Class<?> beanClass) {
        for (Annotation ann : beanClass.getAnnotations()) {
            Class<? extends Annotation> annType = ann.annotationType();
            // 内置元注解（@Target/@Retention/@Documented/@Inherited）不参与语义判断
            if (annType.getName().startsWith("java.lang.annotation.")) {
                continue;
            }
            if (!isComponentAnnotation(annType)) {
                continue;
            }
            String value = readValue(ann, annType);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /** 判断某个注解类型是否（元注解递归地）贴着 {@link Component}；带 visiting 防循环（B-2）。 */
    private boolean isComponentAnnotation(Class<? extends Annotation> annType) {
        return isComponentAnnotation(annType, new HashSet<>());
    }

    private boolean isComponentAnnotation(Class<? extends Annotation> annType, Set<Class<? extends Annotation>> visiting) {
        if (annType == Component.class) {
            return true;
        }
        // 内置元注解（@Target/@Retention/@Documented/@Inherited）不参与语义判断
        if (annType.getName().startsWith("java.lang.annotation.")) {
            return false;
        }
        if (!visiting.add(annType)) {
            return false; // @AnnA→@AnnB→@AnnA 循环互标：跳过而非无限递归
        }
        try {
            for (Annotation meta : annType.getAnnotations()) {
                if (isComponentAnnotation(meta.annotationType(), visiting)) {
                    return true;
                }
            }
        } finally {
            visiting.remove(annType);
        }
        return false;
    }

    private String readValue(Annotation ann, Class<? extends Annotation> annType) {
        try {
            Object value = annType.getMethod("value").invoke(ann);
            return value instanceof String ? (String) value : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}