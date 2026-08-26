package com.minispring.boot;

import com.minispring.context.annotation.AnnotationConfigApplicationContext;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在真正裁掉 optional jar 的新 JVM 中启动应用。普通单测的测试 classpath 总带 HikariCP，
 * 无法发现公共签名或父类把 optional 类型泄漏到类加载边界的问题。
 */
class OptionalClasspathIsolationTest {

    @Test
    void applicationStartsWithoutHikariJdbcOrWebJars() throws Exception {
        String fullClasspath = System.getProperty(
                "surefire.test.class.path", System.getProperty("java.class.path"));
        String isolatedClasspath = Arrays.stream(fullClasspath.split(
                        java.util.regex.Pattern.quote(File.pathSeparator)))
                .filter(OptionalClasspathIsolationTest::isStableClasspathEntry)
                .reduce((left, right) -> left + File.pathSeparator + right)
                .orElseThrow(() -> new IllegalStateException("隔离 classpath 为空"));

        assertFalse(isolatedClasspath.toLowerCase(Locale.ROOT).contains("hikaricp"));
        assertFalse(isolatedClasspath.toLowerCase(Locale.ROOT).contains("mini-spring-jdbc"));
        assertFalse(isolatedClasspath.toLowerCase(Locale.ROOT).contains("mini-spring-web"));

        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe" : "java";
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", isolatedClasspath,
                Probe.class.getName())
                .redirectErrorStream(true)
                .start();

        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            throw new AssertionError("隔离 classpath 子进程未在 15 秒内退出");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), () -> "隔离 classpath 启动失败:\n" + output);
        assertTrue(output.contains("OPTIONAL_CLASSPATH_OK"), () -> "缺少成功锚点:\n" + output);
    }

    private static boolean isStableClasspathEntry(String entry) {
        String normalized = entry.replace('\\', '/').toLowerCase(Locale.ROOT);
        String fileName = Path.of(entry).getFileName().toString().toLowerCase(Locale.ROOT);
        return !normalized.contains("/hikaricp/")
                && !fileName.startsWith("hikaricp-")
                && !normalized.contains("/mini-spring-jdbc/")
                && !fileName.startsWith("mini-spring-jdbc-")
                && !normalized.contains("/mini-spring-web/")
                && !fileName.startsWith("mini-spring-web-");
    }

    public static final class Probe {
        public static void main(String[] args) {
            AnnotationConfigApplicationContext context =
                    MiniSpringApplication.run(ProbeApplication.class);
            try {
                if (context.containsBean("dataSource") || context.containsBean("webServer")) {
                    throw new IllegalStateException("裁掉 optional jar 后不应装配数据源或服务器");
                }
                System.out.println("OPTIONAL_CLASSPATH_OK");
            } finally {
                context.close();
            }
        }
    }

    @MiniSpringBootApplication
    static class ProbeApplication {
    }
}
