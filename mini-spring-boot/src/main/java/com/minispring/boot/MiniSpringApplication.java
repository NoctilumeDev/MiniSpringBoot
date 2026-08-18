package com.minispring.boot;

import com.minispring.config.support.ConfigFilePropertySourceLoader;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;
import com.minispring.core.env.Environment;
import com.minispring.core.env.StandardEnvironment;
import com.minispring.web.server.SunHttpServer;
import com.minispring.web.server.WebServer;
import com.minispring.web.servlet.DispatcherServlet;

/**
 * 应用启动器：把「手动 new 环境 + 手动加载配置 + 手写 @Bean 注册基础设施 + 手动起服务器」的样板，
 * 收敛成一条 {@link #run(Class, String...)}。
 *
 * <p>启动顺序：建环境 → 加载 {@code application.*} → 建上下文（内部完成注册/条件装配/延迟导入/refresh，
 * refresh 完广播 {@code ContextRefreshedEvent}）→ <b>Web 环境探测并启动内嵌服务器</b>（A-3 收口：
 * 容器里存在 {@link DispatcherServlet} Bean 时自动创建 {@link SunHttpServer} 并监听
 * {@code server.port}，缺省 9090；无 DispatcherServlet 的纯后端应用自动跳过）→ 打印 Banner →
 * 广播 {@link StartedEvent} → 注册 JVM 关闭钩子（停服务器 + 关上下文）。
 */
public final class MiniSpringApplication {

    /** 内嵌服务器注册进容器的单例名，业务代码可由此拿到服务器做停机等控制。 */
    public static final String WEB_SERVER_BEAN_NAME = "webServer";

    private MiniSpringApplication() {
    }

    public static AnnotationConfigApplicationContext run(Class<?> primarySource, String... args) {
        long start = System.currentTimeMillis();

        // 1) 环境：系统属性 + 环境变量
        StandardEnvironment environment = new StandardEnvironment();
        // 2) 加载 classpath 上的 application.properties / application.yml 及 profile 变体
        new ConfigFilePropertySourceLoader().load(environment);

        // 3) 建上下文；构造器内部已完成「注册 + 条件装配 + 延迟导入 + refresh」
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(environment, primarySource);

        // 4) A-3：Web 环境探测 —— 有 DispatcherServlet 就自动起内嵌服务器，一条 run() 真实启动
        WebServer webServer = startWebServerIfPresent(context, environment);

        // 5) Banner + 启动耗时
        Banner.print(System.currentTimeMillis() - start);

        // 6) 广播「启动完成」
        context.publishEvent(new StartedEvent(context));

        // 7) 关闭钩子：先停服务器再关上下文（与启动顺序相反）
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (webServer != null) {
                webServer.stop();
            }
            context.close();
        }, "minispring-shutdown"));

        // args 暂保留：留给后续「命令行参数 → 属性 / profile / 端口」覆盖用，教学子集不解析
        return context;
    }

    /** 容器里存在 {@link DispatcherServlet} Bean → 启动内嵌服务器并注册为单例；否则返回 null（纯后端应用）。 */
    private static WebServer startWebServerIfPresent(AnnotationConfigApplicationContext context, Environment environment) {
        String[] names = context.getBeanNamesForType(DispatcherServlet.class);
        if (names.length == 0) {
            return null;
        }
        DispatcherServlet servlet = context.getBean(names[0], DispatcherServlet.class);
        int port = Integer.parseInt(environment.getProperty("server.port", "9090"));
        WebServer webServer = new SunHttpServer(servlet);
        webServer.start(port);
        context.registerSingleton(WEB_SERVER_BEAN_NAME, webServer);
        System.out.println("  [http] 内嵌服务器已启动: http://localhost:" + port);
        return webServer;
    }
}
