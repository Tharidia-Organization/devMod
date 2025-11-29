package com.frenkvs.devmod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends Screen {

    public SettingsScreen() {
        super(Component.literal("Impostazioni Mob Viewer"));
    }

    @Override
    protected void init() {
        int w = 200; // Larghezza bottoni
        int h = 20;
        int x = width / 2 - w / 2;
        int y = height / 4;

        // 1. Toggle Overlay (Scritte HUD)
        this.addRenderableWidget(Button.builder(
                        Component.literal("Overlay HUD: " + (ModConfig.showOverlay ? "ATTIVO" : "SPENTO")),
                        button -> {
                            ModConfig.showOverlay = !ModConfig.showOverlay;
                            button.setMessage(Component.literal("Overlay HUD: " + (ModConfig.showOverlay ? "ATTIVO" : "SPENTO")));
                        })
                .pos(x, y).size(w, h).build());

        // 2. Toggle Rendering (Cerchi/Blocchi)
        this.addRenderableWidget(Button.builder(
                        Component.literal("Render Mondo: " + (ModConfig.showRender ? "ATTIVO" : "SPENTO")),
                        button -> {
                            ModConfig.showRender = !ModConfig.showRender;
                            button.setMessage(Component.literal("Render Mondo: " + (ModConfig.showRender ? "ATTIVO" : "SPENTO")));
                        })
                .pos(x, y + 25).size(w, h).build());

        // 3. Render Mode (Blocchi vs Cerchio)
        this.addRenderableWidget(Button.builder(
                        Component.literal("Stile: " + (ModConfig.renderAsBlocks ? "GRIGLIA BLOCCHI" : "CERCHIO SEMPLICE")),
                        button -> {
                            ModConfig.renderAsBlocks = !ModConfig.renderAsBlocks;
                            button.setMessage(Component.literal("Stile: " + (ModConfig.renderAsBlocks ? "GRIGLIA BLOCCHI" : "CERCHIO SEMPLICE")));
                        })
                .pos(x, y + 50).size(w, h).build());

        // 4. Colore Overlay
        this.addRenderableWidget(Button.builder(
                        Component.literal("Colore Vista: " + ModConfig.getColorName()),
                        button -> {
                            ModConfig.cycleColor();
                            button.setMessage(Component.literal("Colore Vista: " + ModConfig.getColorName()));
                        })
                .pos(x, y + 75).size(w, h).build());

        // Chiudi
        this.addRenderableWidget(Button.builder(Component.literal("CHIUDI"), b -> this.onClose())
                .pos(x, y + 120).size(w, h).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, this.title, width / 2, 20, 0xFFFFFF);
    }
}