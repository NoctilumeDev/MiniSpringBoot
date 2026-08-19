package com.minispring.context.event;

import com.minispring.context.ApplicationEvent;
import com.minispring.context.ApplicationListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * M2（M0-M9 复审第二轮）的约束用例：监听器泛型经<b>子接口固化</b>
 * （{@code interface FixedListener extends ApplicationListener<FixedEvent>}，raw Class 形态）
 * 时，事件类型必须正确反解——否则 supportsEvent 退化为「接收所有事件」，invoke 处
 * ClassCastException 被 B-4 的 catch 吞掉（构建日志可见 CCE 噪音即退化证据）。
 * 修复覆盖两种子接口形态：raw Class（固化泛型）与 ParameterizedType（带自身参数）。
 */
class SimpleApplicationEventMulticasterTest {

    static class FixedEvent extends ApplicationEvent {
        FixedEvent(Object source) {
            super(source);
        }
    }

    static class OtherEvent extends ApplicationEvent {
        OtherEvent(Object source) {
            super(source);
        }
    }

    /** 子接口固化泛型：FixedListener extends ApplicationListener&lt;FixedEvent&gt;。 */
    interface FixedListener extends ApplicationListener<FixedEvent> {
    }

    static class SubInterfaceListener implements FixedListener {
        final List<String> received = new ArrayList<>();

        @Override
        public void onApplicationEvent(FixedEvent event) {
            received.add("fixed");
        }
    }

    @Test
    void subInterfaceGenericResolutionFiltersEvents() {
        SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();
        SubInterfaceListener listener = new SubInterfaceListener();
        multicaster.addApplicationListener(listener);

        multicaster.multicastEvent(new OtherEvent(this));
        // 修复前：子接口泛型解析失败 → 退化为接收所有事件 → 这里就会收到 OtherEvent（用例失败）
        assertEquals(0, listener.received.size(), "经子接口固化泛型的监听器不得接收无关事件");

        multicaster.multicastEvent(new FixedEvent(this));
        assertEquals(1, listener.received.size(), "声明的事件类型必须正常接收");
    }
}
