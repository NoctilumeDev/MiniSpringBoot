package com.minispring.core;

/**
 * 排序信号：声明一个「在同类事物里我该排多靠前」的优先级。
 *
 * <p>{@code order} 越小越靠前（越先执行）。默认 {@link #LOWEST_PRECEDENCE}（最靠后），
 * 这是 Spring 的约定：不显式声明顺序的组件，永远排在显式声明者之后。
 */
public interface Ordered {

    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;
    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;

    int getOrder();
}