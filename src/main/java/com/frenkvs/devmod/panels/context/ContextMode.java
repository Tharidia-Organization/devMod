package com.frenkvs.devmod.panels.context;

import com.frenkvs.devmod.ui.UIConstants;

/**
 * Modi di contesto che determinano quali pannelli mostrare automaticamente.
 *
 * Il sistema rileva automaticamente il contesto basandosi su:
 * - Eventi di combattimento recenti
 * - Sessione di testing attiva
 * - Interazioni con entita'
 * - Input utente esplicito
 */
public enum ContextMode {

    /**
     * Modalita' combattimento: attivata quando il player e' in combattimento.
     * Mostra: CombatPanel, EntityInfoPanel per target
     */
    COMBAT("Combat", UIConstants.Status.ERROR, true, false, true),

    /**
     * Modalita' esplorazione: default quando non in combattimento o test.
     * Mostra: EntityInfoPanel on hover, ToolStatusPanel
     */
    EXPLORE("Explore", UIConstants.Status.INFO, true, true, false),

    /**
     * Modalita' testing: attivata durante sessione di test.
     * Mostra: TestProgressPanel, ToolStatusPanel, riduce altri pannelli
     */
    TEST("Test", UIConstants.Status.SUCCESS, true, true, true),

    /**
     * Modalita' custom: l'utente controlla manualmente tutti i pannelli.
     * Nessun pannello automatico.
     */
    CUSTOM("Custom", UIConstants.Text.MUTED, false, false, false);

    private final String displayName;
    private final int color;
    private final boolean autoEntityInfo;
    private final boolean showToolStatus;
    private final boolean showProgressPanel;

    ContextMode(String displayName, int color, boolean autoEntityInfo,
                boolean showToolStatus, boolean showProgressPanel) {
        this.displayName = displayName;
        this.color = color;
        this.autoEntityInfo = autoEntityInfo;
        this.showToolStatus = showToolStatus;
        this.showProgressPanel = showProgressPanel;
    }

    /**
     * Nome visualizzato nel UI.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Colore associato a questo modo.
     */
    public int getColor() {
        return color;
    }

    /**
     * Se mostrare automaticamente EntityInfoPanel per entita' target.
     */
    public boolean showsAutoEntityInfo() {
        return autoEntityInfo;
    }

    /**
     * Se mostrare il pannello stato tool.
     */
    public boolean showsToolStatus() {
        return showToolStatus;
    }

    /**
     * Se mostrare il pannello progresso (test/combat).
     */
    public boolean showsProgressPanel() {
        return showProgressPanel;
    }

    /**
     * Se questo modo gestisce automaticamente i pannelli.
     */
    public boolean isAutomatic() {
        return this != CUSTOM;
    }

    /**
     * Ottiene la priorita' dei pannelli in questo modo.
     * Priorita' piu' alta = pannelli rimangono piu' a lungo.
     */
    public int getPanelPriority() {
        return switch (this) {
            case COMBAT -> 3;   // Alta priorita' - pannelli combat persistono
            case TEST -> 2;     // Media-alta - test info importante
            case EXPLORE -> 1;  // Normale
            case CUSTOM -> 0;   // Bassa - utente gestisce tutto
        };
    }

    /**
     * Tempo di auto-hide per EntityInfoPanel in questo modo (ms).
     * 0 = non nascondere automaticamente.
     */
    public long getEntityInfoTimeout() {
        return switch (this) {
            case COMBAT -> 0;       // Non nascondere in combat
            case TEST -> 10000;     // 10 secondi
            case EXPLORE -> 5000;   // 5 secondi
            case CUSTOM -> 0;       // Manuale
        };
    }
}
