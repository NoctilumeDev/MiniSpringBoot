package com.minispring.boot;

import com.minispring.autoconfigure.web.WebMvcAutoConfiguration;
import com.minispring.context.annotation.AnnotationConfigApplicationContext;
import com.minispring.context.annotation.Configuration;
import com.minispring.core.BeansException;
import com.minispring.web.server.WebServer;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A-3/D45 回归：一条 run() 经 Lifecycle 机制真实启动内嵌服务器（boot 不引用任何 web 类）；
 * 纯后端应用（classpath 无 web 自动配置可装配）不起服务器。
 */
class MiniSpringApplicationWebServerTest {

    @MiniSpringBootApplication
    static class WebApp {
    }

    @Configuration
    static class PlainApp {
    }

    @Test
    void runStartsEmbeddedServerViaLifecycle() throws Exception {
        AnnotationConfigApplicationContext context = MiniSpringApplication.run(WebApp.class);
        try {
            // 服务器作为运行期单例可取（test 作用域引 web 接口仅为断言与清理）
            WebServer webServer = context.getBean(WebMvcAutoConfiguration.WEB_SERVER_BEAN_NAME, WebServer.class);
            assertNotNull(webServer, "run() 应经 Lifecycle 启动内嵌服务器并注册为单例");

            // 端口真实监听：打到 9090 必须有 HTTP 响应（DispatcherServlet 的 404 也是响应）
            HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:9090/__probe__").openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int status = conn.getResponseCode();
            assertTrue(status == 404 || status == 200, "探针请求应得到 404/200，实际 " + status);
        } finally {
            context.getBean(WebMvcAutoConfiguration.WEB_SERVER_BEAN_NAME, WebServer.class).stop();
        }
    }

    @Test
    void runSkipsServerForPlainBackendApp() {
        AnnotationConfigApplicationContext context = MiniSpringApplication.run(PlainApp.class);
        try {
            assertThrows(BeansException.class,
                    () -> context.getBean(WebMvcAutoConfiguration.WEB_SERVER_BEAN_NAME, WebServer.class),
                    "无 web 自动配置的纯后端应用不应注册 webServer");
        } finally {
            context.close();
        }
    }
}
