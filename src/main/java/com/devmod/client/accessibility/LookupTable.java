package com.devmod.client.accessibility;

import java.util.HashMap;
import java.util.Map;

/**
 * Lookup tables for exact color remapping of key DevMod UI colors.
 *
 * <p>Each CVD mode has a curated set of remappings for the ~30 most important
 * semantic colors used across HUD overlays. These ensure critical information
 * (health, damage, status) remains distinguishable.
 *
 * <p>Colors not in the LUT fall through to the matrix transform in
 * {@link ColorblindMode}.
 */
enum LookupTable {
    PROTANOPIA(buildProtanopia()),
    DEUTERANOPIA(buildDeuteranopia()),
    TRITANOPIA(buildTritanopia());

    private final Map<Integer, Integer> table;

    LookupTable(Map<Integer, Integer> table) {
        this.table = table;
    }

    /**
     * Attempts to remap a color via the LUT.
     * Returns the original color if no mapping exists.
     */
    int remap(int argbColor) {
        int rgb = argbColor & 0x00FFFFFF;
        Integer mapped = table.get(rgb);
        if (mapped != null) {
            return (argbColor & 0xFF000000) | mapped;
        }
        return argbColor;
    }

    // ============================================================================
    // PROTANOPIA: Red-blind
    // Reds -> orange/brown, green stays, health uses orange instead of red
    // ============================================================================
    private static Map<Integer, Integer> buildProtanopia() {
        Map<Integer, Integer> m = new HashMap<>();

        // Health / Danger: red -> bright orange
        m.put(0xFF5555, 0xFF8800); // Minecraft red -> orange
        m.put(0xFF0000, 0xFF8800); // Pure red -> orange
        m.put(0xE06B5B, 0xE89040); // Palette ERROR -> warm orange
        m.put(0xCC3333, 0xCC7733); // Dark red -> brown-orange
        m.put(0xFF4444, 0xFF8844); // Bright red -> bright orange

        // Success / Healing: green stays but more saturated cyan-green
        m.put(0x55FF55, 0x55FFAA); // Minecraft green -> cyan-green
        m.put(0x00FF00, 0x00FFAA); // Pure green -> cyan-green
        m.put(0x45D483, 0x45D4B0); // Palette SUCCESS -> teal-green

        // Progress bar fills
        m.put(0xFF3333, 0xFF8833); // FILL_RED -> orange
        m.put(0x33FF33, 0x33FFAA); // FILL_GREEN -> cyan-green
        m.put(0xFFCC33, 0xFFCC33); // FILL_YELLOW stays

        // Status colors
        m.put(0xFF4040, 0xFF8840); // RECORDING (red) -> orange
        m.put(0x40FF40, 0x40FFAA); // ACTIVE (green) -> cyan-green

        // Combat recap bars
        m.put(0xFF6B6B, 0xFF9B4B); // BAR_DAMAGE -> warm orange
        m.put(0xFF4545, 0xFF8545); // BAR_CRIT -> orange

        // Style rank colors (ensure distinguishable spectrum)
        // D=gray, C=blue stays, B=green->cyan, A=yellow stays, S=orange, SS=magenta, SSS=gold
        m.put(0x00FF00, 0x00FFAA); // B rank green -> cyan-green

        // Flash colors
        m.put(0xFF2020, 0xFF8820); // HEADSHOT flash red -> orange
        m.put(0x20FF20, 0x20FFAA); // HEAL flash green -> cyan-green

        return m;
    }

    // ============================================================================
    // DEUTERANOPIA: Green-blind (most common)
    // Greens -> yellow/brown, reds -> orange, blue stays
    // ============================================================================
    private static Map<Integer, Integer> buildDeuteranopia() {
        Map<Integer, Integer> m = new HashMap<>();

        // Health / Danger: red -> bright orange (further from yellow)
        m.put(0xFF5555, 0xFF7722); // Minecraft red -> deep orange
        m.put(0xFF0000, 0xFF7722); // Pure red -> deep orange
        m.put(0xE06B5B, 0xE08030); // Palette ERROR -> warm orange
        m.put(0xCC3333, 0xCC6622); // Dark red -> brown-orange
        m.put(0xFF4444, 0xFF7744); // Bright red -> orange

        // Success / Healing: green -> bright yellow
        m.put(0x55FF55, 0xFFFF55); // Minecraft green -> yellow
        m.put(0x00FF00, 0xFFFF00); // Pure green -> bright yellow
        m.put(0x45D483, 0xD4D445); // Palette SUCCESS -> yellow-green
        m.put(0x33FF33, 0xFFFF33); // Bright green -> yellow

        // Progress bar fills
        m.put(0xFF3333, 0xFF7733); // FILL_RED -> orange
        m.put(0x33FF33, 0xFFFF33); // FILL_GREEN -> yellow

        // Status colors
        m.put(0xFF4040, 0xFF7740); // RECORDING (red) -> orange
        m.put(0x40FF40, 0xFFFF40); // ACTIVE (green) -> yellow

        // Combat recap bars
        m.put(0xFF6B6B, 0xFF8B4B); // BAR_DAMAGE -> warm orange
        m.put(0xFF4545, 0xFF7545); // BAR_CRIT -> orange

        // Flash colors
        m.put(0xFF2020, 0xFF7720); // HEADSHOT flash red -> orange
        m.put(0x20FF20, 0xFFFF20); // HEAL flash green -> yellow

        // Quest status
        m.put(0x00FF00, 0xFFFF00); // completed green -> yellow

        return m;
    }

    // ============================================================================
    // TRITANOPIA: Blue-blind
    // Blues -> cyan/teal, yellows -> pink/salmon
    // ============================================================================
    private static Map<Integer, Integer> buildTritanopia() {
        Map<Integer, Integer> m = new HashMap<>();

        // Blues -> teal/cyan (shifted away from purple)
        m.put(0x5555FF, 0x55FFFF); // Minecraft blue -> cyan
        m.put(0x0000FF, 0x00CCCC); // Pure blue -> teal
        m.put(0x4E9CFF, 0x4ECCCC); // Palette ACCENT_BLUE -> teal

        // Yellows -> pink/salmon (distinct from red and green)
        m.put(0xFFFF55, 0xFF88AA); // Minecraft yellow -> pink
        m.put(0xFFFF00, 0xFF88AA); // Pure yellow -> pink
        m.put(0xF2C14E, 0xF2889E); // Palette WARNING -> salmon-pink

        // Progress fills
        m.put(0x3333FF, 0x33CCCC); // FILL_CYAN stays similar
        m.put(0xFFCC33, 0xFF88AA); // FILL_YELLOW -> pink

        // Warning colors: orange stays, yellow -> pink
        m.put(0xF2A74B, 0xF2A74B); // Palette ACCENT_AMBER stays (orange)

        // Status
        m.put(0x4040FF, 0x40CCCC); // INACTIVE-ish blue -> teal

        // Purple -> reddish-purple (more red component)
        m.put(0xAA55FF, 0xCC55AA); // Purple -> magenta-ish

        // Flash
        m.put(0x2020FF, 0x20CCCC); // Shield flash blue -> teal

        return m;
    }
}
