package com.minispring.web.json;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-6（D14 收口）回归：List 字段按泛型实参逐元素映射，不得一律转 String。
 * 修复前：List&lt;Integer&gt;/List&lt;Long&gt; 字段拿到的是 List&lt;String&gt;，运行时取元素 ClassCastException。
 */
class JsonObjectMapperListTest {

    static class Order {
        List<Integer> tags;
        List<Item> items;
    }

    static class Item {
        String name;
        int qty;
    }

    @Test
    void integerListElementsKeepTheirType() {
        Order order = new JsonObjectMapper().readValue("{\"tags\":[1,2,3]}", Order.class);
        assertEquals(3, order.tags.size());
        for (Integer tag : order.tags) {
            assertInstanceOf(Integer.class, tag, "List<Integer> 的元素必须是 Integer，不能被 asString 成 String");
        }
        assertEquals(2, order.tags.get(1));
    }

    @Test
    void pojoListElementsAreMappedByElementType() {
        Order order = new JsonObjectMapper().readValue(
                "{\"items\":[{\"name\":\"book\",\"qty\":2},{\"name\":\"pen\",\"qty\":5}]}", Order.class);
        assertEquals(2, order.items.size());
        assertEquals("book", order.items.get(0).name);
        assertEquals(2, order.items.get(0).qty);
        assertEquals("pen", order.items.get(1).name);
        assertEquals(5, order.items.get(1).qty);
    }

    @Test
    void topLevelListUsesNaturalTypes() {
        List<Object> list = new JsonObjectMapper().readValue("[1,\"a\",true]", List.class);
        assertEquals(3, list.size());
        assertTrue(list.get(0) instanceof Number);
        assertEquals("a", list.get(1));
        assertEquals(Boolean.TRUE, list.get(2));
    }
}
