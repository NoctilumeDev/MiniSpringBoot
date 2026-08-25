package com.minispring.core.support;

import com.minispring.core.BeanDefinition;
import com.minispring.core.BeansException;
import com.minispring.core.DisposableBean;
import com.minispring.core.PropertyValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IoC 容器的基线单测（仅作最低基线，不作为「落地验收」依据）。
 */
class DefaultListableBeanFactoryTest {

    static class A {
        private B b;

        B getB() {
            return b;
        }
    }

    static class B {
        private A a;

        A getA() {
            return a;
        }
    }

    @Test
    void resolvesCircularDependency() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

        BeanDefinition bdA = new BeanDefinition(A.class);
        bdA.addPropertyValue(PropertyValue.ref("b", "b"));
        factory.registerBeanDefinition("a", bdA);

        BeanDefinition bdB = new BeanDefinition(B.class);
        bdB.addPropertyValue(PropertyValue.ref("a", "a"));
        factory.registerBeanDefinition("b", bdB);

        A a = factory.getBean("a", A.class);
        assertNotNull(a.getB());
        assertSame(a, a.getB().getA());
    }

    static class Proto {
    }

    static class InheritedFactoryMethod {
        public Integer build(Object dependency) {
            return 42;
        }
    }

    static class OverloadedFactory extends InheritedFactoryMethod {
        String build() {
            return "plain";
        }
    }

    /** 构造慢的单例：拉宽并发 getBean 的竞态窗口，让 N5 用例稳定复现。 */
    static class SlowBean {
        SlowBean() {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static final List<String> DESTROY_LOG = new ArrayList<>();

    /** 依赖方（先创建）。bean 名刻意取 "aaa"：其哈希桶位（1）先于 "zzz"（10）被
     *  ConcurrentHashMap 遍历——若销毁仍按定义表无序遍历，会先销毁依赖方，本用例必失败。 */
    static class DisposableDepA implements DisposableBean {
        @Override
        public void destroy() {
            DESTROY_LOG.add("aaa");
        }
    }

    /** 使用方（后创建，持有 aaa 的引用）。 */
    static class DisposableUserZ implements DisposableBean {
        @SuppressWarnings("unused")
        private DisposableDepA dep;

        @Override
        public void destroy() {
            DESTROY_LOG.add("zzz");
        }
    }

    @Test
    void prototypeCreatesNewInstanceEveryTime() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

        BeanDefinition bd = new BeanDefinition(Proto.class);
        bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        factory.registerBeanDefinition("proto", bd);

        assertNotSame(factory.getBean("proto"), factory.getBean("proto"));
    }

    @Test
    void nameOnlyFactoryMetadataRejectsOverloadedMethods() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerBeanDefinition("factory", new BeanDefinition(OverloadedFactory.class));

        BeanDefinition product = new BeanDefinition(String.class);
        product.setFactoryBeanName("factory");
        product.setFactoryMethodName("build");
        factory.registerBeanDefinition("product", product);

        BeansException exception = assertThrows(BeansException.class, () -> factory.getBean("product"));
        String causeMessage = exception.getCause() == null ? "" : String.valueOf(exception.getCause().getMessage());
        assertTrue(exception.getMessage().contains("重载") || causeMessage.contains("重载"),
                "仅有名称的工厂元数据遇到重载时必须给出可读拒绝，实际: " + exception);
    }

    /** N4：销毁必须按创建逆序——使用者（zzz）先于其依赖（aaa）销毁。 */
    @Test
    void destroysSingletonsInReverseCreationOrder() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        DESTROY_LOG.clear();

        factory.registerBeanDefinition("aaa", new BeanDefinition(DisposableDepA.class));
        BeanDefinition bdUser = new BeanDefinition(DisposableUserZ.class);
        bdUser.addPropertyValue(PropertyValue.ref("dep", "aaa"));
        factory.registerBeanDefinition("zzz", bdUser);

        factory.getBean("zzz"); // 触发创建：aaa 先入一级缓存，zzz 后
        factory.destroySingletons();

        assertEquals(List.of("zzz", "aaa"), DESTROY_LOG);
    }

    /** N5：并发 getBean 同名单例不得误报循环依赖（修复前第二线程撞 currentlyInCreation 直接抛错）。 */
    @Test
    void concurrentGetBeanDoesNotFalseReportCircularDependency() throws Exception {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerBeanDefinition("slow", new BeanDefinition(SlowBean.class));

        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Object> get = () -> {
            barrier.await();
            return factory.getBean("slow");
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> f1 = pool.submit(get);
            Future<Object> f2 = pool.submit(get);
            assertSame(f1.get(), f2.get());
        } finally {
            pool.shutdownNow();
        }
    }

    static class HalfBaked {
        @SuppressWarnings("unused")
        private Object dep;
    }

    /**
     * H1（M0-M9 复审第二轮）：创建失败必须清掉三级缓存残留。
     * 修复前：第一次 getBean 注入失败抛错，但 singletonFactories 里的工厂残留——
     * 第二次 getBean 从三级缓存命中「字段未注入的半成品」静默返回（无任何报错）。
     * 本用例锚定：重试必须再次抛错，绝不允许拿到坏对象。
     */
    @Test
    void failedCreationDoesNotLeaveFactoryResidue() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        BeanDefinition bd = new BeanDefinition(HalfBaked.class);
        bd.addPropertyValue(PropertyValue.ref("dep", "no-such-bean"));
        factory.registerBeanDefinition("halfBaked", bd);

        // 第一次：注入失败（ref 的 bean 不存在）
        assertThrows(BeansException.class, () -> factory.getBean("halfBaked"));
        // 第二次（H1 核心）：不得从三级缓存拿到半成品——必须再次抛同样类型的错误
        assertThrows(BeansException.class, () -> factory.getBean("halfBaked"),
                "创建失败的 Bean 不得在重试时静默返回半成品");
    }
}
