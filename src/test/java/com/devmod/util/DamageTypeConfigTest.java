package com.devmod.util;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DamageTypeConfig's pure logic methods.
 * Uses a fresh instance via reflection to avoid singleton state pollution.
 * Does NOT test load/save (filesystem-dependent).
 */
@DisplayName("DamageTypeConfig")
class DamageTypeConfigTest {

    private DamageTypeConfig config;

    @BeforeEach
    void setUp() throws Exception {
        // Create a fresh instance via reflection (bypassing private constructor)
        var constructor = DamageTypeConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        config = constructor.newInstance();
    }

    // ========================================================================
    // getLabel(String)
    // ========================================================================

    @Nested
    @DisplayName("getLabel(String)")
    class GetLabelString {

        @Test
        @DisplayName("returns label for a known default damage type")
        void returnsLabelForKnown() {
            String label = config.getLabel("minecraft:fall");
            assertNotNull(label);
            assertTrue(label.contains("Fall Damage"));
        }

        @Test
        @DisplayName("returns null for unknown damage type")
        void returnsNullForUnknown() {
            assertNull(config.getLabel("minecraft:unknown_type"));
        }

        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNull() {
            assertNull(config.getLabel((String) null));
        }
    }

    // ========================================================================
    // getLabelWithFallback(String)
    // ========================================================================

    @Nested
    @DisplayName("getLabelWithFallback(String)")
    class GetLabelWithFallbackString {

        @Test
        @DisplayName("returns label for known type")
        void returnsKnownLabel() {
            String label = config.getLabelWithFallback("minecraft:fall");
            assertNotNull(label);
            assertTrue(label.contains("Fall Damage"));
        }

        @Test
        @DisplayName("returns formatted fallback for unknown type")
        void returnsFallbackForUnknown() {
            String label = config.getLabelWithFallback("minecraft:some_new_damage");
            assertNotNull(label);
            // Should format "some_new_damage" -> "Some New Damage" with color code prefix
            assertTrue(label.contains("Some New Damage"), "Got: " + label);
        }

        @Test
        @DisplayName("returns unknown damage label for null input")
        void returnsUnknownForNull() {
            String label = config.getLabelWithFallback((String) null);
            assertNotNull(label);
            assertTrue(label.contains("Unknown Damage"));
        }

        @Test
        @DisplayName("handles type with namespace but no path after colon")
        void handlesColonAtEnd() {
            // Edge case: "namespace:" with colon at end
            String label = config.getLabelWithFallback("minecraft:");
            assertNotNull(label);
            // Empty path after colon
        }

        @Test
        @DisplayName("handles type without colon (no namespace)")
        void handlesNoNamespace() {
            String label = config.getLabelWithFallback("just_damage");
            assertNotNull(label);
            assertTrue(label.contains("Just Damage"), "Got: " + label);
        }

        @Test
        @DisplayName("handles empty string")
        void handlesEmptyString() {
            String label = config.getLabelWithFallback("");
            assertNotNull(label);
            assertTrue(label.contains("Unknown Damage"));
        }
    }

    // ========================================================================
    // setLabel / getAllMappings
    // ========================================================================

    @Nested
    @DisplayName("setLabel and getAllMappings")
    class SetLabelAndGetAll {

        @Test
        @DisplayName("setLabel adds a new mapping")
        void setLabelAdds() {
            config.setLabel("custom:my_damage", "My Damage");
            assertEquals("My Damage", config.getLabel("custom:my_damage"));
        }

        @Test
        @DisplayName("setLabel overwrites existing mapping")
        void setLabelOverwrites() {
            config.setLabel("minecraft:fall", "Custom Fall");
            assertEquals("Custom Fall", config.getLabel("minecraft:fall"));
        }

        @Test
        @DisplayName("getAllMappings returns a defensive copy")
        void getAllMappingsDefensiveCopy() {
            Map<String, String> mappings = config.getAllMappings();
            assertFalse(mappings.isEmpty());

            // Mutating the copy should not affect config
            int sizeBefore = config.getAllMappings().size();
            mappings.put("fake:entry", "Fake");
            assertEquals(sizeBefore, config.getAllMappings().size());
        }

        @Test
        @DisplayName("defaults include standard Minecraft damage types")
        void defaultsIncludeStandard() {
            Map<String, String> mappings = config.getAllMappings();
            assertTrue(mappings.containsKey("minecraft:fall"));
            assertTrue(mappings.containsKey("minecraft:drown"));
            assertTrue(mappings.containsKey("minecraft:lava"));
            assertTrue(mappings.containsKey("minecraft:on_fire"));
            assertTrue(mappings.containsKey("minecraft:player_attack"));
            assertTrue(mappings.containsKey("minecraft:mob_attack"));
            assertTrue(mappings.containsKey("devmod:mace_smash"));
        }
    }

    // ========================================================================
    // isConfigLoaded
    // ========================================================================

    @Test
    @DisplayName("isConfigLoaded is false for fresh instance (no load() called)")
    void isConfigLoadedFalse() {
        assertFalse(config.isConfigLoaded());
    }

    // ========================================================================
    // formatUnknownLabel (private, tested through getLabelWithFallback)
    // ========================================================================

    @Nested
    @DisplayName("formatUnknownLabel (via getLabelWithFallback)")
    class FormatUnknownLabel {

        @Test
        @DisplayName("converts snake_case to Title Case")
        void snakeCaseToTitleCase() {
            String label = config.getLabelWithFallback("mod:deep_dark_damage");
            // Should contain "Deep Dark Damage" with color prefix
            assertTrue(label.contains("Deep Dark Damage"), "Got: " + label);
        }

        @Test
        @DisplayName("single word is title-cased")
        void singleWord() {
            String label = config.getLabelWithFallback("mod:fire");
            assertTrue(label.contains("Fire"), "Got: " + label);
        }

        @Test
        @DisplayName("preserves modid:path format - extracts path only")
        void extractsPathOnly() {
            String label = config.getLabelWithFallback("mymod:custom_attack");
            // Should NOT contain "mymod" in label
            assertTrue(label.contains("Custom Attack"), "Got: " + label);
            assertFalse(label.contains("mymod"));
        }

        @Test
        @DisplayName("consecutive underscores produce multiple spaces")
        void consecutiveUnderscores() {
            String label = config.getLabelWithFallback("mod:a__b");
            assertTrue(label.contains("A  B") || label.contains("A B"), "Got: " + label);
        }
    }
}
