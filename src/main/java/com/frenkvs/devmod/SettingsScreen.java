package com.frenkvs.devmod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class SettingsScreen extends Screen {

    public SettingsScreen() {
        super(Component.translatable("devmod.settings.title"));
    }

    @Override
    protected void init() {
        int w = 120; // Larghezza bottoni più piccola
        int h = 16;  // Altezza bottoni più piccola
        int x = 10;  // Posizione vicino al bordo sinistro
        int y = 10;  // Posizione vicino al bordo superiore

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
                .pos(x, y + 20).size(w, h).build());

        // 3. Colore Vista
        this.addRenderableWidget(Button.builder(
                        Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey())),
                        button -> {
                            ModConfig.cycleColor();
                            button.setMessage(Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey())));
                        })
                .pos(x, y + 40).size(w, h).build());

        // SPAZIO SEPARATORE
        int group2Y = y + 70; // Più spazio tra i gruppi

        // 4. Aggro Distance - Friendly Mobs
        this.addRenderableWidget(Button.builder(
                        Component.literal("Aggro Friendly: " + (ModConfig.renderFriendlyAggro ? "ON" : "OFF")),
                        button -> {
                            ModConfig.renderFriendlyAggro = !ModConfig.renderFriendlyAggro;
                            button.setMessage(Component.literal("Aggro Friendly: " + (ModConfig.renderFriendlyAggro ? "ON" : "OFF")));
                        })
                .pos(x, group2Y).size(w, h).build());

        // 5. Attack Distance - Friendly Mobs
        this.addRenderableWidget(Button.builder(
                        Component.literal("Attack Friendly: " + (ModConfig.renderFriendlyAttack ? "ON" : "OFF")),
                        button -> {
                            ModConfig.renderFriendlyAttack = !ModConfig.renderFriendlyAttack;
                            button.setMessage(Component.literal("Attack Friendly: " + (ModConfig.renderFriendlyAttack ? "ON" : "OFF")));
                        })
                .pos(x, group2Y + 20).size(w, h).build());

        // 6. Aggro Distance - Hostile Mobs
        this.addRenderableWidget(Button.builder(
                        Component.literal("Aggro Hostile: " + (ModConfig.renderHostileAggro ? "ON" : "OFF")),
                        button -> {
                            ModConfig.renderHostileAggro = !ModConfig.renderHostileAggro;
                            button.setMessage(Component.literal("Aggro Hostile: " + (ModConfig.renderHostileAggro ? "ON" : "OFF")));
                        })
                .pos(x, group2Y + 40).size(w, h).build());

        // 7. Attack Distance - Hostile Mobs
        this.addRenderableWidget(Button.builder(
                        Component.literal("Attack Hostile: " + (ModConfig.renderHostileAttack ? "ON" : "OFF")),
                        button -> {
                            ModConfig.renderHostileAttack = !ModConfig.renderHostileAttack;
                            button.setMessage(Component.literal("Attack Hostile: " + (ModConfig.renderHostileAttack ? "ON" : "OFF")));
                        })
                .pos(x, group2Y + 60).size(w, h).build());

        // SPAZIO SEPARATORE E SLIDER
        int sliderY = group2Y + 90; // Spazio prima dello slider

        // 8. Render Distance Slider
        this.addRenderableWidget(new RenderDistanceSlider(x, sliderY, w, h, ModConfig.renderDistanceChunks));

        // Chiudi
        this.addRenderableWidget(Button.builder(Component.translatable("devmod.settings.close"), b -> this.onClose())
                .pos(width - w - 10, height - h - 10).size(w, h).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, this.title, width / 2, 20, 0xFFFFFF);
    }

    private static class RenderDistanceSlider extends AbstractSliderButton {
        public RenderDistanceSlider(int x, int y, int width, int height, int initialValue) {
            super(x, y, width, height, Component.literal("Render Distance: " + initialValue + " chunks"), (initialValue - 1) / 9.0);
        }

        @Override
        protected void updateMessage() {
            int value = (int) (this.value * 9) + 1;
            this.setMessage(Component.literal("Render Distance: " + value + " chunks"));
            applyValue(); // Update config value in real-time during drag
        }

        @Override
        protected void applyValue() {
            ModConfig.renderDistanceChunks = (int) (this.value * 9) + 1;
        }
    }
}
