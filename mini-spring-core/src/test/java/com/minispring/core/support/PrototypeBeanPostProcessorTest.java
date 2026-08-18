package com.minispring.core.support;

import com.minispring.core.BeanDefinition;
import com.minispring.core.BeanPostProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * B-3 回归：prototype 作用域的 BeanPostProcessor 不得在每次 getBean 时重复注册进处理器链。
 * 修复前：getBean 三次 → 处理器链里出现三个实例，处理次数膨胀；修复后：prototype BPP 一律不注册。
 */
class PrototypeBeanPostProcessorTest {

    /** 计数型 BPP：每处理一个 Bean 计一次（用于观察它是否被注册进处理器链）。 */
    static class CountingProcessor implements BeanPostProcessor {
        static int processed;

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            processed++;
            return bean;
        }
    }

    static class NormalBean {
    }

    @Test
    void prototypePostProcessorIsNeverRegisteredIntoChain() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        BeanDefinition protoBd = new BeanDefinition(CountingProcessor.class);
        protoBd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        factory.registerBeanDefinition("countingProcessor", protoBd);
        factory.registerBeanDefinition("normalBean", new BeanDefinition(NormalBean.class));

        CountingProcessor.processed = 0;
        // prototype：每次 getBean 都是新实例；修复前每次 createBean 都会把它注册进处理器链
        factory.getBean("countingProcessor");
        factory.getBean("countingProcessor");
        factory.getBean("countingProcessor");
        // 再取普通单例：若 prototype BPP 被注册，processed 会是 3（修复前）；修复后必须是 0
        factory.getBean("normalBean");
        assertEquals(0, CountingProcessor.processed,
                "prototype 作用域的 BPP 不应被注册进处理器链（B-3 修复前 processed=3）");
        factory.close();
    }
}
