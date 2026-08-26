package com.minispring.web.json;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 极简 JSON 序列化器：反射遍历字段，把 Java 对象写成 JSON 字符串。
 *
 * <p>支持 String / 基本与包装类型 / 枚举 / 数组 / List / Map / 普通 POJO /
 * {@link JsonNode}。对象图按身份检测当前递归链，输出字符数和嵌套深度都有硬上限；
 * 因而循环引用或异常膨胀会明确失败，不会演变为 StackOverflowError/OOM。
 */
public final class JsonSerializer {

    static final int MAX_DEPTH = 128;
    static final int MAX_OUTPUT_CHARS = 1024 * 1024;

    public String serialize(Object value) {
        WriteContext context = new WriteContext();
        writeValue(context, value, 0);
        return context.result();
    }

    private void writeValue(WriteContext context, Object value, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("JSON 序列化嵌套深度超过上限 " + MAX_DEPTH);
        }
        if (value == null) {
            context.append("null");
        } else if (value instanceof String) {
            writeString(context, (String) value);
        } else if (value instanceof Character || value instanceof CharSequence) {
            writeString(context, value.toString());
        } else if (value instanceof Boolean) {
            context.append(value.toString());
        } else if (value instanceof Number) {
            writeNumber(context, (Number) value);
        } else if (value instanceof Enum) {
            writeString(context, ((Enum<?>) value).name());
        } else if (value instanceof java.util.Date) {
            writeString(context, ((java.util.Date) value).toInstant().toString());
        } else {
            context.enter(value);
            try {
                if (value instanceof JsonNode) {
                    writeJsonNode(context, (JsonNode) value, depth);
                } else if (value.getClass().isArray()) {
                    writeArray(context, value, depth);
                } else if (value instanceof List) {
                    writeList(context, (List<?>) value, depth);
                } else if (value instanceof Map) {
                    writeMap(context, (Map<?, ?>) value, depth);
                } else {
                    writeObject(context, value, depth);
                }
            } finally {
                context.exit(value);
            }
        }
    }

    /** NaN 和 Infinity 不是合法 JSON 数字，统一显式拒绝。 */
    private void writeNumber(WriteContext context, Number number) {
        double d = number.doubleValue();
        if (!Double.isFinite(d)) {
            throw new IllegalStateException("无法序列化为 JSON 的数字: " + number
                    + "（NaN/Infinity 不在 JSON 规范内，请检查计算链路）");
        }
        context.append(number.toString());
    }

    private void writeObject(WriteContext context, Object obj, int depth) {
        context.append('{');
        boolean first = true;
        Set<String> fieldNames = new HashSet<>();
        for (Field field : collectFields(obj.getClass())) {
            if (!fieldNames.add(field.getName())) {
                throw new IllegalStateException("JSON 序列化检测到重复字段名: " + field.getName());
            }
            Object value;
            try {
                field.setAccessible(true);
                value = field.get(obj);
            } catch (IllegalAccessException ignored) {
                // 先读取字段值，再写字段名，避免取值异常留下半截 JSON；不可读字段整体跳过。
                continue;
            }
            if (!first) {
                context.append(',');
            }
            writeString(context, field.getName());
            context.append(':');
            writeValue(context, value, depth + 1);
            first = false;
        }
        context.append('}');
    }

    private void writeArray(WriteContext context, Object array, int depth) {
        context.append('[');
        int len = Array.getLength(array);
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                context.append(',');
            }
            writeValue(context, Array.get(array, i), depth + 1);
        }
        context.append(']');
    }

    private void writeList(WriteContext context, List<?> list, int depth) {
        context.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                context.append(',');
            }
            writeValue(context, list.get(i), depth + 1);
        }
        context.append(']');
    }

    private void writeMap(WriteContext context, Map<?, ?> map, int depth) {
        context.append('{');
        boolean first = true;
        Set<String> serializedKeys = new HashSet<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!serializedKeys.add(key)) {
                throw new IllegalStateException("JSON 序列化检测到重复对象键: " + key);
            }
            if (!first) {
                context.append(',');
            }
            writeString(context, key);
            context.append(':');
            writeValue(context, entry.getValue(), depth + 1);
            first = false;
        }
        context.append('}');
    }

    private void writeJsonNode(WriteContext context, JsonNode node, int depth) {
        switch (node.type()) {
            case NULL:
                context.append("null");
                break;
            case BOOLEAN:
                context.append(Boolean.toString(node.asBoolean()));
                break;
            case NUMBER:
                String text = node.asString();
                if (!Double.isFinite(Double.parseDouble(text))) {
                    throw new IllegalStateException("无法序列化为 JSON 的数字: " + text + "（溢出为 Infinity）");
                }
                context.append(text);
                break;
            case STRING:
                writeString(context, node.asString());
                break;
            case ARRAY:
                context.append('[');
                for (int i = 0; i < node.size(); i++) {
                    if (i > 0) {
                        context.append(',');
                    }
                    writeValue(context, node.get(i), depth + 1);
                }
                context.append(']');
                break;
            case OBJECT:
                context.append('{');
                boolean first = true;
                for (Map.Entry<String, JsonNode> entry : node.entries().entrySet()) {
                    if (!first) {
                        context.append(',');
                    }
                    writeString(context, entry.getKey());
                    context.append(':');
                    writeValue(context, entry.getValue(), depth + 1);
                    first = false;
                }
                context.append('}');
                break;
            default:
                throw new IllegalStateException("未知 JsonNode 类型: " + node.type());
        }
    }

    private void writeString(WriteContext context, String value) {
        context.append('"');
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"':
                    context.append("\\\"");
                    break;
                case '\\':
                    context.append("\\\\");
                    break;
                case '\n':
                    context.append("\\n");
                    break;
                case '\r':
                    context.append("\\r");
                    break;
                case '\t':
                    context.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        context.append(String.format("\\u%04x", (int) c));
                    } else {
                        context.append(c);
                    }
            }
        }
        context.append('"');
    }

    private List<Field> collectFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                int mod = field.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) {
                    continue;
                }
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static final class WriteContext {
        private final StringBuilder output = new StringBuilder();
        private final IdentityHashMap<Object, Boolean> ancestors = new IdentityHashMap<>();

        private void enter(Object value) {
            if (ancestors.put(value, Boolean.TRUE) != null) {
                throw new IllegalStateException("JSON 序列化检测到循环引用: "
                        + value.getClass().getName());
            }
        }

        private void exit(Object value) {
            ancestors.remove(value);
        }

        private void append(char value) {
            ensureCapacity(1);
            output.append(value);
        }

        private void append(String value) {
            ensureCapacity(value.length());
            output.append(value);
        }

        private void ensureCapacity(int additionalChars) {
            if (additionalChars > MAX_OUTPUT_CHARS - output.length()) {
                throw new IllegalStateException("JSON 序列化输出超过上限 "
                        + MAX_OUTPUT_CHARS + " 字符");
            }
        }

        private String result() {
            return output.toString();
        }
    }
}
