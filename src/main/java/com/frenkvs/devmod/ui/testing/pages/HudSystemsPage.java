package com.frenkvs.devmod.ui.testing.pages;

import com.frenkvs.devmod.Config;
import com.frenkvs.devmod.actions.ActionIds;
import com.frenkvs.devmod.actions.ActionOrigin;
import com.frenkvs.devmod.actions.ActionRegistry;
import com.frenkvs.devmod.actions.client.ClientActionContexts;
import com.frenkvs.devmod.hud.Impact3DPanelManager;
import com.frenkvs.devmod.hud.ImpactHudOverlay;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.testing.VoxelLabTab;
import com.frenkvs.devmod.ui.testing.panel.*;

import java.util.ArrayList;
import java.util.List;

import static com.frenkvs.devmod.ui.testing.pages.PageUtils.safeGetBool;

/**
 * HUD Systems Page - Configuration for all HUD overlays.
 * Includes Impact HUD 2D/3D, position, offset, and preset management.
 */
public class HudSystemsPage extends AbstractVoxelLabPage {

    // 2D HUD toggles
    private EditorButton hud2dToggle;
    private EditorButton historyToggle;
    private EditorButton dpsToggle;

    // 3D Panel toggle
    private EditorButton panel3dToggle;

    // Position buttons
    private final List<EditorButton> positionButtons = new ArrayList<>();
    private Config.HudPosition currentPosition = Config.HudPosition.TOP_RIGHT;

    // Offset buttons
    private EditorButton offsetXMinus;
    private EditorButton offsetXPlus;
    private EditorButton offsetYMinus;
    private EditorButton offsetYPlus;

    // Preset buttons
    private EditorButton exportPreset;
    private EditorButton importPreset;
    private EditorButton resetDefaults;

    // Status message
    private String statusMessage = "";
    private long statusMessageTime = 0;

    public HudSystemsPage() {
        super(VoxelLabTab.HUD_SYSTEMS);
    }

