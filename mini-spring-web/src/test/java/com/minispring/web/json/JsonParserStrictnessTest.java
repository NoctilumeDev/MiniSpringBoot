package com.minispring.web.json;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonParserStrictnessTest {

    @Test
    void acceptsTheJsonNumberGrammar() {
        for (String valid : List.of("0", "-0", "10", "-10", "0.25", "-0.25", "1e2", "1E+2", "1e-2")) {
            assertDoesNotThrow(() -> new JsonParser(valid).parse(), valid);
        }
    }

    @Test
    void rejectsLeadingZerosAndIncompleteNumbers() {
        for (String invalid : List.of("00", "01", "-01", "1.", "1e", "1e+", "-", ".1")) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> new JsonParser(invalid).parse(), invalid);
            assertTrue(failure.getMessage().contains("JSON 解析失败"));
        }
    }

    @Test
    void rejectsRawControlCharactersButAcceptsEscapedOnes() {
        String rawControl = "\"" + ((char) 1) + "\"";
        assertThrows(IllegalArgumentException.class, () -> new JsonParser(rawControl).parse());
        assertThrows(IllegalArgumentException.class, () -> new JsonParser("\"line\nbreak\"").parse());

        JsonNode escaped = new JsonParser("\"\\u0001\"").parse();
        assertEquals(String.valueOf((char) 1), escaped.asString());
    }

    @Test
    void onlyFourJsonWhitespaceCharactersAreAccepted() {
        assertDoesNotThrow(() -> new JsonParser(" \t\r\n true").parse());
        String nonJsonWhitespace = String.valueOf((char) 0x00a0) + "true";
        assertThrows(IllegalArgumentException.class, () -> new JsonParser(nonJsonWhitespace).parse());
    }

    @Test
    void duplicateObjectKeysAreRejected() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new JsonParser("{\"role\":\"reader\",\"role\":\"admin\"}").parse());
        assertTrue(failure.getMessage().contains("重复键"));
    }
}
