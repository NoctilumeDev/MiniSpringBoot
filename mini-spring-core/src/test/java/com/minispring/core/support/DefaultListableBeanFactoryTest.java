package com.minispring.core.support;

import com.minispring.core.BeanDefinition;
import com.minispring.core.PropertyValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    static class Proto {
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

    @Test
    void prototypeCreatesNewInstanceEveryTime() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

        BeanDefinition bd = new BeanDefinition(Proto.class);
        bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        factory.registerBeanDefinition("proto", bd);

        assertNotSame(factory.getBean("proto"), factory.getBean("proto"));
    }
}