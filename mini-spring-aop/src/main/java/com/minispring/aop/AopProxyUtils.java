package com.minispring.aop;

import java.util.LinkedHashSet;
import java.util.Set;

/** JDK 代理所需的接口解析：覆盖目标类、父类以及接口继承层次。 */
public final class AopProxyUtils {

    private AopProxyUtils() {
    }

    public static Class<?>[] completeProxiedInterfaces(Class<?> targetClass) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> current = targetClass;
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Class<?> candidate : current.getInterfaces()) {
                collectInterfaceHierarchy(candidate, interfaces);
            }
        }
        return interfaces.toArray(Class<?>[]::new);
    }

    private static void collectInterfaceHierarchy(Class<?> candidate, Set<Class<?>> interfaces) {
        if (!interfaces.add(candidate)) {
            return;
        }
        for (Class<?> parent : candidate.getInterfaces()) {
            collectInterfaceHierarchy(parent, interfaces);
        }
    }
}
