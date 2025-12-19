package com.frenkvs.devmod.ui.testing;

import com.frenkvs.devmod.Config;
import com.frenkvs.devmod.hud.Impact3DPanelManager;
import com.frenkvs.devmod.hud.ImpactHudOverlay;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Factory and manager for Impact HUD control buttons.
 * Centralizes button creation and state synchronization.
 */
public final class ImpactHudButtons {

    // ═══════════════════════════════════════════════════════════════
    // 2D HUD BUTTONS
    // ═══════════════════════════════════════════════════════════════

    private final EditorButton hud2dToggle;
    private final EditorButton historyToggle;
    private final EditorButton dpsToggle;

    // ═══════════════════════════════════════════════════════════════
    // 3D PANEL BUTTON
    // ═══════════════════════════════════════════════════════════════

    private final EditorButton panel3dToggle;

    // ═══════════════════════════════════════════════════════════════
    // VFX BUTTONS
    // ═══════════════════════════════════════════════════════════════

    private final EditorButton vfxMasterToggle;
    private final EditorButton vfxVortexToggle;
    private final EditorButton vfxSlashToggle;
    private final EditorButton vfxLinesToggle;

    // ═══════════════════════════════════════════════════════════════
    // INTENSITY BUTTONS
    // ═══════════════════════════════════════════════════════════════

    private final EditorButton intensityLow;
    private final EditorButton intensityMed;
    private final EditorButton intensityHigh;
    private final EditorButton intensityMax;

    // ═══════════════════════════════════════════════════════════════
    // POSITION BUTTONS
    // ═══════════════════════════════════════════════════════════════

    private final List<EditorButton> positionButtons = new ArrayList<>();
    private Config.HudPosition currentPosition = Config.HudPosition.TOP_RIGHT;

    // ═══════════════════════════════════════════════════════════════
    // OFFSET BUTTONS
    // ═══════════════════════════════════════════════════════════════

    private final EditorButton offsetXMinus;
    private final EditorButton offsetXPlus;
    private final EditorButton offsetYMinus;
    private final EditorButton offsetYPlus;

    // ═══════════════════════════════════════════════════════════════
    // PRESET BUTTONS
    // ═══════════════════════════════════════════════════════════════

    private final EditorButton exportPreset;
    private final EditorButton importPreset;
    private final EditorButton resetDefaults;

    // Callbacks
    private Consumer<String> statusCallback = s -> {};
    private Runnable syncCallback = () -> {};

    public ImpactHudButtons() {
        // Load current position
        try {
            currentPosition = Config.IMPACT_HUD_POSITION.get();
        } catch (Exception ignored) {}

        // === 2D HUD ===
        hud2dToggle = new EditorButton("impact-2d", "2D HUD Overlay")
            .style(EditorButton.Style.PRIMARY)
            .toggleable(true)
            .icon("\uD83D\uDCCA")
            .onToggle(v -> ImpactHudOverlay.setEnabled(Boolean.TRUE.equals(v)));

        historyToggle = new EditorButton("impact-history", "History")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> Config.IMPACT_HUD_HISTORY_ENABLED.set(Boolean.TRUE.equals(v)));

