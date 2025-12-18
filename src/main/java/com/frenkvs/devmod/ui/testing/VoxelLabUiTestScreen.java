package com.frenkvs.devmod.ui.testing;

import com.frenkvs.devmod.Config;
import com.frenkvs.devmod.hud.Impact3DPanelManager;
import com.frenkvs.devmod.hud.ImpactHudOverlay;
import com.frenkvs.devmod.hud.ImpactHudPresets;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sandbox UI per visualizzare rapidamente tutte le varianti di EditorButton
 * e gestione completa dell'Impact HUD System.
 * Pensato per la sezione Voxel Lab / QA visuale.
 */
public class VoxelLabUiTestScreen extends Screen {

    private static final int PADDING = 16;
    private static final int GRID_COLUMNS = 2;
    private static final int GRID_SPACING = 12;
    private static final int DEMO_WIDTH = 200;
    private static final int SECTION_WIDTH = 280;
    private static final int BTN_HEIGHT = 22;
    private static final int SMALL_BTN_HEIGHT = 18;

    // === UI Showcase ===
    private final EditorButton showCasesButton = new EditorButton("showcases", "Mostra tipologie")
        .style(EditorButton.Style.PRIMARY)
        .toggleable(true)
        .hotkeyHint("[Click]")
        .onToggle(v -> showCases = v);

    private boolean showCases = false;
    private final List<DemoButton> demoButtons = new ArrayList<>();
    private boolean demosBuilt = false;

    // === Impact HUD 2D Controls ===
    private final EditorButton impactHud2dToggle = new EditorButton("impact-2d", "2D HUD Overlay")
        .style(EditorButton.Style.PRIMARY)
        .toggleable(true)
        .icon("\uD83D\uDCCA")
        .onToggle(v -> ImpactHudOverlay.setEnabled(Boolean.TRUE.equals(v)));

    private final EditorButton impactHistoryToggle = new EditorButton("impact-history", "History")
        .style(EditorButton.Style.GHOST)
        .size(EditorButton.Size.SMALL)
        .toggleable(true)
        .onToggle(v -> Config.IMPACT_HUD_HISTORY_ENABLED.set(Boolean.TRUE.equals(v)));

    private final EditorButton impactDpsToggle = new EditorButton("impact-dps", "DPS Tracker")
        .style(EditorButton.Style.GHOST)
        .size(EditorButton.Size.SMALL)
        .toggleable(true)
        .onToggle(v -> Config.IMPACT_HUD_DPS_ENABLED.set(Boolean.TRUE.equals(v)));

    // === Impact 3D Panel Controls ===
    private final EditorButton impact3dToggle = new EditorButton("impact-3d", "3D World Panel")
        .style(EditorButton.Style.PRIMARY)
        .toggleable(true)
        .icon("\uD83C\uDF10")
        .onToggle(v -> Impact3DPanelManager.INSTANCE.setEnabled(Boolean.TRUE.equals(v)));

    // === Impact VFX Controls ===
    private final EditorButton vfxMasterToggle = new EditorButton("vfx-master", "VFX Master")
        .style(EditorButton.Style.SUCCESS)
        .toggleable(true)
        .icon("\u2728")
        .onToggle(v -> Config.IMPACT_VFX_ENABLED.set(Boolean.TRUE.equals(v)));

    private final EditorButton vfxVortexToggle = new EditorButton("vfx-vortex", "Vortex")
        .style(EditorButton.Style.GHOST)
        .size(EditorButton.Size.SMALL)
        .toggleable(true)
        .onToggle(v -> Config.IMPACT_VFX_VORTEX_ENABLED.set(Boolean.TRUE.equals(v)));

    private final EditorButton vfxSlashToggle = new EditorButton("vfx-slash", "Slash")
        .style(EditorButton.Style.GHOST)
        .size(EditorButton.Size.SMALL)
        .toggleable(true)
        .onToggle(v -> Config.IMPACT_VFX_SLASH_ENABLED.set(Boolean.TRUE.equals(v)));

