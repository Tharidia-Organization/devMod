package com.devmod.area.aesthetic;

import java.util.Locale;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * BIBBIA ESTETICA - REGOLA 1: PALETTE STATI EDITOR
 *
 * Ogni stato dell'Area Editor ha colori primario e secondario fissi.
 * Questi colori definiscono l'identità visiva del sistema Area Builder.
 */
public enum EditorState {
    /**
     * Editor piazzato, non attivo.
     */
    IDLE(DesignTokens.AreaBuilder.STATE_IDLE_PRIMARY, DesignTokens.AreaBuilder.STATE_IDLE_SECONDARY),

    /**
     * Mostra anteprima area.
     */
    PREVIEW(DesignTokens.AreaBuilder.STATE_PREVIEW_PRIMARY, DesignTokens.AreaBuilder.STATE_PREVIEW_SECONDARY),

    /**
     * Costruzione in progress.
     */
    BUILDING(DesignTokens.AreaBuilder.STATE_BUILDING_PRIMARY, DesignTokens.AreaBuilder.STATE_BUILDING_SECONDARY),

    /**
     * Costruzione terminata.
     */
    COMPLETE(DesignTokens.AreaBuilder.STATE_COMPLETE_PRIMARY, DesignTokens.AreaBuilder.STATE_COMPLETE_SECONDARY),

    /**
     * Overlap, bounds, permessi.
     */
    ERROR(DesignTokens.AreaBuilder.STATE_ERROR_PRIMARY, DesignTokens.AreaBuilder.STATE_ERROR_SECONDARY),

    /**
     * Area non modificabile.
     */
    LOCKED(DesignTokens.AreaBuilder.STATE_LOCKED_PRIMARY, DesignTokens.AreaBuilder.STATE_LOCKED_SECONDARY);

    /**
     * Primary color for this state.
     */
    private final int primary;

    /**
     * Secondary color for this state.
     */
    private final int secondary;

    EditorState(int primary, int secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    public int getPrimary() { return primary; }
    public int getSecondary() { return secondary; }

    /**
     * Returns the primary color with full alpha.
     */
    public int getPrimaryARGB() {
        return DesignTokens.AreaBuilder.ALPHA_FULL | primary;
    }

    /**
     * Returns the secondary color with full alpha.
     */
    public int getSecondaryARGB() {
        return DesignTokens.AreaBuilder.ALPHA_FULL | secondary;
    }

    /**
     * Returns the primary color as RGB float array (for particles).
     */
    public float[] getPrimaryRGB() {
        return new float[] {
            ((primary >> 16) & 0xFF) / 255.0f,
            ((primary >> 8) & 0xFF) / 255.0f,
            (primary & 0xFF) / 255.0f
        };
    }

    /**
     * Returns the localization key for this state.
     */
    public String getTranslationKey() {
        return "area.state." + name().toLowerCase(Locale.ROOT);
    }
}
