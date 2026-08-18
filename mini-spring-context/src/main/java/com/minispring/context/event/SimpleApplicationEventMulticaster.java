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

    private Class<?> resolveFromInterface(Type iface) {
        if (!(iface instanceof ParameterizedType)) {
            return null;
        }
        ParameterizedType pt = (ParameterizedType) iface;
        if (pt.getRawType() != ApplicationListener.class) {
            return null;
        }
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
        ((ApplicationListener<ApplicationEvent>) listener).onApplicationEvent(event);
    }
}