package com.minispring.context.annotation;

import com.minispring.core.BeansException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A-4（D3 收口）回归：@Autowired 的构造器 / 方法 / required=false 语义，以及构造器循环依赖的可读错误。
 * 修复前：@Target 承诺了构造器/方法注入但未实现（静默忽略）；构造器循环依赖会 StackOverflow。
 */
class AutowiredInjectionTest {

    // ---- 受测 Bean ----

    static class Dependency {
        String name() {
            return "dep";
        }
    }

    static class CtorBean {
        final Dependency dependency;

        @Autowired
        CtorBean(Dependency dependency) {
            this.dependency = dependency;
        }
    }

    static class MethodBean {
        Dependency injected;
        int injectedCount;

        @Autowired
        void configure(Dependency dependency) {
            this.injected = dependency;
            this.injectedCount++;
        }
    }

    static class OptionalMethodBean {
        boolean called;

        @Autowired(required = false)
        void maybeConfigure(Dependency missing) {
            this.called = true;
        }
    }

    static class CycleA {
        @Autowired
        CycleA(CycleB b) {
        }
    }

    static class CycleB {
        @Autowired
        CycleB(CycleA a) {
        }
    }

    // ---- 用例 ----

    @Test
    void constructorInjectionResolvesDependency() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Dependency.class, CtorBean.class);
        try {
            CtorBean bean = ctx.getBean("ctorBean", CtorBean.class);
            assertEquals("dep", bean.dependency.name());
        } finally {
            ctx.close();
        }
    }

    @Test
    void methodInjectionInvokedOnceAfterFieldInjection() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Dependency.class, MethodBean.class);
        try {
            MethodBean bean = ctx.getBean("methodBean", MethodBean.class);
            assertEquals("dep", bean.injected.name());
            assertEquals(1, bean.injectedCount);
        } finally {
            ctx.close();
        }
    }

    @Test
    void optionalMethodSkippedWhenDependencyMissing() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(OptionalMethodBean.class);
        try {
            OptionalMethodBean bean = ctx.getBean("optionalMethodBean", OptionalMethodBean.class);
            // required=false 且依赖缺失：整个方法被跳过，不抛异常
            assertTrue(!bean.called, "required=false 且依赖缺失时，@Autowired 方法不应被调用");
        } finally {
            ctx.close();
        }
    }

    @Test
    void constructorCycleFailsWithReadableErrorNotStackOverflow() {
        BeansException ex = assertThrows(BeansException.class,
                () -> new AnnotationConfigApplicationContext(CycleA.class, CycleB.class));
        assertTrue(ex.getMessage().contains("循环依赖"), "应给出可读的循环依赖错误，实际: " + ex.getMessage());
    }
}
