package com.devmod.notification;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationParamsCodecTest {

    @Nested
    @DisplayName("toJson")
    class ToJsonTests {

        @Test
        @DisplayName("Null map returns empty string")
        void nullMapReturnsEmpty() {
            assertEquals("", NotificationParamsCodec.toJson(null));
        }

        @Test
        @DisplayName("Empty map returns empty string")
        void emptyMapReturnsEmpty() {
            assertEquals("", NotificationParamsCodec.toJson(Map.of()));
        }

        @Test
        @DisplayName("Single entry produces valid JSON array")
        void singleEntryProducesValidJsonArray() {
            Map<String, String> map = Map.of("key", "value");
            String json = NotificationParamsCodec.toJson(map);

            assertNotNull(json);
            assertFalse(json.isBlank());
            assertTrue(json.startsWith("["));
            assertTrue(json.contains("\"key\""));
            assertTrue(json.contains("\"value\""));
        }

        @Test
        @DisplayName("Multiple entries produce valid JSON")
        void multipleEntriesProduceValidJson() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("a", "1");
            map.put("b", "2");
            map.put("c", "3");

            String json = NotificationParamsCodec.toJson(map);
            assertNotNull(json);
            assertTrue(json.startsWith("["));
        }
    }

    @Nested
    @DisplayName("fromJson")
    class FromJsonTests {

        @Test
        @DisplayName("Null JSON returns empty map")
        void nullJsonReturnsEmpty() {
            Map<String, String> result = NotificationParamsCodec.fromJson(null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Empty string returns empty map")
        void emptyStringReturnsEmpty() {
            Map<String, String> result = NotificationParamsCodec.fromJson("");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Blank string returns empty map")
        void blankStringReturnsEmpty() {
            Map<String, String> result = NotificationParamsCodec.fromJson("   ");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Valid JSON object format is parsed")
        void validJsonObjectIsParsed() {
            String json = "{\"key\":\"value\",\"num\":\"42\"}";
            Map<String, String> result = NotificationParamsCodec.fromJson(json);

            assertEquals(2, result.size());
            assertEquals("value", result.get("key"));
            assertEquals("42", result.get("num"));
        }

        @Test
        @DisplayName("Valid JSON array format is parsed")
        void validJsonArrayIsParsed() {
            String json = "[{\"key\":\"a\",\"value\":\"1\"},{\"key\":\"b\",\"value\":\"2\"}]";
            Map<String, String> result = NotificationParamsCodec.fromJson(json);

            assertEquals(2, result.size());
            assertEquals("1", result.get("a"));
            assertEquals("2", result.get("b"));
        }

        @Test
        @DisplayName("Malformed JSON returns empty map")
        void malformedJsonReturnsEmpty() {
            Map<String, String> result = NotificationParamsCodec.fromJson("not valid json");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Empty JSON object returns empty map")
        void emptyJsonObjectReturnsEmpty() {
            Map<String, String> result = NotificationParamsCodec.fromJson("{}");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Empty JSON array returns empty map")
        void emptyJsonArrayReturnsEmpty() {
            Map<String, String> result = NotificationParamsCodec.fromJson("[]");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Array entry with null key is skipped")
        void arrayEntryWithNullKeyIsSkipped() {
            String json = "[{\"key\":null,\"value\":\"val\"},{\"key\":\"good\",\"value\":\"v\"}]";
            Map<String, String> result = NotificationParamsCodec.fromJson(json);
            assertEquals(1, result.size());
            assertEquals("v", result.get("good"));
        }
    }

    @Nested
    @DisplayName("Round-trip")
    class RoundTripTests {

        @Test
        @DisplayName("Encode then decode preserves entries")
        void encodeThenDecodePreservesEntries() {
            Map<String, String> original = new LinkedHashMap<>();
            original.put("badge", "Dragon Slayer");
            original.put("rarity", "legendary");
            original.put("amount", "500");

            String json = NotificationParamsCodec.toJson(original);
            Map<String, String> decoded = NotificationParamsCodec.fromJson(json);

            assertEquals(original.size(), decoded.size());
            assertEquals("Dragon Slayer", decoded.get("badge"));
            assertEquals("legendary", decoded.get("rarity"));
            assertEquals("500", decoded.get("amount"));
        }

        @Test
        @DisplayName("Round-trip with special characters")
        void roundTripWithSpecialChars() {
            Map<String, String> original = new LinkedHashMap<>();
            original.put("msg", "Hello \"world\" <>&");
            original.put("unicode", "\u00e9\u00e8\u00ea");

            String json = NotificationParamsCodec.toJson(original);
            Map<String, String> decoded = NotificationParamsCodec.fromJson(json);

            assertEquals(original.get("msg"), decoded.get("msg"));
            assertEquals(original.get("unicode"), decoded.get("unicode"));
        }

        @Test
        @DisplayName("Round-trip with empty values")
        void roundTripWithEmptyValues() {
            Map<String, String> original = new LinkedHashMap<>();
            original.put("key", "");

            String json = NotificationParamsCodec.toJson(original);
            Map<String, String> decoded = NotificationParamsCodec.fromJson(json);

            assertEquals("", decoded.get("key"));
        }
    }
}
