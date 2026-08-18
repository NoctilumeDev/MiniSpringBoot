package com.minispring.web.server;

import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * 基于 JDK 内置 {@link HttpServer} 的内嵌服务器。
 *
 * <p>JDK 已经替我们完成了 TCP 三次握手、HTTP 报文解析、请求行/头/体的拆解；
 * 我们只需把每个 {@link HttpExchange} 包装成自己的请求/响应抽象，交给前端控制器。
 */
public class SunHttpServer implements WebServer {

    private final HttpHandler handler;
    private HttpServer server;

    public SunHttpServer(HttpHandler handler) {
        this.handler = handler;
    }

    @Override
    public void start(int port) {
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new IllegalStateException("启动内嵌服务器失败，端口 " + port, e);
        }
        // 所有路径（含子路径）都进入同一个前端控制器，路由交给它内部完成
        this.server.createContext("/", exchange -> {
            HttpRequest request = new SunHttpRequest(exchange);
            HttpResponse response = new SunHttpResponse(exchange);
            try {
                handler.handle(request, response);
            } catch (Exception e) {
                // 兜底：任何漏网的异常都转成 500，避免连接被直接断开
                if (!response.isCommitted()) {
                    response.setStatus(500);
                    response.setContentType("text/plain; charset=utf-8");
                    response.write("Internal Server Error: " + e.getMessage());
                }
            } finally {
                exchange.close();
            }
        });
        this.server.start();
    }

    @Override
    public void stop() {
        if (this.server != null) {
            this.server.stop(0);
        }
    }
}