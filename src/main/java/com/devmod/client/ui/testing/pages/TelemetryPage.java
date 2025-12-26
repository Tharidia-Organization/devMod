package com.devmod.client.ui.testing.pages;

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
import com.devmod.config.Config;
import com.devmod.util.I18n;

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
        panelContainer.addPanel(new HeaderPanel(
            I18n.translate("devmod.testing.voxel_lab.telemetry.header").getString()));

        // Master Controls
        panelContainer.addPanel(
            SectionPanel.builder("section-master",
                I18n.translate("devmod.testing.voxel_lab.telemetry.label.recording").getString())
                .description(I18n.translate("devmod.testing.voxel_lab.telemetry.section.recording_desc").getString())
                .addButton(telemetryMasterToggle)
                .build()
        );

        // Event Types Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-events",
                I18n.translate("devmod.testing.voxel_lab.telemetry.section.event_types").getString(),
                SectionPanel.builder("section-events-content",
                        I18n.translate("devmod.testing.voxel_lab.telemetry.section.what_to_record").getString())
                    .addRow(hitsToggle, deathsToggle, spawnsToggle)
                    .build(),
                0xFF00AAFF)
        );

        // Tick Interval Slider
        panelContainer.addPanel(
            SliderPanel.of("slider-interval",
                I18n.translate("devmod.testing.voxel_lab.telemetry.slider.tick_interval").getString(),
                () -> (double) safeGetInt(Config.TELEMETRY_TICK_INTERVAL, 20),
                v -> Config.TELEMETRY_TICK_INTERVAL.set(v.intValue()),
                1.0, 100.0, 1.0,
                I18n.translate("devmod.testing.voxel_lab.format.ticks").getString())
        );

        // Status Panel
        panelContainer.addPanel(new SpacerPanel("spacer-status", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-telemetry")
                .addStatus(I18n.translate("devmod.testing.voxel_lab.telemetry.label.recording").getString(),
                    () -> safeGetBool(Config.TELEMETRY_ENABLED))
                .addStatus(I18n.translate("devmod.testing.voxel_lab.telemetry.label.hits").getString(),
                    () -> safeGetBool(Config.TELEMETRY_HITS_ENABLED))
                .addStatus(I18n.translate("devmod.testing.voxel_lab.telemetry.label.deaths").getString(),
                    () -> safeGetBool(Config.TELEMETRY_DEATHS_ENABLED))
                .addStatus(I18n.translate("devmod.testing.voxel_lab.telemetry.label.spawns").getString(),
                    () -> safeGetBool(Config.TELEMETRY_SPAWNS_ENABLED))
                .build()
        );
    }

    private void createButtons() {
        telemetryMasterToggle = new EditorButton("toggle-telemetry",
            I18n.translate("devmod.testing.voxel_lab.telemetry.toggle.recording").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.TELEMETRY_ENABLED))
            .style(EditorButton.Style.DANGER)
            .icon("\u25CF")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_TELEMETRY_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.TELEMETRY_ENABLED)));

        hitsToggle = new EditorButton("toggle-hits",
            I18n.translate("devmod.testing.voxel_lab.telemetry.label.hits").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.TELEMETRY_HITS_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .icon("\u2694")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_TELEMETRY_HITS_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.TELEMETRY_HITS_ENABLED)));

        deathsToggle = new EditorButton("toggle-deaths",
            I18n.translate("devmod.testing.voxel_lab.telemetry.label.deaths").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.TELEMETRY_DEATHS_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .icon("\u2620")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_TELEMETRY_DEATHS_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.TELEMETRY_DEATHS_ENABLED)));

        spawnsToggle = new EditorButton("toggle-spawns",
            I18n.translate("devmod.testing.voxel_lab.telemetry.label.spawns").getString())
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
