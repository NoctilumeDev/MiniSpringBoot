package com.minispring.core.env;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 默认环境实现：构造时先放入「系统属性、环境变量」两个最高优先级的源头，
 * 配置文件的源头由上层（mini-spring-config）再往里加，落在它们之后。
 *
 * <p>优先级（从高到低）：系统属性 → 环境变量 → （由上层追加的）配置文件。
 */
public class StandardEnvironment implements Environment {

    public static final String SYSTEM_PROPERTIES = "systemProperties";
    public static final String SYSTEM_ENVIRONMENT = "systemEnvironment";

    private final MutablePropertySources propertySources = new MutablePropertySources();
    private String[] activeProfiles = new String[0];

    public StandardEnvironment() {
        propertySources.addLast(new MapPropertySource(SYSTEM_PROPERTIES, copyOf(System.getProperties())));
        propertySources.addLast(new MapPropertySource(SYSTEM_ENVIRONMENT, copyOf(System.getenv())));
    }

    private static Map<String, Object> copyOf(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    public MutablePropertySources getPropertySources() {
        return propertySources;
    }

    public void setActiveProfiles(String... profiles) {
        this.activeProfiles = profiles;
    }

    @Override
    public String[] getActiveProfiles() {
        return activeProfiles;
    }

    @Override
    public String getProperty(String key) {
        for (PropertySource ps : propertySources.asList()) {
            Object value = ps.getProperty(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public boolean containsProperty(String key) {
        return getProperty(key) != null;
    }

    @Override
    public String resolvePlaceholders(String text) {
        if (text == null) {
            return null;
        }
        return doResolve(text, new HashSet<>());
    }

    private String doResolve(String text, Set<String> visiting) {
        int start = text.indexOf("${");
        if (start < 0) {
            return text;
        }
        int end = findPlaceholderEnd(text, start);
        String content = text.substring(start + 2, end);

        // 第一个冒号分隔 key 与默认值（默认值里允许再出现冒号）
        int sep = content.indexOf(':');
        String key = sep >= 0 ? content.substring(0, sep) : content;
        String defaultValue = sep >= 0 ? content.substring(sep + 1) : null;

        // 循环引用防护：${a} 的「整条解析链」结束前，key 始终待在 visiting 里；
        // 若解析值 / 默认值 / 拼接结果里又引回同一 key，立即判死，避免 StackOverflow（D27）。
        if (!visiting.add(key)) {
            throw new IllegalStateException("占位符循环引用: " + key);
        }
        try {
            String resolvedKey = doResolve(key, visiting);

            String value = getProperty(resolvedKey);
            String replacement;
            if (value != null) {
                replacement = value;
            } else if (defaultValue != null) {
                replacement = doResolve(defaultValue, visiting);
            } else {
                throw new IllegalStateException("无法解析占位符 [" + content + "]（在源 [" + text + "] 中）");
            }

            String before = text.substring(0, start);
            String after = text.substring(end + 1);
            return doResolve(before + replacement + after, visiting);
        } finally {
            visiting.remove(key);
        }
    }

    /** 找到与第 {@code start} 个 {@code ${} 匹配的右大括号，正确跳过嵌套占位符。 */
    private static int findPlaceholderEnd(String text, int start) {
        int depth = 1;
        for (int i = start + 2; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                depth++;
                i++; // 跳过 '{'
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("占位符未闭合: " + text);
    }
}