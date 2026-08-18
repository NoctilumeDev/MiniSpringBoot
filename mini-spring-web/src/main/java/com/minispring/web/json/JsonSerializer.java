package com.minispring.web.json;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 序列化器：反射遍历字段，把 Java 对象写成 JSON 字符串。
 *
 * <p>支持：String / 基本与包装类型 / 枚举 / 数组 / List / Map / 普通 POJO / {@link JsonNode}。
 * 不做循环引用检测（自引用对象会无限递归），教学子集如实标注。
 */
public final class JsonSerializer {

    public String serialize(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Character || value instanceof CharSequence) {
            writeString(sb, value.toString());
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Number) {
            writeNumber(sb, (Number) value);
        } else if (value instanceof Enum) {
            writeString(sb, ((Enum<?>) value).name());
        } else if (value instanceof JsonNode) {
            writeJsonNode(sb, (JsonNode) value);
        } else if (value.getClass().isArray()) {
            writeArray(sb, value);
        } else if (value instanceof List) {
            writeList(sb, (List<?>) value);
        } else if (value instanceof Map) {
            writeMap(sb, (Map<?, ?>) value);
        } else {
            writeObject(sb, value);
        }
    }

    /** N7（M0-M9 复审）：NaN/Infinity 不是合法 JSON（自家解析器都读不回）——显式报错而非输出裸字面量。 */
    private void writeNumber(StringBuilder sb, Number number) {
        double d = number.doubleValue();
        if (!Double.isFinite(d)) {
            throw new IllegalStateException("无法序列化为 JSON 的数字: " + number
                    + "（NaN/Infinity 不在 JSON 规范内，请检查计算链路）");
        }
        sb.append(number);
    }

    private void writeObject(StringBuilder sb, Object obj) {
        sb.append('{');
        boolean first = true;
        for (Field field : collectFields(obj.getClass())) {
            try {
                field.setAccessible(true);
                if (!first) {
                    sb.append(',');
                }
                writeString(sb, field.getName());
                sb.append(':');
                writeValue(sb, field.get(obj));
                first = false;
            } catch (IllegalAccessException ignored) {
                // 读取不到的字段跳过
            }
        }
        sb.append('}');
    }

    private void writeArray(StringBuilder sb, Object array) {
        sb.append('[');
        int len = Array.getLength(array);
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(',');
            }
            writeValue(sb, Array.get(array, i));
        }
        sb.append(']');
    }

    private void writeList(StringBuilder sb, List<?> list) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            writeValue(sb, list.get(i));
        }
        sb.append(']');
    }

    private void writeMap(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            writeString(sb, String.valueOf(entry.getKey()));
            sb.append(':');
            writeValue(sb, entry.getValue());
            first = false;
        }
        sb.append('}');
    }

    private void writeJsonNode(StringBuilder sb, JsonNode node) {
        switch (node.type()) {
            case NULL:
                sb.append("null");
                break;
            case BOOLEAN:
                sb.append(node.asBoolean());
                break;
            case NUMBER:
                // B-1：数字节点输出原文，不加引号（原与 STRING 共用 writeString 会把 42 写成 "42"）
                // N7：原文须能落到有限 double（1e999 溢出为 Infinity 同样非法），否则显式报错
                String text = node.asString();
                if (!Double.isFinite(Double.parseDouble(text))) {
                    throw new IllegalStateException("无法序列化为 JSON 的数字: " + text + "（溢出为 Infinity）");
                }
                sb.append(text);
                break;
            case STRING:
                writeString(sb, node.asString());
                break;
            case ARRAY:
                sb.append('[');
                for (int i = 0; i < node.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    writeJsonNode(sb, node.get(i));
                }
                sb.append(']');
                break;
            case OBJECT:
                sb.append('{');
                boolean first = true;
                for (Map.Entry<String, JsonNode> entry : node.entries().entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    writeString(sb, entry.getKey());
                    sb.append(':');
                    writeJsonNode(sb, entry.getValue());
                    first = false;
                }
                sb.append('}');
                break;
            default:
                sb.append("null");
        }
    }

    private void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
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
}