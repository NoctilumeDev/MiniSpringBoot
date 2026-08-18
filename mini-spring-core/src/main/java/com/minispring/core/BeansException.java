package com.minispring.core;

/**
 * 容器操作抛出的统一运行时异常。
 */
public class BeansException extends RuntimeException {

    public BeansException(String message) {
        super(message);
    }

    public BeansException(String message, Throwable cause) {
        super(message, cause);
    }
}