    private final EditorButton vfxLinesToggle = new EditorButton("vfx-lines", "Lines")
        .style(EditorButton.Style.GHOST)
        .size(EditorButton.Size.SMALL)
        .toggleable(true)
        .onToggle(v -> Config.IMPACT_VFX_LINES_ENABLED.set(Boolean.TRUE.equals(v)));

    // === Intensity Controls ===
    private final EditorButton intensityLow = new EditorButton("int-low", "Low")
        .size(EditorButton.Size.SMALL)
        .toggleable(true)
        .onToggle(v -> { if (v) setIntensity(0.5); });

    private final EditorButton intensityMed = new EditorButton("int-med", "Normal")
        .size(EditorButton.Size.SMALL)
        .toggleable(true)
        .onToggle(v -> { if (v) setIntensity(1.0); });

    private final EditorButton intensityHigh = new EditorButton("int-high", "High")
        .size(EditorButton.Size.SMALL)
        .toggleable(true)
        .onToggle(v -> { if (v) setIntensity(1.5); });

    private final EditorButton intensityMax = new EditorButton("int-max", "Max")
        .size(EditorButton.Size.SMALL)
        .toggleable(true)
        .onToggle(v -> { if (v) setIntensity(2.0); });

    // === Position Controls ===
    private final List<EditorButton> positionButtons = new ArrayList<>();
    private Config.HudPosition currentPosition = Config.HudPosition.TOP_RIGHT;

    // === Offset Controls ===
    private final EditorButton offsetXMinus = new EditorButton("ox-", "-")
        .size(EditorButton.Size.SMALL)
        .onClick(() -> adjustOffset(true, -10));
    private final EditorButton offsetXPlus = new EditorButton("ox+", "+")
        .size(EditorButton.Size.SMALL)
        .onClick(() -> adjustOffset(true, 10));
    private final EditorButton offsetYMinus = new EditorButton("oy-", "-")
        .size(EditorButton.Size.SMALL)
        .onClick(() -> adjustOffset(false, -10));
    private final EditorButton offsetYPlus = new EditorButton("oy+", "+")
        .size(EditorButton.Size.SMALL)
        .onClick(() -> adjustOffset(false, 10));

    // === Preset Controls ===
    private final EditorButton exportPreset = new EditorButton("export", "Export Preset")
        .style(EditorButton.Style.GHOST)
        .icon("\uD83D\uDCE4")
        .onClick(() -> {
            boolean ok = ImpactHudPresets.exportToDefault();
            exportStatus = ok ? "Exported!" : "Failed!";
            exportStatusTime = System.currentTimeMillis();
        });

    private final EditorButton importPreset = new EditorButton("import", "Import Preset")
        .style(EditorButton.Style.GHOST)
        .icon("\uD83D\uDCE5")
        .onClick(() -> {
            boolean ok = ImpactHudPresets.importFromDefault();
            exportStatus = ok ? "Imported!" : "Failed!";
            exportStatusTime = System.currentTimeMillis();
            syncAllToggles();
        });

    private final EditorButton resetDefaults = new EditorButton("reset", "Reset Defaults")
        .style(EditorButton.Style.DANGER)
        .size(EditorButton.Size.SMALL)
        .onClick(this::resetToDefaults);

    private String exportStatus = "";
    private long exportStatusTime = 0;

    // Collect all interactive buttons for easy iteration
    private final List<EditorButton> allButtons = new ArrayList<>();

    public VoxelLabUiTestScreen() {
        super(Component.literal("Voxel Lab - UI Test"));
    }

    @Override
    protected void init() {
        demosBuilt = false;
        buildPositionButtons();
        buildAllButtonsList();
        syncAllToggles();
        try {
            currentPosition = Config.IMPACT_HUD_POSITION.get();
        } catch (Exception ignored) {}
    }

