package com.minispring.web.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * B-1 回归：JsonNode 数字节点序列化不得带引号。
 * 修复前：NUMBER 与 STRING 共用 writeString，{"count":42} 被写成 {"count":"42"}。
 */
class JsonSerializerNumberTest {

    @Test
    void numberNodeIsSerializedWithoutQuotes() {
        JsonNode root = new JsonParser("{\"count\":42,\"ratio\":3.5,\"name\":\"mini\"}").parse();
        String json = new JsonSerializer().serialize(root);
        assertEquals("{\"count\":42,\"ratio\":3.5,\"name\":\"mini\"}", json);
    }

    @Test
    void numberInsideArrayAndNestedObjectStaysUnquoted() {
        JsonNode root = new JsonParser("{\"ids\":[1,2,3],\"inner\":{\"n\":7}}").parse();
        String json = new JsonSerializer().serialize(root);
        assertEquals("{\"ids\":[1,2,3],\"inner\":{\"n\":7}}", json);
    }

    /** N7：Java 对象携带 NaN/Infinity 时必须报错——修复前会静默输出 NaN/Infinity 这种自家解析器读不回的非法 JSON。 */
    @Test
    void nanAndInfinityAreRejectedNotSilentlyWritten() {
        Map<String, Object> bad = Map.of("ratio", Double.NaN);
        assertThrows(IllegalStateException.class, () -> new JsonSerializer().serialize(bad));
    }

    /** N7（JsonNode 路径）：1e999 解析后 asDouble 溢出为 Infinity，序列化时必须报错。 */
    @Test
    void overflowNumberNodeIsRejected() {
        JsonNode root = new JsonParser("{\"huge\":1e999}").parse();
        assertThrows(IllegalStateException.class, () -> new JsonSerializer().serialize(root));
    }

    /** N7 对称性：合法有限数字不受影响（含负数与科学计数法）。 */
    @Test
    void finiteNumbersStillSerialize() {
        JsonNode root = new JsonParser("[1.5,-2,3e2]").parse();
        assertEquals("[1.5,-2,3e2]", new JsonSerializer().serialize(root));
        assertEquals("[1,2]", new JsonSerializer().serialize(List.of(1, 2)));
    }
}
