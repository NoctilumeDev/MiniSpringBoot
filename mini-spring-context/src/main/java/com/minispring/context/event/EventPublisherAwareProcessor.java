package com.minispring.context.event;

import com.minispring.context.ApplicationEventPublisherAware;
import com.minispring.core.BeanPostProcessor;

/**
 * 把 {@link ApplicationEventPublisher} 注入给实现了 {@link ApplicationEventPublisherAware} 的 Bean
 * （对齐 Spring 的 ApplicationEventPublisherAware 机制）。注册期：上下文构造时、refresh 之前。
 */
public class EventPublisherAwareProcessor implements BeanPostProcessor {

    private final ApplicationEventPublisherAdapter adapter;

    public EventPublisherAwareProcessor(SimpleApplicationEventMulticaster multicaster) {
        this.adapter = new ApplicationEventPublisherAdapter(multicaster);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof ApplicationEventPublisherAware) {
            ((ApplicationEventPublisherAware) bean).setApplicationEventPublisher(adapter);
        }
        return bean;
    }

    /** 事件发布器适配：委托广播器分发。 */
    private static final class ApplicationEventPublisherAdapter implements com.minispring.context.ApplicationEventPublisher {
        private final SimpleApplicationEventMulticaster multicaster;

        ApplicationEventPublisherAdapter(SimpleApplicationEventMulticaster multicaster) {
            this.multicaster = multicaster;
        }

        @Override
        public void publishEvent(com.minispring.context.ApplicationEvent event) {
            multicaster.multicastEvent(event);
        }
    }
}
