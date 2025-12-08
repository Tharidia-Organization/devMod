package com.frenkvs.devmod.client.screen;

import com.frenkvs.devmod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class VerticalGridConfigScreen extends Screen {

    private final Screen parent;

    public VerticalGridConfigScreen(Screen parent) {
        super(Component.literal("Configurazione Griglia Verticale"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 200;
        int h = 20;
        int x = this.width / 2 - w / 2;
        int y = 30;
        int step = 25;

        // 1. GRIGLIA VERTICALE
        this.addRenderableWidget(Button.builder(
                Component.literal("Stato Griglia: " + (ModConfig.showVerticalLevels ? "§aATTIVA" : "§cDISATTIVA")),
                b -> {
                    ModConfig.showVerticalLevels = !ModConfig.showVerticalLevels;
                    b.setMessage(Component.literal("Stato Griglia: " + (ModConfig.showVerticalLevels ? "§aATTIVA" : "§cDISATTIVA")));
                }).pos(x, y).size(w, h).build());

        y += step + 5;

        // 2. Lock Y
        this.addRenderableWidget(Button.builder(
                Component.literal("Blocca Altezza (Lock Y): " + (ModConfig.gridLockY ? "§aON" : "§cOFF")),
                b -> {
                    ModConfig.gridLockY = !ModConfig.gridLockY;
                    if (ModConfig.gridLockY && Minecraft.getInstance().player != null) {
                        ModConfig.lockedYValue = Math.floor(Minecraft.getInstance().player.getY());
                    }
                    b.setMessage(Component.literal("Blocca Altezza (Lock Y): " + (ModConfig.gridLockY ? "§aON" : "§cOFF")));
                }).pos(x, y).size(w, h).build());

        y += step;

        // 3. Spaziatura
        this.addRenderableWidget(new GridSpacingSlider(x, y, w, h, ModConfig.gridSpacingY));
        y += step;

        // 4. Raggio Griglia
        this.addRenderableWidget(new GridRadiusSlider(x, y, w, h, ModConfig.gridRadius));
        y += step;

        // 5. Piani Sopra
        this.addRenderableWidget(new GridFloorsUpSlider(x, y, w, h, ModConfig.gridFloorsUp));
        y += step;

        // 6. Piani Sotto
        this.addRenderableWidget(new GridFloorsDownSlider(x, y, w, h, ModConfig.gridFloorsDown));
        y += step;

        // --- TOOL EXTRA ---

        Button separator = Button.builder(Component.literal("--- TOOL EXTRA ---"), b -> {}).pos(x, y).size(w, 15).build();
        separator.active = false;
        this.addRenderableWidget(separator);
        y += 20;

        // 7. Toggle Cerchio (MODIFICATO)
        this.addRenderableWidget(Button.builder(
                Component.literal("Guida Cerchio: " + (ModConfig.showCircleGuide ? "§aON" : "§cOFF") + " (R: " + ModConfig.circleRadius + ")"),
                b -> {
                    ModConfig.showCircleGuide = !ModConfig.showCircleGuide;

                    // SE ATTIVIAMO, SALVIAMO LA POSIZIONE ATTUALE COME CENTRO FISSO
                    if (ModConfig.showCircleGuide && Minecraft.getInstance().player != null) {
                        ModConfig.circleCenterX = (int) Math.floor(Minecraft.getInstance().player.getX());
                        ModConfig.circleCenterY = (int) Math.floor(Minecraft.getInstance().player.getY());
                        ModConfig.circleCenterZ = (int) Math.floor(Minecraft.getInstance().player.getZ());
                    }

                    b.setMessage(Component.literal("Guida Cerchio: " + (ModConfig.showCircleGuide ? "§aON" : "§cOFF") + " (R: " + ModConfig.circleRadius + ")"));
                }).pos(x, y).size(w, h).build());

        y += step;

        // 8. Slider Raggio Cerchio
        this.addRenderableWidget(new CircleRadiusSlider(x, y, w, h, ModConfig.circleRadius));

        y += step;

        // 9. Reset Misuratore
        this.addRenderableWidget(Button.builder(
                Component.literal("Resetta Misuratore"),
                b -> {
                    ModConfig.measurePos1 = null;
                    ModConfig.measurePos2 = null;
                    if(Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(Component.literal("§eMisuratore Resettato."), true);
                    }
                }).pos(x, y).size(w, h).build());

        y += step + 10;

        this.addRenderableWidget(Button.builder(Component.literal("INDIETRO"), b -> this.onClose())
                .pos(x, y).size(w, h).build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, this.title, width / 2, 10, 0xFFFFFF);
        if (ModConfig.showCircleGuide) {
            guiGraphics.drawCenteredString(font, "§bCerchio fissato a: " + ModConfig.circleCenterX + ", " + ModConfig.circleCenterY + ", " + ModConfig.circleCenterZ, width / 2, 25, 0xFFFFFF);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private static class GridSpacingSlider extends AbstractSliderButton {
        public GridSpacingSlider(int x, int y, int width, int height, int val) { super(x, y, width, height, Component.literal("Spaziatura Piani: " + val), (val - 1) / 19.0); }
        @Override protected void updateMessage() { int val = (int)(this.value * 19) + 1; setMessage(Component.literal("Spaziatura Piani: " + val)); applyValue(); }
        @Override protected void applyValue() { ModConfig.gridSpacingY = (int)(this.value * 19) + 1; }
    }
    private static class GridRadiusSlider extends AbstractSliderButton {
        public GridRadiusSlider(int x, int y, int width, int height, int val) { super(x, y, width, height, Component.literal("Raggio Griglia: " + val), (val - 5) / 59.0); }
        @Override protected void updateMessage() { int val = (int)(this.value * 59) + 5; setMessage(Component.literal("Raggio Griglia: " + val)); applyValue(); }
        @Override protected void applyValue() { ModConfig.gridRadius = (int)(this.value * 59) + 5; }
    }
    private static class GridFloorsUpSlider extends AbstractSliderButton {
        public GridFloorsUpSlider(int x, int y, int width, int height, int val) { super(x, y, width, height, Component.literal("Piani Sopra: " + val), val / 10.0); }
        @Override protected void updateMessage() { int val = (int)(this.value * 10); setMessage(Component.literal("Piani Sopra: " + val)); applyValue(); }
        @Override protected void applyValue() { ModConfig.gridFloorsUp = (int)(this.value * 10); }
    }
    private static class GridFloorsDownSlider extends AbstractSliderButton {
        public GridFloorsDownSlider(int x, int y, int width, int height, int val) { super(x, y, width, height, Component.literal("Piani Sotto: " + val), val / 10.0); }
        @Override protected void updateMessage() { int val = (int)(this.value * 10); setMessage(Component.literal("Piani Sotto: " + val)); applyValue(); }
        @Override protected void applyValue() { ModConfig.gridFloorsDown = (int)(this.value * 10); }
    }
    private static class CircleRadiusSlider extends AbstractSliderButton {
        public CircleRadiusSlider(int x, int y, int width, int height, int val) { super(x, y, width, height, Component.literal("Raggio Cerchio: " + val), (val - 1) / 49.0); }
        @Override protected void updateMessage() { int val = (int)(this.value * 49) + 1; setMessage(Component.literal("Raggio Cerchio: " + val)); applyValue(); }
        @Override protected void applyValue() { ModConfig.circleRadius = (int)(this.value * 49) + 1; }
    }
}