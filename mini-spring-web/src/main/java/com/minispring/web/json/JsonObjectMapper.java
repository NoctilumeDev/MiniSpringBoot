package com.minispring.web.json;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
            // B-6：顶层 List（无字段泛型可依），按节点自然类型映射而非一律 asString
            List<Object> list = new ArrayList<>();
            for (JsonNode item : node.items()) {
                list.add(item == null || item.isNull() ? null : naturalValue(item));
            }
            return list;
        }
        throw new IllegalArgumentException("无法将 JSON 映射到类型 " + targetType.getName());
    }

    /** 无显式元素类型（raw List / 顶层 List）时，按 JSON 节点自身类型取自然值。 */
    private Object naturalValue(JsonNode node) {
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isString()) {
            return node.asString();
        }
        if (node.isNumber()) {
            String s = node.asString();
            return (s.contains(".") || s.contains("e") || s.contains("E"))
                    ? (Object) Double.parseDouble(s)
                    : (Object) Long.parseLong(s);
        }
        throw new IllegalArgumentException("无法确定 JSON 节点的自然类型映射: " + node.type());
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
                field.set(instance, mapField(child, field));
            }
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("构造对象失败: " + targetType.getName(), e);
        }
    }

    /** B-6：List 字段按其泛型实参逐元素映射（List&lt;Integer&gt;/List&lt;Long&gt;/List&lt;POJO&gt; 均正确）。 */
    private Object mapField(JsonNode child, Field field) {
        Class<?> type = field.getType();
        if (List.class.isAssignableFrom(type) && child.isArray()) {
            Class<?> elementClass = resolveListElementType(field);
            List<Object> list = new ArrayList<>();
            for (JsonNode item : child.items()) {
                list.add(item == null || item.isNull() ? null
                        : (elementClass == Object.class ? naturalValue(item) : mapToObject(item, elementClass)));
            }
            return list;
        }
        return mapToObject(child, type);
    }

    /** 解析 List 字段的泛型实参；raw List（无泛型）返回 Object.class 走自然类型映射。 */
    private Class<?> resolveListElementType(Field field) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType) {
            Type arg = ((ParameterizedType) generic).getActualTypeArguments()[0];
            if (arg instanceof Class) {
                return (Class<?>) arg;
            }
        }
        return Object.class;
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