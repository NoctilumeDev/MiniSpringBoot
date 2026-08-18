package com.minispring.context.annotation;

import com.minispring.context.ApplicationEvent;
import com.minispring.context.ApplicationEventPublisher;
import com.minispring.context.ApplicationEventPublisherAware;
import com.minispring.context.ApplicationListener;
import com.minispring.core.InitializingBean;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A-6（D35 收口）回归：监听器先于业务单例注册 —— Bean 在初始化回调（afterPropertiesSet/initMethod）里
 * 发布的事件必须被收到。修复前：监听器在所有单例预实例化之后才收集，初始化期事件全部丢失。
 */
class EventListenerEarlyPublishTest {

    /** 测试用具体事件。 */
    static class CustomEvent extends ApplicationEvent {
        CustomEvent(Object source) {
            super(source);
        }
    }

    /** 记录型监听器。 */
    static class RecordingListener implements ApplicationListener<CustomEvent> {
        final List<CustomEvent> received = new ArrayList<>();

        @Override
        public void onApplicationEvent(CustomEvent event) {
            received.add(event);
        }
    }

    /** 在初始化回调里发布事件的 Bean（拿到 publisher 靠 ApplicationEventPublisherAware 注入）。 */
    static class EarlyPublisher implements ApplicationEventPublisherAware, InitializingBean {
        ApplicationEventPublisher publisher;

        @Override
        public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public void afterPropertiesSet() {
            publisher.publishEvent(new CustomEvent(this));
        }
    }

    @Test
    void eventPublishedDuringInitializationIsReceived() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(RecordingListener.class, EarlyPublisher.class);
        try {
            RecordingListener listener = ctx.getBean("recordingListener", RecordingListener.class);
            assertTrue(listener.received.size() >= 1,
                    "初始化回调（afterPropertiesSet）里发布的事件必须被监听器收到（A-6 修复前会丢失）");
        } finally {
            ctx.close();
        }
    }
}
