package com.minispring.demo;

import com.minispring.context.annotation.Autowired;
import com.minispring.context.annotation.Qualifier;
import com.minispring.context.annotation.Service;

/** 用 @Qualifier 显式点名 englishGreeter。 */
@Service
public class EnglishOnlyService {

    @Autowired
    @Qualifier("englishGreeter")
    private Greeter greeter;

    public String sayHello(String name) {
        return greeter.greet(name);
    }
}