package com.minispring.context.event;

import com.minispring.context.ApplicationEvent;
import com.minispring.context.ApplicationListener;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 从 {@code ApplicationListener<E>} 的泛型实参里反解出关心的事件类型，覆盖四种声明形态：
     * <ul>
     *   <li>直接实现：{@code class L implements ApplicationListener<FooEvent>}</li>
     *   <li>经父类固化（D39）：{@code class Sub extends Base}，Base 直接实现并固化泛型</li>
     *   <li>经子接口固化（M2 初修，raw Class 形态）：{@code interface F extends ApplicationListener<FooEvent>}</li>
     *   <li>经带自身类型参数的子接口 / 泛型父类（M2 追修）：
     *       {@code interface S<E> extends ApplicationListener<E>} + {@code class L implements S<FooEvent>}，
     *       或 {@code class Sub extends Base<FooEvent>} + {@code class Base<E> implements ApplicationListener<E>}</li>
     * </ul>
     *
     * <p>包级可见：供同包单测<b>直接断言解析结果</b>。行为断言（received 计数）对「解析退化」
     * 不构成约束——退化时监听器对一切事件放行，无关事件在桥接方法的 checkcast 处抛
     * ClassCastException、被 {@code invoke} 的 B-4 catch 吞掉，计数碰巧仍为 0，用例假通过
     * （M2 追修实测教训：初版用例全绿但过滤实际未生效）。
     */
    Class<?> resolveEventType(ApplicationListener<?> listener) {
        Class<?> clazz = listener.getClass();
        Map<TypeVariable<?>, Type> bindings = Collections.emptyMap();
        while (clazz != null && clazz != Object.class) {
            for (Type iface : clazz.getGenericInterfaces()) {
                Class<?> resolved = resolveFromInterface(iface, bindings);
                if (resolved != null) {
                    return resolved;
                }
            }
            // 泛型父类（class Sub extends Base<FooEvent>）：先把「Base 的类型变量 → 实参」
            // 记入绑定表，再上到 Base 解析它声明的接口——否则 Base 处只见 ApplicationListener<E>，
            // E 是未解的类型变量，解析静默失败退化「接收所有事件」
            Type superType = clazz.getGenericSuperclass();
            if (superType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) superType;
                bindings = bindTypeVariables((Class<?>) pt.getRawType(), pt, bindings);
            }
            clazz = clazz.getSuperclass();
        }
        // 未显式声明泛型（裸实现）时，退化为「接收所有事件」
        return ApplicationEvent.class;
    }

    /**
     * 沿接口继承树上溯解析 ApplicationListener 的泛型实参，携带「类型变量 → 实参」绑定表。
     *
     * <p>M2 追修：带自身类型参数的子接口（{@code interface S<E> extends ApplicationListener<E>}）
     * 的实参在<b>实现处</b>给出（{@code implements S<FooEvent>}），上溯到
     * {@code ApplicationListener<E>} 时 E 仍是类型变量——必须靠绑定表代入才能解出 FooEvent。
     * 初版递归丢了绑定（从 raw Class 的 getGenericInterfaces() 重走，实参信息已丢失），
     * 宣称覆盖该形态实未覆盖：静默退化「接收所有事件」，且行为用例因 CCE 被吞而假通过。
     */
    private Class<?> resolveFromInterface(Type iface, Map<TypeVariable<?>, Type> bindings) {
        Class<?> raw;
        ParameterizedType pt = null;
        if (iface instanceof ParameterizedType) {
            pt = (ParameterizedType) iface;
            raw = (Class<?>) pt.getRawType();
        } else if (iface instanceof Class) {
            raw = (Class<?>) iface;
        } else {
            return null;
        }
        if (raw == ApplicationListener.class) {
            // 裸用 ApplicationListener（raw type，无实参）：交上层兜底
            return pt != null ? resolveTypeArgument(pt.getActualTypeArguments()[0], bindings) : null;
        }
        if (ApplicationListener.class.isAssignableFrom(raw)) {
            // 子接口（ParameterizedType / raw Class 皆可能）：携带绑定沿继承树上溯
            // （接口继承无环，编译器保证）
            Map<TypeVariable<?>, Type> next = (pt != null) ? bindTypeVariables(raw, pt, bindings) : bindings;
            for (Type superIface : raw.getGenericInterfaces()) {
                Class<?> resolved = resolveFromInterface(superIface, next);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    /**
     * 从「参数化类型的实参」建立绑定表：{@code S<FooEvent>} → {E → FooEvent}。
     * 实参若本身是外层声明的类型变量，先经外层表代入（支持多层子接口嵌套）。
     */
    private Map<TypeVariable<?>, Type> bindTypeVariables(Class<?> raw, ParameterizedType pt,
                                                          Map<TypeVariable<?>, Type> outer) {
        TypeVariable<?>[] vars = raw.getTypeParameters();
        Type[] args = pt.getActualTypeArguments();
        Map<TypeVariable<?>, Type> bindings = new HashMap<>();
        for (int i = 0; i < vars.length && i < args.length; i++) {
            Type arg = args[i];
            if (arg instanceof TypeVariable) {
                Type resolved = outer.get(arg);
                if (resolved != null) {
                    arg = resolved;
                }
            }
            bindings.put(vars[i], arg);
        }
        return bindings;
    }

    /** 泛型实参 → 事件类型：类型变量经绑定表代入；普通类直取；参数化类型取其原始类型。 */
    private Class<?> resolveTypeArgument(Type arg, Map<TypeVariable<?>, Type> bindings) {
        if (arg instanceof TypeVariable) {
            Type bound = bindings.get(arg);
            if (bound != null) {
                arg = bound;
            }
        }
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
