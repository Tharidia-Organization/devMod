package com.devmod.portal;

import com.devmod.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RuneType enum.
 */
class RuneTypeTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Test
    @DisplayName("All 5 rune types are present")
    void all5RuneTypesPresent() {
        assertEquals(5, RuneType.values().length,
            "Should have exactly 5 rune types: HASTE, GATE, ENHANCER, STRONG_ENHANCER, INFINITY");
    }

    @Test
    @DisplayName("Rune types have correct names")
    void runeTypesHaveCorrectNames() {
        assertEquals("haste", RuneType.HASTE.getSerializedName());
        assertEquals("gate", RuneType.GATE.getSerializedName());
        assertEquals("enhancer", RuneType.ENHANCER.getSerializedName());
        assertEquals("strong_enhancer", RuneType.STRONG_ENHANCER.getSerializedName());
        assertEquals("infinity", RuneType.INFINITY.getSerializedName());
    }

    @Test
    @DisplayName("byIndex returns correct rune")
    void byIndexReturnsCorrectRune() {
        assertEquals(RuneType.HASTE, RuneType.byIndex(0));
        assertEquals(RuneType.GATE, RuneType.byIndex(1));
        assertEquals(RuneType.ENHANCER, RuneType.byIndex(2));
        assertEquals(RuneType.STRONG_ENHANCER, RuneType.byIndex(3));
        assertEquals(RuneType.INFINITY, RuneType.byIndex(4));
    }

    @Test
    @DisplayName("byIndex with invalid index returns null")
    void byIndexInvalidReturnsNull() {
        assertNull(RuneType.byIndex(-1));
        assertNull(RuneType.byIndex(5));
        assertNull(RuneType.byIndex(100));
    }

    @Test
    @DisplayName("Rune glow colors are valid RGB")
    void glowColorsAreValidRgb() {
        for (RuneType rune : RuneType.values()) {
            int color = rune.getGlowColor();
            assertTrue(color >= 0 && color <= 0xFFFFFF,
                rune.name() + " glow color should be valid RGB (0x000000-0xFFFFFF)");
        }
    }

    @Test
    @DisplayName("Each rune has distinct glow color")
    void eachRuneHasDistinctColor() {
        RuneType[] runes = RuneType.values();
        for (int i = 0; i < runes.length; i++) {
            for (int j = i + 1; j < runes.length; j++) {
                assertNotEquals(runes[i].getGlowColor(), runes[j].getGlowColor(),
                    runes[i].name() + " and " + runes[j].name() + " should have different colors");
            }
        }
    }

    @Test
    @DisplayName("getIndex returns correct ordinal")
    void getIndexReturnsOrdinal() {
        for (RuneType rune : RuneType.values()) {
            assertEquals(rune.ordinal(), rune.getIndex(),
                rune.name() + " index should match ordinal");
        }
    }
}
