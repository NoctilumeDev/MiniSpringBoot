package com.minispring.boot;

import com.minispring.context.Lifecycle;
import com.minispring.context.annotation.Bean;
import com.minispring.core.DisposableBean;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniSpringApplicationFailureTest {
    private static final List<String> starts = new ArrayList<>();
    private static final List<String> stops = new ArrayList<>();
    private static SocketResource resource;
    private static Throwable startupFailure;
    private static final Error STOP_FAILURE = new AssertionError("stop failed");

    // 显式作为 run 的入口注册；不参与其他测试应用的 @ComponentScan。
    static class PartialApplication {
        @Bean SocketResource resource() throws IOException { return new SocketResource(); }
        @Bean Lifecycle first(SocketResource resource) { return new Component("first"); }
        @Bean Lifecycle second(SocketResource resource) { return new Component("second"); }
        @Bean Lifecycle third(SocketResource resource) { return new Component("third"); }
    }

    static class SocketResource implements DisposableBean {
        final ServerSocket socket = new ServerSocket(0);
        SocketResource() throws IOException { resource = this; }
        @Override public void destroy() throws IOException { socket.close(); }
    }

    static class Component implements Lifecycle {
        private final String name;
        Component(String name) { this.name = name; }
        @Override public void start() {
            starts.add(name);
            if (starts.size() == 2) {
                if (startupFailure instanceof Error error) { throw error; }
                throw (RuntimeException) startupFailure;
            }
        }
        @Override public void stop() {
            stops.add(name);
            if (name.equals(starts.get(1))) { throw STOP_FAILURE; }
        }
    }

    @Test
    void failedStartStopsAttemptedComponentsInReverseAndClosesBeans() throws Exception {
        verifyCleanup(new IllegalStateException("start failed"));
    }

    @Test
    void startupErrorIsPreservedEvenIfStopAlsoThrowsError() throws Exception {
        verifyCleanup(new AssertionError("start failed"));
    }

    private static void verifyCleanup(Throwable failure) throws Exception {
        starts.clear();
        stops.clear();
        startupFailure = failure;
        try {
            Throwable actual = assertThrows(failure.getClass(),
                    () -> MiniSpringApplication.run(PartialApplication.class));
            assertSame(failure, actual, "cleanup must preserve the original startup failure");
            assertEquals(2, starts.size(), "a component after the failure must not start");
            List<String> reversed = new ArrayList<>(starts);
            Collections.reverse(reversed);
            assertEquals(reversed, stops, "only attempted components stop, in reverse order");
            assertTrue(resource.socket.isClosed(), "the owning context must close its beans");
            assertTrue(List.of(actual.getSuppressed()).contains(STOP_FAILURE));
        } finally {
            if (resource != null) { resource.socket.close(); }
        }
    }
}
