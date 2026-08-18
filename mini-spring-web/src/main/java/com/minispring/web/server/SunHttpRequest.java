package com.minispring.web.server;

import com.minispring.web.http.HttpRequest;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把 JDK 的 {@link HttpExchange} 包装成框架自己的 {@link HttpRequest}，屏蔽底层实现细节。
 */
final class SunHttpRequest implements HttpRequest {

    /** 请求体大小上限（字节）：与 JsonParser 的 MAX_DEPTH 同属输入面 DoS 防护。 */
    static final int MAX_BODY_BYTES = 1024 * 1024;

    private final HttpExchange exchange;
    private final Map<String, String> queryParameters;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final String path;
    private String body;

    SunHttpRequest(HttpExchange exchange) {
        this.exchange = exchange;
        this.queryParameters = parseQuery(exchange.getRequestURI().getRawQuery());
        this.path = exchange.getRequestURI().getPath();
    }

    @Override
    public String getMethod() {
        return exchange.getRequestMethod();
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getQueryParameter(String name) {
        return queryParameters.get(name);
    }

    @Override
    public String getBody() {
        if (body == null) {
            try {
                // 32（M0-M9 复审）：请求体设 1 MiB 上限——readAllBytes 会让超大请求体
                // 全量进内存，单请求即可 OOM；多读 1 字节用于判定超限
                byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
                if (bytes.length > MAX_BODY_BYTES) {
                    throw new IllegalStateException("请求体超过上限 " + MAX_BODY_BYTES
                            + " 字节，已拒绝读取（DoS 防护）");
                }
                body = new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("读取请求体失败", e);
            }
        }
        return body;
    }

    @Override
    public String getHeader(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return map;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            map.put(decode(key), decode(value));
        }
        return map;
    }

    private String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 非法百分号编码（孤立 % 或 %ZZ）：保留原串，避免整条请求因单个坏参数断连（B7）
            return s;
        }
    }
}