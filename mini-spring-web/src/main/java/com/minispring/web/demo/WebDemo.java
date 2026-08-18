package com.minispring.web.demo;

import com.minispring.config.support.ConfigFilePropertySourceLoader;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;
import com.minispring.core.env.StandardEnvironment;
import com.minispring.web.server.SunHttpServer;
import com.minispring.web.server.WebServer;
import com.minispring.web.servlet.DispatcherServlet;

/**
 * M5 落地演示：真实启动内嵌 HTTP 服务器，让浏览器 / curl 能访问接口。
 *
 * <p>验收：浏览器真实打开 GET /hello、GET /users/{id}、POST /users（@RequestBody JSON）。
 */
public class WebDemo {

    public static void main(String[] args) {
        StandardEnvironment environment = new StandardEnvironment();
        new ConfigFilePropertySourceLoader().load(environment);
        int port = Integer.parseInt(environment.getProperty("server.port", "8080"));

        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(environment, WebConfig.class);

        DispatcherServlet servlet = context.getBean("dispatcherServlet", DispatcherServlet.class);
        WebServer server = new SunHttpServer(servlet);
        server.start(port);

        System.out.println("=== M5 Web/MVC 落地演示 ===");
        System.out.println("  内嵌服务器已启动: http://localhost:" + port);
        System.out.println("  接口: GET /hello | GET /users/{id} | POST /users | GET /（静态资源）");
        System.out.println("  （服务器线程会保持 JVM 存活，Ctrl+C 结束）");
    }
}