package com.minispring.config.support;

import com.minispring.core.env.MapPropertySource;
import com.minispring.core.env.StandardEnvironment;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 从 classpath 加载 {@code application.yml} / {@code application.properties} 及其 profile 变体，
 * 按优先级塞进 {@link StandardEnvironment}。
 *
 * <p>优先级（从高到低）：系统属性 → 环境变量 → application-{profile}.* → application.* 。
 */
public class ConfigFilePropertySourceLoader {

    private static final String DEFAULT_NAME = "application";

    public void load(StandardEnvironment environment) {
        // 默认文件遵循 Spring Boot 同位置优先级：.properties 高于 .yml。
        // addLast 追加到末尾，先加入的 index 更小 → 优先级更高，故先 addLast properties。
        String anchor = null;
        if (addLastIfPresent(environment, DEFAULT_NAME + ".properties")) {
            anchor = DEFAULT_NAME + ".properties";
        }
        if (addLastIfPresent(environment, DEFAULT_NAME + ".yml") && anchor == null) {
            anchor = DEFAULT_NAME + ".yml";
        }
        if (anchor == null) {
            return; // 没有任何配置文件
        }

        // profile 文件位于默认文件之前；后激活的 profile 优先。倒序遍历可让最后激活项
        // 最先插入，同层则先插入 properties、再插入 yml，以保持 .properties 优先。
        List<String> profiles = new ArrayList<>(Arrays.asList(environment.getActiveProfiles()));
        for (int i = profiles.size() - 1; i >= 0; i--) {
            String profile = profiles.get(i);
            addBeforeIfPresent(environment, DEFAULT_NAME + "-" + profile + ".properties", anchor);
            addBeforeIfPresent(environment, DEFAULT_NAME + "-" + profile + ".yml", anchor);
        }
    }

    private boolean addLastIfPresent(StandardEnvironment environment, String location) {
        Map<String, Object> map = loadMap(location);
        if (map == null) {
            return false;
        }
        environment.getPropertySources().addLast(new MapPropertySource(location, map));
        return true;
    }

    private void addBeforeIfPresent(StandardEnvironment environment, String location, String anchor) {
        Map<String, Object> map = loadMap(location);
        if (map == null) {
            return;
        }
        environment.getPropertySources().addBefore(anchor, new MapPropertySource(location, map));
    }

    private Map<String, Object> loadMap(String location) {
        InputStream in = getClass().getClassLoader().getResourceAsStream(location);
        if (in == null) {
            return null;
        }
        return (location.endsWith(".yml") || location.endsWith(".yaml"))
                ? new YamlPropertySourceLoader().load(in)
                : new PropertiesPropertySourceLoader().load(in);
    }
}