    private void buildAllButtonsList() {
        allButtons.clear();
        allButtons.add(showCasesButton);
        // 2D HUD
        allButtons.add(impactHud2dToggle);
        allButtons.add(impactHistoryToggle);
        allButtons.add(impactDpsToggle);
        // 3D Panel
        allButtons.add(impact3dToggle);
        // VFX
        allButtons.add(vfxMasterToggle);
        allButtons.add(vfxVortexToggle);
        allButtons.add(vfxSlashToggle);
        allButtons.add(vfxLinesToggle);
        // Intensity
        allButtons.add(intensityLow);
        allButtons.add(intensityMed);
        allButtons.add(intensityHigh);
        allButtons.add(intensityMax);
        // Position
        allButtons.addAll(positionButtons);
        // Offset
        allButtons.add(offsetXMinus);
        allButtons.add(offsetXPlus);
        allButtons.add(offsetYMinus);
        allButtons.add(offsetYPlus);
        // Presets
        allButtons.add(exportPreset);
        allButtons.add(importPreset);
        allButtons.add(resetDefaults);
    }

    private void buildPositionButtons() {
        positionButtons.clear();
        for (Config.HudPosition pos : Config.HudPosition.values()) {
            EditorButton btn = new EditorButton("pos-" + pos.name(), formatPositionName(pos))
                .size(EditorButton.Size.SMALL)
                .toggleable(true)
                .onToggle(v -> {
                    if (v) {
                        currentPosition = pos;
                        Config.IMPACT_HUD_POSITION.set(pos);
                        updatePositionButtonStates();
                    }
                });
            positionButtons.add(btn);
        }
    }

    private String formatPositionName(Config.HudPosition pos) {
        return switch (pos) {
            case TOP_LEFT -> "TL";
            case TOP_RIGHT -> "TR";
            case CENTER_LEFT -> "CL";
            case CENTER_RIGHT -> "CR";
            case BOTTOM_LEFT -> "BL";
            case BOTTOM_RIGHT -> "BR";
        };
    }

    private void syncAllToggles() {
        // 2D HUD
        impactHud2dToggle.toggled(ImpactHudOverlay.isEnabled());
        impactHistoryToggle.toggled(getConfigBool(Config.IMPACT_HUD_HISTORY_ENABLED));
        impactDpsToggle.toggled(getConfigBool(Config.IMPACT_HUD_DPS_ENABLED));
        // 3D Panel
        impact3dToggle.toggled(Impact3DPanelManager.INSTANCE.isEnabled());
        // VFX
        vfxMasterToggle.toggled(getConfigBool(Config.IMPACT_VFX_ENABLED));
        vfxVortexToggle.toggled(getConfigBool(Config.IMPACT_VFX_VORTEX_ENABLED));
        vfxSlashToggle.toggled(getConfigBool(Config.IMPACT_VFX_SLASH_ENABLED));
        vfxLinesToggle.toggled(getConfigBool(Config.IMPACT_VFX_LINES_ENABLED));
        // Intensity
        double intensity = getConfigDouble(Config.IMPACT_VFX_INTENSITY, 1.0);
        intensityLow.toggled(intensity < 0.7);
        intensityMed.toggled(intensity >= 0.7 && intensity < 1.3);
        intensityHigh.toggled(intensity >= 1.3 && intensity < 1.8);
        intensityMax.toggled(intensity >= 1.8);
        // Position
        updatePositionButtonStates();
    }

    private void updatePositionButtonStates() {
        Config.HudPosition[] positions = Config.HudPosition.values();
        for (int i = 0; i < positionButtons.size() && i < positions.length; i++) {
            positionButtons.get(i).toggled(positions[i] == currentPosition);
        }
    }

    private void setIntensity(double value) {
        Config.IMPACT_VFX_INTENSITY.set(value);
        syncAllToggles();
    }

    private void adjustOffset(boolean isX, int delta) {
        try {
            if (isX) {
                int current = Config.IMPACT_HUD_OFFSET_X.get();
                Config.IMPACT_HUD_OFFSET_X.set(Math.max(0, Math.min(200, current + delta)));
            } else {
                int current = Config.IMPACT_HUD_OFFSET_Y.get();
                Config.IMPACT_HUD_OFFSET_Y.set(Math.max(0, Math.min(200, current + delta)));
            }
        } catch (Exception ignored) {}
    }

