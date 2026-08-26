package com.minispring.web.server;

import com.sun.net.httpserver.HttpServer;
import com.minispring.web.http.HttpStatusException;
import com.minispring.web.mvc.HandlerMapping;
import com.minispring.web.servlet.DispatcherServlet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SunHttpServerLifecycleTest {

    @Test
    void rejectsResourceBudgetsOutsideHardLimitsBeforeStartup() {
        HttpHandler handler = (request, response) -> response.write("ok");

        assertThrows(IllegalArgumentException.class,
                () -> new SunHttpServer(handler, "127.0.0.1", 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SunHttpServer(handler, "127.0.0.1",
                        SunHttpServer.MAX_WORKER_THREADS + 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SunHttpServer(handler, "127.0.0.1", 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SunHttpServer(handler, "127.0.0.1", 1,
                        SunHttpServer.MAX_QUEUE_CAPACITY + 1));
    }

    @Test
    void stopOwnsExecutorLifecycleAndAllowsCleanRestart() throws Exception {
        SunHttpServer webServer = new SunHttpServer((request, response) -> response.write("ok"));
        ExecutorService firstExecutor = null;
        ExecutorService secondExecutor = null;
        try {
            webServer.start(0);
            firstExecutor = executorOf(webServer);
            assertFalse(firstExecutor.isShutdown());
            assertTrue(serverOf(webServer).getAddress().getAddress().isLoopbackAddress(),
                    "默认绑定必须限制在 loopback");
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
    void saturationStaysInsideThreadAndQueueBudgets() throws Exception {
        int workerThreads = 2;
        int queueCapacity = 2;
        CountDownLatch workersEntered = new CountDownLatch(workerThreads);
        CountDownLatch releaseHandlers = new CountDownLatch(1);
        AtomicInteger activeHandlers = new AtomicInteger();
        AtomicInteger peakHandlers = new AtomicInteger();
        SunHttpServer webServer = new SunHttpServer((request, response) -> {
            int active = activeHandlers.incrementAndGet();
            peakHandlers.accumulateAndGet(active, Math::max);
            workersEntered.countDown();
            try {
                if (!releaseHandlers.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test release timeout");
                }
                response.write("ok");
            } finally {
                activeHandlers.decrementAndGet();
            }
        }, "127.0.0.1", workerThreads, queueCapacity);

        ExecutorService clients = Executors.newFixedThreadPool(5);
        CountDownLatch startClients = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            webServer.start(0);
            for (int i = 0; i < 5; i++) {
                results.add(clients.submit(() -> {
                    startClients.await();
                    try {
                        return requestResult(webServer).status;
                    } catch (ConnectException saturatedBacklog) {
                        return -1;
                    }
                }));
            }
            startClients.countDown();

            assertTrue(workersEntered.await(5, TimeUnit.SECONDS), "固定 worker 应进入阻塞点");
            ThreadPoolExecutor bounded = (ThreadPoolExecutor) executorOf(webServer);
            long queueDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (bounded.getQueue().size() < queueCapacity && System.nanoTime() < queueDeadline) {
                Thread.sleep(10);
            }
            assertEquals(workerThreads, bounded.getMaximumPoolSize());
            assertEquals(queueCapacity, bounded.getQueue().size(), "饱和时队列不得越过配置上限");
            assertTrue(bounded.getLargestPoolSize() <= workerThreads, "饱和时不得增生工作线程");
            assertTrue(peakHandlers.get() <= workerThreads + 1,
                    "CallerRuns 只允许 dispatcher 形成一个反压执行点");

            releaseHandlers.countDown();
            int successfulResponses = 0;
            for (Future<Integer> result : results) {
                int status = result.get(5, TimeUnit.SECONDS);
                assertTrue(status == 200 || status == -1,
                        "饱和边界只能完成请求或在 TCP backlog 明确拒绝，实际 " + status);
                if (status == 200) {
                    successfulResponses++;
                }
            }
            assertTrue(successfulResponses >= workerThreads + queueCapacity,
                    "已进入 worker/queue 预算的请求必须完整处理");
        } finally {
            releaseHandlers.countDown();
            webServer.stop();
            clients.shutdownNow();
            assertTrue(clients.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void outerFailureBoundaryDoesNotExposeExceptionMessage() throws Exception {
        SunHttpServer webServer = new SunHttpServer((request, response) -> {
            throw new IllegalStateException("jdbc:mysql://db/internal?password=secret");
        });
        try {
            webServer.start(0);
            Response response = requestResult(webServer);
            assertEquals(500, response.status);
            assertEquals("500 Internal Server Error", response.body);
            assertFalse(response.body.contains("password"));
            assertFalse(response.body.contains("secret"));
        } finally {
            webServer.stop();
        }
    }

    @Test
    void outerBoundaryPreservesExplicitClientErrorsForDirectHandlers() throws Exception {
        SunHttpServer webServer = new SunHttpServer((request, response) -> {
            throw new HttpStatusException(413, "payload rejected");
        });
        try {
            webServer.start(0);
            Response response = requestResult(webServer);
            assertEquals(413, response.status);
            assertEquals("413 Payload Too Large: payload rejected", response.body);
        } finally {
            webServer.stop();
        }
    }

    @Test
    void realHttpRequestsRejectOversizedAndMalformedUtf8Bodies() throws Exception {
        DispatcherServlet dispatcher = new DispatcherServlet();
        Field mapping = DispatcherServlet.class.getDeclaredField("handlerMapping");
        mapping.setAccessible(true);
        mapping.set(dispatcher, (HandlerMapping) request -> {
            request.getBody();
            return null;
        });

        SunHttpServer webServer = new SunHttpServer(dispatcher);
        try {
            webServer.start(0);

            Response oversized = postResult(webServer, new byte[SunHttpRequest.MAX_BODY_BYTES + 1]);
            assertEquals(413, oversized.status);
            assertTrue(oversized.body.startsWith("413 Payload Too Large"));

            Response malformedUtf8 = postResult(webServer, new byte[]{(byte) 0xc3, 0x28});
            assertEquals(400, malformedUtf8.status);
            assertTrue(malformedUtf8.body.contains("请求体不是合法 UTF-8"));
        } finally {
            webServer.stop();
        }
    }

    private static String request(SunHttpServer webServer) throws Exception {
        Response response = requestResult(webServer);
        assertEquals(200, response.status);
        return response.body;
    }

    private static Response requestResult(SunHttpServer webServer) throws Exception {
        HttpServer server = serverOf(webServer);
        URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/probe");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        try {
            int status = connection.getResponseCode();
            var stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = stream == null ? ""
                    : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new Response(status, body);
        } finally {
            connection.disconnect();
        }
    }

    private static Response postResult(SunHttpServer webServer, byte[] body) throws Exception {
        HttpServer server = serverOf(webServer);
        URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/probe");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setFixedLengthStreamingMode(body.length);
        connection.setDoOutput(true);
        try {
            connection.getOutputStream().write(body);
            int status = connection.getResponseCode();
            var stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String responseBody = stream == null ? ""
                    : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new Response(status, responseBody);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpServer serverOf(SunHttpServer webServer) throws Exception {
        return (HttpServer) field("server").get(webServer);
    }

    private static ExecutorService executorOf(SunHttpServer webServer) throws Exception {
        return (ExecutorService) field("executor").get(webServer);
    }

    private static Field field(String name) throws Exception {
        Field field = SunHttpServer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private record Response(int status, String body) {
    }
}
