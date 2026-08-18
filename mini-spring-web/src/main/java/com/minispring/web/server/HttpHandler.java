package com.minispring.web.server;

import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;

/**
 * 处理单次 HTTP 请求的入口（相当于 Servlet 的 {@code service}）。
 * 前端控制器 {@code DispatcherServlet} 就是它的实现。
 */
@FunctionalInterface
public interface HttpHandler {

    void handle(HttpRequest request, HttpResponse response) throws Exception;
}