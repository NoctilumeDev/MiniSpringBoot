package com.minispring.aop;

/**
 * AOP 代理抽象：给目标对象生成一个「披着拦截器链」的代理。
 * 本框架内核暂只用 JDK 动态代理实现（要求目标对象有接口）。
 */
public interface AopProxy {

    Object getProxy();
}