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

/** 业务接口可由父类继承；JDK 自动代理不能只检查目标类直接声明的接口。 */
class InheritedInterfaceAutoProxyTest {

    interface AuditedService {
        String execute();
    }

    static class BaseAuditedService implements AuditedService {
        @Override
        public String execute() {
            return "done";
        }
    }

    static class SpecializedAuditedService extends BaseAuditedService {
    }

    @Aspect
    static class AuditAspect {
        private int calls;

        @Before("execution(* com.minispring.aop.framework.autoproxy.InheritedInterfaceAutoProxyTest$SpecializedAuditedService.execute(..))")
        public void before() {
            calls++;
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        AspectJAutoProxyCreator aspectJAutoProxyCreator() {
            return new AspectJAutoProxyCreator();
        }

        @Bean
        AuditAspect auditAspect() {
            return new AuditAspect();
        }

        @Bean
        AuditedService auditedService() {
            return new SpecializedAuditedService();
        }
    }

    @Test
    void subclassInheritingBusinessInterfaceStillReceivesJdkProxy() {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(TestConfig.class);
        try {
            AuditedService service = context.getBean("auditedService", AuditedService.class);
            AuditAspect aspect = context.getBean("auditAspect", AuditAspect.class);

            assertTrue(Proxy.isProxyClass(service.getClass()),
                    "继承父类业务接口的目标仍应暴露 JDK 代理");
            assertEquals("done", service.execute());
            assertEquals(1, aspect.calls);
        } finally {
            context.close();
        }
    }
}
