package com.frenkvs.devmod.ui.testing.pages;

import com.frenkvs.devmod.Config;
import com.frenkvs.devmod.hud.Impact3DPanelManager;
import com.frenkvs.devmod.hud.ImpactHudOverlay;
import com.frenkvs.devmod.hud.ImpactHudPresets;
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
            .onToggle(v -> ImpactHudOverlay.setEnabled(Boolean.TRUE.equals(v)));

        historyToggle = new EditorButton("toggle-history", "History")
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_HUD_HISTORY_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> Config.IMPACT_HUD_HISTORY_ENABLED.set(Boolean.TRUE.equals(v)));

        dpsToggle = new EditorButton("toggle-dps", "DPS Tracker")
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_HUD_DPS_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> Config.IMPACT_HUD_DPS_ENABLED.set(Boolean.TRUE.equals(v)));

        // 3D Panel toggle
        panel3dToggle = new EditorButton("toggle-3d", "Enable 3D Panel")
            .toggleable(true)
            .toggled(Impact3DPanelManager.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\uD83C\uDF10")
            .onToggle(v -> Impact3DPanelManager.INSTANCE.setEnabled(Boolean.TRUE.equals(v)));

        // Position buttons
        positionButtons.clear();
        for (Config.HudPosition pos : Config.HudPosition.values()) {
            EditorButton btn = new EditorButton("pos-" + pos.name(), formatPositionName(pos))
                .toggleable(true)
                .toggled(pos == currentPosition)
                .size(EditorButton.Size.SMALL)
                .onToggle(v -> {
                    if (Boolean.TRUE.equals(v)) {
                        currentPosition = pos;
                        Config.IMPACT_HUD_POSITION.set(pos);
                        updatePositionButtonStates();
                    }
                });
            positionButtons.add(btn);
        }

        // Offset buttons
        offsetXMinus = new EditorButton("ox-", "X-")
            .size(EditorButton.Size.SMALL)
            .onClick(() -> adjustOffset(true, -10));

        offsetXPlus = new EditorButton("ox+", "X+")
            .size(EditorButton.Size.SMALL)
            .onClick(() -> adjustOffset(true, 10));

        offsetYMinus = new EditorButton("oy-", "Y-")
            .size(EditorButton.Size.SMALL)
            .onClick(() -> adjustOffset(false, -10));

        offsetYPlus = new EditorButton("oy+", "Y+")
            .size(EditorButton.Size.SMALL)
            .onClick(() -> adjustOffset(false, 10));

        // Preset buttons
        exportPreset = new EditorButton("export", "Export")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .icon("\uD83D\uDCE4")
            .onClick(() -> {
                boolean success = ImpactHudPresets.exportToDefault();
                showStatus(success ? "Exported!" : "Export failed!");
            });

        importPreset = new EditorButton("import", "Import")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .icon("\uD83D\uDCE5")
            .onClick(() -> {
                boolean success = ImpactHudPresets.importFromDefault();
                showStatus(success ? "Imported!" : "Import failed!");
                syncButtonStates();
            });

        resetDefaults = new EditorButton("reset", "Reset Defaults")
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(this::resetToDefaults);
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

        ImpactHudOverlay.setEnabled(true);
        Impact3DPanelManager.INSTANCE.setEnabled(true);
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
