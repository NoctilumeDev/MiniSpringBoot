package com.minispring.demo.app;

import com.minispring.boot.MiniSpringApplication;
import com.minispring.boot.MiniSpringBootApplication;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;

/**
 * 后端 demo 收口：一条 {@link MiniSpringApplication#run} 启动
 * 「Web/MVC + AOP + 外部化配置 + 自动装配 + 事件广播 + 内嵌服务器」（A-3 收口，无任何手动样板）。
 */
@MiniSpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = MiniSpringApplication.run(DemoApplication.class, args);

        // @Value 处理器由 ValueAutoConfiguration 自动装配，环境值真实注入
        AppProperties appProperties = context.getBean("appProperties", AppProperties.class);
        System.out.println("  [@Value] app.name=" + appProperties.getName() + ", server.port=" + appProperties.getPort());

        // 事件按序触发：刷新完成 → 启动完成（关闭时还会有 Closed）
        DemoEventListener listener = context.getBean("demoEventListener", DemoEventListener.class);
        System.out.println("  [event] 已收到事件: " + listener.getRecordedEvents());

        System.out.println("  [http] 接口: GET /hello | GET /users/{id} | POST /users | GET /void | GET /sleep | GET /（静态资源）");
        System.out.println("  [http] 全链路能力: GET /capability/aop/order | GET /capability/aop/fail | GET /capability/autoconfig | GET /capability/starter/format");
    }
}
