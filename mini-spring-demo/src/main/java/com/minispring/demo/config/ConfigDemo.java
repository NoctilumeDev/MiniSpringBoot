package com.minispring.demo.config;

import com.minispring.config.support.ConfigFilePropertySourceLoader;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;
import com.minispring.core.env.StandardEnvironment;

/**
 * M4 落地演示（随「demo 收口」迁至 mini-spring-demo）：
 * 从 {@code application.yml} / {@code application-prod.yml} 真实读值，切换 profile 验证覆盖。
 */
public class ConfigDemo {

    public static void main(String[] args) {
        System.out.println("=== M4 外部化配置落地演示（已迁至 demo 模块）===");
        run("prod");
        System.out.println();
        run(null); // 不指定 profile，回到默认配置
        System.out.println("=== M4 落地验证通过 ===");
    }

    private static void run(String profile) {
        StandardEnvironment environment = new StandardEnvironment();
        if (profile != null) {
            environment.setActiveProfiles(profile);
        }
        // 从 classpath 加载 application.yml + application-{profile}.yml + application.properties
        new ConfigFilePropertySourceLoader().load(environment);

        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(environment, ConfigConfig.class);
        ConfigAppProperties app = ctx.getBean("configAppProperties", ConfigAppProperties.class);

        System.out.println("    --- profile = " + (profile == null ? "(默认)" : profile) + " ---");
        check("MiniSpringBoot".equals(app.getName()), "@Value 注入 app.name（yaml）");
        check("properties-value".equals(app.getOwner()), "@Value 注入 app.owner=properties-value（同层 properties 优先于 yml）");
        check(app.getTimeout() == 30, "@Value 注入 app.timeout（int 类型转换）");
        check(app.getFallbackTimeout() == 3000, "@Value 默认值 ${app.missing:3000}");
        check("ioc".equals(app.getFirstFeature()) && "aop".equals(app.getSecondFeature()),
                "@Value 注入 app.features[0]/[1]（yaml 列表拍平）");
        check("0.4.0".equals(app.getVersion()), "@Value 注入 app.version（properties 解析）");
        check("localhost".equals(app.getSmtpHost()), "@Value 注入 smtp.host（yaml 二级嵌套）");
        check("MiniSpringBoot".equals(app.getNestedPlaceholder()), "@Value 嵌套占位符 ${app.${app.pointer}}");

        if ("prod".equals(profile)) {
            check(app.getPort() == 8443, "@Value 注入 server.port=8443（prod 覆盖默认）");
            check("properties-profile-wins".equals(app.getConflict()),
                    "@Value 注入 app.conflict=properties-profile-wins（profile 层 properties 优先于 yml）");
        } else {
            check(app.getPort() == 9090, "@Value 注入 server.port=9090（默认值）");
        }
        System.out.println("    [OK] 环境真实生效：name=" + app.getName() + ", port=" + app.getPort());
        ctx.close();
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException("验收失败: " + message);
        }
        System.out.println("    [OK] " + message);
    }
}
