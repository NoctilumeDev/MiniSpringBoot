package com.minispring.starter.demo;

/**
 * 「starter 内部」提供的格式服务契约：模拟一个第三方库的对外能力。
 */
public interface FormatService {

    String format(String text);
}