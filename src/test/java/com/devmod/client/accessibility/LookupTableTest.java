package com.devmod.client.accessibility;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LookupTableTest {

    // ========== Protanopia remapping ==========

    @Test
    void protanopia_remapsPureRed() {
        int input = 0xFFFF0000;
        int result = LookupTable.PROTANOPIA.remap(input);
        assertEquals(0xFFFF8800, result);
    }

    @Test
    void protanopia_remapsMinecraftRed() {
        int input = 0xFFFF5555;
        int result = LookupTable.PROTANOPIA.remap(input);
        assertEquals(0xFFFF8800, result);
    }

    @Test
    void protanopia_remapsMinecraftGreen() {
        int input = 0xFF55FF55;
        int result = LookupTable.PROTANOPIA.remap(input);
        assertEquals(0xFF55FFAA, result);
    }

    // ========== Deuteranopia remapping ==========

    @Test
    void deuteranopia_remapsPureGreenToYellow() {
        int input = 0xFF00FF00;
        int result = LookupTable.DEUTERANOPIA.remap(input);
        assertEquals(0xFFFFFF00, result);
    }

    @Test
    void deuteranopia_remapsMinecraftRedToOrange() {
        int input = 0xFFFF5555;
        int result = LookupTable.DEUTERANOPIA.remap(input);
        assertEquals(0xFFFF7722, result);
    }

    // ========== Tritanopia remapping ==========

    @Test
    void tritanopia_remapsPureBlueToTeal() {
        int input = 0xFF0000FF;
        int result = LookupTable.TRITANOPIA.remap(input);
        assertEquals(0xFF00CCCC, result);
    }

    @Test
    void tritanopia_remapsMinecraftBlue() {
        int input = 0xFF5555FF;
        int result = LookupTable.TRITANOPIA.remap(input);
        assertEquals(0xFF55FFFF, result);
    }

    @Test
    void tritanopia_remapsPureYellowToPink() {
        int input = 0xFFFFFF00;
        int result = LookupTable.TRITANOPIA.remap(input);
        assertEquals(0xFFFF88AA, result);
    }

    // ========== Alpha channel preservation ==========

    @ParameterizedTest
    @EnumSource(LookupTable.class)
    void allTables_preserveAlphaForKnownColors(LookupTable table) {
        // Test with a color that's in ALL tables (red/green variants)
        int halfAlpha = 0x80FF0000;
        int result = table.remap(halfAlpha);
        int alpha = (result >> 24) & 0xFF;
        assertEquals(0x80, alpha, table + " must preserve alpha channel");
    }

    @ParameterizedTest
    @EnumSource(LookupTable.class)
    void allTables_preserveZeroAlpha(LookupTable table) {
        int zeroAlpha = 0x00FF5555;
        int result = table.remap(zeroAlpha);
        int alpha = (result >> 24) & 0xFF;
        assertEquals(0x00, alpha, table + " must preserve zero alpha");
    }

    // ========== Unknown colors pass through ==========

    @ParameterizedTest
    @EnumSource(LookupTable.class)
    void allTables_returnUnknownColorsUnchanged(LookupTable table) {
        // A color not in any LUT
        int unknownColor = 0xFF010101;
        assertEquals(unknownColor, table.remap(unknownColor),
            table + " should return unknown colors unchanged");
    }

    @Test
    void remap_returnsOriginalForBlack() {
        assertEquals(0xFF000000, LookupTable.PROTANOPIA.remap(0xFF000000));
    }

    @Test
    void remap_returnsOriginalForWhite() {
        // White (0xFFFFFF) isn't in any LUT
        assertEquals(0xFFFFFFFF, LookupTable.PROTANOPIA.remap(0xFFFFFFFF));
    }

    // ========== Enum coverage ==========

    @Test
    void hasThreeEntries() {
        assertEquals(3, LookupTable.values().length);
    }
}
