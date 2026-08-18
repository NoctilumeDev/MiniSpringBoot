package com.minispring.demo.autoconfig;

/**
 * 自动配置提供的默认问候实现（用户未自定时兜底）。
 */
public class DefaultGreetingService implements GreetingService {

    @Override
    public String greet() {
        return "Hello（来自自动配置的默认实现）";
    }
}