package com.minispring.core;

/**
 * 销毁回调：容器关闭时调用一次 {@link #destroy()}。
 */
public interface DisposableBean {

    void destroy() throws Exception;
}