package com.devmod.foundry.tool.modifier;

/**
 * Slot types for foundry modifiers.
 */
public enum FoundryModifierSlot {
    /** Standard upgrade modifiers for tools */
    UPGRADE,
    /** Ability modifiers with special effects */
    ABILITY,
    /** Material-inherent traits (no slots used) */
    TRAIT,
    /** Defense modifiers for armor */
    DEFENSE,
    /** Slotless modifiers that don't consume any slots */
    SLOTLESS;

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

    /**
     * @return true if this slot type consumes slots
     */
    public boolean consumesSlots() {
        return this != TRAIT && this != SLOTLESS;
    }
}
