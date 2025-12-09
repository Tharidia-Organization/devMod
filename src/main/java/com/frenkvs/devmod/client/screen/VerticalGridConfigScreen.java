package com.frenkvs.devmod.client.screen;

import com.frenkvs.devmod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.systems.RenderSystem;

// Importiamo i componenti Sci-Fi dal menu principale
import com.frenkvs.devmod.client.screen.SettingsScreen.SciFiButton;
import com.frenkvs.devmod.client.screen.SettingsScreen.SciFiSlider;

public class VerticalGridConfigScreen extends Screen {

    private final Screen parent;
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 400;

    public VerticalGridConfigScreen(Screen parent) {
        super(Component.literal("Builder Tools"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelTop = (this.height - PANEL_HEIGHT) / 2;
        int x = panelLeft + (PANEL_WIDTH / 2) - 100; // Centrato
        int y = panelTop + 45;
        int w = 200;
        int h = 20;
        int step = 25;

        // 1. GRIGLIA & LOCK
        this.addRenderableWidget(new SciFiButton(x, y, w, h,
                Component.literal("Griglia: " + (ModConfig.showVerticalLevels ? "§aON" : "§cOFF")),
                b -> {
                    ModConfig.showVerticalLevels = !ModConfig.showVerticalLevels;
                    b.setMessage(Component.literal("Griglia: " + (ModConfig.showVerticalLevels ? "§aON" : "§cOFF")));
                }));
        y += step;

        this.addRenderableWidget(new SciFiButton(x, y, w, h,
                Component.literal("Posizione: " + (ModConfig.gridLockPos ? "§eBLOCCATA" : "§7DINAMICA")),
                b -> {
                    ModConfig.gridLockPos = !ModConfig.gridLockPos;
                    if (ModConfig.gridLockPos && Minecraft.getInstance().player != null) {
                        ModConfig.lockedX = Math.floor(Minecraft.getInstance().player.getX());
                        ModConfig.lockedY = Math.floor(Minecraft.getInstance().player.getY());
                        ModConfig.lockedZ = Math.floor(Minecraft.getInstance().player.getZ());
                    }
                    b.setMessage(Component.literal("Posizione: " + (ModConfig.gridLockPos ? "§eBLOCCATA" : "§7DINAMICA")));
                }));
        y += step + 5;

        // SLIDERS GRIGLIA
        this.addRenderableWidget(new SimpleSciFiSlider(x, y, w, h, "Spaziatura Piani: ", 1.0, 20.0, (double)ModConfig.gridSpacingY, v -> ModConfig.gridSpacingY = v.intValue()));
        y += step + 20;

        // 2. GUIDA FORME
        this.addRenderableWidget(new SciFiButton(x, y, w, h,
                Component.literal("Guida Forma: " + (ModConfig.showShapeGuide ? "§aON" : "§cOFF")),
                b -> {
                    ModConfig.showShapeGuide = !ModConfig.showShapeGuide;
                    if (ModConfig.showShapeGuide && Minecraft.getInstance().player != null) {
                        ModConfig.shapeCenterX = (int) Math.floor(Minecraft.getInstance().player.getX());
                        ModConfig.shapeCenterY = (int) Math.floor(Minecraft.getInstance().player.getY());
                        ModConfig.shapeCenterZ = (int) Math.floor(Minecraft.getInstance().player.getZ());
                    }
                    b.setMessage(Component.literal("Guida Forma: " + (ModConfig.showShapeGuide ? "§aON" : "§cOFF")));
                }));
        y += step;

        this.addRenderableWidget(new SciFiButton(x, y, w, h,
                Component.literal("Tipo: §b" + ModConfig.currentShape.name()),
                b -> {
                    int next = (ModConfig.currentShape.ordinal() + 1) % ModConfig.ShapeType.values().length;
                    ModConfig.currentShape = ModConfig.ShapeType.values()[next];
                    b.setMessage(Component.literal("Tipo: §b" + ModConfig.currentShape.name()));
                }));
        y += step + 5;

        // SLIDER RAGGI
        this.addRenderableWidget(new SimpleSciFiSlider(x, y, w, h, "Raggio A (Main): ", 1.0, 50.0, (double)ModConfig.shapeRadius, v -> ModConfig.shapeRadius = v.intValue()));
        y += step;
        this.addRenderableWidget(new SimpleSciFiSlider(x, y, w, h, "Raggio B (Ellisse): ", 1.0, 50.0, (double)ModConfig.shapeRadiusB, v -> ModConfig.shapeRadiusB = v.intValue()));
        y += step + 20;

        // 3. RILEVATORE BUCHI
        this.addRenderableWidget(new SciFiButton(x, y, w, h,
                Component.literal("Trova Buchi: " + (ModConfig.showLeakDetector ? "§cON" : "§aOFF")),
                b -> {
                    ModConfig.showLeakDetector = !ModConfig.showLeakDetector;
                    b.setMessage(Component.literal("Trova Buchi: " + (ModConfig.showLeakDetector ? "§cON" : "§aOFF")));
                }));
        y += step + 20;

        // 4. METRO / RESET
        this.addRenderableWidget(new SciFiButton(x, y, w, h,
                Component.literal("Resetta Metro"),
                b -> {
                    ModConfig.measurePos1 = null; ModConfig.measurePos2 = null;
                    if(Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(Component.literal("§eMetro resettato."), true);
                }));

        // INDIETRO
        this.addRenderableWidget(new SciFiButton(panelLeft + (PANEL_WIDTH - 100) / 2, panelTop + PANEL_HEIGHT - 40, 100, 22, Component.literal("§l§f[ BACK ]"), b -> this.onClose()));
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelTop = (this.height - PANEL_HEIGHT) / 2;

        guiGraphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xFF1A1A2E);
        int borderColor = 0xFF00AAFF;
        guiGraphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + 2, borderColor);
        guiGraphics.fill(panelLeft, panelTop + PANEL_HEIGHT - 2, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, borderColor);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 2, panelTop + PANEL_HEIGHT, borderColor);
        guiGraphics.fill(panelLeft + PANEL_WIDTH - 2, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, borderColor);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.drawCenteredString(font, "§6§l[ BUILDER TOOLS CONFIG ]", this.width / 2, panelTop + 10, 0xFFFFFFFF);

        if (ModConfig.showShapeGuide) {
            guiGraphics.drawCenteredString(font, "§7Centro Forma: " + ModConfig.shapeCenterX + ", " + ModConfig.shapeCenterY + ", " + ModConfig.shapeCenterZ, width / 2, panelTop + 245, 0xAAAAAA);
        }

        if (ModConfig.showVerticalLevels && ModConfig.gridLockPos) {
            guiGraphics.drawCenteredString(font, "§eGriglia Bloccata a: " + (int)ModConfig.lockedX + ", " + (int)ModConfig.lockedY + ", " + (int)ModConfig.lockedZ, width / 2, panelTop + 90, 0xFFFFFF00);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // --- CORREZIONE CLASSE SLIDER ---
    // Questa classe ora gestisce correttamente la conversione da 0.0-1.0 ai valori reali (es. 1-20)
    // PRIMA di salvare nel config, evitando il reset a 0.
    private static class SimpleSciFiSlider extends SciFiSlider {
        private final String prefix;
        private final double min, max;
        private final java.util.function.Consumer<Double> realApplier;

        public SimpleSciFiSlider(int x, int y, int width, int height, String prefix, double min, double max, double current, java.util.function.Consumer<Double> applier) {
            // Passiamo 'null' al padre come applier, così gestiamo noi il salvataggio manuale
            super(x, y, width, height,
                    Component.literal(prefix + (int)current),
                    (current - min) / (max - min),
                    null);

            this.prefix = prefix;
            this.min = min;
            this.max = max;
            this.realApplier = applier;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            // Solo aggiornamento visivo
            int val = (int)(min + (max - min) * value);
            setMessage(Component.literal(prefix + val));
        }

        @Override
        protected void applyValue() {
            // Qui calcoliamo il valore REALE e lo salviamo
            this.updateMessage();
            int val = (int)(min + (max - min) * value);
            if (realApplier != null) {
                realApplier.accept((double)val);
            }
        }
    }
}