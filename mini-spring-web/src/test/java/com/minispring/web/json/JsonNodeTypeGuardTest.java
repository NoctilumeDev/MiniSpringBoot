package com.minispring.web.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-7 / B8 对称回归：JsonNode 类型转换在节点类型不匹配时报错，不静默给默认值。
 * B-7 修复前：asBoolean 对非布尔节点静默返回 false；B8 修复前：asInt 系列对 null 直接 NPE。
 */
class JsonNodeTypeGuardTest {

    @Test
    void asBooleanThrowsOnNonBooleanNode() {
        JsonNode number = new JsonParser("42").parse();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, number::asBoolean);
        assertTrue(ex.getMessage().contains("布尔"));
    }

    @Test
    void asBooleanReturnsValueOnBooleanNode() {
        JsonNode bool = new JsonParser("true").parse();
        assertEquals(true, bool.asBoolean());
    }

    @Test
    void asIntThrowsOnNonNumberNode() {
        JsonNode text = new JsonParser("\"42\"").parse();
        assertThrows(IllegalArgumentException.class, text::asInt);
        assertThrows(IllegalArgumentException.class, text::asLong);
        assertThrows(IllegalArgumentException.class, text::asDouble);
    }

    @Test
    void asIntReturnsValueOnNumberNode() {
        JsonNode number = new JsonParser("42").parse();
        assertEquals(42, number.asInt());
        assertEquals(42L, number.asLong());
        assertEquals(42.0, number.asDouble());
    }
}
