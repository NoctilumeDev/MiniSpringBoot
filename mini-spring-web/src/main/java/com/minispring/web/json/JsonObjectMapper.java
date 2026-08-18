package com.minispring.web.json;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 自写 JSON 的高级入口：把「JSON 文本 ↔ Java 对象」封装成两个方法，
 * 内部复用 {@link JsonParser}（反序列化）与 {@link JsonSerializer}（序列化），
 * 以及反射字段映射——是 {@code @RequestBody} / {@code @ResponseBody} 背后的那层「Jackson 等价物」。
 */
public final class JsonObjectMapper {

    private final JsonSerializer serializer = new JsonSerializer();

    public String writeValueAsString(Object value) {
        return serializer.serialize(value);
    }

    public <T> T readValue(String json, Class<T> targetType) {
        JsonNode node = new JsonParser(json).parse();
        return targetType.cast(mapToObject(node, targetType));
    }

    @SuppressWarnings("unchecked")
    private Object mapToObject(JsonNode node, Class<?> targetType) {
        if (node.isNull()) {
            return null;
        }
        if (targetType == String.class) {
            return node.asString();
        }
        if (targetType == int.class || targetType == Integer.class) {
            return node.asInt();
        }
        if (targetType == long.class || targetType == Long.class) {
            return node.asLong();
        }
        if (targetType == double.class || targetType == Double.class) {
            return node.asDouble();
        }
        if (targetType == float.class || targetType == Float.class) {
            return (float) node.asDouble();
        }
        if (targetType == short.class || targetType == Short.class) {
            return (short) node.asInt();
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return (byte) node.asInt();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return node.asBoolean();
        }
        if (targetType == char.class || targetType == Character.class) {
            String s = node.asString();
            return (s == null || s.isEmpty()) ? '\0' : s.charAt(0);
        }
        if (node.isObject()) {
            return mapToPojo(node, targetType);
        }
        if (node.isArray() && List.class.isAssignableFrom(targetType)) {
            List<Object> list = new ArrayList<>();
            for (JsonNode item : node.items()) {
                list.add(item == null ? null : item.asString());
            }
            return list;
        }
        throw new IllegalArgumentException("无法将 JSON 映射到类型 " + targetType.getName());
    }

    private Object mapToPojo(JsonNode node, Class<?> targetType) {
        try {
            Constructor<?> constructor = targetType.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object instance = constructor.newInstance();
            for (Field field : collectFields(targetType)) {
                JsonNode child = node.get(field.getName());
                if (child == null) {
                    continue;
                }
                field.setAccessible(true);
                field.set(instance, mapToObject(child, field.getType()));
            }
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("构造对象失败: " + targetType.getName(), e);
        }
    }

    private List<Field> collectFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }
}