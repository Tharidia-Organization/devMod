package com.devmod.recipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

@DisplayName("ResultData")
class ResultDataTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    // ========================================================================
    // Factory Methods
    // ========================================================================

    @Nested
    @DisplayName("Factory Methods")
    class FactoryTests {

        @Test
        @DisplayName("empty() creates empty result")
        void emptyCreatesEmpty() {
            ResultData result = ResultData.empty();
            assertTrue(result.isEmpty());
            assertNull(result.itemId());
        }

        @Test
        @DisplayName("of(ResourceLocation) creates with count 1")
        void ofResourceLocationCountOne() {
            ResultData result = ResultData.of(ResourceLocation.parse("minecraft:diamond"));
            assertFalse(result.isEmpty());
            assertEquals(1, result.count());
            assertEquals("minecraft:diamond", result.itemId().toString());
        }

        @Test
        @DisplayName("of(ResourceLocation, count) creates with count")
        void ofResourceLocationWithCount() {
            ResultData result = ResultData.of(ResourceLocation.parse("minecraft:torch"), 4);
            assertEquals(4, result.count());
        }

        @Test
        @DisplayName("of(String) creates from string ID")
        void ofStringCreates() {
            ResultData result = ResultData.of("minecraft:iron_ingot");
            assertFalse(result.isEmpty());
            assertEquals("minecraft:iron_ingot", result.itemId().toString());
        }

        @Test
        @DisplayName("of(String, count) creates from string with count")
        void ofStringWithCount() {
            ResultData result = ResultData.of("minecraft:torch", 4);
            assertEquals(4, result.count());
        }
    }

    // ========================================================================
    // Count Clamping
    // ========================================================================

    @Nested
    @DisplayName("Count Clamping")
    class CountClampingTests {

        @Test
        @DisplayName("Negative count becomes 1")
        void negativeCountBecomesOne() {
            ResultData result = new ResultData(ResourceLocation.parse("minecraft:diamond"), -5, null);
            assertEquals(1, result.count());
        }

        @Test
        @DisplayName("Count above 64 becomes 64")
        void countAbove64Becomes64() {
            ResultData result = new ResultData(ResourceLocation.parse("minecraft:diamond"), 100, null);
            assertEquals(64, result.count());
        }

        @Test
        @DisplayName("Count within range is preserved")
        void countWithinRangePreserved() {
            ResultData result = new ResultData(ResourceLocation.parse("minecraft:diamond"), 32, null);
            assertEquals(32, result.count());
        }
    }

    // ========================================================================
    // Query Methods
    // ========================================================================

    @Nested
    @DisplayName("Query Methods")
    class QueryTests {

        @Test
        @DisplayName("isEmpty true for null itemId")
        void isEmptyForNullItemId() {
            ResultData result = new ResultData(null, 1, null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("isEmpty true for zero count")
        void isEmptyForZeroCount() {
            ResultData result = ResultData.empty();
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("hasComponents false when no components")
        void hasComponentsFalseForNull() {
            ResultData result = ResultData.of("minecraft:diamond");
            assertFalse(result.hasComponents());
        }
    }

    // ========================================================================
    // Builder Methods
    // ========================================================================

    @Nested
    @DisplayName("Builder Methods")
    class BuilderTests {

        @Test
        @DisplayName("withCount creates copy with new count")
        void withCountCreatesCopy() {
            ResultData original = ResultData.of("minecraft:diamond", 1);
            ResultData modified = original.withCount(5);
            assertEquals(5, modified.count());
            assertEquals(1, original.count());
        }

        @Test
        @DisplayName("withCount clamps to valid range")
        void withCountClamps() {
            ResultData result = ResultData.of("minecraft:diamond").withCount(100);
            assertEquals(64, result.count());
        }
    }

    // ========================================================================
    // Display
    // ========================================================================

    @Nested
    @DisplayName("Display")
    class DisplayTests {

        @Test
        @DisplayName("Empty result displays as 'Empty'")
        void emptyDisplaysEmpty() {
            assertEquals("Empty", ResultData.empty().getDisplayName());
        }

        @Test
        @DisplayName("Single item shows path")
        void singleItemShowsPath() {
            ResultData result = ResultData.of("minecraft:diamond");
            assertEquals("diamond", result.getDisplayName());
        }

        @Test
        @DisplayName("Multiple count shows count suffix")
        void multipleCountShowsSuffix() {
            ResultData result = ResultData.of("minecraft:diamond", 3);
            assertEquals("diamond x3", result.getDisplayName());
        }
    }

    // ========================================================================
    // Equality
    // ========================================================================

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("Equal results are equal")
        void equalResultsAreEqual() {
            ResultData a = ResultData.of("minecraft:diamond", 3);
            ResultData b = ResultData.of("minecraft:diamond", 3);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Different items are not equal")
        void differentItemsNotEqual() {
            ResultData a = ResultData.of("minecraft:diamond");
            ResultData b = ResultData.of("minecraft:iron_ingot");
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("Different counts are not equal")
        void differentCountsNotEqual() {
            ResultData a = ResultData.of("minecraft:diamond", 1);
            ResultData b = ResultData.of("minecraft:diamond", 2);
            assertNotEquals(a, b);
        }
    }

    // ========================================================================
    // JSON Serialization
    // ========================================================================

    @Nested
    @DisplayName("JSON Serialization")
    class JsonTests {

        @Test
        @DisplayName("toJson produces valid JSON with id")
        void toJsonProducesValidJson() {
            ResultData result = ResultData.of("minecraft:diamond", 3);
            JsonObject json = result.toJson();
            assertTrue(json.has("id"));
            assertEquals("minecraft:diamond", json.get("id").getAsString());
            assertEquals(3, json.get("count").getAsInt());
        }

        @Test
        @DisplayName("toJson omits count when 1")
        void toJsonOmitsCountWhenOne() {
            ResultData result = ResultData.of("minecraft:diamond");
            JsonObject json = result.toJson();
            assertFalse(json.has("count"));
        }

        @Test
        @DisplayName("empty toJson returns empty object")
        void emptyToJsonReturnsEmptyObject() {
            ResultData result = ResultData.empty();
            JsonObject json = result.toJson();
            assertFalse(json.has("id"));
        }

        @Test
        @DisplayName("fromJson round-trips correctly")
        void fromJsonRoundTrips() {
            ResultData original = ResultData.of("minecraft:diamond", 5);
            JsonObject json = original.toJson();
            ResultData deserialized = ResultData.fromJson(json);
            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("fromJson with null returns empty")
        void fromJsonNullReturnsEmpty() {
            ResultData result = ResultData.fromJson(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("fromJson with 'item' key (legacy format)")
        void fromJsonLegacyFormat() {
            JsonObject json = new JsonObject();
            json.addProperty("item", "minecraft:iron_ingot");
            json.addProperty("count", 2);
            ResultData result = ResultData.fromJson(json);
            assertEquals("minecraft:iron_ingot", result.itemId().toString());
            assertEquals(2, result.count());
        }
    }

    // ========================================================================
    // toString
    // ========================================================================

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("Empty result toString")
        void emptyToString() {
            assertEquals("ResultData{empty}", ResultData.empty().toString());
        }

        @Test
        @DisplayName("Non-empty result toString contains item and count")
        void nonEmptyToString() {
            String str = ResultData.of("minecraft:diamond", 3).toString();
            assertTrue(str.contains("minecraft:diamond"));
            assertTrue(str.contains("x3"));
        }
    }
}
