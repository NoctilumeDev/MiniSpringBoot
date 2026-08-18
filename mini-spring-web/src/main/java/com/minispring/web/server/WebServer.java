package com.minispring.web.server;

/**
 * 内嵌服务器的 SPI：屏蔽底层服务器的启动细节，让「协议层」与「业务处理」解耦。
 *
 * <p>首版实现是 {@link SunHttpServer}；将来换 Jetty / Netty 时，只需新写一个实现，
 * 上层（前端控制器、参数绑定）完全不动——这正是 Spring Boot「内嵌容器可替换」的本质。
 */
public interface WebServer {

    void start(int port);

    void stop();
}