package com.frenkvs.devmod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends Screen {

    public SettingsScreen() {
        super(Component.translatable("devmod.settings.title"));
    }

    @Override
    protected void init() {
        int w = 200; // Larghezza bottoni
        int h = 20;
        int x = width / 2 - w / 2;
        int y = height / 4;

        // 1. Toggle Overlay (Scritte HUD)
        this.addRenderableWidget(Button.builder(
                        Component.translatable("devmod.settings.overlay", Component.translatable(ModConfig.showOverlay ? "devmod.settings.enabled" : "devmod.settings.disabled")),
                        button -> {
                            ModConfig.showOverlay = !ModConfig.showOverlay;
                            button.setMessage(Component.translatable("devmod.settings.overlay", Component.translatable(ModConfig.showOverlay ? "devmod.settings.enabled" : "devmod.settings.disabled")));
                        })
                .pos(x, y).size(w, h).build());

        // 2. Toggle Rendering (Sfere)
        this.addRenderableWidget(Button.builder(
                        Component.translatable("devmod.settings.render", Component.translatable(ModConfig.showRender ? "devmod.settings.enabled" : "devmod.settings.disabled")),
                        button -> {
                            ModConfig.showRender = !ModConfig.showRender;
                            button.setMessage(Component.translatable("devmod.settings.render", Component.translatable(ModConfig.showRender ? "devmod.settings.enabled" : "devmod.settings.disabled")));
                        })
                .pos(x, y + 25).size(w, h).build());

        // 3. Colore Vista
        this.addRenderableWidget(Button.builder(
                        Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey())),
                        button -> {
                            ModConfig.cycleColor();
                            button.setMessage(Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey())));
                        })
                .pos(x, y + 50).size(w, h).build());

        // Chiudi
        this.addRenderableWidget(Button.builder(Component.translatable("devmod.settings.close"), b -> this.onClose())
                .pos(x, y + 95).size(w, h).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, this.title, width / 2, 20, 0xFFFFFF);
    }
}
