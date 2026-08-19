package com.minispring.context.event;

import com.minispring.context.ApplicationEvent;
import com.minispring.context.ApplicationListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * M2（M0-M9 复审第二轮）+ 追修：监听器泛型的<b>四种声明形态</b>都必须正确解析出事件类型。
 *
 * <p><b>为什么必须直接断言 {@code resolveEventType}</b>：行为断言（received 计数）对
 * 「解析退化」不构成约束——退化时监听器对一切事件放行，无关事件在桥接方法的 checkcast
 * 处抛 ClassCastException、被 {@code invoke} 的 B-4 catch 吞掉，计数碰巧仍为 0，
 * <b>用例假通过</b>。初版用例正是如此：测试全绿但过滤实际未生效，构建日志里的 CCE
 * 噪音才是唯一线索（用户复审指出后追修）。直接断言解析结果才是约束性锚点——解析退化时
 * 返回兜底 {@code ApplicationEvent.class}，用例必失败。
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

    /** 形态一：直接实现。 */
    static class DirectListener implements ApplicationListener<FixedEvent> {
        final List<String> received = new ArrayList<>();

        @Override
        public void onApplicationEvent(FixedEvent event) {
            received.add("direct");
        }
    }

    /** 形态二：经子接口固化泛型（raw Class 形态，M2 初修）。 */
    interface FixedListener extends ApplicationListener<FixedEvent> {
    }

    static class FixedFormListener implements FixedListener {
        final List<String> received = new ArrayList<>();

        @Override
        public void onApplicationEvent(FixedEvent event) {
            received.add("fixed");
        }
    }

    /** 形态三：经带自身类型参数的子接口（M2 追修——初版宣称覆盖、实未覆盖）。 */
    interface SmartListener<E extends ApplicationEvent> extends ApplicationListener<E> {
    }

    static class SmartFormListener implements SmartListener<FixedEvent> {
        final List<String> received = new ArrayList<>();

        @Override
        public void onApplicationEvent(FixedEvent event) {
            received.add("smart");
        }
    }

    /** 形态四：经泛型父类固化（class Sub extends Base&lt;FixedEvent&gt;，M2 追修补齐）。 */
    static class GenericBase<E extends ApplicationEvent> implements ApplicationListener<E> {
        final List<String> received = new ArrayList<>();

        @Override
        public void onApplicationEvent(E event) {
            received.add("base");
        }
    }

    static class ConcreteSub extends GenericBase<FixedEvent> {
    }

    // ---- 约束性锚点：直接断言解析结果（退化 → 兜底 ApplicationEvent.class → 用例必失败） ----

    @Test
    void resolvesDirectImplementation() {
        assertSame(FixedEvent.class,
                new SimpleApplicationEventMulticaster().resolveEventType(new DirectListener()));
    }

    @Test
    void resolvesRawClassSubInterface() {
        assertSame(FixedEvent.class,
                new SimpleApplicationEventMulticaster().resolveEventType(new FixedFormListener()));
    }

    @Test
    void resolvesParameterizedSubInterface() {
        // 修复前：SmartListener<FixedEvent> 上溯到 ApplicationListener<E> 时 E 仍是类型变量
        // （实参绑定丢失）→ 解析返回 null → 兜底 ApplicationEvent.class，本用例失败
        assertSame(FixedEvent.class,
                new SimpleApplicationEventMulticaster().resolveEventType(new SmartFormListener()));
    }

    @Test
    void resolvesGenericSuperclass() {
        // 修复前：Base 声明的 ApplicationListener<E> 解不出 E → 兜底 ApplicationEvent.class
        assertSame(FixedEvent.class,
                new SimpleApplicationEventMulticaster().resolveEventType(new ConcreteSub()));
    }

    // ---- 端到端行为：四种形态一起，无关事件不投递、声明事件正常投递 ----

    @Test
    void allFormsOnlyReceiveDeclaredEvents() {
        SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();
        DirectListener direct = new DirectListener();
        FixedFormListener fixed = new FixedFormListener();
        SmartFormListener smart = new SmartFormListener();
        ConcreteSub sub = new ConcreteSub();
        multicaster.addApplicationListener(direct);
        multicaster.addApplicationListener(fixed);
        multicaster.addApplicationListener(smart);
        multicaster.addApplicationListener(sub);

        multicaster.multicastEvent(new OtherEvent(this));
        assertEquals(0, direct.received.size(), "直接实现形态不得接收无关事件");
        assertEquals(0, fixed.received.size(), "子接口固化形态不得接收无关事件");
        assertEquals(0, smart.received.size(), "带参子接口形态不得接收无关事件");
        assertEquals(0, sub.received.size(), "泛型父类形态不得接收无关事件");

        multicaster.multicastEvent(new FixedEvent(this));
        assertEquals(1, direct.received.size());
        assertEquals(1, fixed.received.size());
        assertEquals(1, smart.received.size());
        assertEquals(1, sub.received.size(), "声明的事件类型必须正常接收");
    }
}
