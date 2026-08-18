package com.minispring.core.support;

import com.minispring.core.BeanDefinition;
import com.minispring.core.BeanDefinitionRegistry;
import com.minispring.core.BeanFactory;
import com.minispring.core.BeanPostProcessor;
import com.minispring.core.BeansException;
import com.minispring.core.DisposableBean;
import com.minispring.core.InitializingBean;
import com.minispring.core.ObjectFactory;
import com.minispring.core.PropertyValue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的 Bean 工厂实现（放在 support 子包，对外只暴露 {@link BeanFactory} 等接口）。
 *
 * <p>负责：BeanDefinition 注册、Bean 创建、生命周期（实例化 → 填充 → 初始化 → 销毁），
 * 以及用「三级缓存」破解循环依赖。
 *
 * <h3>三级缓存</h3>
 * <ul>
 *   <li>一级 {@code singletonObjects}      ：已完全就绪的单例</li>
 *   <li>二级 {@code earlySingletonObjects} ：被提前暴露的半成品（将来可能已代理）</li>
 *   <li>三级 {@code singletonFactories}    ：能产出半成品的工厂</li>
 * </ul>
 */
public class DefaultListableBeanFactory implements BeanFactory, BeanDefinitionRegistry, AutoCloseable {

    // 图纸仓库
    private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>();

    // 三级缓存
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>();
    private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>();
    private final Map<String, ObjectFactory<?>> singletonFactories = new ConcurrentHashMap<>();

    // 质检员（BeanPostProcessor）
    private final List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    public void addBeanPostProcessor(BeanPostProcessor processor) {
        this.beanPostProcessors.add(processor);
    }

