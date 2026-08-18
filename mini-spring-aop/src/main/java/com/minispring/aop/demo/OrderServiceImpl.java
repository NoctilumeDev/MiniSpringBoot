package com.minispring.aop.demo;

import com.minispring.context.annotation.Service;

/** 下单服务的真正实现：被 {@link LoggingAspect} 的切点命中后，会被容器换成代理。 */
@Service
public class OrderServiceImpl implements OrderService {

    @Override
    public String placeOrder(String product) {
        System.out.println("    [业务] 真实执行下单逻辑：" + product);
        return "已下单[" + product + "]";
    }
}