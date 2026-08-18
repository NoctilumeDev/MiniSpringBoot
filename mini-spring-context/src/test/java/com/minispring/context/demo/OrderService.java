package com.minispring.context.demo;

import com.minispring.context.annotation.Autowired;
import com.minispring.context.annotation.Service;

/** 循环依赖的一方 A：依赖 UserService。 */
@Service
public class OrderService {

    @Autowired
    private UserService userService;

    public UserService getUserService() {
        return userService;
    }
}