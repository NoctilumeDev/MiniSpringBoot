package com.minispring.aop.demo;

/** 下单服务接口：只有接口才会被 JDK 动态代理拦截，这是本框架 AOP 的约定。 */
public interface OrderService {

    String placeOrder(String product);
}