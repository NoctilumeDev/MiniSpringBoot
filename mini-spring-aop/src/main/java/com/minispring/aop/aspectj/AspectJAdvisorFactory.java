package com.minispring.aop.aspectj;

import com.minispring.aop.Advisor;
import com.minispring.aop.AspectJExpressionPointcut;
import com.minispring.aop.PointcutAdvisor;
import com.minispring.aop.annotation.After;
import com.minispring.aop.annotation.Around;
import com.minispring.aop.annotation.Before;
import com.minispring.aop.interceptor.AfterReturningAdviceInterceptor;
import com.minispring.aop.interceptor.AspectJAroundAdvice;
import com.minispring.aop.interceptor.MethodBeforeAdviceInterceptor;

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
        for (Method method : aspectInstance.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Before.class)) {
                PointcutAdvisor advisor = new PointcutAdvisor(
                        new AspectJExpressionPointcut(method.getAnnotation(Before.class).value()),
                        new MethodBeforeAdviceInterceptor(aspectInstance, method));
                advisors.add(advisor);
            } else if (method.isAnnotationPresent(After.class)) {
                advisors.add(new PointcutAdvisor(
                        new AspectJExpressionPointcut(method.getAnnotation(After.class).value()),
                        new AfterReturningAdviceInterceptor(aspectInstance, method)));
            } else if (method.isAnnotationPresent(Around.class)) {
                advisors.add(new PointcutAdvisor(
                        new AspectJExpressionPointcut(method.getAnnotation(Around.class).value()),
                        new AspectJAroundAdvice(aspectInstance, method)));
            }
        }
        return advisors;
    }
}