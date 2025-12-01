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

    // Il metodo init() viene chiamato ogni volta che apri il menu (Tasto K).
    // È QUI che dobbiamo dire al gioco "Aggiungi questi bottoni allo schermo".
    @Override
    protected void init() {
        int w = 120; // Larghezza standard bottone
        int h = 16;  // Altezza standard bottone
        int x = 10;  // Margine sinistro
        int y = 30;  // Margine dall'alto (ho abbassato un po' per il titolo)

        // =================================================================
        // COLONNA SINISTRA: Opzioni Visive Generali (C'erano già)
        // =================================================================

        // 1. Overlay (Scritte a schermo)
        this.addRenderableWidget(Button.builder(
                Component.literal("Overlay HUD: " + (ModConfig.showOverlay ? "ON" : "OFF")),
                b -> {
                    ModConfig.showOverlay = !ModConfig.showOverlay;
                    b.setMessage(Component.literal("Overlay HUD: " + (ModConfig.showOverlay ? "ON" : "OFF")));
                }).pos(x, y).size(w, h).build());

        // 2. Render Sfere
        this.addRenderableWidget(Button.builder(
                Component.literal("Render Sfere: " + (ModConfig.showRender ? "ON" : "OFF")),
                b -> {
                    ModConfig.showRender = !ModConfig.showRender;
                    b.setMessage(Component.literal("Render Sfere: " + (ModConfig.showRender ? "ON" : "OFF")));
                }).pos(x, y + 20).size(w, h).build());

        // 3. Colore
        this.addRenderableWidget(Button.builder(
                        Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey())),
                        button -> {
                            ModConfig.cycleColor();
                            button.setMessage(Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey())));
                        })
                .pos(x, y + 40).size(w, h).build());


        // =================================================================
        // COLONNA DESTRA: NUOVE Opzioni Debug (Stuck & Path) <--- NUOVO!
        // =================================================================
        // Calcoliamo la posizione X della colonna destra (spostata di 140 pixel)
        int x2 = x + w + 20;

        // 4. Stuck Detector (Rileva mob bloccati)
        this.addRenderableWidget(Button.builder(
                Component.literal("Stuck Detector: " + (ModConfig.enableStuckDebug ? "ON" : "OFF")),
                b -> {
                    ModConfig.enableStuckDebug = !ModConfig.enableStuckDebug;
                    b.setMessage(Component.literal("Stuck Detector: " + (ModConfig.enableStuckDebug ? "ON" : "OFF")));
                }).pos(x2, y).size(w + 20, h).build());

        // 5. Mostra Path AI (Visualizza il percorso)
        this.addRenderableWidget(Button.builder(
                Component.literal("Mostra Path AI: " + (ModConfig.showMobPath ? "ON" : "OFF")),
                b -> {
                    ModConfig.showMobPath = !ModConfig.showMobPath;
                    b.setMessage(Component.literal("Mostra Path AI: " + (ModConfig.showMobPath ? "ON" : "OFF")));
                    
                    // Send config sync packet to server
                    if (Minecraft.getInstance().player != null) {
                        PacketDistributor.sendToServer(new ConfigSyncPayload(ModConfig.showMobPath));
                    }
                }).pos(x2, y + 20).size(w + 20, h).build());

        // 6. Debug in Chat (Messaggi scritti)
        this.addRenderableWidget(Button.builder(
                Component.literal("Debug Chat: " + (ModConfig.showStuckChat ? "ON" : "OFF")),
                b -> {
                    ModConfig.showStuckChat = !ModConfig.showStuckChat;
                    b.setMessage(Component.literal("Debug Chat: " + (ModConfig.showStuckChat ? "ON" : "OFF")));
                }).pos(x2, y + 40).size(w + 20, h).build());

        // 7. Slider Tempo (Quanto tempo prima di dire "Stuck")
        // Qui usiamo la classe personalizzata TimeSlider definita in fondo al file
        this.addRenderableWidget(new TimeSlider(x2, y + 65, w + 20, h, ModConfig.stuckThresholdSeconds));


        // =================================================================
        // PARTE INFERIORE: Distanze Aggro/Attack (Spostate più in basso)
        // =================================================================
        int group2Y = y + 100; // Spazio verticale maggiore per separare i gruppi

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
                }).pos(x, group2Y + 20).size(w, h).build());

        // Hostile Aggro
        this.addRenderableWidget(Button.builder(
                Component.literal("Aggro Hostile: " + (ModConfig.renderHostileAggro ? "ON" : "OFF")),
                button -> {
                    ModConfig.renderHostileAggro = !ModConfig.renderHostileAggro;
                    button.setMessage(Component.literal("Aggro Hostile: " + (ModConfig.renderHostileAggro ? "ON" : "OFF")));
                }).pos(x, group2Y + 40).size(w, h).build());

        // Hostile Attack
        this.addRenderableWidget(Button.builder(
                Component.literal("Attack Hostile: " + (ModConfig.renderHostileAttack ? "ON" : "OFF")),
                button -> {
                    ModConfig.renderHostileAttack = !ModConfig.renderHostileAttack;
                    button.setMessage(Component.literal("Attack Hostile: " + (ModConfig.renderHostileAttack ? "ON" : "OFF")));
                }).pos(x, group2Y + 60).size(w, h).build());

        // Render Distance Slider
        int sliderY = group2Y + 90;
        this.addRenderableWidget(new RenderDistanceSlider(x, sliderY, w, h, ModConfig.renderDistanceChunks));

        // Bottone Chiudi
        this.addRenderableWidget(Button.builder(Component.translatable("devmod.settings.close"), b -> this.onClose())
                .pos(width - w - 10, height - h - 10).size(w, h).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, this.title, width / 2, 10, 0xFFFFFF);
    }

    // --- CLASSI INTERNE (Slider personalizzati) ---

    // Slider per la distanza di render (Esistente)
    private static class RenderDistanceSlider extends AbstractSliderButton {
        public RenderDistanceSlider(int x, int y, int width, int height, int initialValue) {
            super(x, y, width, height, Component.literal("Render Dist: " + initialValue + " chunks"), (initialValue - 1) / 9.0);
        }

        @Override
        protected void updateMessage() {
            int value = (int) (this.value * 9) + 1;
            this.setMessage(Component.literal("Render Dist: " + value + " chunks"));
            applyValue();
        }

        @Override
        protected void applyValue() {
            ModConfig.renderDistanceChunks = (int) (this.value * 9) + 1;
        }
    }

    // NUOVO: Slider per il tempo di Stuck (3s, 4s, 5s...)
    private static class TimeSlider extends AbstractSliderButton {
        public TimeSlider(int x, int y, int width, int height, int initialVal) {
            // (initialVal - 1) / 9.0 serve a posizionare la levetta nel punto giusto all'apertura
            super(x, y, width, height, Component.literal("Stuck Time: " + initialVal + "s"), (initialVal - 1) / 9.0);
        }
        @Override
        protected void updateMessage() {
            // Converte la posizione della levetta (0.0 - 1.0) in secondi (1 - 10)
            int val = (int)(this.value * 9) + 1;
            this.setMessage(Component.literal("Stuck Time: " + val + "s"));
            applyValue();
        }
        @Override
        protected void applyValue() {
            // Salva il valore nella configurazione
            ModConfig.stuckThresholdSeconds = (int)(this.value * 9) + 1;
        }
    }
}