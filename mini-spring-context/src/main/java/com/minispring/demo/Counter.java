package com.minispring.demo;

/** 用于验证 @Scope("prototype") 的简单计数器。 */
public class Counter {

    private int count = 0;

    public int increment() {
        return ++count;
    }
}