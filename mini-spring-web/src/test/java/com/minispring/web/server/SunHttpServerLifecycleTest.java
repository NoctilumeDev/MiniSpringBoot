package com.minispring.web.server;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    @Test
    void stopAllowsAcceptedResponseToFinishBeforeClosingWorkers() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch stopping = new CountDownLatch(1);
        SunHttpServer webServer = new SunHttpServer((request, response) -> {
            entered.countDown();
            if (release.await(10, TimeUnit.SECONDS)) {
                response.write("complete");
            }
        });
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            webServer.start(0);
            int port = portOf(webServer);
            ExecutorService workers = executorOf(webServer);
            Future<String> response = callers.submit(() -> request(port));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            Future<?> stopped = callers.submit(() -> {
                stopping.countDown();
                webServer.stop();
            });
            assertTrue(stopping.await(3, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> stopped.get(200, TimeUnit.MILLISECONDS),
                    "stop 不应立即切断已经接收但尚未完成的响应");
            release.countDown();
            assertEquals("complete", response.get(5, TimeUnit.SECONDS));
            stopped.get(8, TimeUnit.SECONDS);
            assertTrue(workers.isTerminated());
            assertThrows(IOException.class, () -> request(port));
            webServer.stop();
        } finally {
            release.countDown();
            webServer.stop();
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void stopBoundsGracePeriodAndInterruptsStalledCooperativeHandler() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        SunHttpServer webServer = new SunHttpServer((request, response) -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        });
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            webServer.start(0);
            int port = portOf(webServer);
            ExecutorService workers = executorOf(webServer);
            Future<Boolean> disconnected = caller.submit(() -> {
                try {
                    request(port);
                    return false;
                } catch (IOException expected) {
                    return true;
                }
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            long started = System.nanoTime();
            webServer.stop();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMillis >= 4000, "停滞请求也应得到约定的 5 秒宽限期");
            assertTrue(elapsedMillis < 12000, "停机不得无限等待处理器");
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertTrue(workers.isTerminated());
            assertTrue(disconnected.get(3, TimeUnit.SECONDS));
            assertThrows(IOException.class, () -> request(port));
        } finally {
            release.countDown();
            webServer.stop();
            caller.shutdownNow();
            assertTrue(caller.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    private static String request(SunHttpServer webServer) throws Exception {
        return request(portOf(webServer));
    }

    private static int portOf(SunHttpServer webServer) throws Exception {
        HttpServer server = (HttpServer) field("server").get(webServer);
        return server.getAddress().getPort();
    }

    private static String request(int port) throws IOException {
        URL url = new URL("http://127.0.0.1:" + port + "/probe");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(10000);
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
