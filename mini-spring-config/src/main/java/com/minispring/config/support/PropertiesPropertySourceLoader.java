package com.minispring.config.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** 解析扁平的 {@code key=value} 形式的 .properties 文件。 */
public class PropertiesPropertySourceLoader {

    public Map<String, Object> load(InputStream in) {
        Properties props = new Properties();
        try (InputStream ignored = in) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("读取 properties 失败", e);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (String name : props.stringPropertyNames()) {
            map.put(name, props.getProperty(name));
        }
        return map;
    }
}