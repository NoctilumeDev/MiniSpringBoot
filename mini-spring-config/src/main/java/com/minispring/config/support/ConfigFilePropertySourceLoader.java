package com.minispring.config.support;

import com.minispring.core.env.MapPropertySource;
import com.minispring.core.env.StandardEnvironment;

import java.io.InputStream;
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
        // 默认文件：properties 最低、yml 次之（下标越小优先级越高，故 yml 先 addLast 拿到更高优先级）；
        // 记录第一个加载的默认文件源作为插入 profile 的锚点。
        String anchor = null;
        if (addLastIfPresent(environment, DEFAULT_NAME + ".yml")) {
            anchor = DEFAULT_NAME + ".yml";
        }
        if (addLastIfPresent(environment, DEFAULT_NAME + ".properties") && anchor == null) {
            anchor = DEFAULT_NAME + ".properties";
        }
        if (anchor == null) {
            return; // 没有任何配置文件
        }

        // 带 profile 的文件，优先级高于默认文件（插到锚点之前）；与默认层同规则：yml 高于 properties，故先插 yml
        for (String profile : environment.getActiveProfiles()) {
            addBeforeIfPresent(environment, DEFAULT_NAME + "-" + profile + ".yml", anchor);
            addBeforeIfPresent(environment, DEFAULT_NAME + "-" + profile + ".properties", anchor);
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