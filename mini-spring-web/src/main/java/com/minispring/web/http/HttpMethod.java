package com.minispring.web.http;

/**
 * HTTP 方法。既是请求行里的方法，也是 {@code @RequestMapping(method=...)} 的匹配条件。
 */
public enum HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS;

    /** 从请求行方法名解析；未知方法返回 null（路由匹配时按「不匹配」处理）。 */
    public static HttpMethod resolve(String method) {
        if (method == null) {
            return null;
        }
        try {
            return HttpMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}