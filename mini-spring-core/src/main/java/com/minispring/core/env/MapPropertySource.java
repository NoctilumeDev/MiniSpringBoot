package com.minispring.core.env;

import java.util.Map;

/** 以 Map 为后端的配置源头：properties / yaml 解析拍平后都落到这里。 */
public class MapPropertySource implements PropertySource {

    private final String name;
    private final Map<String, Object> source;

    public MapPropertySource(String name, Map<String, Object> source) {
        this.name = name;
        this.source = source;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getProperty(String key) {
        return source.get(key);
    }
}