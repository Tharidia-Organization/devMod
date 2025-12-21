package com.frenkvs.devmod.ui.radial.model;

import java.util.Objects;

/**
 * Macro-categories that organize the radial menu into 6 logical groups.
 * Each macro-category contains a focused set of sub-categories displayed in the outer ring.
 *
 * <p>Layout:</p>
 * <pre>
 *     ANALYZE (top)
 *     TELEMETRY (upper-right)
 *     COMBAT (right-lower)
 *     ARENA (bottom)
 *     PLAY (lower-left)
 *     TOOLS (left-upper)
 * </pre>
 *
 * <p>Each macro has:</p>
 * <ul>
 *   <li>name - Display name shown in tooltips</li>
 *   <li>icon - Emoji icon shown in center hub segment</li>
 *   <li>color - ARGB color (0xFFxxxxxx format) for theming</li>
 *   <li>description - Tooltip description of category purpose</li>
 * </ul>
 */
public enum MacroCategory {
    /**
     * Debug, spatial analysis, and visualization tools.
     * Color: Blue (0xFF4488FF)
     */
    ANALYZE("Analyze", "\uD83D\uDD0E", 0xFF4488FF, "Debug, spatial analysis, visualization"),

    /**
     * Telemetry data, dashboards, and exports.
     * Color: Cyan (0xFF66CCFF)
     */
    TELEMETRY("Telemetry", "\uD83D\uDCE1", 0xFF66CCFF, "Telemetry, dashboards, exports"),

    /**
     * Combat tools, abilities, and heatmaps.
     * Color: Red (0xFFFF4444)
     */
    COMBAT("Combat", "\u2694", 0xFFFF4444, "Combat tools, abilities, heatmaps"),

    /**
     * Arena systems and operations.
     * Color: Emerald (0xFF55DD88)
     */
    ARENA("Arena", "\uD83C\uDFF0", 0xFF55DD88, "Arena ops, templates, autosmoke"),

    /**
     * Quests, endurance mode, and party play.
     * Color: Green (0xFF44FF88)
     */
    PLAY("Play", "\uD83C\uDFAE", 0xFF44FF88, "Quests, endurance, party"),

    /**
     * Settings, editors, and testing utilities.
     * Color: Orange (0xFFFFAA00)
     */
    TOOLS("Tools", "\uD83D\uDEE0", 0xFFFFAA00, "Settings, editors, testing utilities");

    private final String name;
    private final int color;
    private final String description;
    private final String icon;

    MacroCategory(String name, String icon, int color, String description) {
        this.name = Objects.requireNonNull(name, "MacroCategory name cannot be null");
        this.icon = Objects.requireNonNull(icon, "MacroCategory icon cannot be null");
        this.color = color;
        this.description = Objects.requireNonNull(description, "MacroCategory description cannot be null");
    }

    /**
     * Returns the display name of this macro-category.
     * @return the name (e.g., "Analyze", "Combat")
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the emoji icon for this macro-category.
     */
    public String getIcon() {
        return icon;
    }

    /**
     * Returns the ARGB color for this macro-category.
     * @return the color in 0xAARRGGBB format
     */
    public int getColor() {
        return color;
    }

    /**
     * Returns the tooltip description for this macro-category.
     * @return the description text
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the macro-category at the given ordinal index.
     * @param index the ordinal index (0-3)
     * @return the macro-category at that index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public static MacroCategory fromIndex(int index) {
        MacroCategory[] values = values();
        if (index < 0 || index >= values.length) {
            throw new IndexOutOfBoundsException(
                "MacroCategory index " + index + " out of range [0, " + (values.length - 1) + "]");
        }
        return values[index];
    }

    /**
     * Returns the number of macro-categories.
     * @return 6
     */
    public static int count() {
        return values().length;
    }

    /**
     * Checks if this macro-category is adjacent to another in the hub layout.
     * @param other the other macro-category
     * @return true if adjacent (differ by 1 in ordinal, wrapping)
     */
    public boolean isAdjacentTo(MacroCategory other) {
        if (other == null) return false;
        int diff = Math.abs(this.ordinal() - other.ordinal());
        return diff == 1 || diff == count() - 1;
    }
}
