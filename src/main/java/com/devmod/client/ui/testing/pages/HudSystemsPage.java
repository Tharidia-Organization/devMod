package com.devmod.client.ui.testing.pages;

import java.util.ArrayList;
import java.util.List;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.overlay.Impact3DPanelManager;
import com.devmod.client.overlay.ImpactHudOverlay;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.testing.VoxelLabTab;
import com.devmod.client.ui.testing.panel.CollapsiblePanel;
import com.devmod.client.ui.testing.panel.GridPanel;
import com.devmod.client.ui.testing.panel.HeaderPanel;
import com.devmod.client.ui.testing.panel.SectionPanel;
import com.devmod.client.ui.testing.panel.SpacerPanel;
import com.devmod.client.ui.testing.panel.StatusPanel;
import com.devmod.config.Config;
import com.devmod.util.I18n;

import static com.devmod.client.ui.testing.pages.PageUtils.safeGetBool;

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
        panelContainer.addPanel(new HeaderPanel(
            I18n.translate("devmod.testing.voxel_lab.hud.header").getString()));

        // 2D HUD Section
        panelContainer.addPanel(
            SectionPanel.builder("section-2d",
                I18n.translate("devmod.testing.voxel_lab.section.hud_2d").getString())
                .description(I18n.translate("devmod.testing.voxel_lab.hud.section.hud_2d_desc").getString())
                .addButton(hud2dToggle)
                .addRow(historyToggle, dpsToggle)
                .build()
        );

        // 3D Panel Section
        panelContainer.addPanel(
            SectionPanel.builder("section-3d",
                I18n.translate("devmod.testing.voxel_lab.section.hud_3d").getString())
                .description(I18n.translate("devmod.testing.voxel_lab.hud.section.hud_3d_desc").getString())
                .addButton(panel3dToggle)
                .build()
        );

        // Position Section (collapsible)
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-position",
                I18n.translate("devmod.testing.voxel_lab.section.hud_position").getString(),
                GridPanel.of("grid-position",
                    I18n.translate("devmod.testing.voxel_lab.section.select_position").getString(),
                    positionButtons, 2),
                0xFFFFAA00)
        );

        // Offset Section
        panelContainer.addPanel(
            SectionPanel.builder("section-offset",
                I18n.translate("devmod.testing.voxel_lab.hud.section.offset_adjustment").getString())
                .description(I18n.translate("devmod.testing.voxel_lab.hud.section.offset_desc").getString())
                .addRow(offsetXMinus, offsetXPlus, offsetYMinus, offsetYPlus)
                .build()
        );

        // Presets Section
        panelContainer.addPanel(
            SectionPanel.builder("section-presets",
                I18n.translate("devmod.testing.voxel_lab.section.presets").getString())
                .withSeparator()
                .addRow(exportPreset, importPreset)
                .addButton(resetDefaults)
                .build()
        );

        // Status Panel
        panelContainer.addPanel(new SpacerPanel("spacer-status", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-hud")
                .addStatus(I18n.translate("devmod.testing.voxel_lab.hud.status.hud_2d").getString(),
                    ImpactHudOverlay::isEnabled)
                .addStatus(I18n.translate("devmod.testing.voxel_lab.hud.status.panel_3d").getString(),
                    () -> Impact3DPanelManager.INSTANCE.isEnabled())
                .addStatus(I18n.translate("devmod.testing.impact_hud.history").getString(),
                    () -> safeGetBool(Config.IMPACT_HUD_HISTORY_ENABLED))
                .addStatus(I18n.translate("devmod.testing.voxel_lab.hud.status.dps").getString(),
                    () -> safeGetBool(Config.IMPACT_HUD_DPS_ENABLED))
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
        hud2dToggle = new EditorButton("toggle-hud2d",
            I18n.translate("devmod.testing.voxel_lab.hud.toggle.hud_2d").getString())
            .toggleable(true)
            .toggled(ImpactHudOverlay.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\uD83D\uDCCA")
            .onToggle(v -> invokeToggleAction(ActionIds.HUD_IMPACT_TOGGLE,
                Boolean.TRUE.equals(v), ImpactHudOverlay.isEnabled()));

        historyToggle = new EditorButton("toggle-history",
            I18n.translate("devmod.testing.impact_hud.history").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_HUD_HISTORY_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_HUD_HISTORY_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_HUD_HISTORY_ENABLED)));

        dpsToggle = new EditorButton("toggle-dps",
            I18n.translate("devmod.testing.impact_hud.dps").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_HUD_DPS_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_HUD_DPS_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_HUD_DPS_ENABLED)));

        // 3D Panel toggle
        panel3dToggle = new EditorButton("toggle-3d",
            I18n.translate("devmod.testing.voxel_lab.hud.toggle.panel_3d").getString())
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
        offsetXMinus = new EditorButton("ox-",
            I18n.translate("devmod.testing.voxel_lab.hud.offset.x_minus").getString())
            .size(EditorButton.Size.SMALL)
            .onClick(() -> invokeAction(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_MINUS));

        offsetXPlus = new EditorButton("ox+",
            I18n.translate("devmod.testing.voxel_lab.hud.offset.x_plus").getString())
            .size(EditorButton.Size.SMALL)
            .onClick(() -> invokeAction(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_PLUS));

        offsetYMinus = new EditorButton("oy-",
            I18n.translate("devmod.testing.voxel_lab.hud.offset.y_minus").getString())
            .size(EditorButton.Size.SMALL)
            .onClick(() -> invokeAction(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_MINUS));

        offsetYPlus = new EditorButton("oy+",
            I18n.translate("devmod.testing.voxel_lab.hud.offset.y_plus").getString())
            .size(EditorButton.Size.SMALL)
            .onClick(() -> invokeAction(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_PLUS));

        // Preset buttons
        exportPreset = new EditorButton("export", I18n.ui("export").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .icon("\uD83D\uDCE4")
            .onClick(() -> {
                boolean success = ActionRegistry.invoke(ActionIds.CONFIG_IMPACT_HUD_PRESET_EXPORT,
                    ClientActionContexts.forClient(ActionOrigin.UI));
                showStatus(success
                    ? "+ " + I18n.translate("devmod.testing.impact_hud.exported").getString()
                    : "! " + I18n.translate("devmod.testing.impact_hud.export_failed").getString());
            });

        importPreset = new EditorButton("import", I18n.ui("import").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .icon("\uD83D\uDCE5")
            .onClick(() -> {
                boolean success = ActionRegistry.invoke(ActionIds.CONFIG_IMPACT_HUD_PRESET_IMPORT,
                    ClientActionContexts.forClient(ActionOrigin.UI));
                showStatus(success
                    ? "+ " + I18n.translate("devmod.testing.impact_hud.imported").getString()
                    : "! " + I18n.translate("devmod.testing.impact_hud.import_failed").getString());
                syncButtonStates();
            });

        resetDefaults = new EditorButton("reset", I18n.ui("reset_defaults").getString())
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
        showStatus("+ " + I18n.translate("devmod.testing.voxel_lab.hud.message.reset_defaults").getString());
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
        String key = switch (pos) {
            case TOP_LEFT -> "top_left";
            case TOP_RIGHT -> "top_right";
            case CENTER_LEFT -> "center_left";
            case CENTER_RIGHT -> "center_right";
            case BOTTOM_LEFT -> "bottom_left";
            case BOTTOM_RIGHT -> "bottom_right";
        };
        return I18n.translate("devmod.testing.voxel_lab.hud.position." + key).getString();
    }
}
