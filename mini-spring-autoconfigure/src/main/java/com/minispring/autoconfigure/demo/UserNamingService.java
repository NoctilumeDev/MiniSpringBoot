package com.minispring.autoconfigure.demo;

/**
 * 用户自定义的命名实现：用于覆盖自动配置的默认实现，验证「用户优先、自动回退」。
 */
public class UserNamingService implements NamingService {

    @Override
    public String name() {
        return "naming（用户自定，覆盖了自动配置）";
    }
}