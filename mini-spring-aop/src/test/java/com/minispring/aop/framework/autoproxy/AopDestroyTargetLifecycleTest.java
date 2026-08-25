package com.minispring.aop.framework.autoproxy;

import com.minispring.aop.annotation.Aspect;
import com.minispring.aop.annotation.Before;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;
import com.minispring.context.annotation.Bean;
import com.minispring.context.annotation.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AOP 暴露代理不应改变容器对原始 Bean 生命周期的所有权。 */
class AopDestroyTargetLifecycleTest {

    interface ManagedService {
        void work();
    }

    static class ManagedServiceImpl implements ManagedService {
        private int shutdownCalls;

        @Override
        public void work() {
        }

        void shutdown() {
            shutdownCalls++;
        }
    }

    @Aspect
    static class LifecycleAspect {
        @Before("execution(* com.minispring.aop.framework.autoproxy.AopDestroyTargetLifecycleTest$ManagedServiceImpl.work(..))")
        public void before() {
        }
    }

    @Configuration
    static class LifecycleConfig {
        private final ManagedServiceImpl target = new ManagedServiceImpl();

        @Bean
        AspectJAutoProxyCreator aspectJAutoProxyCreator() {
            return new AspectJAutoProxyCreator();
        }

        @Bean
        LifecycleAspect lifecycleAspect() {
            return new LifecycleAspect();
        }

        @Bean(destroyMethod = "shutdown")
        ManagedService managedService() {
            return target;
        }
    }

    @Test
    void customDestroyMethodRunsOnRawTargetBehindJdkProxy() {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(LifecycleConfig.class);
        LifecycleConfig config = context.getBean("lifecycleConfig", LifecycleConfig.class);
        ManagedService exposed = context.getBean("managedService", ManagedService.class);

        assertTrue(Proxy.isProxyClass(exposed.getClass()), "前置条件：容器对外应暴露 JDK 代理");
        context.close();

        assertEquals(1, config.target.shutdownCalls,
                "关闭上下文时必须在代理背后的原始目标上恰好调用一次实现类专属 destroyMethod");
    }
}