        dpsToggle = new EditorButton("impact-dps", "DPS Tracker")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> Config.IMPACT_HUD_DPS_ENABLED.set(Boolean.TRUE.equals(v)));

        // === 3D Panel ===
        panel3dToggle = new EditorButton("impact-3d", "3D World Panel")
            .style(EditorButton.Style.PRIMARY)
            .toggleable(true)
            .icon("\uD83C\uDF10")
            .onToggle(v -> Impact3DPanelManager.INSTANCE.setEnabled(Boolean.TRUE.equals(v)));

        // === VFX ===
        vfxMasterToggle = new EditorButton("vfx-master", "VFX Master")
            .style(EditorButton.Style.SUCCESS)
            .toggleable(true)
            .icon("\u2728")
            .onToggle(v -> Config.IMPACT_VFX_ENABLED.set(Boolean.TRUE.equals(v)));

        vfxVortexToggle = new EditorButton("vfx-vortex", "Vortex")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> Config.IMPACT_VFX_VORTEX_ENABLED.set(Boolean.TRUE.equals(v)));

        vfxSlashToggle = new EditorButton("vfx-slash", "Slash")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> Config.IMPACT_VFX_SLASH_ENABLED.set(Boolean.TRUE.equals(v)));

        vfxLinesToggle = new EditorButton("vfx-lines", "Lines")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> Config.IMPACT_VFX_LINES_ENABLED.set(Boolean.TRUE.equals(v)));

        // === Intensity ===
        intensityLow = new EditorButton("int-low", "Low")
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) setIntensity(0.5); });

        intensityMed = new EditorButton("int-med", "Normal")
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) setIntensity(1.0); });

        intensityHigh = new EditorButton("int-high", "High")
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) setIntensity(1.5); });

        intensityMax = new EditorButton("int-max", "Max")
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) setIntensity(2.0); });

        // === Position ===
        for (Config.HudPosition pos : Config.HudPosition.values()) {
            EditorButton btn = new EditorButton("pos-" + pos.name(), formatPositionName(pos))
                .size(EditorButton.Size.SMALL)
                .toggleable(true)
                .onToggle(v -> {
                    if (Boolean.TRUE.equals(v)) {
                        currentPosition = pos;
                        Config.IMPACT_HUD_POSITION.set(pos);
                        updatePositionStates();
                    }
                });
            positionButtons.add(btn);
        }

        // === Offset ===
        offsetXMinus = new EditorButton("ox-", "-").size(EditorButton.Size.SMALL).onClick(() -> adjustOffset(true, -10));
        offsetXPlus = new EditorButton("ox+", "+").size(EditorButton.Size.SMALL).onClick(() -> adjustOffset(true, 10));
        offsetYMinus = new EditorButton("oy-", "-").size(EditorButton.Size.SMALL).onClick(() -> adjustOffset(false, -10));
        offsetYPlus = new EditorButton("oy+", "+").size(EditorButton.Size.SMALL).onClick(() -> adjustOffset(false, 10));

        // === Presets ===
        exportPreset = new EditorButton("export", "Export")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .icon("\uD83D\uDCE4");

        importPreset = new EditorButton("import", "Import")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .icon("\uD83D\uDCE5");

        resetDefaults = new EditorButton("reset", "Reset Defaults")
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL);
    }

    // ═══════════════════════════════════════════════════════════════
    // CALLBACKS
    // ═══════════════════════════════════════════════════════════════

    public ImpactHudButtons onStatus(Consumer<String> callback) {
        this.statusCallback = callback;

        // Wire up preset buttons with callback
        exportPreset.onClick(() -> {
            boolean success = com.frenkvs.devmod.hud.ImpactHudPresets.exportToDefault();
            statusCallback.accept(success ? "Exported!" : "Export failed!");
        });

        importPreset.onClick(() -> {
            boolean success = com.frenkvs.devmod.hud.ImpactHudPresets.importFromDefault();
            statusCallback.accept(success ? "Imported!" : "Import failed!");
            syncAll();
        });

        resetDefaults.onClick(this::resetToDefaults);

        return this;
    }

    public ImpactHudButtons onSync(Runnable callback) {
        this.syncCallback = callback;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE SYNC
    // ═══════════════════════════════════════════════════════════════

    public void syncAll() {
        hud2dToggle.toggled(ImpactHudOverlay.isEnabled());
        historyToggle.toggled(getConfigBool(Config.IMPACT_HUD_HISTORY_ENABLED));
        dpsToggle.toggled(getConfigBool(Config.IMPACT_HUD_DPS_ENABLED));
        panel3dToggle.toggled(Impact3DPanelManager.INSTANCE.isEnabled());
        vfxMasterToggle.toggled(getConfigBool(Config.IMPACT_VFX_ENABLED));
        vfxVortexToggle.toggled(getConfigBool(Config.IMPACT_VFX_VORTEX_ENABLED));
        vfxSlashToggle.toggled(getConfigBool(Config.IMPACT_VFX_SLASH_ENABLED));
        vfxLinesToggle.toggled(getConfigBool(Config.IMPACT_VFX_LINES_ENABLED));

        double intensity = getConfigDouble(Config.IMPACT_VFX_INTENSITY, 1.0);
        intensityLow.toggled(intensity < 0.7);
        intensityMed.toggled(intensity >= 0.7 && intensity < 1.3);
        intensityHigh.toggled(intensity >= 1.3 && intensity < 1.8);
        intensityMax.toggled(intensity >= 1.8);

        updatePositionStates();
        syncCallback.run();
    }

    private void updatePositionStates() {
        Config.HudPosition[] positions = Config.HudPosition.values();
        for (int i = 0; i < positionButtons.size() && i < positions.length; i++) {
            positionButtons.get(i).toggled(positions[i] == currentPosition);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ACTIONS
    // ═══════════════════════════════════════════════════════════════

    private void setIntensity(double value) {
        Config.IMPACT_VFX_INTENSITY.set(value);
        syncAll();
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

        syncAll();
        statusCallback.accept("Reset!");
    }

    // ═══════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public EditorButton hud2dToggle() { return hud2dToggle; }
    public EditorButton historyToggle() { return historyToggle; }
    public EditorButton dpsToggle() { return dpsToggle; }
    public EditorButton panel3dToggle() { return panel3dToggle; }
    public EditorButton vfxMasterToggle() { return vfxMasterToggle; }
    public EditorButton vfxVortexToggle() { return vfxVortexToggle; }
    public EditorButton vfxSlashToggle() { return vfxSlashToggle; }
    public EditorButton vfxLinesToggle() { return vfxLinesToggle; }
    public EditorButton intensityLow() { return intensityLow; }
    public EditorButton intensityMed() { return intensityMed; }
    public EditorButton intensityHigh() { return intensityHigh; }
    public EditorButton intensityMax() { return intensityMax; }
    public List<EditorButton> positionButtons() { return positionButtons; }
    public EditorButton offsetXMinus() { return offsetXMinus; }
    public EditorButton offsetXPlus() { return offsetXPlus; }
    public EditorButton offsetYMinus() { return offsetYMinus; }
    public EditorButton offsetYPlus() { return offsetYPlus; }
    public EditorButton exportPreset() { return exportPreset; }
    public EditorButton importPreset() { return importPreset; }
    public EditorButton resetDefaults() { return resetDefaults; }

    // ═══════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════

    private static String formatPositionName(Config.HudPosition pos) {
        return switch (pos) {
            case TOP_LEFT -> "TL";
            case TOP_RIGHT -> "TR";
            case CENTER_LEFT -> "CL";
            case CENTER_RIGHT -> "CR";
            case BOTTOM_LEFT -> "BL";
            case BOTTOM_RIGHT -> "BR";
        };
    }

    private static boolean getConfigBool(ModConfigSpec.BooleanValue config) {
        try { return config.get(); } catch (Exception e) { return false; }
    }

    private static double getConfigDouble(ModConfigSpec.DoubleValue config, double def) {
        try { return config.get(); } catch (Exception e) { return def; }
    }

    public static int getOffsetX() {
        try { return Config.IMPACT_HUD_OFFSET_X.get(); } catch (Exception e) { return 10; }
    }

    public static int getOffsetY() {
        try { return Config.IMPACT_HUD_OFFSET_Y.get(); } catch (Exception e) { return 10; }
    }
}
