package com.minispring.config.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** 解析扁平的 {@code key=value} 形式的 .properties 文件。 */
public class PropertiesPropertySourceLoader {

    public Map<String, Object> load(InputStream in) {
        Properties props = new Properties();
        // M3（M0-M9 复审第二轮）：必须经 Reader 按 UTF-8 读——Properties.load(InputStream)
        // 按 JDBC 时代规范固定 ISO-8859-1，中文值必乱码；与 YamlPropertySourceLoader 的
        // UTF-8 读取对称（此前 demo 无中文 properties 值所以未暴露）
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            props.load(reader);
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