package com.minispring.aop.aspectj;

import com.minispring.aop.Advisor;
import com.minispring.aop.AspectJExpressionPointcut;
import com.minispring.aop.PointcutAdvisor;
import com.minispring.aop.annotation.After;
import com.minispring.aop.annotation.Around;
import com.minispring.aop.annotation.Before;
import com.minispring.aop.interceptor.AfterAdviceInterceptor;
import com.minispring.aop.interceptor.AspectJAroundAdvice;
import com.minispring.aop.interceptor.MethodBeforeAdviceInterceptor;
import com.minispring.core.Ordered;
import com.minispring.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 把「@Aspect 切面实例」上的通知方法，翻译成一个个 {@link Advisor}。
 * 这一步正是 Spring AOP 里 {@code ReflectiveAspectJAdvisorFactory} 的「可读版」。
 */
public class AspectJAdvisorFactory {

    private final Object aspectInstance;

    public AspectJAdvisorFactory(Object aspectInstance) {
        this.aspectInstance = aspectInstance;
    }

    public List<Advisor> getAdvisors() {
        List<Advisor> advisors = new ArrayList<>();
        // L7：沿父类链收集通知方法——通知写在切面基类（含非 public）同样生效，
        // 与注解扫描/字段注入的「继承照顾」纪律对称
        for (Method method : collectMethods(aspectInstance.getClass())) {
            int order = resolveOrder(method);
            if (method.isAnnotationPresent(Before.class)) {
                advisors.add(new PointcutAdvisor(
                        new AspectJExpressionPointcut(method.getAnnotation(Before.class).value()),
                        new MethodBeforeAdviceInterceptor(aspectInstance, method), order));
            } else if (method.isAnnotationPresent(After.class)) {
                advisors.add(new PointcutAdvisor(
                        new AspectJExpressionPointcut(method.getAnnotation(After.class).value()),
                        new AfterAdviceInterceptor(aspectInstance, method), order));
            } else if (method.isAnnotationPresent(Around.class)) {
                advisors.add(new PointcutAdvisor(
                        new AspectJExpressionPointcut(method.getAnnotation(Around.class).value()),
                        new AspectJAroundAdvice(aspectInstance, method), order));
            }
        }
        return advisors;
    }

    /** 收集类及其父类的所有方法（跳过桥接/合成方法）。 */
    private List<Method> collectMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.isBridge() && !method.isSynthetic()) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    /** 优先级来源：方法级 {@code @Order} > 切面类级 {@code @Order} > 缺省最低优先级（D5）。 */
    private int resolveOrder(Method method) {
        Order methodOrder = method.getAnnotation(Order.class);
        if (methodOrder != null) {
            return methodOrder.value();
        }
        Order classOrder = aspectInstance.getClass().getAnnotation(Order.class);
        return classOrder != null ? classOrder.value() : Ordered.LOWEST_PRECEDENCE;
    }
}