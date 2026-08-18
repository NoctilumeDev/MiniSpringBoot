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
                body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}