package com.frenkvs.devmod.panels.core;

import com.frenkvs.devmod.ui.UIConstants;

/**
 * Tipi di pannelli flottanti disponibili nel sistema.
 * Ogni tipo ha configurazioni specifiche per rendering e comportamento.
 */
public enum PanelType {
    /** Pannello info entita' - mostra stats del mob/player target */
    ENTITY_INFO("Entity Info", 180, 120, 8000, true, UIConstants.Status.INFO),

    /** Pannello combattimento - mostra danni inflitti/ricevuti */
    COMBAT("Combat", 160, 100, 4000, false, UIConstants.Status.ERROR),

    /** Pannello stato tool - mostra overlay attivi */
    TOOL_STATUS("Tools", 140, 80, 0, true, UIConstants.Text.ACCENT),

    /** Pannello progresso test - mostra test corrente */
    TEST_PROGRESS("Test", 200, 90, 0, true, UIConstants.Status.SUCCESS),

    /** Pannello custom generico */
    CUSTOM("Custom", 150, 100, 5000, true, UIConstants.Text.PRIMARY);

    private final String displayName;
    private final int defaultWidth;
    private final int defaultHeight;
    private final long autoExpireMs;
    private final boolean canPin;
    private final int accentColor;

    PanelType(String displayName, int defaultWidth, int defaultHeight,
              long autoExpireMs, boolean canPin, int accentColor) {
        this.displayName = displayName;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.autoExpireMs = autoExpireMs;
        this.canPin = canPin;
        this.accentColor = accentColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultWidth() {
        return defaultWidth;
    }

    public int getDefaultHeight() {
        return defaultHeight;
    }

    /**
     * Tempo dopo cui il pannello si chiude automaticamente.
     * 0 = non scade mai (richiede chiusura manuale o pin).
     */
    public long getAutoExpireMs() {
        return autoExpireMs;
    }

    /**
     * Se questo tipo di pannello puo' essere pinnato.
     */
    public boolean canPin() {
        return canPin;
    }

    /**
     * Colore accent per bordi e highlights.
     */
    public int getAccentColor() {
        return accentColor;
    }

    /**
     * Se il pannello scade automaticamente.
     */
    public boolean hasAutoExpire() {
        return autoExpireMs > 0;
    }
}
