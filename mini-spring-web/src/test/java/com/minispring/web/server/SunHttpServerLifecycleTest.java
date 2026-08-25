package com.minispring.web.server;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SunHttpServerLifecycleTest {

    @Test
    void stopOwnsExecutorLifecycleAndAllowsCleanRestart() throws Exception {
        SunHttpServer webServer = new SunHttpServer((request, response) -> response.write("ok"));
        ExecutorService firstExecutor = null;
        ExecutorService secondExecutor = null;
        try {
            webServer.start(0);
            firstExecutor = executorOf(webServer);
            assertFalse(firstExecutor.isShutdown());
            assertEquals("ok", request(webServer));
            assertThrows(IllegalStateException.class, () -> webServer.start(0),
                    "同一实例重复 start 不得覆盖并泄漏原服务器");

            webServer.stop();
            assertTrue(firstExecutor.isShutdown(), "stop 必须关闭由服务器创建的 executor");
            assertTrue(firstExecutor.awaitTermination(3, TimeUnit.SECONDS), "工作线程应在 stop 后退出");
            assertNull(field("server").get(webServer));
            assertNull(field("executor").get(webServer));

            webServer.start(0);
            secondExecutor = executorOf(webServer);
            assertNotSame(firstExecutor, secondExecutor, "重启必须创建新的 executor 生命周期");
            assertEquals("ok", request(webServer));
        } finally {
            webServer.stop();
            if (firstExecutor != null) {
                assertTrue(firstExecutor.isShutdown());
            }
            if (secondExecutor != null) {
                assertTrue(secondExecutor.isShutdown());
                assertTrue(secondExecutor.awaitTermination(3, TimeUnit.SECONDS));
            }
        }

        // stop 是幂等收口操作，重复调用不能重新创建资源或抛错。
        webServer.stop();
    }

    private static String request(SunHttpServer webServer) throws Exception {
        HttpServer server = (HttpServer) field("server").get(webServer);
        URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/probe");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        try {
            assertEquals(200, connection.getResponseCode());
            return new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static ExecutorService executorOf(SunHttpServer webServer) throws Exception {
        return (ExecutorService) field("executor").get(webServer);
    }

    private static Field field(String name) throws Exception {
        Field field = SunHttpServer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
