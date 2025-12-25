package com.devmod.client.ui.testing.pages;

import com.devmod.config.Config;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.testing.VoxelLabTab;
import com.devmod.client.ui.testing.panel.CollapsiblePanel;
import com.devmod.client.ui.testing.panel.HeaderPanel;
import com.devmod.client.ui.testing.panel.SectionPanel;
import com.devmod.client.ui.testing.panel.SliderPanel;
import com.devmod.client.ui.testing.panel.SpacerPanel;
import com.devmod.client.ui.testing.panel.StatusPanel;

import static com.devmod.client.ui.testing.pages.PageUtils.safeGetBool;
import static com.devmod.client.ui.testing.pages.PageUtils.safeGetInt;

/**
 * Telemetry Page - Configuration for data collection and analytics.
 * Controls telemetry recording, export, and event tracking.
 */
public class TelemetryPage extends AbstractVoxelLabPage {

    // Master toggle
    private EditorButton telemetryMasterToggle;

    // Event type toggles
    private EditorButton hitsToggle;
    private EditorButton deathsToggle;
    private EditorButton spawnsToggle;

    public TelemetryPage() {
        super(VoxelLabTab.TELEMETRY);
    }

    @Override
    protected void buildPanels() {
        createButtons();

        // Header
        panelContainer.addPanel(new HeaderPanel("TELEMETRY"));

        // Master Controls
        panelContainer.addPanel(
            SectionPanel.builder("section-master", "Recording")
                .description("Master telemetry controls")
                .addButton(telemetryMasterToggle)
                .build()
        );

        // Event Types Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-events", "Event Types",
                SectionPanel.builder("section-events-content", "What to Record")
                    .addRow(hitsToggle, deathsToggle, spawnsToggle)
                    .build(),
                0xFF00AAFF)
        );

        // Tick Interval Slider
        panelContainer.addPanel(
            SliderPanel.of("slider-interval", "Tick Interval",
                () -> (double) safeGetInt(Config.TELEMETRY_TICK_INTERVAL, 20),
                v -> Config.TELEMETRY_TICK_INTERVAL.set(v.intValue()),
                1.0, 100.0, 1.0, "%.0f ticks")
        );

        // Status Panel
        panelContainer.addPanel(new SpacerPanel("spacer-status", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-telemetry")
                .addStatus("Recording", () -> safeGetBool(Config.TELEMETRY_ENABLED))
                .addStatus("Hits", () -> safeGetBool(Config.TELEMETRY_HITS_ENABLED))
                .addStatus("Deaths", () -> safeGetBool(Config.TELEMETRY_DEATHS_ENABLED))
                .addStatus("Spawns", () -> safeGetBool(Config.TELEMETRY_SPAWNS_ENABLED))
                .build()
        );
    }

    private void createButtons() {
        telemetryMasterToggle = new EditorButton("toggle-telemetry", "Enable Recording")
            .toggleable(true)
            .toggled(safeGetBool(Config.TELEMETRY_ENABLED))
            .style(EditorButton.Style.DANGER)
            .icon("\u25CF")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_TELEMETRY_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.TELEMETRY_ENABLED)));

        hitsToggle = new EditorButton("toggle-hits", "Hits")
            .toggleable(true)
            .toggled(safeGetBool(Config.TELEMETRY_HITS_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .icon("\u2694")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_TELEMETRY_HITS_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.TELEMETRY_HITS_ENABLED)));

        deathsToggle = new EditorButton("toggle-deaths", "Deaths")
            .toggleable(true)
            .toggled(safeGetBool(Config.TELEMETRY_DEATHS_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .icon("\u2620")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_TELEMETRY_DEATHS_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.TELEMETRY_DEATHS_ENABLED)));

        spawnsToggle = new EditorButton("toggle-spawns", "Spawns")
            .toggleable(true)
            .toggled(safeGetBool(Config.TELEMETRY_SPAWNS_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .icon("\u2605")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_TELEMETRY_SPAWNS_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.TELEMETRY_SPAWNS_ENABLED)));
    }

    @Override
    protected void onTick() {
        syncButtonStates();
    }

    private void syncButtonStates() {
        telemetryMasterToggle.toggled(safeGetBool(Config.TELEMETRY_ENABLED));
        hitsToggle.toggled(safeGetBool(Config.TELEMETRY_HITS_ENABLED));
        deathsToggle.toggled(safeGetBool(Config.TELEMETRY_DEATHS_ENABLED));
        spawnsToggle.toggled(safeGetBool(Config.TELEMETRY_SPAWNS_ENABLED));
    }

    private void invokeToggleAction(String actionId, boolean desired, boolean current) {
        if (desired == current) {
            return;
        }
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
    }

}
