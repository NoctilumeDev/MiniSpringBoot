package com.minispring.context.demo;

import com.minispring.context.annotation.Service;

/** 英文问候实现（非 @Primary，作为多候选时被 @Qualifier 点名的对象）。 */
@Service
public class EnglishGreeter implements Greeter {

    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }
}