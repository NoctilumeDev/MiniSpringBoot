package com.minispring.context.annotation;

import com.minispring.core.BeansException;
import com.minispring.core.DisposableBean;
import com.minispring.core.InitializingBean;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextRefreshFailureTest {
    private static final List<Resource> resources = new ArrayList<>();
    private static final List<String> destroyed = new ArrayList<>();

    @Configuration
    static class FailedRefresh {
        @Bean Resource first() throws IOException { return new Resource("first"); }
        @Bean Resource second(@Qualifier("first") Resource first) throws IOException {
            return new Resource("second");
        }
        @Bean BadInit broken(@Qualifier("second") Resource second) { return new BadInit(); }
    }

    @Configuration
    static class SuccessfulRefresh {
        @Bean Resource first() throws IOException { return new Resource("first"); }
    }

    static class BadInit implements InitializingBean {
        @Override public void afterPropertiesSet() { throw new IllegalStateException("init failed"); }
    }

    static class Resource implements DisposableBean {
        final String name;
        final ServerSocket socket = new ServerSocket(0);
        Resource(String name) throws IOException { this.name = name; resources.add(this); }
        @Override public void destroy() throws IOException { destroyed.add(name); socket.close(); }
    }

    @Test
    void failedRefreshReleasesCompletedBeansInReverseCreationOrder() throws Exception {
        resources.clear();
        destroyed.clear();
        try {
            assertThrows(BeansException.class, () -> new AnnotationConfigApplicationContext(FailedRefresh.class));
            assertEquals(List.of("second", "first"), destroyed);
            assertTrue(resources.stream().allMatch(r -> r.socket.isClosed()));
        } finally {
            for (Resource resource : resources) { resource.socket.close(); }
        }
    }

    @Test
    void successfulRefreshKeepsResourcesUntilNormalClose() throws Exception {
        resources.clear();
        destroyed.clear();
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SuccessfulRefresh.class);
        try {
            assertFalse(context.getBean("first", Resource.class).socket.isClosed());
        } finally {
            context.close();
        }
        assertEquals(List.of("first"), destroyed);
        assertTrue(resources.get(0).socket.isClosed());
    }
}
