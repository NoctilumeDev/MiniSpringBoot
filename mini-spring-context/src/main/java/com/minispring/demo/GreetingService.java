package com.minispring.demo;

import com.minispring.context.annotation.Autowired;
import com.minispring.context.annotation.Service;

/** 按类型注入 Greeter：有两个候选，靠 @Primary 裁决选 FrenchGreeter。 */
@Service
public class GreetingService {

    @Autowired
    private Greeter greeter;

    public String sayHello(String name) {
        return greeter.greet(name);
    }

    public Greeter greeter() {
        return greeter;
    }
}