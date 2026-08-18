package com.minispring.context.annotation;

import com.minispring.core.BeansException;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link AnnotatedTypeMetadata} 的默认真实实现：统一包装「类」与「方法」。
 *
 * <p>注解查找支持<b>元注解</b>：例如 {@code @ConditionalOnClass} 上标着 {@code @Conditional}，
 * 对 {@code @Conditional} 的查询会命中原注解。这样「派生注解」机制才能成立。
 */
final class SimpleAnnotationMetadata implements AnnotatedTypeMetadata {

    private final AnnotatedElement element;
    private final Class<?> introspectedClass;
    private final Class<?> expectedType;

    private SimpleAnnotationMetadata(AnnotatedElement element, Class<?> introspectedClass, Class<?> expectedType) {
        this.element = element;
        this.introspectedClass = introspectedClass;
        this.expectedType = expectedType;
    }

    /** 类级元数据（配置类 / 组件类）。 */
    static AnnotatedTypeMetadata of(Class<?> clazz) {
        return new SimpleAnnotationMetadata(clazz, clazz, null);
    }

    /** 方法级元数据（{@code @Bean} 方法），期望类型取方法返回类型。 */
    static AnnotatedTypeMetadata of(Method method) {
        return new SimpleAnnotationMetadata(method, method.getDeclaringClass(), method.getReturnType());
    }

    @Override
    public boolean isAnnotated(Class<? extends Annotation> annotationType) {
        return findAnnotation(element, annotationType) != null;
    }

    @Override
    public Map<String, Object> getAnnotationAttributes(Class<? extends Annotation> annotationType) {
        Annotation annotation = findAnnotation(element, annotationType);
        if (annotation == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (Method attribute : annotationType.getDeclaredMethods()) {
            try {
                attributes.put(attribute.getName(), attribute.invoke(annotation));
            } catch (ReflectiveOperationException e) {
                throw new BeansException("读取注解属性[" + annotationType.getSimpleName() + "." + attribute.getName() + "]失败", e);
            }
        }
        return attributes;
    }

    @Override
    public Class<?> getIntrospectedClass() {
        return introspectedClass;
    }

    @Override
    public Class<?> getExpectedType() {
        return expectedType;
    }

    /**
     * 在 {@code element} 上（含元注解，递归）查找 {@code annotationType}；找不到返回 {@code null}。
     */
    static <A extends Annotation> A findAnnotation(AnnotatedElement element, Class<A> annotationType) {
        A direct = element.getDeclaredAnnotation(annotationType);
        if (direct != null) {
            return direct;
        }
        for (Annotation present : element.getDeclaredAnnotations()) {
            Class<? extends Annotation> presentType = present.annotationType();
            if (presentType == annotationType) {
                return annotationType.cast(present);
            }
            // 内置注解（@Target/@Retention/@Documented/@Inherited）不参与语义查找
            if (presentType.getName().startsWith("java.lang.annotation.")) {
                continue;
            }
            A found = findAnnotation(presentType, annotationType);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return introspectedClass.getSimpleName() + (expectedType != null ? "." + expectedType.getSimpleName() : "");
    }
}