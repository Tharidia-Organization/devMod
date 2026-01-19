package com.devmod.foundry.tool.modifier;

/**
 * Slot types for foundry modifiers.
 */
public enum FoundryModifierSlot {
    UPGRADE,
    ABILITY,
    TRAIT;

    public static FoundryModifierSlot fromString(String raw) {
        if (raw == null) {
            return UPGRADE;
        }
        try {
            return FoundryModifierSlot.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UPGRADE;
        }
    }
}
