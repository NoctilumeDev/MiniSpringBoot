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
    private OutputStream bodyStream;

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
                // B-8（D29 关闭）：首次 write 用 chunked（length=0）而非定长，
                // 之后可以继续 write；流的关闭交给外层 exchange.close()，write 不再自关。
                // 空 body 仍用 -1 显式声明「无响应体」。
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : 0);
                committed = true;
            }
            if (bytes.length > 0) {
                if (bodyStream == null) {
                    bodyStream = exchange.getResponseBody();
                }
                bodyStream.write(bytes);
                bodyStream.flush();
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