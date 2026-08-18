package com.minispring.web.json;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自写 JSON 栈的基线单测（仅作最低基线，不作为「落地验收」依据）。
 */
class JsonObjectMapperTest {

    static class Person {
        private String name;
        private int age;
        private List<String> tags;

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public List<String> getTags() {
            return tags;
        }
    }

    @Test
    void parsesNestedStructuresAndEscapes() {
        JsonNode node = new JsonParser("{\"name\":\"张三\",\"note\":\"a\\\"b\\n\",\"age\":18,\"ok\":true,\"empty\":null}")
                .parse();
        assertTrue(node.isObject());
        assertEquals("张三", node.get("name").asString());
        assertEquals("a\"b\n", node.get("note").asString());
        assertEquals(18, node.get("age").asInt());
        assertEquals(true, node.get("ok").asBoolean());
        assertTrue(node.get("empty").isNull());
    }

    @Test
    void mapsJsonToPojoAndBack() {
        JsonObjectMapper mapper = new JsonObjectMapper();
        Person p = mapper.readValue("{\"name\":\"Alice\",\"age\":30,\"tags\":[\"a\",\"b\"]}", Person.class);

        assertEquals("Alice", p.getName());
        assertEquals(30, p.getAge());

        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("\"name\":\"Alice\""));
        assertTrue(json.contains("\"age\":30"));
    }

    @Test
    void serializesNullAsJsonNull() {
        assertEquals("null", new JsonObjectMapper().writeValueAsString(null));
        assertNull(new JsonObjectMapper().readValue("null", String.class));
    }
}