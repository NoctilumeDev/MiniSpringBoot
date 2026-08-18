package com.minispring.web.server;

import com.minispring.web.http.HttpResponse;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 把 JDK 的 {@link HttpExchange} 包装成框架自己的 {@link HttpResponse}。
 * 首次 {@link #write(byte[])} 时才真正向外发送响应头（含状态码），发送后不能再改。
 */
final class SunHttpResponse implements HttpResponse {

    private final HttpExchange exchange;
    private int status = 200;
    private boolean committed = false;

    SunHttpResponse(HttpExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public void setContentType(String contentType) {
        exchange.getResponseHeaders().set("Content-Type", contentType);
    }

    @Override
    public void setHeader(String name, String value) {
        exchange.getResponseHeaders().set(name, value);
    }

    @Override
    public boolean isCommitted() {
        return committed;
    }

    @Override
    public void write(byte[] bytes) {
        try {
            if (!committed) {
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                committed = true;
            }
            if (bytes.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("写响应失败", e);
        }
    }

    @Override
    public void write(String text) {
        write(text.getBytes(StandardCharsets.UTF_8));
    }
}