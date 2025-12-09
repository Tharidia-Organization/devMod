package com.frenkvs.devmod.ui.hub;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Interfaccia base per i pannelli del TestingHub.
 * Ogni pannello gestisce il proprio rendering e input.
 */
public interface HubPanel {

    /**
     * Renderizza il pannello.
     */
    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /**
     * Gestisce click del mouse.
     * @return true se l'evento è stato gestito
     */
    boolean mouseClicked(double mouseX, double mouseY, int button);

    /**
     * Gestisce rilascio del mouse.
     * @return true se l'evento è stato gestito
     */
    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Gestisce scroll del mouse.
     * @return true se l'evento è stato gestito
     */
    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    /**
     * Gestisce input da tastiera.
     * @return true se l'evento è stato gestito
     */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Gestisce input carattere.
     * @return true se l'evento è stato gestito
     */
    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    /**
     * Verifica se il mouse è sopra il pannello.
     */
    boolean isMouseOver(int mouseX, int mouseY);

    /**
     * Aggiorna lo stato del pannello.
     */
    default void tick() {}

    /**
     * Forza refresh dei dati.
     */
    default void refresh() {}

    /**
     * Restituisce i bounds del pannello.
     */
    default int getX() { return 0; }
    default int getY() { return 0; }
    default int getWidth() { return 0; }
    default int getHeight() { return 0; }
}
