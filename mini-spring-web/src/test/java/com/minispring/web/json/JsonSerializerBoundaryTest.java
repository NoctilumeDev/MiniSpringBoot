package com.minispring.web.json;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSerializerBoundaryTest {

    private final JsonSerializer serializer = new JsonSerializer();

    @Test
    void rejectsPojoAndCollectionCyclesWithoutStackOverflow() {
        SelfReference pojo = new SelfReference();
        pojo.self = pojo;
        assertCycleFailure(pojo);

        List<Object> list = new ArrayList<>();
        list.add(list);
        assertCycleFailure(list);

        JsonNode array = JsonNode.array();
        array.add(array);
        assertCycleFailure(array);
    }

    @Test
    void repeatedSiblingReferenceIsNotMistakenForACycle() {
        Map<String, Object> shared = new LinkedHashMap<>();
        shared.put("id", 7);

        assertEquals("[{\"id\":7},{\"id\":7}]", serializer.serialize(List.of(shared, shared)));
    }

    @Test
    void rejectsExcessiveDepthBeforeTheJvmStackIsAtRisk() {
        List<Object> root = new ArrayList<>();
        List<Object> cursor = root;
        for (int i = 0; i < JsonSerializer.MAX_DEPTH + 2; i++) {
            List<Object> child = new ArrayList<>();
            cursor.add(child);
            cursor = child;
        }

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> serializer.serialize(root));
        assertTrue(failure.getMessage().contains("嵌套深度"));
    }

    @Test
    void rejectsOutputBeyondTheConfiguredBudget() {
        String oversized = "x".repeat(JsonSerializer.MAX_OUTPUT_CHARS);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> serializer.serialize(oversized));
        assertTrue(failure.getMessage().contains("输出超过上限"));
    }

    @Test
    void rejectsMapKeysThatCollapseToTheSameJsonName() {
        Map<Object, Object> value = new LinkedHashMap<>();
        value.put(1, "numeric");
        value.put("1", "text");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> serializer.serialize(value));
        assertTrue(failure.getMessage().contains("重复对象键"));
    }

    @Test
    void rejectsHiddenPojoFieldsThatWouldEmitDuplicateNames() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> serializer.serialize(new ChildWithHiddenId()));
        assertTrue(failure.getMessage().contains("重复字段名"));
    }

    private void assertCycleFailure(Object value) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> serializer.serialize(value));
        assertTrue(failure.getMessage().contains("循环引用"));
    }

    private static final class SelfReference {
        private SelfReference self;
    }

    private static class ParentWithId {
        private int id = 1;
    }

    private static final class ChildWithHiddenId extends ParentWithId {
        private int id = 2;
    }
}
