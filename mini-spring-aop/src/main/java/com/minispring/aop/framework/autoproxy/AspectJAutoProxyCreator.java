package com.minispring.aop.framework.autoproxy;

import com.minispring.aop.Advisor;
import com.minispring.aop.JdkDynamicAopProxy;
import com.minispring.aop.annotation.Aspect;
import com.minispring.aop.aspectj.AspectJAdvisorFactory;
import com.minispring.core.BeanDefinitionRegistry;
import com.minispring.core.BeanFactory;
import com.minispring.core.BeanFactoryAware;
import com.minispring.core.BeanPostProcessor;
import com.minispring.core.ListableBeanFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自动代理创建器：一个 BeanPostProcessor，在 Bean「初始化完成后」判断它是否命中了某个切点，
 * 命中则把原对象替换成 JDK 代理——这就是 Spring AOP 自动代理的核心。
 */
public class AspectJAutoProxyCreator implements BeanPostProcessor, BeanFactoryAware {

    private ListableBeanFactory beanFactory;
    private BeanDefinitionRegistry registry;
    private List<Advisor> advisors;
    private boolean buildingAdvisors = false;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = (ListableBeanFactory) beanFactory;
        this.registry = (BeanDefinitionRegistry) beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // JDK 代理只作用于有接口的对象；无接口类（含切面自身、普通 Bean）直接放行
        if (bean.getClass().getInterfaces().length == 0) {
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
            this.advisors = collected;
            return collected;
        } finally {
            buildingAdvisors = false;
        }
    }
}