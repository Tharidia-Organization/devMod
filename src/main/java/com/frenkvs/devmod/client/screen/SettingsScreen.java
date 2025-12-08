package com.frenkvs.devmod.client.screen;

import com.frenkvs.devmod.config.ModConfig;
import com.frenkvs.devmod.network.payload.ConfigSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class SettingsScreen extends Screen {

    public SettingsScreen() {
        super(Component.translatable("devmod.settings.title"));
    }

    @Override
    protected void init() {
        int w = 120; // Larghezza standard bottone
        int h = 20;  // Altezza standard bottone (Standard Minecraft è 20, non 16)
        int x = 10;  // Margine sinistro
        int y = 30;  // Margine dall'alto
        int step = 25; // Spazio verticale tra i bottoni (DEFINITO ORA)

        // =================================================================
        // COLONNA SINISTRA
        // =================================================================

        // 1. Overlay
        this.addRenderableWidget(Button.builder(
                Component.literal("Overlay HUD: " + (ModConfig.showOverlay ? "ON" : "OFF")),
                b -> {
                    ModConfig.showOverlay = !ModConfig.showOverlay;
                    b.setMessage(Component.literal("Overlay HUD: " + (ModConfig.showOverlay ? "ON" : "OFF")));
                }).pos(x, y).size(w, h).build());
        y += step;

        // 2. Render Sfere
        this.addRenderableWidget(Button.builder(
                Component.literal("Render Sfere: " + (ModConfig.showRender ? "ON" : "OFF")),
                b -> {
                    ModConfig.showRender = !ModConfig.showRender;
                    b.setMessage(Component.literal("Render Sfere: " + (ModConfig.showRender ? "ON" : "OFF")));
                }).pos(x, y).size(w, h).build());
        y += step;

        // 3. Colore
        this.addRenderableWidget(Button.builder(
                        Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey())),
                        button -> {
                            ModConfig.cycleColor();
                            button.setMessage(Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey())));
                        })
                .pos(x, y).size(w, h).build());
        y += step;

        // 4. CONFIGURA GRIGLIA (Il bottone che dava errore)
        // Ho corretto le variabili xL e btnW usando x e w
        this.addRenderableWidget(Button.builder(
                        Component.literal("Griglia & Tool..."),
                        b -> {
                            Minecraft.getInstance().setScreen(new VerticalGridConfigScreen(this));
                        })
                .pos(x, y)
                .size(w, h)
                .build());


        // =================================================================
        // COLONNA DESTRA
        // =================================================================
        int x2 = x + w + 20; // Posizione X colonna destra
        int yRight = 30;     // Reset Y per la colonna destra

        // 5. Stuck Detector
        this.addRenderableWidget(Button.builder(
                Component.literal("Stuck Detector: " + (ModConfig.enableStuckDebug ? "ON" : "OFF")),
                b -> {
                    ModConfig.enableStuckDebug = !ModConfig.enableStuckDebug;
                    b.setMessage(Component.literal("Stuck Detector: " + (ModConfig.enableStuckDebug ? "ON" : "OFF")));
                }).pos(x2, yRight).size(w + 20, h).build());
        yRight += step;

        // 6. Mostra Path AI (CON LOGICA NETWORK TUA)
        this.addRenderableWidget(Button.builder(
                Component.literal("Mostra Path AI: " + (ModConfig.showMobPath ? "ON" : "OFF")),
                b -> {
                    ModConfig.showMobPath = !ModConfig.showMobPath;
                    b.setMessage(Component.literal("Mostra Path AI: " + (ModConfig.showMobPath ? "ON" : "OFF")));

                    // TUA LOGICA MANTENUTA: Send config sync packet to server
                    if (Minecraft.getInstance().player != null) {
                        PacketDistributor.sendToServer(new ConfigSyncPayload(ModConfig.showMobPath));
                    }
                }).pos(x2, yRight).size(w + 20, h).build());
        yRight += step;

        // 7. Debug Chat
        this.addRenderableWidget(Button.builder(
                Component.literal("Debug Chat: " + (ModConfig.showStuckChat ? "ON" : "OFF")),
                b -> {
                    ModConfig.showStuckChat = !ModConfig.showStuckChat;
                    b.setMessage(Component.literal("Debug Chat: " + (ModConfig.showStuckChat ? "ON" : "OFF")));
                }).pos(x2, yRight).size(w + 20, h).build());
        yRight += step;

        // 8. Slider Tempo
        this.addRenderableWidget(new TimeSlider(x2, yRight, w + 20, h, ModConfig.stuckThresholdSeconds));


        // =================================================================
        // PARTE INFERIORE (Aggro/Attack)
        // =================================================================
        int group2Y = 140; // Spostiamo giù per non sovrapporre

        // Friendly Aggro
        this.addRenderableWidget(Button.builder(
                Component.literal("Aggro Friendly: " + (ModConfig.renderFriendlyAggro ? "ON" : "OFF")),
                button -> {
                    ModConfig.renderFriendlyAggro = !ModConfig.renderFriendlyAggro;
                    button.setMessage(Component.literal("Aggro Friendly: " + (ModConfig.renderFriendlyAggro ? "ON" : "OFF")));
                }).pos(x, group2Y).size(w, h).build());

        // Friendly Attack
        this.addRenderableWidget(Button.builder(
                Component.literal("Attack Friendly: " + (ModConfig.renderFriendlyAttack ? "ON" : "OFF")),
                button -> {
                    ModConfig.renderFriendlyAttack = !ModConfig.renderFriendlyAttack;
                    button.setMessage(Component.literal("Attack Friendly: " + (ModConfig.renderFriendlyAttack ? "ON" : "OFF")));
                }).pos(x, group2Y + step).size(w, h).build());

        // Hostile Aggro (Colonna destra parte bassa)
        this.addRenderableWidget(Button.builder(
                Component.literal("Aggro Hostile: " + (ModConfig.renderHostileAggro ? "ON" : "OFF")),
                button -> {
                    ModConfig.renderHostileAggro = !ModConfig.renderHostileAggro;
                    button.setMessage(Component.literal("Aggro Hostile: " + (ModConfig.renderHostileAggro ? "ON" : "OFF")));
                }).pos(x2, group2Y).size(w, h).build());

        // Hostile Attack
        this.addRenderableWidget(Button.builder(
                Component.literal("Attack Hostile: " + (ModConfig.renderHostileAttack ? "ON" : "OFF")),
                button -> {
                    ModConfig.renderHostileAttack = !ModConfig.renderHostileAttack;
                    button.setMessage(Component.literal("Attack Hostile: " + (ModConfig.renderHostileAttack ? "ON" : "OFF")));
                }).pos(x2, group2Y + step).size(w, h).build());

        // Render Distance Slider
        int sliderY = group2Y + (step * 2) + 10;
        this.addRenderableWidget(new RenderDistanceSlider(this.width / 2 - 100, sliderY, 200, h, ModConfig.renderDistanceChunks));

        // Bottone Chiudi
        this.addRenderableWidget(Button.builder(Component.translatable("devmod.settings.close"), b -> this.onClose())
                .pos(this.width / 2 - 50, this.height - 25).size(100, h).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, this.title, width / 2, 10, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // --- CLASSI INTERNE ---

    private static class RenderDistanceSlider extends AbstractSliderButton {
        public RenderDistanceSlider(int x, int y, int width, int height, int initialValue) {
            super(x, y, width, height, Component.literal("Render Dist: " + initialValue + " chunks"), (initialValue - 1) / 9.0);
        }
        @Override protected void updateMessage() { int value = (int) (this.value * 9) + 1; this.setMessage(Component.literal("Render Dist: " + value + " chunks")); applyValue(); }
        @Override protected void applyValue() { ModConfig.renderDistanceChunks = (int) (this.value * 9) + 1; }
    }

    private static class TimeSlider extends AbstractSliderButton {
        public TimeSlider(int x, int y, int width, int height, int initialVal) {
            super(x, y, width, height, Component.literal("Stuck Time: " + initialVal + "s"), (initialVal - 1) / 9.0);
        }
        @Override protected void updateMessage() { int val = (int)(this.value * 9) + 1; this.setMessage(Component.literal("Stuck Time: " + val + "s")); applyValue(); }
        @Override protected void applyValue() { ModConfig.stuckThresholdSeconds = (int)(this.value * 9) + 1; }
    }
}