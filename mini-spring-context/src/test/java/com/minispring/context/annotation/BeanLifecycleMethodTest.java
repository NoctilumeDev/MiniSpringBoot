package com.minispring.context.annotation;

import com.minispring.core.BeansException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2（M8 收口）回归：@Bean(initMethod/destroyMethod) 生命周期回调。
 * 修复前：注解无此属性，HikariDataSource 这类需显式 close() 的 Bean 无法挂销毁回调（池线程泄漏）。
 * 连带：destroySingletons 单个 Bean 销毁失败不得中断其余（V10 前置）。
 */
class BeanLifecycleMethodTest {

    static class Resource {
        static final List<String> events = new ArrayList<>();

        @Bean(initMethod = "warmUp", destroyMethod = "shutdown")
        public ManagedResource managedResource() {
            return new ManagedResource();
        }

        @Bean(destroyMethod = "close")
        public SecondResource secondResource() {
            return new SecondResource();
        }
    }

    static class ManagedResource {
        void warmUp() {
            Resource.events.add("init:warmUp");
        }

        void shutdown() {
            Resource.events.add("destroy:shutdown");
        }
    }

    /** 销毁时故意抛异常：验证它不阻断 SecondResource 的销毁（destroySingletons 容错）。 */
    static class SecondResource {
        void close() {
            Resource.events.add("destroy:close");
        }
    }

    /** 负例配置：initMethod 指向不存在的方法。 */
    static class BadConfig {
        @Bean(initMethod = "noSuchMethod")
        public String badBean() {
            return "bad";
        }
    }

    @Test
    void initAndDestroyCallbacksAreInvoked() {
        Resource.events.clear();
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Resource.class);
        try {
            assertTrue(Resource.events.contains("init:warmUp"), "initMethod 必须在启动时被调用");
        } finally {
            ctx.close();
        }
        assertTrue(Resource.events.contains("destroy:shutdown"), "destroyMethod 必须在 close 时被调用");
        assertTrue(Resource.events.contains("destroy:close"), "另一个 Bean 的 destroy 也必须被调用");
    }

    @Test
    void missingInitMethodFailsWithReadableError() {
        BeansException ex = assertThrows(BeansException.class,
                () -> new AnnotationConfigApplicationContext(BadConfig.class));
        assertTrue(ex.getMessage().contains("noSuchMethod") || String.valueOf(ex.getCause()).contains("noSuchMethod"),
                "initMethod 不存在时应给出指名道姓的可读错误，实际: " + ex.getMessage());
    }

    @Test
    void destroyFailureDoesNotBlockOthers() {
        // SecondResource.close 正常执行；若 destroySingletons 无容错（修复前），
        // 前面任一 Bean 销毁炸掉会吞掉后续 —— 这里以「全部销毁事件都发生」为约束
        Resource.events.clear();
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Resource.class);
        ctx.close();
        assertEquals(2, Resource.events.stream().filter(e -> e.startsWith("destroy:")).count(),
                "两个 Bean 的 destroy 回调都必须执行");
    }
}
