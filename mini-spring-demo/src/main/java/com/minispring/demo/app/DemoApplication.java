package com.minispring.demo.app;

import com.minispring.boot.MiniSpringApplication;
import com.minispring.boot.MiniSpringBootApplication;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;
import com.minispring.web.server.SunHttpServer;
import com.minispring.web.server.WebServer;
import com.minispring.web.servlet.DispatcherServlet;

/**
 * 后端 demo 收口：一条 {@link MiniSpringApplication#run} 启动
 * 「Web/MVC + AOP + 外部化配置 + 自动装配 + 事件广播」，无需任何手动样板。
 */
@MiniSpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = MiniSpringApplication.run(DemoApplication.class, args);

        // @Value 处理器由 ValueAutoConfiguration 自动装配，这里验证环境值真实注入
        AppProperties appProperties = context.getBean("appProperties", AppProperties.class);
        System.out.println("  [@Value] app.name=" + appProperties.getName() + ", server.port=" + appProperties.getPort());

        // 事件按序触发：刷新完成 → 启动完成（关闭时还会有 Closed）
        DemoEventListener listener = context.getBean("demoEventListener", DemoEventListener.class);
        System.out.println("  [event] 已收到事件: " + listener.getRecordedEvents());

        int port = Integer.parseInt(context.getEnvironment().getProperty("server.port", "9090"));
        DispatcherServlet servlet = context.getBean("dispatcherServlet", DispatcherServlet.class);
        WebServer server = new SunHttpServer(servlet);
        server.start(port);

        System.out.println("  [http] 内嵌服务器已启动: http://localhost:" + port);
        System.out.println("  [http] 接口: GET /hello | GET /users/{id} | POST /users | GET /void | GET /sleep | GET /（静态资源）");
    }
}