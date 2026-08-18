package com.minispring.demo;

import com.minispring.context.annotation.Primary;
import com.minispring.context.annotation.Service;

/** 法语问候实现，标注 @Primary，多候选时它胜出。 */
@Service
@Primary
public class FrenchGreeter implements Greeter {

    @Override
    public String greet(String name) {
        return "Bonjour, " + name;
    }
}