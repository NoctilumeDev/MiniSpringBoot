package com.minispring.context.annotation;

import com.minispring.core.BeansException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 四种注入入口必须共享同一个多 @Primary 拒绝规则，不能按注册顺序静默选第一个。 */
class PrimaryCandidateConsistencyTest {

    interface Gateway {
    }

    @Primary
    static class FirstPrimary implements Gateway {
    }

    @Primary
    static class SecondPrimary implements Gateway {
    }

    static class RegularGateway implements Gateway {
    }

    static class ConstructorConsumer {
        final Gateway gateway;

        @Autowired
        ConstructorConsumer(Gateway gateway) {
            this.gateway = gateway;
        }
    }

    static class FieldConsumer {
        @Autowired
        Gateway gateway;
    }

    static class MethodConsumer {
        @Autowired
        void configure(Gateway gateway) {
        }
    }

    static class FactoryConsumer {
        FactoryConsumer(Gateway gateway) {
        }
    }

    @Configuration
    static class FactoryConfig {
        @Bean
        FactoryConsumer factoryConsumer(Gateway gateway) {
            return new FactoryConsumer(gateway);
        }
    }

    @Test
    void constructorInjectionRejectsMultiplePrimaryCandidates() {
        assertMultiplePrimary(() -> new AnnotationConfigApplicationContext(
                FirstPrimary.class, SecondPrimary.class, ConstructorConsumer.class));
    }

    @Test
    void factoryMethodInjectionRejectsMultiplePrimaryCandidates() {
        assertMultiplePrimary(() -> new AnnotationConfigApplicationContext(
                FirstPrimary.class, SecondPrimary.class, FactoryConfig.class));
    }

    @Test
    void fieldInjectionRejectsMultiplePrimaryCandidates() {
        assertMultiplePrimary(() -> new AnnotationConfigApplicationContext(
                FirstPrimary.class, SecondPrimary.class, FieldConsumer.class));
    }

    @Test
    void methodInjectionRejectsMultiplePrimaryCandidates() {
        assertMultiplePrimary(() -> new AnnotationConfigApplicationContext(
                FirstPrimary.class, SecondPrimary.class, MethodConsumer.class));
    }

    @Test
    void exactlyOnePrimaryCandidateStillWins() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                FirstPrimary.class, RegularGateway.class, ConstructorConsumer.class);
        try {
            ConstructorConsumer consumer = context.getBean("constructorConsumer", ConstructorConsumer.class);
            assertInstanceOf(FirstPrimary.class, consumer.gateway);
        } finally {
            context.close();
        }
    }

    private static void assertMultiplePrimary(Runnable action) {
        BeansException exception = assertThrows(BeansException.class, action::run);
        String messages = messageChain(exception);
        assertTrue(messages.contains("多个 @Primary 候选"), "应拒绝多主候选，实际: " + messages);
        assertTrue(messages.contains("firstPrimary") && messages.contains("secondPrimary"),
                "错误应点明冲突候选，实际: " + messages);
    }

    private static String messageChain(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
        }
        return messages.toString();
    }
}
