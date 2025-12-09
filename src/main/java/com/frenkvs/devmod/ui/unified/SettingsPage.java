package com.frenkvs.devmod.ui.unified;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Interfaccia base per tutte le pagine di settings.
 * Ogni pagina gestisce una categoria specifica di impostazioni.
 */
public interface SettingsPage {

    /**
     * Ottiene la categoria di questa pagina.
     */
    SettingsCategory getCategory();

    /**
     * Ottiene il titolo della pagina.
     */
    String getTitle();

    /**
     * Inizializza la pagina. Chiamato quando la pagina viene selezionata.
     */
    default void init() {}

    /**
     * Renderizza il contenuto della pagina.
     *
     * @param graphics Context di rendering
     * @param font Font per il testo
     * @param x Coordinata X area contenuto
     * @param y Coordinata Y area contenuto
     * @param width Larghezza area contenuto
     * @param height Altezza area contenuto
     * @param mouseX Posizione mouse X
     * @param mouseY Posizione mouse Y
     */
    void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY);

    /**
     * Gestisce un click del mouse.
     *
     * @param mouseX Posizione mouse X
     * @param mouseY Posizione mouse Y
     * @param button Pulsante (0=sinistro, 1=destro)
     * @param contentX Coordinata X area contenuto
     * @param contentY Coordinata Y area contenuto
     * @param contentWidth Larghezza area contenuto
     * @return true se il click e' stato gestito
     */
    boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth);

    /**
     * Gestisce il rilascio del mouse.
     */
    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Gestisce il drag del mouse.
     */
    default boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    /**
     * Gestisce lo scroll del mouse.
     */
    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    /**
     * Gestisce input da tastiera.
     */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Chiamato ogni tick per aggiornamenti.
     */
    default void tick() {}

    /**
     * Chiamato quando la pagina viene chiusa/cambiata.
     */
    default void onClose() {}

    /**
     * Verifica se ci sono modifiche non salvate.
     */
    default boolean hasUnsavedChanges() {
        return false;
    }

    /**
     * Salva le modifiche.
     */
    default void saveChanges() {}

    /**
     * Resetta ai valori default.
     */
    default void resetToDefaults() {}

    /**
     * Ottiene l'altezza totale del contenuto (per scrolling).
     */
    default int getContentHeight() {
        return 200;
    }
}
