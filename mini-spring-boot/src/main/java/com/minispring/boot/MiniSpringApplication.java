package com.minispring.boot;

import com.minispring.config.support.ConfigFilePropertySourceLoader;
import com.minispring.context.Lifecycle;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;
import com.minispring.core.env.StandardEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 应用启动器：把「手动 new 环境 + 手动加载配置 + 手写 @Bean 注册基础设施 + 手动起服务器」的样板，
 * 收敛成一条 {@link #run(Class, String...)}。
 *
 * <p>启动顺序：建环境 → 加载 {@code application.*} → 建上下文（注册/条件装配/延迟导入/refresh，
 * refresh 完广播 {@code ContextRefreshedEvent}）→ <b>启动全部 {@link Lifecycle} 组件</b>（A-3/D45 收口：
 * 内嵌服务器由 web 自动配置以 Lifecycle 形态装配，此处只驱动接口、不引用任何 web 类——
 * 无 web 依赖的纯后端应用自动跳过，与 spring-boot 只认 Lifecycle 不认 Tomcat 同构）→
 * Banner → 广播 {@link StartedEvent} → 注册 JVM 关闭钩子（逆序停 Lifecycle + 关上下文）。
 */
public final class MiniSpringApplication {

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

        // 4) A-3/D45：驱动容器里全部 Lifecycle 组件（如内嵌服务器）；纯后端应用无此类组件，自然跳过
        List<Lifecycle> lifecycles = new ArrayList<>();
        for (String name : context.getBeanNamesForType(Lifecycle.class)) {
            lifecycles.add(context.getBean(name, Lifecycle.class));
        }
        for (Lifecycle lifecycle : lifecycles) {
            lifecycle.start();
        }

        // 5) Banner + 启动耗时
        Banner.print(System.currentTimeMillis() - start);

        // 6) 广播「启动完成」
        context.publishEvent(new StartedEvent(context));

        // 7) 关闭钩子：与启动顺序相反——逆序停 Lifecycle，再关上下文（广播 Closed + 销毁 Bean）
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Collections.reverse(lifecycles);
            for (Lifecycle lifecycle : lifecycles) {
                try {
                    lifecycle.stop();
                } catch (RuntimeException e) {
                    System.err.println("停止 Lifecycle[" + lifecycle.getClass().getName() + "]失败: " + e);
                }
            }
            context.close();
        }, "minispring-shutdown"));

        // args 暂保留：留给后续「命令行参数 → 属性 / profile / 端口」覆盖用，教学子集不解析
        return context;
    }
}
