package com.minispring.autoconfigure;

import com.minispring.core.BeansException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 自动配置 SPI 文件的读取器。
 *
 * <p>约定：任何「starter」只需在某 jar 的 classpath 根放一个
 * {@code META-INF/minispring/EnableAutoConfiguration.imports} 文件，每行一个自动配置类全限定名，
 * 即可被自动配置机制发现——与 Spring Boot 3 的 imports 文件同构，是「添加依赖即生效」的载体。
 */
public final class AutoConfigurationLoader {

    public static final String IMPORT_FILE = "META-INF/minispring/EnableAutoConfiguration.imports";

    private AutoConfigurationLoader() {
    }

    /**
     * 汇总 classpath 上所有同名 SPI 文件中的类名（去重不在此处，靠注册时的 beanName 去重兜底）。
     */
    public static String[] load(ClassLoader classLoader) {
        List<String> classNames = new ArrayList<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(IMPORT_FILE);
            while (resources.hasMoreElements()) {
                readClassNames(resources.nextElement(), classNames);
            }
        } catch (IOException e) {
            throw new BeansException("读取自动配置 SPI 文件[" + IMPORT_FILE + "]失败", e);
        }
        return classNames.toArray(new String[0]);
    }

    private static void readClassNames(URL url, List<String> classNames) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String name = line.trim();
                if (name.isEmpty() || name.startsWith("#")) {
                    continue;
                }
                classNames.add(name);
            }
        }
    }
}