    // ---------- BeanDefinitionRegistry ----------

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) {
        this.beanDefinitionMap.put(beanName, beanDefinition);
    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) {
        BeanDefinition bd = this.beanDefinitionMap.get(beanName);
        if (bd == null) {
            throw new BeansException("未找到 Bean 定义: " + beanName);
        }
        return bd;
    }

    @Override
    public boolean containsBeanDefinition(String beanName) {
        return this.beanDefinitionMap.containsKey(beanName);
    }

    // ---------- BeanFactory ----------

    @Override
    public Object getBean(String name) {
        BeanDefinition bd = getBeanDefinition(name);
        if (!bd.isSingleton()) {
            // prototype：每次新建，不缓存、不参与循环依赖破解、容器不负责销毁
            return createBean(name, bd);
        }

        // 一级缓存命中 → 直接返回已就绪的单例
        Object bean = singletonObjects.get(name);
        if (bean != null) {
            return bean;
        }
        // 二级缓存命中 → 返回提前暴露的半成品
        bean = earlySingletonObjects.get(name);
        if (bean != null) {
            return bean;
        }
        // 三级缓存命中 → 从工厂取出半成品，升级到二级缓存后返回
        ObjectFactory<?> factory = singletonFactories.get(name);
        if (factory != null) {
            bean = factory.getObject();
            earlySingletonObjects.put(name, bean);
            singletonFactories.remove(name);
            return bean;
        }
        // 都没有 → 完整创建
        return createBean(name, bd);
    }

    @Override
    public <T> T getBean(String name, Class<T> requiredType) {
        Object bean = getBean(name);
        if (!requiredType.isInstance(bean)) {
            throw new BeansException("Bean[" + name + "] 类型不匹配");
        }
        return requiredType.cast(bean);
    }

    @Override
    public boolean containsBean(String name) {
        return containsBeanDefinition(name);
    }

    // ---------- 创建主流程 ----------

    private Object createBean(String beanName, BeanDefinition bd) {
        Object bean = instantiate(beanName, bd);
        // 提前暴露的引用需单独捕获，避免 bean 后续被重新赋值导致 lambda 无法引用
        Object exposed = bean;

        if (bd.isSingleton()) {
            // 提前暴露：把「能产出半成品」的工厂放进三级缓存
            ObjectFactory<?> factory = () -> getEarlyBeanReference(beanName, exposed);
            singletonFactories.put(beanName, factory);
        }

        populateBean(beanName, bd, bean);
        bean = initializeBean(beanName, bd, bean);

        if (bd.isSingleton()) {
            singletonObjects.put(beanName, bean);
            earlySingletonObjects.remove(beanName);
            singletonFactories.remove(beanName);
        }
        return bean;
    }

    /** 获取提前引用。M1 阶段无 AOP 代理，直接返回原始对象；M3 之后在此处可能返回代理。 */
    private Object getEarlyBeanReference(String beanName, Object bean) {
        return bean;
    }

    private Object instantiate(String beanName, BeanDefinition bd) {
        try {
            return bd.getBeanClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new BeansException("实例化 Bean[" + beanName + "] 失败", e);
        }
    }

    /** 属性填充：把 PropertyValue 逐个注入字段；引用类型的值会触发其依赖 Bean 的真实创建。 */
    private void populateBean(String beanName, BeanDefinition bd, Object bean) {
        for (PropertyValue pv : bd.getPropertyValues()) {
            Object value = pv.isRef() ? getBean((String) pv.getValue()) : pv.getValue();
            applyPropertyValue(beanName, bean, pv.getName(), value);
        }
    }

    private void applyPropertyValue(String beanName, Object bean, String name, Object value) {
        try {
            Field field = findField(bean.getClass(), name);
            if (field == null) {
                throw new BeansException("Bean[" + beanName + "] 中找不到字段: " + name);
            }
            field.setAccessible(true);
            field.set(bean, value);
        } catch (IllegalAccessException e) {
            throw new BeansException("注入字段[" + name + "]失败", e);
        }
    }

    private Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /** 初始化：before → InitializingBean / initMethod → after。 */
    private Object initializeBean(String beanName, BeanDefinition bd, Object bean) {
        for (BeanPostProcessor processor : beanPostProcessors) {
            bean = processor.postProcessBeforeInitialization(bean, beanName);
        }

        invokeInitMethods(beanName, bd, bean);

        for (BeanPostProcessor processor : beanPostProcessors) {
            bean = processor.postProcessAfterInitialization(bean, beanName);
        }
        return bean;
    }

    private void invokeInitMethods(String beanName, BeanDefinition bd, Object bean) {
        if (bean instanceof InitializingBean) {
            try {
                ((InitializingBean) bean).afterPropertiesSet();
            } catch (Exception e) {
                throw new BeansException("Bean[" + beanName + "] 初始化回调失败", e);
            }
        }
        String initMethod = bd.getInitMethodName();
        if (initMethod != null && !initMethod.isEmpty()) {
            invokeNoArgMethod(beanName, bean, initMethod);
        }
    }

    private void invokeNoArgMethod(String beanName, Object bean, String methodName) {
        try {
            Method method = bean.getClass().getMethod(methodName);
            method.invoke(bean);
        } catch (Exception e) {
            throw new BeansException("Bean[" + beanName + "] 方法[" + methodName + "] 调用失败", e);
        }
    }

    // ---------- 销毁 ----------

    public void destroySingletons() {
        for (Map.Entry<String, BeanDefinition> entry : beanDefinitionMap.entrySet()) {
            if (entry.getValue().isSingleton()) {
                destroyBean(entry.getKey(), singletonObjects.get(entry.getKey()));
            }
        }
        singletonObjects.clear();
        earlySingletonObjects.clear();
        singletonFactories.clear();
    }

    @Override
    public void close() {
        destroySingletons();
    }

    private void destroyBean(String beanName, Object bean) {
        if (bean == null) {
            return;
        }
        if (bean instanceof DisposableBean) {
            try {
                ((DisposableBean) bean).destroy();
            } catch (Exception e) {
                throw new BeansException("Bean[" + beanName + "] 销毁回调失败", e);
            }
        }
        BeanDefinition bd = beanDefinitionMap.get(beanName);
        String destroyMethod = bd == null ? null : bd.getDestroyMethodName();
        if (destroyMethod != null && !destroyMethod.isEmpty()) {
            invokeNoArgMethod(beanName, bean, destroyMethod);
        }
    }
}