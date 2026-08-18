package com.minispring.aop;

import com.minispring.core.Ordered;

/**
 * 顾问：一个「切点 + 一段拦截逻辑」的组合。容器里有多少切面方法，就有多少个 Advisor。
 *
 * <p>同时实现 {@link Ordered}：多个 Advisor 命中同一方法时，按 {@code order} 由小到大组成拦截链（D5）。
 */
public interface Advisor extends Ordered {

    Pointcut getPointcut();

    MethodInterceptor getMethodInterceptor();
}