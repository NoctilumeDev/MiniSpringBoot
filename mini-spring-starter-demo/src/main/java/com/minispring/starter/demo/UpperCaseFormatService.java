package com.minispring.starter.demo;

/**
 * {@link FormatService} 的默认实现（转大写）：由 starter 的自动配置兜底装配。
 */
public class UpperCaseFormatService implements FormatService {

    @Override
    public String format(String text) {
        return text.toUpperCase();
    }
}