    private void resetToDefaults() {
        // Reset all to defaults
        Config.IMPACT_HUD_ENABLED.set(true);
        Config.IMPACT_HUD_POSITION.set(Config.HudPosition.TOP_RIGHT);
        Config.IMPACT_HUD_OFFSET_X.set(10);
        Config.IMPACT_HUD_OFFSET_Y.set(10);
        Config.IMPACT_HUD_HISTORY_ENABLED.set(true);
        Config.IMPACT_HUD_DPS_ENABLED.set(true);
        Config.IMPACT_VFX_ENABLED.set(true);
        Config.IMPACT_VFX_VORTEX_ENABLED.set(true);
        Config.IMPACT_VFX_SLASH_ENABLED.set(true);
        Config.IMPACT_VFX_LINES_ENABLED.set(true);
        Config.IMPACT_VFX_INTENSITY.set(1.0);

        ImpactHudOverlay.setEnabled(true);
        Impact3DPanelManager.INSTANCE.setEnabled(true);
        currentPosition = Config.HudPosition.TOP_RIGHT;

        syncAllToggles();
        exportStatus = "Reset!";
        exportStatusTime = System.currentTimeMillis();
    }

    private static boolean getConfigBool(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue config) {
        try { return config.get(); } catch (Exception e) { return false; }
    }

    private static double getConfigDouble(net.neoforged.neoforge.common.ModConfigSpec.DoubleValue config, double def) {
        try { return config.get(); } catch (Exception e) { return def; }
    }