    @Override
    protected void buildPanels() {
        loadCurrentPosition();
        createButtons();

        // Header
        panelContainer.addPanel(new HeaderPanel("HUD SYSTEMS"));

        // 2D HUD Section
        panelContainer.addPanel(
            SectionPanel.builder("section-2d", "2D HUD Overlay")
                .description("On-screen damage breakdown display")
                .addButton(hud2dToggle)
                .addRow(historyToggle, dpsToggle)
                .build()
        );

        // 3D Panel Section
        panelContainer.addPanel(
            SectionPanel.builder("section-3d", "3D World Panel")
                .description("In-world floating damage display")
                .addButton(panel3dToggle)
                .build()
        );

        // Position Section (collapsible)
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-position", "HUD Position",
                GridPanel.of("grid-position", "Select Position", positionButtons, 2),
                0xFFFFAA00)
        );

        // Offset Section
        panelContainer.addPanel(
            SectionPanel.builder("section-offset", "Offset Adjustment")
                .description("Fine-tune HUD position")
                .addRow(offsetXMinus, offsetXPlus, offsetYMinus, offsetYPlus)
                .build()
        );

        // Presets Section
        panelContainer.addPanel(
            SectionPanel.builder("section-presets", "Presets")
                .withSeparator()
                .addRow(exportPreset, importPreset)
                .addButton(resetDefaults)
                .build()
        );

        // Status Panel
        panelContainer.addPanel(new SpacerPanel("spacer-status", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-hud")
                .addStatus("2D HUD", ImpactHudOverlay::isEnabled)
                .addStatus("3D Panel", () -> Impact3DPanelManager.INSTANCE.isEnabled())
                .addStatus("History", () -> safeGetBool(Config.IMPACT_HUD_HISTORY_ENABLED))
                .addStatus("DPS", () -> safeGetBool(Config.IMPACT_HUD_DPS_ENABLED))
                .messageSupplier(this::getStatusMessage)
                .build()
        );
    }

    private void loadCurrentPosition() {
        try {
            currentPosition = Config.IMPACT_HUD_POSITION.get();
        } catch (Exception ignored) {}
    }

    private void createButtons() {
        // 2D HUD toggles
        hud2dToggle = new EditorButton("toggle-hud2d", "Enable 2D HUD")
            .toggleable(true)
            .toggled(ImpactHudOverlay.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\uD83D\uDCCA")
            .onToggle(v -> invokeToggleAction(ActionIds.HUD_IMPACT_TOGGLE,
                Boolean.TRUE.equals(v), ImpactHudOverlay.isEnabled()));

        historyToggle = new EditorButton("toggle-history", "History")
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_HUD_HISTORY_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_HUD_HISTORY_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_HUD_HISTORY_ENABLED)));

        dpsToggle = new EditorButton("toggle-dps", "DPS Tracker")
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_HUD_DPS_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_HUD_DPS_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_HUD_DPS_ENABLED)));

        // 3D Panel toggle
        panel3dToggle = new EditorButton("toggle-3d", "Enable 3D Panel")
            .toggleable(true)
            .toggled(Impact3DPanelManager.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\uD83C\uDF10")
            .onToggle(v -> invokeToggleAction(ActionIds.HUD_IMPACT_3D_TOGGLE,
                Boolean.TRUE.equals(v), Impact3DPanelManager.INSTANCE.isEnabled()));

        // Position buttons
        positionButtons.clear();
        for (Config.HudPosition pos : Config.HudPosition.values()) {
            EditorButton btn = new EditorButton("pos-" + pos.name(), formatPositionName(pos))
                .toggleable(true)
                .toggled(pos == currentPosition)
                .size(EditorButton.Size.SMALL)
                .onToggle(v -> {
                    if (Boolean.TRUE.equals(v)) {
                        ActionRegistry.invoke(resolvePositionAction(pos),
                            ClientActionContexts.forClient(ActionOrigin.UI));
                        currentPosition = pos;
                        updatePositionButtonStates();
                    }
                });
            positionButtons.add(btn);
        }

        // Offset buttons
        offsetXMinus = new EditorButton("ox-", "X-")
            .size(EditorButton.Size.SMALL)
            .onClick(() -> invokeAction(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_MINUS));

        offsetXPlus = new EditorButton("ox+", "X+")
            .size(EditorButton.Size.SMALL)
            .onClick(() -> invokeAction(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_PLUS));

        offsetYMinus = new EditorButton("oy-", "Y-")
            .size(EditorButton.Size.SMALL)
            .onClick(() -> invokeAction(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_MINUS));

        offsetYPlus = new EditorButton("oy+", "Y+")
            .size(EditorButton.Size.SMALL)
            .onClick(() -> invokeAction(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_PLUS));

        // Preset buttons
        exportPreset = new EditorButton("export", "Export")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .icon("\uD83D\uDCE4")
            .onClick(() -> {
                boolean success = ActionRegistry.invoke(ActionIds.CONFIG_IMPACT_HUD_PRESET_EXPORT,
                    ClientActionContexts.forClient(ActionOrigin.UI));
                showStatus(success ? "Exported!" : "Export failed!");
            });

        importPreset = new EditorButton("import", "Import")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .icon("\uD83D\uDCE5")
            .onClick(() -> {
                boolean success = ActionRegistry.invoke(ActionIds.CONFIG_IMPACT_HUD_PRESET_IMPORT,
                    ClientActionContexts.forClient(ActionOrigin.UI));
                showStatus(success ? "Imported!" : "Import failed!");
                syncButtonStates();
            });

        resetDefaults = new EditorButton("reset", "Reset Defaults")
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> {
                ActionRegistry.invoke(ActionIds.CONFIG_IMPACT_HUD_RESET_DEFAULTS,
                    ClientActionContexts.forClient(ActionOrigin.UI));
                resetToDefaults();
            });
    }

    private void resetToDefaults() {
        currentPosition = Config.HudPosition.TOP_RIGHT;

        syncButtonStates();
        showStatus("Reset to defaults!");
    }

    private void showStatus(String message) {
        statusMessage = message;
        statusMessageTime = System.currentTimeMillis();
    }

    private String getStatusMessage() {
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusMessageTime > 2000) {
            statusMessage = "";
        }
        return statusMessage;
    }

    @Override
    protected void onTick() {
        syncButtonStates();
    }

    private void syncButtonStates() {
        hud2dToggle.toggled(ImpactHudOverlay.isEnabled());
        historyToggle.toggled(safeGetBool(Config.IMPACT_HUD_HISTORY_ENABLED));
        dpsToggle.toggled(safeGetBool(Config.IMPACT_HUD_DPS_ENABLED));
        panel3dToggle.toggled(Impact3DPanelManager.INSTANCE.isEnabled());
        updatePositionButtonStates();
    }

    private void updatePositionButtonStates() {
        Config.HudPosition[] positions = Config.HudPosition.values();
        for (int i = 0; i < positionButtons.size() && i < positions.length; i++) {
            positionButtons.get(i).toggled(positions[i] == currentPosition);
        }
    }

    private void invokeToggleAction(String actionId, boolean desired, boolean current) {
        if (desired == current) {
            return;
        }
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
    }

    private void invokeAction(String actionId) {
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
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

    private static String formatPositionName(Config.HudPosition pos) {
        return switch (pos) {
            case TOP_LEFT -> "Top Left";
            case TOP_RIGHT -> "Top Right";
            case CENTER_LEFT -> "Center Left";
            case CENTER_RIGHT -> "Center Right";
            case BOTTOM_LEFT -> "Bottom Left";
            case BOTTOM_RIGHT -> "Bottom Right";
        };
    }
}
