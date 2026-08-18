package com.minispring.boot;

import com.minispring.boot.MiniSpringApplication;
import com.minispring.boot.MiniSpringBootApplication;
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
 * A-3 回归：一条 run() 真实启动内嵌服务器；纯后端应用（无自动配置）不起服务器。
 * 修复前：run() 只建上下文，启动服务器需要 demo 手写 4 行样板。
 */
class MiniSpringApplicationWebServerTest {

    @MiniSpringBootApplication
    static class WebApp {
    }

    @Configuration
    static class PlainApp {
    }

    @Test
    void runStartsEmbeddedWebServerForWebApp() throws Exception {
        AnnotationConfigApplicationContext context = MiniSpringApplication.run(WebApp.class);
        try {
            // 服务器作为运行期单例可取
            WebServer webServer = context.getBean(MiniSpringApplication.WEB_SERVER_BEAN_NAME, WebServer.class);
            assertNotNull(webServer, "run() 应自动启动内嵌服务器并注册为单例");

            // 端口真实监听：打到 9090 必须有 HTTP 响应（DispatcherServlet 的 404 也是响应）
            HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:9090/__probe__").openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int status = conn.getResponseCode();
            assertTrue(status == 404 || status == 200, "探针请求应得到 404/200，实际 " + status);
        } finally {
            context.getBean(MiniSpringApplication.WEB_SERVER_BEAN_NAME, WebServer.class).stop();
        }
    }

    @Test
    void runSkipsServerForPlainBackendApp() {
        AnnotationConfigApplicationContext context = MiniSpringApplication.run(PlainApp.class);
        try {
            assertThrows(BeansException.class,
                    () -> context.getBean(MiniSpringApplication.WEB_SERVER_BEAN_NAME, WebServer.class),
                    "无 DispatcherServlet 的纯后端应用不应注册 webServer");
        } finally {
            context.close();
        }
    }
}
