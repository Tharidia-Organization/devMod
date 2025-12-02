package com.frenkvs.devmod.client.screen;

import com.frenkvs.devmod.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends Screen {

    // Dimensioni del pannello
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 260;

    public SettingsScreen() {
        super(Component.literal("DevMod Control Panel"));
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Permette al gioco di continuare a girare dietro il menu
    }

    @Override
    protected void init() {
        // Calcolo coordinate per centrare il pannello
        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelTop = (this.height - PANEL_HEIGHT) / 2;

        // Margini interni
        int xL = panelLeft + 10;          // Colonna Sinistra
        int xR = panelLeft + PANEL_WIDTH / 2 + 5; // Colonna Destra
        int y = panelTop + 25;            // Inizio verticale (sotto il titolo)
        int btnW = 150;                   // Larghezza bottoni
        int btnH = 16;                    // Altezza bottoni
        int step = 18;                    // Spazio verticale tra bottoni

        // --- COLONNA SINISTRA: VISUALS ---

        // 1. Base Visuals
        this.addRenderableWidget(Button.builder(Component.literal("Overlay HUD: " + (ModConfig.showOverlay ? "ON" : "OFF")), b -> { ModConfig.showOverlay = !ModConfig.showOverlay; b.setMessage(Component.literal("Overlay HUD: " + (ModConfig.showOverlay ? "ON" : "OFF"))); }).pos(xL, y).size(btnW, btnH).build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("Render Sfere: " + (ModConfig.showRender ? "ON" : "OFF")), b -> { ModConfig.showRender = !ModConfig.showRender; b.setMessage(Component.literal("Render Sfere: " + (ModConfig.showRender ? "ON" : "OFF"))); }).pos(xL, y).size(btnW, btnH).build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey())), b -> { ModConfig.cycleColor(); b.setMessage(Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey()))); }).pos(xL, y).size(btnW, btnH).build());
        y += step + 5; // Spazietto

        // NUOVO BOTTONE FRECCE
        this.addRenderableWidget(Button.builder(Component.literal("Show Arrows: " + (ModConfig.showArrowHits ? "ON" : "OFF")), b -> { ModConfig.showArrowHits = !ModConfig.showArrowHits; b.setMessage(Component.literal("Show Arrows: " + (ModConfig.showArrowHits ? "ON" : "OFF"))); }).pos(xL, y).size(btnW, btnH).build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey())), b -> { ModConfig.cycleColor(); b.setMessage(Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey()))); }).pos(xL, y).size(btnW, btnH).build());
        y += step + 5;

        // 2. Anchors & Markers
        this.addRenderableWidget(Button.builder(Component.literal("Name Tags: " + (ModConfig.showAnchors ? "ON" : "OFF")), b -> { ModConfig.showAnchors = !ModConfig.showAnchors; b.setMessage(Component.literal("Name Tags: " + (ModConfig.showAnchors ? "ON" : "OFF"))); }).pos(xL, y).size(btnW, btnH).build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("Markers (Invisible): " + (ModConfig.showMarkers ? "ON" : "OFF")), b -> { ModConfig.showMarkers = !ModConfig.showMarkers; b.setMessage(Component.literal("Markers (Invisible): " + (ModConfig.showMarkers ? "ON" : "OFF"))); }).pos(xL, y).size(btnW, btnH).build());
        y += step + 5;

        // 3. Line of Sight
        this.addRenderableWidget(Button.builder(Component.literal("Line of Sight: " + (ModConfig.showLoS ? "ON" : "OFF")), b -> { ModConfig.showLoS = !ModConfig.showLoS; b.setMessage(Component.literal("Line of Sight: " + (ModConfig.showLoS ? "ON" : "OFF"))); }).pos(xL, y).size(btnW, btnH).build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("All Mobs LoS: " + (ModConfig.showAllMobsLoS ? "ON" : "OFF")), b -> { ModConfig.showAllMobsLoS = !ModConfig.showAllMobsLoS; b.setMessage(Component.literal("All Mobs LoS: " + (ModConfig.showAllMobsLoS ? "ON" : "OFF"))); }).pos(xL, y).size(btnW, btnH).build());
        y += step;
        this.addRenderableWidget(new AllMobsRadiusSlider(xL, y, btnW, btnH, ModConfig.allMobsLoSRadius));
        y += step;
        this.addRenderableWidget(new LoSRangeSlider(xL, y, btnW, btnH, ModConfig.losDistance));


        // --- COLONNA DESTRA: DEBUG & LOGIC ---
        // Resettiamo Y per la colonna destra
        y = panelTop + 25;

        // 1. Logic Debug
        this.addRenderableWidget(Button.builder(Component.literal("Stuck Detector: " + (ModConfig.enableStuckDebug ? "ON" : "OFF")), b -> { ModConfig.enableStuckDebug = !ModConfig.enableStuckDebug; b.setMessage(Component.literal("Stuck Detector: " + (ModConfig.enableStuckDebug ? "ON" : "OFF"))); }).pos(xR, y).size(btnW, btnH).build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("Show AI Path: " + (ModConfig.showMobPath ? "ON" : "OFF")), b -> { ModConfig.showMobPath = !ModConfig.showMobPath; b.setMessage(Component.literal("Show AI Path: " + (ModConfig.showMobPath ? "ON" : "OFF"))); }).pos(xR, y).size(btnW, btnH).build());
        y += step;
        this.addRenderableWidget(Button.builder(Component.literal("Debug Chat: " + (ModConfig.showStuckChat ? "ON" : "OFF")), b -> { ModConfig.showStuckChat = !ModConfig.showStuckChat; b.setMessage(Component.literal("Debug Chat: " + (ModConfig.showStuckChat ? "ON" : "OFF"))); }).pos(xR, y).size(btnW, btnH).build());
        y += step;
        this.addRenderableWidget(new TimeSlider(xR, y, btnW, btnH, ModConfig.stuckThresholdSeconds));
        y += step + 10; // Spazio extra

        // 2. Render Distance
        this.addRenderableWidget(new RenderDistanceSlider(xR, y, btnW, btnH, ModConfig.renderDistanceChunks));
        y += step + 10;

        // 3. Render Toggles (Aggro/Attack) - Un po' compressi
        int miniBtnW = (btnW - 5) / 2; // Bottoni metà larghezza

        // Riga 1: Friendly
        this.addRenderableWidget(Button.builder(Component.literal("F-Aggro"), b -> { ModConfig.renderFriendlyAggro = !ModConfig.renderFriendlyAggro; }).pos(xR, y).size(miniBtnW, btnH).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Friendly Aggro Range"))).build());
        this.addRenderableWidget(Button.builder(Component.literal("F-Atk"), b -> { ModConfig.renderFriendlyAttack = !ModConfig.renderFriendlyAttack; }).pos(xR + miniBtnW + 5, y).size(miniBtnW, btnH).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Friendly Attack Range"))).build());
        y += step;

        // Riga 2: Hostile
        this.addRenderableWidget(Button.builder(Component.literal("H-Aggro"), b -> { ModConfig.renderHostileAggro = !ModConfig.renderHostileAggro; }).pos(xR, y).size(miniBtnW, btnH).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Hostile Aggro Range"))).build());
        this.addRenderableWidget(Button.builder(Component.literal("H-Atk"), b -> { ModConfig.renderHostileAttack = !ModConfig.renderHostileAttack; }).pos(xR + miniBtnW + 5, y).size(miniBtnW, btnH).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Hostile Attack Range"))).build());


        // --- BOTTONE CHIUDI (IN BASSO AL CENTRO) ---
        int closeW = 100;
        this.addRenderableWidget(Button.builder(Component.literal("CHIUDI"), b -> this.onClose())
                .pos(panelLeft + (PANEL_WIDTH - closeW) / 2, panelTop + PANEL_HEIGHT - 25)
                .size(closeW, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelTop = (this.height - PANEL_HEIGHT) / 2;

        // 1. Sfondo (Glass)
        guiGraphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xDD101010);

        // 2. Bordo (Ciano)
        int borderColor = 0xFF00AAAA;
        guiGraphics.hLine(panelLeft, panelLeft + PANEL_WIDTH, panelTop, borderColor);
        guiGraphics.hLine(panelLeft, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, borderColor);
        guiGraphics.vLine(panelLeft, panelTop, panelTop + PANEL_HEIGHT, borderColor);
        guiGraphics.vLine(panelLeft + PANEL_WIDTH, panelTop, panelTop + PANEL_HEIGHT, borderColor);

        // --- FIX NITIDEZZA ---

        // Resetta il colore del renderizzatore a Bianco Opaco (1.0, 1.0, 1.0, 1.0)
        // Questo evita che la trasparenza dello sfondo "infetti" il testo
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Colore Bianco Puro OPACO (FF alpha)
        int whiteOpaque = 0xFFFFFFFF;

        // 3. Titolo (Grassetto §l per renderlo più leggibile)
        // Aggiungiamo §l (Bold) e §f (White)
        Component titleText = Component.literal("§l" + this.title.getString());
        int titleWidth = font.width(titleText);
        guiGraphics.drawString(font, titleText, this.width / 2 - titleWidth / 2, panelTop + 8, whiteOpaque, false);

        // 4. Headers Colonne (Grassetto)
        // §b = Ciano, §l = Bold
        guiGraphics.drawString(font, "§b§l[ VISUALS ]", panelLeft + 15, panelTop + 25 - 10, whiteOpaque, false);
        // §6 = Oro, §l = Bold
        guiGraphics.drawString(font, "§6§l[ DEBUG & TOOLS ]", panelLeft + PANEL_WIDTH / 2 + 10, panelTop + 25 - 10, whiteOpaque, false);

        // 5. Renderizza i bottoni
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // =================================================================================
    // CLASSI INTERNE (SLIDERS)
    // =================================================================================

    // 1. Slider Distanza Render
    private static class RenderDistanceSlider extends AbstractSliderButton {
        public RenderDistanceSlider(int x, int y, int width, int height, int initialValue) { super(x, y, width, height, Component.literal("Render Dist: " + initialValue + " chunks"), (initialValue - 1) / 9.0); }
        @Override protected void updateMessage() { int value = (int) (this.value * 9) + 1; this.setMessage(Component.literal("Render Dist: " + value + " chunks")); applyValue(); }
        @Override protected void applyValue() { ModConfig.renderDistanceChunks = (int) (this.value * 9) + 1; }
    }

    // 2. Slider Tempo Stuck (1-10s)
    private static class TimeSlider extends AbstractSliderButton {
        public TimeSlider(int x, int y, int width, int height, int initialVal) { super(x, y, width, height, Component.literal("Stuck Time: " + initialVal + "s"), (initialVal - 1) / 9.0); }
        @Override protected void updateMessage() { int val = (int)(this.value * 9) + 1; this.setMessage(Component.literal("Stuck Time: " + val + "s")); applyValue(); }
        @Override protected void applyValue() { ModConfig.stuckThresholdSeconds = (int)(this.value * 9) + 1; }
    }

    // 3. Slider Distanza LoS (5-64 blocchi)
    private static class LoSRangeSlider extends AbstractSliderButton {
        public LoSRangeSlider(int x, int y, int width, int height, int initialVal) {
            super(x, y, width, height, Component.literal("LoS Ray Len: " + initialVal), (initialVal - 5) / 59.0);
        }
        @Override protected void updateMessage() {
            int val = (int)(this.value * 59) + 5;
            this.setMessage(Component.literal("LoS Ray Len: " + val));
            applyValue();
        }
        @Override protected void applyValue() {
            ModConfig.losDistance = (int)(this.value * 59) + 5;
        }
    }

    // 4. Slider Raggio Attivazione All Mobs (1-100 blocchi)
    private static class AllMobsRadiusSlider extends AbstractSliderButton {
        public AllMobsRadiusSlider(int x, int y, int width, int height, int initialVal) {
            super(x, y, width, height, Component.literal("Scan Radius: " + initialVal), (initialVal - 1) / 99.0);
        }
        @Override protected void updateMessage() {
            int val = (int)(this.value * 99) + 1;
            this.setMessage(Component.literal("Scan Radius: " + val));
            applyValue();
        }
        @Override protected void applyValue() {
            ModConfig.allMobsLoSRadius = (int)(this.value * 99) + 1;
        }
    }
}