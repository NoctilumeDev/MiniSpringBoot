package com.minispring.context.demo;

import com.minispring.context.annotation.Autowired;
import com.minispring.context.annotation.Service;

/** 循环依赖的另一方 B：依赖 OrderService。 */
@Service
public class UserService {

    @Autowired
    private OrderService orderService;

    public OrderService getOrderService() {
        return orderService;
    }
}