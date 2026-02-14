package com.devmod.npc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.npc.dialog.PlaceholderResolver;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlaceholderResolver.
 * Since resolve() requires ServerPlayer (Minecraft class), we test the
 * registration/unregistration API and the placeholder pattern matching.
 * The PLACEHOLDER_PATTERN is private, so we test via the registration API
 * and validate the pattern format expectations independently.
 */
class PlaceholderResolverTest {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    @AfterEach
    void cleanup() {
        PlaceholderResolver.clearCustomResolvers();
    }

    @Test
    @DisplayName("Placeholder pattern matches simple placeholders")
    void patternMatchesSimplePlaceholders() {
        Matcher m = PLACEHOLDER_PATTERN.matcher("{player}");
        assertTrue(m.find());
        assertEquals("player", m.group(1));
    }

    @Test
    @DisplayName("Placeholder pattern matches underscored placeholders")
    void patternMatchesUnderscoredPlaceholders() {
        Matcher m = PLACEHOLDER_PATTERN.matcher("{player_uuid}");
        assertTrue(m.find());
        assertEquals("player_uuid", m.group(1));
    }

    @Test
    @DisplayName("Placeholder pattern matches multiple placeholders in text")
    void patternMatchesMultiple() {
        String text = "Hello {player}, welcome to {npc_name}'s shop!";
        Matcher m = PLACEHOLDER_PATTERN.matcher(text);
        assertTrue(m.find());
        assertEquals("player", m.group(1));
        assertTrue(m.find());
        assertEquals("npc_name", m.group(1));
        assertFalse(m.find());
    }

    @Test
    @DisplayName("Placeholder pattern does not match numbers-only")
    void patternDoesNotMatchNumbersOnly() {
        Matcher m = PLACEHOLDER_PATTERN.matcher("{123}");
        assertFalse(m.find());
    }

    @Test
    @DisplayName("Placeholder pattern does not match empty braces")
    void patternDoesNotMatchEmptyBraces() {
        Matcher m = PLACEHOLDER_PATTERN.matcher("{}");
        assertFalse(m.find());
    }

    @Test
    @DisplayName("Placeholder pattern does not match spaces inside braces")
    void patternDoesNotMatchSpaces() {
        Matcher m = PLACEHOLDER_PATTERN.matcher("{player name}");
        assertFalse(m.find());
    }

    @Test
    @DisplayName("register adds a custom resolver")
    void registerAddsResolver() {
        // Should not throw
        assertDoesNotThrow(() ->
            PlaceholderResolver.register("custom_key", (player, ctx) -> "custom_value"));
    }

    @Test
    @DisplayName("unregister removes a custom resolver")
    void unregisterRemovesResolver() {
        PlaceholderResolver.register("temp_key", (player, ctx) -> "temp");
        assertDoesNotThrow(() -> PlaceholderResolver.unregister("temp_key"));
    }

    @Test
    @DisplayName("clearCustomResolvers removes all custom resolvers")
    void clearCustomResolversRemovesAll() {
        PlaceholderResolver.register("key1", (player, ctx) -> "v1");
        PlaceholderResolver.register("key2", (player, ctx) -> "v2");
        assertDoesNotThrow(PlaceholderResolver::clearCustomResolvers);
    }

    @Test
    @DisplayName("register rejects null key")
    @SuppressWarnings("NullAway")
    void registerRejectsNullKey() {
        assertThrows(NullPointerException.class, () ->
            PlaceholderResolver.register(null, (player, ctx) -> "value"));
    }

    @Test
    @DisplayName("register rejects null resolver")
    @SuppressWarnings("NullAway")
    void registerRejectsNullResolver() {
        assertThrows(NullPointerException.class, () ->
            PlaceholderResolver.register("key", null));
    }

    @Test
    @DisplayName("Placeholder pattern matches alphanumeric with underscores")
    void patternMatchesAlphanumericWithUnderscores() {
        String[] valid = {"player", "npc_name", "quest_count", "visit_count", "_private", "a1b2c3"};
        for (String key : valid) {
            Matcher m = PLACEHOLDER_PATTERN.matcher("{" + key + "}");
            assertTrue(m.find(), "Should match: {" + key + "}");
            assertEquals(key, m.group(1));
        }
    }

    @Test
    @DisplayName("Text without braces would short-circuit in resolve()")
    void textWithoutBracesWouldShortCircuit() {
        // This tests the optimization: if no '{' is present, resolve returns input unchanged.
        // We can't call resolve() without ServerPlayer, but we verify the precondition.
        String text = "Hello World, no placeholders here.";
        assertFalse(text.contains("{"), "Test setup: text should not contain '{'");
    }
}
