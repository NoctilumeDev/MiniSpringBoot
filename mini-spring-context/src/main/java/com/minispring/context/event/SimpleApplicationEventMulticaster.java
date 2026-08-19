package com.minispring.context.event;

import com.minispring.context.ApplicationEvent;
import com.minispring.context.ApplicationListener;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 事件广播器：维护监听器列表，把事件分发给「声明的事件类型能接收该事件」的监听器，同步、按注册顺序广播。
 *
 * <p>监听器「关心哪种事件」由其 {@code ApplicationListener<E>} 的泛型实参决定；
 * 本教学子集不引入异步、不引入线程池，直接当前线程顺序调用。
 */
public class SimpleApplicationEventMulticaster {

    private final List<ApplicationListener<?>> listeners = new ArrayList<>();

    public void addApplicationListener(ApplicationListener<?> listener) {
        this.listeners.add(listener);
    }

    public void multicastEvent(ApplicationEvent event) {
        // 拷贝一份再遍历，避免监听器在回调里反手增删监听器导致并发修改
        for (ApplicationListener<?> listener : new ArrayList<>(listeners)) {
            if (supportsEvent(listener, event)) {
                invoke(listener, event);
            }
        }
    }

    private boolean supportsEvent(ApplicationListener<?> listener, ApplicationEvent event) {
        return resolveEventType(listener).isInstance(event);
    }

    /** 从 {@code ApplicationListener<E>} 的泛型实参里反解出关心的事件类型（向上遍历父类以适应继承场景）。 */
    private Class<?> resolveEventType(ApplicationListener<?> listener) {
        Class<?> clazz = listener.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Type iface : clazz.getGenericInterfaces()) {
                Class<?> resolved = resolveFromInterface(iface);
                if (resolved != null) {
                    return resolved;
                }
            }
            clazz = clazz.getSuperclass();
        }
        // 未显式声明泛型（裸实现）时，退化为「接收所有事件」
        return ApplicationEvent.class;
    }

    /**
     * 解析接口上的 ApplicationListener 泛型实参。M2（M0-M9 复审第二轮）：泛型可能经
     * <b>子接口</b>固化——两种形态都要认：①带自身参数的子接口
     * （{@code interface SmartListener<E> extends ApplicationListener<E>}，出现为
     * ParameterizedType）；②固化泛型的子接口（{@code interface FixedListener extends
     * ApplicationListener<FixedEvent>}，在 getGenericInterfaces() 里出现为 raw Class）。
     * 任一形态解析失败都会退化成「接收所有事件」（D39 只修了父类链，此处补齐子接口链）。
     */
    private Class<?> resolveFromInterface(Type iface) {
        Class<?> raw;
        if (iface instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) iface;
            raw = (Class<?>) pt.getRawType();
            if (raw == ApplicationListener.class) {
                return resolveTypeArgument(pt);
            }
        } else if (iface instanceof Class) {
            raw = (Class<?>) iface;
        } else {
            return null;
        }
        // 子接口（ParameterizedType / raw Class 皆可能）：沿其继承树上溯
        // （接口继承无环，编译器保证）
        if (ApplicationListener.class.isAssignableFrom(raw)) {
            for (Type superIface : raw.getGenericInterfaces()) {
                Class<?> resolved = resolveFromInterface(superIface);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    /** 取 {@code ApplicationListener<X>} 的泛型实参 X：普通类直取，参数化类型取其原始类型。 */
    private Class<?> resolveTypeArgument(ParameterizedType pt) {
        Type arg = pt.getActualTypeArguments()[0];
        if (arg instanceof Class) {
            return (Class<?>) arg;
        }
        if (arg instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) arg).getRawType();
            if (raw instanceof Class) {
                return (Class<?>) raw;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void invoke(ApplicationListener<?> listener, ApplicationEvent event) {
        try {
            ((ApplicationListener<ApplicationEvent>) listener).onApplicationEvent(event);
        } catch (Exception e) {
            // B-4：单个监听器异常不阻断后续监听器（与 Spring SimpleApplicationEventMulticaster 的隔离语义一致）
            System.err.println("事件监听器[" + listener.getClass().getName() + "]处理 "
                    + event.getClass().getSimpleName() + " 失败: " + e);
        }
    }
}