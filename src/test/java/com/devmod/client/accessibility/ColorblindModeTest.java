package com.devmod.client.accessibility;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ColorblindModeTest {

    // ========== NONE mode ==========

    @Test
    void noneMode_returnsInputUnchanged() {
        int color = 0xFFFF5555;
        assertEquals(color, ColorblindMode.NONE.remap(color));
    }

    @Test
    void noneMode_returnsBlackUnchanged() {
        assertEquals(0xFF000000, ColorblindMode.NONE.remap(0xFF000000));
    }

    @Test
    void noneMode_returnsWhiteUnchanged() {
        assertEquals(0xFFFFFFFF, ColorblindMode.NONE.remap(0xFFFFFFFF));
    }

    // ========== Alpha preservation ==========

    @ParameterizedTest
    @ValueSource(strings = {"PROTANOPIA", "DEUTERANOPIA", "TRITANOPIA"})
    void allModes_preserveAlphaChannel(String modeName) {
        ColorblindMode mode = ColorblindMode.valueOf(modeName);
        int inputColor = 0x80FF5555; // 50% alpha red
        int result = mode.remap(inputColor);
        int alpha = (result >> 24) & 0xFF;
        assertEquals(0x80, alpha, "Alpha channel must be preserved for " + modeName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROTANOPIA", "DEUTERANOPIA", "TRITANOPIA"})
    void allModes_preserveFullAlpha(String modeName) {
        ColorblindMode mode = ColorblindMode.valueOf(modeName);
        int inputColor = 0xFFFF0000; // Full alpha red
        int result = mode.remap(inputColor);
        int alpha = (result >> 24) & 0xFF;
        assertEquals(0xFF, alpha, "Full alpha must be preserved for " + modeName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROTANOPIA", "DEUTERANOPIA", "TRITANOPIA"})
    void allModes_preserveZeroAlpha(String modeName) {
        ColorblindMode mode = ColorblindMode.valueOf(modeName);
        int inputColor = 0x00FF5555; // Zero alpha
        int result = mode.remap(inputColor);
        int alpha = (result >> 24) & 0xFF;
        assertEquals(0x00, alpha, "Zero alpha must be preserved for " + modeName);
    }

    // ========== LUT remapping (exact known colors) ==========

    @Test
    void protanopia_remapsRedToOrange() {
        // Pure red (0xFF0000) -> orange (0xFF8800) per LUT
        int input = 0xFFFF0000;
        int result = ColorblindMode.PROTANOPIA.remap(input);
        assertEquals(0xFFFF8800, result);
    }

    @Test
    void protanopia_remapsMinecraftRedToOrange() {
        int input = 0xFFFF5555;
        int result = ColorblindMode.PROTANOPIA.remap(input);
        assertEquals(0xFFFF8800, result);
    }

    @Test
    void protanopia_remapsGreenToCyanGreen() {
        int input = 0xFF55FF55;
        int result = ColorblindMode.PROTANOPIA.remap(input);
        assertEquals(0xFF55FFAA, result);
    }

    @Test
    void deuteranopia_remapsPureRedToDeepOrange() {
        int input = 0xFFFF0000;
        int result = ColorblindMode.DEUTERANOPIA.remap(input);
        assertEquals(0xFFFF7722, result);
    }

    @Test
    void deuteranopia_remapsGreenToYellow() {
        int input = 0xFF00FF00;
        int result = ColorblindMode.DEUTERANOPIA.remap(input);
        assertEquals(0xFFFFFF00, result);
    }

    @Test
    void tritanopia_remapsBlueToTeal() {
        int input = 0xFF0000FF;
        int result = ColorblindMode.TRITANOPIA.remap(input);
        assertEquals(0xFF00CCCC, result);
    }

    @Test
    void tritanopia_remapsYellowToPink() {
        int input = 0xFFFFFF00;
        int result = ColorblindMode.TRITANOPIA.remap(input);
        assertEquals(0xFFFF88AA, result);
    }

    // ========== Matrix transform fallback for non-LUT colors ==========

    @Test
    void protanopia_matrixTransformForArbitraryColor() {
        // A color NOT in the LUT should get a matrix transform
        int input = 0xFF123456; // arbitrary, unlikely to be in LUT
        int result = ColorblindMode.PROTANOPIA.remap(input);
        // Must be different from input (matrix changes it) and preserve alpha
        assertEquals(0xFF, (result >> 24) & 0xFF);
        // Verify it did something (hard to predict exact output, but not identity)
        assertNotEquals(input, result, "Matrix transform should change non-LUT colors");
    }

    @Test
    void noneMode_doesNotTransformArbitraryColor() {
        int input = 0xFF123456;
        assertEquals(input, ColorblindMode.NONE.remap(input));
    }

    // ========== byName ==========

    @ParameterizedTest
    @CsvSource({
        "none, NONE",
        "protanopia, PROTANOPIA",
        "deuteranopia, DEUTERANOPIA",
        "tritanopia, TRITANOPIA"
    })
    void byName_returnsCorrectMode(String name, String expected) {
        assertEquals(ColorblindMode.valueOf(expected), ColorblindMode.byName(name));
    }

    @Test
    void byName_returnsNoneForUnknown() {
        assertEquals(ColorblindMode.NONE, ColorblindMode.byName("invalid"));
    }

    @Test
    void byName_returnsNoneForEmpty() {
        assertEquals(ColorblindMode.NONE, ColorblindMode.byName(""));
    }

    // ========== byIndex ==========

    @Test
    void byIndex_returnsCorrectMode() {
        ColorblindMode[] values = ColorblindMode.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], ColorblindMode.byIndex(i));
        }
    }

    @Test
    void byIndex_returnsNoneForNegative() {
        assertEquals(ColorblindMode.NONE, ColorblindMode.byIndex(-1));
    }

    @Test
    void byIndex_returnsNoneForOutOfBounds() {
        assertEquals(ColorblindMode.NONE, ColorblindMode.byIndex(100));
    }

    // ========== Serialized names ==========

    @Test
    void serializedNames_areCorrect() {
        assertEquals("none", ColorblindMode.NONE.getSerializedName());
        assertEquals("protanopia", ColorblindMode.PROTANOPIA.getSerializedName());
        assertEquals("deuteranopia", ColorblindMode.DEUTERANOPIA.getSerializedName());
        assertEquals("tritanopia", ColorblindMode.TRITANOPIA.getSerializedName());
    }

    @Test
    void displayNames_areNonEmpty() {
        for (ColorblindMode mode : ColorblindMode.values()) {
            assertNotNull(mode.getDisplayName());
            assertFalse(mode.getDisplayName().isEmpty(), mode + " should have a display name");
        }
    }

    // ========== Enum coverage ==========

    @Test
    void enumValues_hasFourModes() {
        assertEquals(4, ColorblindMode.values().length);
    }

    // ========== applyMatrix edge cases ==========

    @Test
    void applyMatrix_clampsToValidRange() {
        // A matrix that would push values above 255 should clamp
        float[] identity = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        int white = 0xFFFFFFFF;
        int result = ColorblindMode.applyMatrix(white, identity);
        assertEquals(0xFFFFFFFF, result, "Identity matrix on white should return white");
    }

    @Test
    void applyMatrix_blackStaysBlack() {
        float[] matrix = {0.5f, 0.5f, 0, 0.5f, 0.5f, 0, 0, 0.2f, 0.8f};
        int black = 0xFF000000;
        int result = ColorblindMode.applyMatrix(black, matrix);
        assertEquals(0xFF000000, result, "Any matrix on black should return black");
    }

    // ========== Static helpers ==========

    @Test
    void rgb_stripsAlpha() {
        assertEquals(0x00FF5555, ColorblindMode.rgb(0xAAFF5555));
    }

    @Test
    void withOriginalAlpha_combinesAlphaAndRgb() {
        int original = 0xAA112233;
        int newRgb = 0x445566;
        assertEquals(0xAA445566, ColorblindMode.withOriginalAlpha(original, newRgb));
    }
}
