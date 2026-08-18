package com.minispring.web.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
