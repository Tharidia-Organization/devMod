package com.devmod.npc;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * NPC state enum with unified color palette.
 * These colors are used for glow outlines, particles, dialog borders, and floating names.
 *
 * <p>BIBBIA ESTETICA R1: Palette Stati NPC - INVIOLABILE
 */
public enum NpcState {
    /** NPC in attesa, disponibile */
    IDLE(DesignTokens.Npc.STATE_IDLE_PRIMARY, DesignTokens.Npc.STATE_IDLE_SECONDARY),

    /** Dialog attivo con player */
    TALKING(DesignTokens.Npc.STATE_TALKING_PRIMARY, DesignTokens.Npc.STATE_TALKING_SECONDARY),

    /** Elaborando (delay action) */
    THINKING(DesignTokens.Npc.STATE_THINKING_PRIMARY, DesignTokens.Npc.STATE_THINKING_SECONDARY),

    /** Eseguendo azione */
    ACTION(DesignTokens.Npc.STATE_ACTION_PRIMARY, DesignTokens.Npc.STATE_ACTION_SECONDARY),

    /** Errore o azione fallita */
    ERROR(DesignTokens.Npc.STATE_ERROR_PRIMARY, DesignTokens.Npc.STATE_ERROR_SECONDARY),

    /** NPC non interagibile */
    UNAVAILABLE(DesignTokens.Npc.STATE_UNAVAILABLE_PRIMARY, DesignTokens.Npc.STATE_UNAVAILABLE_SECONDARY);

    /** Primary color (brighter) */
    private final int primary;

    /** Secondary color (darker) */
    private final int secondary;

    NpcState(int primary, int secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    public int getPrimary() { return primary; }
    public int getSecondary() { return secondary; }

    /**
     * Get primary color with full alpha.
     */
    public int getPrimaryARGB() {
        return DesignTokens.Npc.ALPHA_FULL | primary;
    }

    /**
     * Get secondary color with full alpha.
     */
    public int getSecondaryARGB() {
        return DesignTokens.Npc.ALPHA_FULL | secondary;
    }

    /**
     * Get primary color with custom alpha (0-255).
     */
    public int getPrimaryWithAlpha(int alpha) {
        return (alpha << 24) | primary;
    }

    /**
     * Get secondary color with custom alpha (0-255).
     */
    public int getSecondaryWithAlpha(int alpha) {
        return (alpha << 24) | secondary;
    }

    /**
     * Interpolate between primary and secondary color.
     * @param t Value between 0 (primary) and 1 (secondary)
     */
    public int interpolate(float t) {
        t = Math.max(0, Math.min(1, t));
        int r1 = (primary >> 16) & 0xFF;
        int g1 = (primary >> 8) & 0xFF;
        int b1 = primary & 0xFF;
        int r2 = (secondary >> 16) & 0xFF;
        int g2 = (secondary >> 8) & 0xFF;
        int b2 = secondary & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return DesignTokens.Npc.ALPHA_FULL | (r << 16) | (g << 8) | b;
    }
}
