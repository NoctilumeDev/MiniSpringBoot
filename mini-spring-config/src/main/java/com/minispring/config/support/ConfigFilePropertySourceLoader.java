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
        // 默认文件：properties 优先于 yml（与 Spring Boot 官方一致：同位置 .properties > .yml——
        // D37 曾按「yml 覆盖 properties」反向修复，经官方文档事实核查后纠正）。
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

        // 带 profile 的文件，优先级高于默认文件（插到锚点之前）；同层内 properties 优先于 yml。
        // addBefore 是「后插入的优先级更高」，故先插 properties 再插 yml → properties 靠前。
        for (String profile : environment.getActiveProfiles()) {
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