package com.minispring.context.annotation;

import com.minispring.core.BeanDefinition;
import com.minispring.core.BeanDefinitionRegistry;
import com.minispring.core.BeansException;
import com.minispring.core.InstantiationAwareBeanPostProcessor;
import com.minispring.core.ListableBeanFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Autowired} 注入处理器（A-4/D3 收口：字段 / 构造器 / 方法注入全部落地，@Target 不再撒谎）。
 *
 * <ul>
 *   <li>构造器注入：{@link #determineCandidateConstructors} 选出 {@code @Autowired} 构造器，
 *       参数由容器按「@Qualifier 名 → 唯一类型 → @Primary」解析（构造器循环依赖会得到可读错误）；</li>
 *   <li>字段注入：populateBean 阶段（三级缓存已暴露、初始化回调之前）；</li>
 *   <li>方法注入：字段注入完成后，逐个调用 {@code @Autowired} 方法；
 *       {@code required=false} 时任一依赖缺失则整个方法跳过。</li>
 * </ul>
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
    public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName) {
        List<Constructor<?>> found = null;
        for (Constructor<?> constructor : beanClass.getDeclaredConstructors()) {
            if (!constructor.isAnnotationPresent(Autowired.class)) {
                continue;
            }
            if (found == null) {
                found = new ArrayList<>();
            }
            found.add(constructor);
        }
        return found == null ? null : found.toArray(new Constructor<?>[0]);
    }

    @Override
    public void postProcessProperties(Object bean, String beanName) {
        injectFields(bean, beanName);
        injectMethods(bean, beanName);
    }

    private void injectFields(Object bean, String beanName) {
        for (Field field : findAllFields(bean.getClass())) {
            if (!field.isAnnotationPresent(Autowired.class)) {
                continue;
            }
            String qualifier = qualifierOf(field.getAnnotation(Qualifier.class));
            Object value = resolveDependency(field.getType(), qualifier,
                    field.getAnnotation(Autowired.class).required(), beanName,
                    beanName + "." + field.getName());
            field.setAccessible(true);
            try {
                field.set(bean, value);
            } catch (IllegalAccessException e) {
                throw new BeansException("注入字段[" + beanName + "." + field.getName() + "]失败", e);
            }
        }
    }

    /** 方法注入：字段注入完成后执行；required=false 且任一参数解析不出 → 仅跳过该方法（与 Spring 语义一致）。 */
    private void injectMethods(Object bean, String beanName) {
        for (Method method : findAllMethods(bean.getClass())) {
            Autowired autowired = method.getAnnotation(Autowired.class);
            if (autowired == null) {
                continue;
            }
            method.setAccessible(true);
            Class<?>[] paramTypes = method.getParameterTypes();
            Object[] args = new Object[paramTypes.length];
            boolean missing = false;
            try {
                for (int i = 0; i < paramTypes.length; i++) {
                    String qualifier = qualifierOf(method.getParameters()[i].getAnnotation(Qualifier.class));
                    // 解析按非必需（缺失返回 null），是否报错由方法级 required 决定
                    Object value = resolveDependency(paramTypes[i], qualifier, false, beanName,
                            beanName + "." + method.getName() + "(arg" + i + ")");
                    if (value == null) {
                        if (autowired.required()) {
                            throw new BeansException("注入方法[" + beanName + "." + method.getName()
                                    + "]失败：找不到类型 " + paramTypes[i].getName() + " 的 Bean");
                        }
                        missing = true; // required=false 且依赖缺失：整个方法跳过，继续下一个方法
                        break;
                    }
                    args[i] = value;
                }
                if (!missing) {
                    method.invoke(bean, args);
                }
            } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                throw new BeansException("注入方法[" + beanName + "." + method.getName() + "]调用失败", e);
            }
        }
    }

    /**
     * 字段 / 方法参数共用的依赖解析：@Qualifier 限定名 → beanName → 唯一类型 → @Primary；
     * required=false 允许返回 null。
     *
     * <p>D34（M8 收口）：@Qualifier(v) 不再只当 beanName 用——先在候选类型里匹配
     * {@code BeanDefinition.qualifier}（支持「限定名 ≠ beanName」，多数据源场景的关键），
     * 匹配不到再回退按 beanName。
     */
    private Object resolveDependency(Class<?> requiredType, String qualifier, boolean required,
                                     String beanName, String description) {
        // 1. @Qualifier 指名道姓：限定名 → beanName 两层匹配
        if (qualifier != null && !qualifier.isEmpty()) {
            return resolveByQualifier(qualifier, requiredType, description);
        }
        // 2. 按类型匹配
        String[] candidates = beanFactory.getBeanNamesForType(requiredType);
        if (candidates.length == 1) {
            return beanFactory.getBean(candidates[0]);
        }
        if (candidates.length > 1) {
            return resolveByPrimary(candidates, requiredType, beanName, description);
        }
        // 3. 没有候选
        if (required) {
            throw new BeansException("注入[" + description + "]失败：找不到类型 " + requiredType.getName() + " 的 Bean");
        }
        return null;
    }

    /** D34：限定名裁决——先匹配候选的 BeanDefinition.qualifier，再回退 beanName，两层都空给可读错误。 */
    private Object resolveByQualifier(String qualifier, Class<?> requiredType, String description) {
        String matched = null;
        for (String name : beanFactory.getBeanNamesForType(requiredType)) {
            BeanDefinition bd = registry.getBeanDefinition(name);
            if (qualifier.equals(bd.getQualifier())) {
                if (matched != null) {
                    throw new BeansException("注入[" + description + "]失败：限定名 " + qualifier
                            + " 命中多个 Bean（" + matched + ", " + name + "）");
                }
                matched = name;
            }
        }
        if (matched != null) {
            return beanFactory.getBean(matched);
        }
        // 回退：限定名当 beanName 用（与 Spring 的 @Qualifier("beanName") 语义一致）
        if (beanFactory.containsBean(qualifier)) {
            return beanFactory.getBean(qualifier);
        }
        throw new BeansException("注入[" + description + "]失败：找不到 qualifier=\"" + qualifier
                + "\"（既无此限定名，也无此 beanName），类型 " + requiredType.getName());
    }

    private String qualifierOf(Qualifier qualifier) {
        return (qualifier == null || qualifier.value().isEmpty()) ? null : qualifier.value();
    }

    private Object resolveByPrimary(String[] candidates, Class<?> requiredType, String beanName, String description) {
        String primary = null;
        for (String name : candidates) {
            BeanDefinition bd = registry.getBeanDefinition(name);
            // 统一看 BeanDefinition.isPrimary()——既覆盖类级 @Primary，也覆盖 @Bean 方法级 @Primary（D23）
            if (bd.isPrimary()) {
                if (primary != null) {
                    throw new BeansException("注入[" + description + "]失败：存在多个 @Primary 候选");
                }
                primary = name;
            }
        }
        if (primary != null) {
            return beanFactory.getBean(primary);
        }
        throw new BeansException("注入[" + description + "]失败：" + requiredType.getName()
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

    /** 收集类及其父类的所有方法（含非 public，继承注入也需要照顾）。 */
    private List<Method> findAllMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.isBridge() && !method.isSynthetic()) {
                    methods.add(method);
                }
            }
            current = current.getSuperclass();
        }
        return methods;
    }
}
