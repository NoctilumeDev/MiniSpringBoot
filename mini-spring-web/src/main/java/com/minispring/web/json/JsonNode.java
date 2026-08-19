package com.minispring.web.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 树节点：Object / Array / String / Number / Boolean / null 六种形态的统一点。
 *
 * <p>它是「自写 JSON」栈的中间层：解析器（{@link JsonParser}）产出它，
 * 对象映射器（{@link JsonObjectMapper}）消费它。
 */
public final class JsonNode {

    public enum Type { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }

    private final Type type;
    private final Map<String, JsonNode> objectValue;
    private final List<JsonNode> arrayValue;
    private final String stringValue;
    private final boolean booleanValue;

    private JsonNode(Type type, Map<String, JsonNode> objectValue, List<JsonNode> arrayValue,
                     String stringValue, boolean booleanValue) {
        this.type = type;
        this.objectValue = objectValue;
        this.arrayValue = arrayValue;
        this.stringValue = stringValue;
        this.booleanValue = booleanValue;
    }

    public static JsonNode object() {
        return new JsonNode(Type.OBJECT, new LinkedHashMap<>(), null, null, false);
    }

    public static JsonNode array() {
        return new JsonNode(Type.ARRAY, null, new ArrayList<>(), null, false);
    }

    public static JsonNode ofString(String value) {
        return new JsonNode(Type.STRING, null, null, value, false);
    }

    /** value 为数字的原始文本，如 {@code "9527"} / {@code "3.14"}。 */
    public static JsonNode ofNumber(String value) {
        return new JsonNode(Type.NUMBER, null, null, value, false);
    }

    public static JsonNode ofBoolean(boolean value) {
        return new JsonNode(Type.BOOLEAN, null, null, null, value);
    }

    public static JsonNode ofNull() {
        return new JsonNode(Type.NULL, null, null, null, false);
    }

    public Type type() {
        return type;
    }

    public boolean isObject() {
        return type == Type.OBJECT;
    }

    public boolean isArray() {
        return type == Type.ARRAY;
    }

    public boolean isString() {
        return type == Type.STRING;
    }

    public boolean isNumber() {
        return type == Type.NUMBER;
    }

    public boolean isBoolean() {
        return type == Type.BOOLEAN;
    }

    public boolean isNull() {
        return type == Type.NULL;
    }

    public int size() {
        if (isObject()) {
            return objectValue.size();
        }
        if (isArray()) {
            return arrayValue.size();
        }
        return 0;
    }

    /** 对象形态：按 key 取值；不存在返回 null。 */
    public JsonNode get(String key) {
        return isObject() ? objectValue.get(key) : null;
    }

    public void put(String key, JsonNode value) {
        objectValue.put(key, value);
    }

    /** 数组形态：按下标取值。 */
    public JsonNode get(int index) {
        return isArray() ? arrayValue.get(index) : null;
    }

    public void add(JsonNode value) {
        arrayValue.add(value);
    }

    /** 数组形态的元素集合（供遍历）。 */
    public List<JsonNode> items() {
        return isArray() ? arrayValue : new ArrayList<>();
    }

    /** 对象形态的键值集合（供遍历）。 */
    public Map<String, JsonNode> entries() {
        return isObject() ? objectValue : new LinkedHashMap<>();
    }

    // ----- 取值 & 类型转换（供对象映射器使用）-----

    public String asString() {
        switch (type) {
            case STRING:
            case NUMBER:
                return stringValue;
            case BOOLEAN:
                return Boolean.toString(booleanValue);
            default:
                return null;
        }
    }

    public int asInt() {
        // 类型防护按节点形态判（与 asBoolean 对称）：STRING "42" 不静默转数字，必须显式报错
        if (type != Type.NUMBER) {
            throw new IllegalArgumentException("JSON 节点不是数字，无法转为 int");
        }
        try {
            return Integer.parseInt(stringValue.trim());
        } catch (NumberFormatException e) {
            // L9：值不可转时带上原文（"3.14" 到 int / 溢出）——裸 NumberFormatException 在
            // HTTP 500 里呈现为无上下文的 "null"，违反错误保真纪律（B10 同族）
            throw new IllegalArgumentException("JSON 数字 \"" + stringValue + "\" 无法转为 int（非整数或溢出）", e);
        }
    }

    public long asLong() {
        if (type != Type.NUMBER) {
            throw new IllegalArgumentException("JSON 节点不是数字，无法转为 long");
        }
        try {
            return Long.parseLong(stringValue.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("JSON 数字 \"" + stringValue + "\" 无法转为 long（溢出）", e);
        }
    }

    public double asDouble() {
        if (type != Type.NUMBER) {
            throw new IllegalArgumentException("JSON 节点不是数字，无法转为 double");
        }
        try {
            return Double.parseDouble(stringValue.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("JSON 数字 \"" + stringValue + "\" 无法转为 double", e);
        }
    }

    public boolean asBoolean() {
        if (type != Type.BOOLEAN) {
            // B-7：类型不匹配时明确报错，不静默返回 false（与 asInt/asLong/asDouble 的防护对称）
            throw new IllegalArgumentException("JSON 节点不是布尔值，无法转为 boolean");
        }
        return booleanValue;
    }
}