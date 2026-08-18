package com.minispring.autoconfigure.demo;

/**
 * 问候服务契约：由自动配置提供「默认实现」，也可由用户覆盖。
 */
public interface GreetingService {

    String greet();
}