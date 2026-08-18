package com.minispring.demo.app;

/** 下单服务接口：只有接口才会被 JDK 动态代理拦截，这是本框架 AOP 的约定。 */
public interface OrderService {

    String placeOrder(String product);

    /** 模拟会抛业务异常的方法：用来验证异常能原样透传，不被代理包装。 */
    void failOrder();
}