    private static int getConfigInt(net.neoforged.neoforge.common.ModConfigSpec.IntValue config, int def) {
        try { return config.get(); } catch (Exception e) { return def; }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackgroundLayer(graphics);
        syncAllToggles(); // Keep in sync with live state

        var safeFont = Objects.requireNonNull(font, "font");

        // Header
        graphics.drawString(safeFont, "Voxel Lab / UI Showcase", PADDING, PADDING, UIConstants.Text.TITLE(), false);
        graphics.drawString(safeFont, "EditorButton showcase + Impact HUD complete management", PADDING, PADDING + 12, UIConstants.Text.SECONDARY(), false);

        int buttonY = PADDING + 30;
        showCasesButton.render(graphics, PADDING, buttonY, 160, BTN_HEIGHT, mouseX, mouseY);

        // Impact HUD Section (right side)
        renderImpactHudSection(graphics, mouseX, mouseY);

        if (showCases) {
            ensureDemoButtons();
            renderDemoGrid(graphics, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderImpactHudSection(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font, "font");
        int sectionX = width - SECTION_WIDTH - PADDING;
        int sectionY = PADDING;
        int sectionBottom = height - PADDING;

        // Section background
        graphics.fill(sectionX - 8, sectionY - 4, width - PADDING + 8, sectionBottom, UIConstants.Background.PANEL());

        // === TITLE ===
        graphics.drawString(safeFont, "IMPACT HUD SYSTEM", sectionX, sectionY, 0xFF3D5AFE, false);
        sectionY += 14;
        graphics.fill(sectionX, sectionY, sectionX + SECTION_WIDTH, sectionY + 1, 0xFF3D5AFE);
        sectionY += 8;

        // === 2D HUD OVERLAY ===
        graphics.drawString(safeFont, "2D HUD Overlay", sectionX, sectionY, UIConstants.Text.PRIMARY(), false);
        sectionY += 12;
        impactHud2dToggle.render(graphics, sectionX, sectionY, SECTION_WIDTH, BTN_HEIGHT, mouseX, mouseY);
        sectionY += BTN_HEIGHT + 4;

        int thirdWidth = (SECTION_WIDTH - 8) / 2;
        impactHistoryToggle.render(graphics, sectionX, sectionY, thirdWidth, SMALL_BTN_HEIGHT, mouseX, mouseY);
        impactDpsToggle.render(graphics, sectionX + thirdWidth + 8, sectionY, thirdWidth, SMALL_BTN_HEIGHT, mouseX, mouseY);
        sectionY += SMALL_BTN_HEIGHT + 8;

        // === 3D WORLD PANEL ===
        graphics.drawString(safeFont, "3D World Panel", sectionX, sectionY, UIConstants.Text.PRIMARY(), false);
        sectionY += 12;
        impact3dToggle.render(graphics, sectionX, sectionY, SECTION_WIDTH, BTN_HEIGHT, mouseX, mouseY);
        sectionY += BTN_HEIGHT + 8;

        // === VFX EFFECTS ===
        graphics.drawString(safeFont, "VFX Effects", sectionX, sectionY, UIConstants.Text.PRIMARY(), false);
        sectionY += 12;
        vfxMasterToggle.render(graphics, sectionX, sectionY, SECTION_WIDTH, BTN_HEIGHT, mouseX, mouseY);
        sectionY += BTN_HEIGHT + 4;

        int vfxBtnWidth = (SECTION_WIDTH - 16) / 3;
        vfxVortexToggle.render(graphics, sectionX, sectionY, vfxBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY);
        vfxSlashToggle.render(graphics, sectionX + vfxBtnWidth + 8, sectionY, vfxBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY);
        vfxLinesToggle.render(graphics, sectionX + (vfxBtnWidth + 8) * 2, sectionY, vfxBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY);
        sectionY += SMALL_BTN_HEIGHT + 8;

        // === INTENSITY ===
        graphics.drawString(safeFont, "VFX Intensity", sectionX, sectionY, UIConstants.Text.SECONDARY(), false);
        double intensity = getConfigDouble(Config.IMPACT_VFX_INTENSITY, 1.0);
        String intensityStr = Objects.requireNonNull(String.format("%.0f%%", intensity * 100), "intensity string");
        graphics.drawString(safeFont, intensityStr, sectionX + SECTION_WIDTH - safeFont.width(intensityStr), sectionY, 0xFF00E5FF, false);
        sectionY += 12;

        int intBtnWidth = (SECTION_WIDTH - 24) / 4;
        intensityLow.render(graphics, sectionX, sectionY, intBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY);
        intensityMed.render(graphics, sectionX + intBtnWidth + 8, sectionY, intBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY);
        intensityHigh.render(graphics, sectionX + (intBtnWidth + 8) * 2, sectionY, intBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY);
        intensityMax.render(graphics, sectionX + (intBtnWidth + 8) * 3, sectionY, intBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY);
        sectionY += SMALL_BTN_HEIGHT + 8;

        // === POSITION ===
        graphics.drawString(safeFont, "HUD Position", sectionX, sectionY, UIConstants.Text.SECONDARY(), false);
        sectionY += 12;

        int posBtnWidth = (SECTION_WIDTH - 16) / 3;
        // Row 1: TL, TR
        positionButtons.get(0).render(graphics, sectionX, sectionY, posBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY); // TL
        positionButtons.get(1).render(graphics, sectionX + SECTION_WIDTH - posBtnWidth, sectionY, posBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY); // TR
        sectionY += SMALL_BTN_HEIGHT + 2;
        // Row 2: CL, CR
        positionButtons.get(2).render(graphics, sectionX, sectionY, posBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY); // CL
        positionButtons.get(3).render(graphics, sectionX + SECTION_WIDTH - posBtnWidth, sectionY, posBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY); // CR
        sectionY += SMALL_BTN_HEIGHT + 2;
        // Row 3: BL, BR
        positionButtons.get(4).render(graphics, sectionX, sectionY, posBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY); // BL
        positionButtons.get(5).render(graphics, sectionX + SECTION_WIDTH - posBtnWidth, sectionY, posBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY); // BR
        sectionY += SMALL_BTN_HEIGHT + 8;

        // === OFFSET ===
        graphics.drawString(safeFont, "Offset", sectionX, sectionY, UIConstants.Text.SECONDARY(), false);
        int offsetX = getConfigInt(Config.IMPACT_HUD_OFFSET_X, 10);
        int offsetY = getConfigInt(Config.IMPACT_HUD_OFFSET_Y, 10);
        String offsetStr = Objects.requireNonNull(String.format("X:%d Y:%d", offsetX, offsetY), "offset string");
        graphics.drawString(safeFont, offsetStr, sectionX + SECTION_WIDTH - safeFont.width(offsetStr), sectionY, 0xFFAAAAAA, false);
        sectionY += 12;

        int offsetBtnWidth = 30;
        graphics.drawString(safeFont, "X:", sectionX, sectionY + 4, UIConstants.Text.SECONDARY(), false);
        offsetXMinus.render(graphics, sectionX + 16, sectionY, offsetBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY);
        offsetXPlus.render(graphics, sectionX + 16 + offsetBtnWidth + 4, sectionY, offsetBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY);

        graphics.drawString(safeFont, "Y:", sectionX + 100, sectionY + 4, UIConstants.Text.SECONDARY(), false);
        offsetYMinus.render(graphics, sectionX + 116, sectionY, offsetBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY);
        offsetYPlus.render(graphics, sectionX + 116 + offsetBtnWidth + 4, sectionY, offsetBtnWidth, SMALL_BTN_HEIGHT, mouseX, mouseY);
        sectionY += SMALL_BTN_HEIGHT + 10;

        // === SEPARATOR ===
        graphics.fill(sectionX, sectionY, sectionX + SECTION_WIDTH, sectionY + 1, 0x44FFFFFF);
        sectionY += 8;

        // === PRESETS ===
        graphics.drawString(safeFont, "Presets", sectionX, sectionY, UIConstants.Text.PRIMARY(), false);
        sectionY += 12;

        int presetBtnWidth = (SECTION_WIDTH - 8) / 2;
        exportPreset.render(graphics, sectionX, sectionY, presetBtnWidth, BTN_HEIGHT, mouseX, mouseY);
        importPreset.render(graphics, sectionX + presetBtnWidth + 8, sectionY, presetBtnWidth, BTN_HEIGHT, mouseX, mouseY);
        sectionY += BTN_HEIGHT + 4;

        resetDefaults.render(graphics, sectionX, sectionY, SECTION_WIDTH, SMALL_BTN_HEIGHT, mouseX, mouseY);
        sectionY += SMALL_BTN_HEIGHT + 8;

        // Export status message
        if (!exportStatus.isEmpty() && System.currentTimeMillis() - exportStatusTime < 2000) {
            int statusColor = exportStatus.contains("Failed") ? 0xFFFF4444 : 0xFF44FF88;
            graphics.drawString(safeFont, exportStatus, sectionX, sectionY, statusColor, false);
        }
        sectionY += 12;

        // === STATUS SUMMARY ===
        graphics.fill(sectionX, sectionY, sectionX + SECTION_WIDTH, sectionY + 1, 0x44FFFFFF);
        sectionY += 6;

        boolean hud2d = ImpactHudOverlay.isEnabled();
        boolean hud3d = Impact3DPanelManager.INSTANCE.isEnabled();
        boolean vfx = getConfigBool(Config.IMPACT_VFX_ENABLED);

        String statusLine = String.format("2D:%s  3D:%s  VFX:%s",
            hud2d ? "\u2713" : "\u2717",
            hud3d ? "\u2713" : "\u2717",
            vfx ? "\u2713" : "\u2717");

        int statusColor = (hud2d || hud3d || vfx) ? 0xFF44FF88 : 0xFFFF4444;
        graphics.drawString(safeFont, statusLine, sectionX, sectionY, statusColor, false);
    }

    private void renderBackgroundLayer(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, UIConstants.Background.CONTENT());
    }

    private void ensureDemoButtons() {
        if (demosBuilt) return;
        demoButtons.clear();

        // Define demo buttons (variants)
        demoButtons.add(new DemoButton(new EditorButton("normal", "Normal"), 0, 0, DEMO_WIDTH));
        demoButtons.add(new DemoButton(new EditorButton("primary", "Primary").style(EditorButton.Style.PRIMARY), 0, 0, DEMO_WIDTH));
        demoButtons.add(new DemoButton(new EditorButton("danger", "Danger").style(EditorButton.Style.DANGER), 0, 0, DEMO_WIDTH));
        demoButtons.add(new DemoButton(new EditorButton("success", "Success").style(EditorButton.Style.SUCCESS), 0, 0, DEMO_WIDTH));
        demoButtons.add(new DemoButton(new EditorButton("ghost", "Ghost").style(EditorButton.Style.GHOST), 0, 0, DEMO_WIDTH));
        demoButtons.add(new DemoButton(new EditorButton("disabled", "Disabled").enabled(false), 0, 0, DEMO_WIDTH));

        demoButtons.add(new DemoButton(new EditorButton("icon", "Icona + Label")
            .icon("\u25B6")
            .hotkeyHint("[G]"), 0, 0, DEMO_WIDTH));

        demoButtons.add(new DemoButton(new EditorButton("toggle-off", "Toggle Off")
            .toggleable(true)
            .style(EditorButton.Style.PRIMARY), 0, 0, DEMO_WIDTH));

        demoButtons.add(new DemoButton(new EditorButton("toggle-on", "Toggle On")
            .toggleable(true)
            .toggled(true)
            .style(EditorButton.Style.SUCCESS), 0, 0, DEMO_WIDTH));

        demoButtons.add(new DemoButton(new EditorButton("small", "Small Size")
            .size(EditorButton.Size.SMALL), 0, 0, DEMO_WIDTH));

        demoButtons.add(new DemoButton(new EditorButton("large", "Large Size")
            .size(EditorButton.Size.LARGE)
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2605"), 0, 0, DEMO_WIDTH));

        demoButtons.add(new DemoButton(new EditorButton("hotkey", "Hotkey Hint")
            .hotkeyHint("[CTRL+H]"), 0, 0, DEMO_WIDTH));

        layoutGrid();
        demosBuilt = true;
    }

    private void layoutGrid() {
        int currentY = PADDING + 60;
        int col = 0;
        int rowHeight = 0;

        for (DemoButton demo : demoButtons) {
            int btnHeight = demo.button.getSize().height();
            int x = PADDING + col * (DEMO_WIDTH + GRID_SPACING);
            int y = currentY;

            demo.x = x;
            demo.y = y;
            rowHeight = Math.max(rowHeight, btnHeight);

            col++;
            if (col >= GRID_COLUMNS) {
                col = 0;
                currentY += rowHeight + GRID_SPACING;
                rowHeight = 0;
            }
        }
    }

    private void renderDemoGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int areaTop = PADDING + 56;
        int areaRight = width - SECTION_WIDTH - PADDING - 16;
        graphics.fill(PADDING - 6, areaTop, areaRight, height - PADDING, UIConstants.Background.PANEL());
        graphics.drawString(Objects.requireNonNull(font, "font"),
            "Varianti EditorButton", PADDING, areaTop + 8, UIConstants.Text.PRIMARY(), false);
        graphics.drawString(Objects.requireNonNull(font, "font"),
            "Tip: icona a sx, hotkey a dx, toggle cambia stato/colore", PADDING, areaTop + 18, UIConstants.Text.SECONDARY(), false);

        for (DemoButton demo : demoButtons) {
            demo.button.render(graphics, demo.x, demo.y, demo.width, demo.button.getSize().height(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check all buttons
        for (EditorButton btn : allButtons) {
            if (btn.mouseClicked(mouseX, mouseY, button)) return true;
        }

        if (showCases) {
            for (DemoButton demo : demoButtons) {
                if (demo.button.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Check all buttons
        for (EditorButton btn : allButtons) {
            if (btn.mouseReleased(mouseX, mouseY, button)) return true;
        }

        if (showCases) {
            for (DemoButton demo : demoButtons) {
                if (demo.button.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void resize(@Nonnull Minecraft mc, int newWidth, int newHeight) {
        super.resize(mc, newWidth, newHeight);
        demosBuilt = false;
    }

    private static final class DemoButton {
        final EditorButton button;
        final int width;
        int x;
        int y;

        DemoButton(EditorButton button, int x, int y, int width) {
            this.button = button;
            this.x = x;
            this.y = y;
            this.width = width;
        }
    }
}
