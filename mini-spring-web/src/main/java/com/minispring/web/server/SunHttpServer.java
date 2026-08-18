package com.minispring.web.server;

import com.minispring.web.http.HttpRequest;
import com.minispring.web.http.HttpResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 JDK 内置 {@link HttpServer} 的内嵌服务器。
 *
 * <p>JDK 已经替我们完成了 TCP 三次握手、HTTP 报文解析、请求行/头/体的拆解；
 * 我们只需把每个 {@link HttpExchange} 包装成自己的请求/响应抽象，交给前端控制器。
 *
 * <p>A-3：JDK HttpServer 的 dispatcher / 工作线程继承「创建线程」的 daemon 属性——
 * 若启动方本身是守护线程（如 maven exec:java），服务器会随 main 结束而静默死亡。
 * 这里显式在非守护线程中完成 create/start，并给线程池配非守护 ThreadFactory，
 * 保证「run() 返回后服务器持续服务」，与 Spring Boot 内嵌容器的保活语义一致。
 */
public class SunHttpServer implements WebServer {

    private final HttpHandler handler;
    private HttpServer server;

    public SunHttpServer(HttpHandler handler) {
        this.handler = handler;
    }

    @Override
    public void start(int port) {
        AtomicReference<IllegalStateException> failure = new AtomicReference<>();
        // 在非守护线程里 create + start：dispatcher 线程随之继承非守护属性，成为 JVM 的保活线程
        Thread launcher = new Thread(null, () -> doStart(port, failure), "minispring-http-launcher", 0, false);
        launcher.setDaemon(false);
        launcher.start();
        try {
            launcher.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("启动内嵌服务器被中断，端口 " + port, e);
        }
        IllegalStateException startFailure = failure.get();
        if (startFailure != null) {
            throw startFailure;
        }
    }

    private void doStart(int port, AtomicReference<IllegalStateException> failure) {
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            failure.set(new IllegalStateException("启动内嵌服务器失败，端口 " + port, e));
            return;
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
        // 显式给线程池：JDK HttpServer 不设 executor 时默认单线程串行处理请求（BUG-2 修复）；
        // 工作线程同样显式非守护，避免随启动方线程的 daemon 属性漂移
        this.server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread worker = new Thread(null, runnable, "minispring-http-worker", 0, false);
            worker.setDaemon(false);
            return worker;
        }));
        this.server.start();
    }

    @Override
    public void stop() {
        if (this.server != null) {
            this.server.stop(0);
        }
    }
}
