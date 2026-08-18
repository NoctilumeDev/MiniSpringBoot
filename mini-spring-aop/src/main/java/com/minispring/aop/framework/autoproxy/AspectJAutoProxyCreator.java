package com.minispring.aop.framework.autoproxy;

import com.minispring.aop.Advisor;
import com.minispring.aop.JdkDynamicAopProxy;
import com.minispring.aop.annotation.Aspect;
import com.minispring.aop.aspectj.AspectJAdvisorFactory;
import com.minispring.core.BeanDefinitionRegistry;
import com.minispring.core.BeanFactory;
import com.minispring.core.BeanFactoryAware;
import com.minispring.core.ListableBeanFactory;
import com.minispring.core.SingletonBeanRegistry;
import com.minispring.core.SmartInstantiationAwareBeanPostProcessor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自动代理创建器：一个后处理器，在 Bean「初始化完成后」判断它是否命中了某个切点，命中则把原对象替换成 JDK 代理。
 *
 * <p>同时实现 {@link SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference}，
 * 保证「被代理 Bean 参与循环依赖」时提前暴露的也是代理（B2）：三级缓存里产出的引用与容器最终持有的引用一致。
 */
public class AspectJAutoProxyCreator implements SmartInstantiationAwareBeanPostProcessor, BeanFactoryAware {

    private ListableBeanFactory beanFactory;
    private BeanDefinitionRegistry registry;
    private SingletonBeanRegistry singletonRegistry;
    private List<Advisor> advisors;
    private boolean buildingAdvisors = false;
    // 记录「已在循环依赖中提前代理」的原始对象，避免 postProcessAfterInitialization 二次包装（B2）
    private final Set<Object> earlyProxyReferences = Collections.newSetFromMap(new IdentityHashMap<>());
    // D30：收集期被跳过的业务 Bean（在收集完成前暂时不代理），收集完成后统一补一次代理判定
    private final Map<String, Object> deferredProxyTargets = new LinkedHashMap<>();

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = (ListableBeanFactory) beanFactory;
        this.registry = (BeanDefinitionRegistry) beanFactory;
        this.singletonRegistry = (SingletonBeanRegistry) beanFactory;
    }

    @Override
    public Object getEarlyBeanReference(Object bean, String beanName) {
        Object proxy = wrapIfNecessary(bean, beanName);
        if (proxy != bean) {
            earlyProxyReferences.add(bean);
        }
        return proxy;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // 若该 Bean 已在循环依赖中被提前代理，则不再二次包装；
        // 最终单例会由容器采用提前暴露的代理（见 DefaultListableBeanFactory.createBean）。
        if (earlyProxyReferences.remove(bean)) {
            return bean;
        }
        return wrapIfNecessary(bean, beanName);
    }

    private Object wrapIfNecessary(Object bean, String beanName) {
        // JDK 代理只作用于有接口的对象；无接口类（含切面自身、普通 Bean）直接放行
        if (bean.getClass().getInterfaces().length == 0) {
            return bean;
        }
        // 收集期间先记下、暂不代理，等 advisor 收集完成后再补判定（D30）
        if (buildingAdvisors) {
            deferredProxyTargets.putIfAbsent(beanName, bean);
            return bean;
        }
        List<Advisor> candidateAdvisors = getAdvisors();
        if (candidateAdvisors.isEmpty()) {
            return bean;
        }
        for (Advisor advisor : candidateAdvisors) {
            if (hasMatchingMethod(bean.getClass(), advisor)) {
                return new JdkDynamicAopProxy(bean, candidateAdvisors).getProxy();
            }
        }
        return bean;
    }

    private boolean hasMatchingMethod(Class<?> clazz, Advisor advisor) {
        for (Method method : clazz.getMethods()) {
            if (advisor.getPointcut().matches(method, clazz)) {
                return true;
            }
        }
        return false;
    }

    private List<Advisor> getAdvisors() {
        if (advisors != null) {
            return advisors;
        }
        // 收集过程中若再触发收集（如切面 Bean 也进了后处理），先返回空，避免无限递归
        if (buildingAdvisors) {
            return Collections.emptyList();
        }
        buildingAdvisors = true;
        try {
            List<Advisor> collected = new ArrayList<>();
            for (String name : registry.getBeanDefinitionNames()) {
                Class<?> beanClass = registry.getBeanDefinition(name).getBeanClass();
                if (beanClass != null && beanClass.isAnnotationPresent(Aspect.class)) {
                    Object aspectBean = beanFactory.getBean(name);
                    collected.addAll(new AspectJAdvisorFactory(aspectBean).getAdvisors());
                }
            }
            // D5：多切面命中同一方法时，按 @Order 由小到大组成拦截链
            collected.sort(Comparator.comparingInt(Advisor::getOrder));
            this.advisors = collected;
            return collected;
        } finally {
            buildingAdvisors = false;
            // D30 补偿：仅当收集成功（advisors 已就绪）后，才补一次代理判定，命中则回填一级缓存
            if (advisors != null) {
                for (Map.Entry<String, Object> entry : deferredProxyTargets.entrySet()) {
                    String beanName = entry.getKey();
                    Object rawBean = entry.getValue();
                    Object proxy = wrapIfNecessary(rawBean, beanName);
                    if (proxy != rawBean) {
                        singletonRegistry.registerSingleton(beanName, proxy);
                    }
                }
            }
            deferredProxyTargets.clear();
        }
    }
}