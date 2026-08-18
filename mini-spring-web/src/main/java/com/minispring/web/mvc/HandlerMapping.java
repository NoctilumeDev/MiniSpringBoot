package com.minispring.web.mvc;

import com.minispring.web.http.HttpRequest;

/**
 * 处理器映射：给定请求，找到能处理它的 {@link HandlerMethod}；找不到返回 null（→ 404）。
 */
public interface HandlerMapping {

    /** 匹配成功时，会把路径变量（如 /users/{id} 的 id）作为请求属性写回，供参数解析器读取。 */
    HandlerMethod getHandler(HttpRequest request);
}