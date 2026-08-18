package com.minispring.demo.autoconfig;

/**
 * 自动配置提供的默认命名实现（用户未自定时兜底）。
 */
public class AutoNamingService implements NamingService {

    @Override
    public String name() {
        return "naming（自动配置默认）";
    }
}