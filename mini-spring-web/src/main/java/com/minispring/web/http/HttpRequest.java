package com.minispring.web.http;

/**
 * 框架自己的轻量 HTTP 请求抽象，屏蔽底层 {@code HttpExchange} 的细节，
 * 让前端控制器与参数解析器不依赖具体服务器实现。
 */
public interface HttpRequest {

    /** 请求方法，如 GET / POST。 */
    String getMethod();

    /** 请求路径（不含 query），如 /users/42。 */
    String getPath();

    /** 查询参数；不存在返回 null。 */
    String getQueryParameter(String name);

    /** 请求体原文（POST/PUT 时才有内容）。 */
    String getBody();

    /** 请求头；不存在返回 null。 */
    String getHeader(String name);

    /** 请求级属性，用于在「处理器映射」与「参数解析」之间传递路径变量等上下文。 */
    void setAttribute(String name, Object value);

    Object getAttribute(String name);
}