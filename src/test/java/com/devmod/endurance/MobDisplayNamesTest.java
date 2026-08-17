package com.devmod.endurance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the display-name derivation, including the case the two previous copies both got wrong.
 */
@DisplayName("MobDisplayNames")
class MobDisplayNamesTest {

    @Test
    @DisplayName("a plain vanilla path capitalises each underscore-separated word")
    void vanillaPath() {
        assertEquals("Wither Skeleton", MobDisplayNames.fromPath("wither_skeleton"));
        assertEquals("Zombie", MobDisplayNames.fromPath("zombie"));
    }

    @Test
    @DisplayName("a slash in a modded path is a word separator, not a character to keep")
    void moddedPathWithSlash() {
        // The regression: both old implementations split on '_' only and returned
        // "Ashen Court/bonebound Vanguard".
        assertEquals("Ashen Court Bonebound Vanguard",
            MobDisplayNames.fromPath("ashen_court/bonebound_vanguard"));
        assertEquals("Ashen Court Grave Cantor",
            MobDisplayNames.fromPath("ashen_court/grave_cantor"));
    }

    @Test
    @DisplayName("dots and dashes separate too, since a registry path may contain them")
    void otherLegalSeparators() {
        assertEquals("Some Mob Variant", MobDisplayNames.fromPath("some.mob-variant"));
    }

    @Test
    @DisplayName("repeated, leading and trailing separators do not produce stray spaces")
    void degenerateSeparators() {
        assertEquals("A B", MobDisplayNames.fromPath("_a__b_"));
        assertEquals("A B", MobDisplayNames.fromPath("/a//b/"));
    }

    @Test
    @DisplayName("null and empty are answered, not thrown on")
    void nullAndEmpty() {
        assertEquals("", MobDisplayNames.fromPath(null));
        assertEquals("", MobDisplayNames.fromPath(""));
        assertEquals("", MobDisplayNames.of(null));
    }
}
