package com.devmod.client.ui.testing;

import com.devmod.config.Config;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.overlay.Impact3DPanelManager;
import com.devmod.client.overlay.ImpactHudOverlay;
import com.devmod.client.ui.editor.components.EditorButton;
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
            .onToggle(v -> invokeToggleAction(ActionIds.HUD_IMPACT_TOGGLE,
                Boolean.TRUE.equals(v), ImpactHudOverlay.isEnabled()));

        historyToggle = new EditorButton("impact-history", "History")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_HUD_HISTORY_TOGGLE,
                Boolean.TRUE.equals(v), getConfigBool(Config.IMPACT_HUD_HISTORY_ENABLED)));

        dpsToggle = new EditorButton("impact-dps", "DPS Tracker")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_HUD_DPS_TOGGLE,
                Boolean.TRUE.equals(v), getConfigBool(Config.IMPACT_HUD_DPS_ENABLED)));

        // === 3D Panel ===
        panel3dToggle = new EditorButton("impact-3d", "3D World Panel")
            .style(EditorButton.Style.PRIMARY)
            .toggleable(true)
            .icon("\uD83C\uDF10")
            .onToggle(v -> invokeToggleAction(ActionIds.HUD_IMPACT_3D_TOGGLE,
                Boolean.TRUE.equals(v), Impact3DPanelManager.INSTANCE.isEnabled()));

        // === VFX ===
        vfxMasterToggle = new EditorButton("vfx-master", "VFX Master")
            .style(EditorButton.Style.SUCCESS)
            .toggleable(true)
            .icon("\u2728")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_TOGGLE,
                Boolean.TRUE.equals(v), getConfigBool(Config.IMPACT_VFX_ENABLED)));

        vfxVortexToggle = new EditorButton("vfx-vortex", "Vortex")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_VORTEX_TOGGLE,
                Boolean.TRUE.equals(v), getConfigBool(Config.IMPACT_VFX_VORTEX_ENABLED)));

        vfxSlashToggle = new EditorButton("vfx-slash", "Slash")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_SLASH_TOGGLE,
                Boolean.TRUE.equals(v), getConfigBool(Config.IMPACT_VFX_SLASH_ENABLED)));

        vfxLinesToggle = new EditorButton("vfx-lines", "Lines")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_LINES_TOGGLE,
                Boolean.TRUE.equals(v), getConfigBool(Config.IMPACT_VFX_LINES_ENABLED)));

        // === Intensity ===
        intensityLow = new EditorButton("int-low", "Low")
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) invokeAction(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_LOW); });

        intensityMed = new EditorButton("int-med", "Normal")
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) invokeAction(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MED); });

        intensityHigh = new EditorButton("int-high", "High")
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) invokeAction(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_HIGH); });

        intensityMax = new EditorButton("int-max", "Max")
            .size(EditorButton.Size.SMALL)
            .toggleable(true)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) invokeAction(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MAX); });

        // === Position ===
        for (Config.HudPosition pos : Config.HudPosition.values()) {
            EditorButton btn = new EditorButton("pos-" + pos.name(), formatPositionName(pos))
                .size(EditorButton.Size.SMALL)
                .toggleable(true)
                .onToggle(v -> {
                    if (Boolean.TRUE.equals(v)) {
                        ActionRegistry.invoke(resolvePositionAction(pos),
                            ClientActionContexts.forClient(ActionOrigin.UI));
                        currentPosition = pos;
                        updatePositionStates();
                    }
                });
            positionButtons.add(btn);
        }

        // === Offset ===
        offsetXMinus = new EditorButton("ox-", "-").size(EditorButton.Size.SMALL)
            .onClick(() -> invokeAction(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_MINUS));
        offsetXPlus = new EditorButton("ox+", "+").size(EditorButton.Size.SMALL)
            .onClick(() -> invokeAction(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_PLUS));
        offsetYMinus = new EditorButton("oy-", "-").size(EditorButton.Size.SMALL)
            .onClick(() -> invokeAction(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_MINUS));
        offsetYPlus = new EditorButton("oy+", "+").size(EditorButton.Size.SMALL)
            .onClick(() -> invokeAction(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_PLUS));

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
            boolean success = ActionRegistry.invoke(ActionIds.CONFIG_IMPACT_HUD_PRESET_EXPORT,
                ClientActionContexts.forClient(ActionOrigin.UI));
            statusCallback.accept(success ? "Exported!" : "Export failed!");
        });

        importPreset.onClick(() -> {
            boolean success = ActionRegistry.invoke(ActionIds.CONFIG_IMPACT_HUD_PRESET_IMPORT,
                ClientActionContexts.forClient(ActionOrigin.UI));
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

    private void resetToDefaults() {
        invokeAction(ActionIds.CONFIG_IMPACT_HUD_RESET_DEFAULTS);
        invokeAction(ActionIds.CONFIG_IMPACT_VFX_RESET_DEFAULTS);
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

    private void invokeToggleAction(String actionId, boolean desired, boolean current) {
        if (desired == current) {
            return;
        }
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
    }

    private void invokeAction(String actionId) {
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
        syncAll();
    }

    private static String resolvePositionAction(Config.HudPosition pos) {
        return switch (pos) {
            case TOP_LEFT -> ActionIds.CONFIG_IMPACT_HUD_POSITION_TOP_LEFT;
            case TOP_RIGHT -> ActionIds.CONFIG_IMPACT_HUD_POSITION_TOP_RIGHT;
            case CENTER_LEFT -> ActionIds.CONFIG_IMPACT_HUD_POSITION_CENTER_LEFT;
            case CENTER_RIGHT -> ActionIds.CONFIG_IMPACT_HUD_POSITION_CENTER_RIGHT;
            case BOTTOM_LEFT -> ActionIds.CONFIG_IMPACT_HUD_POSITION_BOTTOM_LEFT;
            case BOTTOM_RIGHT -> ActionIds.CONFIG_IMPACT_HUD_POSITION_BOTTOM_RIGHT;
        };
    }

    public static int getOffsetX() {
        try { return Config.IMPACT_HUD_OFFSET_X.get(); } catch (Exception e) { return 10; }
    }

    public static int getOffsetY() {
        try { return Config.IMPACT_HUD_OFFSET_Y.get(); } catch (Exception e) { return 10; }
    }
}
