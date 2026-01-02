package com.devmod.client.ui.testing.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.devmod.ModConfig;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.rendering.AggroRangeVisualizer;
import com.devmod.client.rendering.ChunkPerformanceVisualizer;
import com.devmod.client.rendering.DebugRenderer;
import com.devmod.client.rendering.HeatmapVisualizer;
import com.devmod.client.rendering.LightLevelOverlay;
import com.devmod.client.rendering.LineOfSightVisualizer;
import com.devmod.client.rendering.RoomBoundsVisualizer;
import com.devmod.client.rendering.SafeSpotVisualizer;
import com.devmod.client.rendering.SpawnabilityOverlay;
import com.devmod.client.rendering.VerticalLevelsVisualizer;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.testing.VoxelLabTab;
import com.devmod.client.ui.testing.panel.CollapsiblePanel;
import com.devmod.client.ui.testing.panel.GridPanel;
import com.devmod.client.ui.testing.panel.HeaderPanel;
import com.devmod.client.ui.testing.panel.SectionPanel;
import com.devmod.client.ui.testing.panel.SliderPanel;
import com.devmod.client.ui.testing.panel.SpacerPanel;
import com.devmod.client.ui.testing.panel.StatusPanel;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.rendering.HeatmapType;
import com.devmod.util.I18n;

public class DebugOverlaysPage extends AbstractVoxelLabPage {

    // Core toggles
    private EditorButton debugMasterToggle;
    private EditorButton lightLevelToggle;
    private EditorButton bodyPartBoxToggle;

    // Entity debug
    private EditorButton lineOfSightToggle;
    private EditorButton aggroRangeToggle;
    private EditorButton safeSpotToggle;

    // Spatial debug
    private EditorButton roomBoundsToggle;
    private EditorButton verticalLevelsToggle;
    private EditorButton spawnabilityToggle;
    private EditorButton chunkPerfToggle;

    // Heatmap toggles
    private final List<EditorButton> heatmapButtons = new ArrayList<>();

    public DebugOverlaysPage() {
        super(VoxelLabTab.DEBUG_OVERLAYS);
        Buttons buttons = createButtons();
        debugMasterToggle = buttons.debugMasterToggle;
        lightLevelToggle = buttons.lightLevelToggle;
        bodyPartBoxToggle = buttons.bodyPartBoxToggle;
        lineOfSightToggle = buttons.lineOfSightToggle;
        aggroRangeToggle = buttons.aggroRangeToggle;
        safeSpotToggle = buttons.safeSpotToggle;
        roomBoundsToggle = buttons.roomBoundsToggle;
        verticalLevelsToggle = buttons.verticalLevelsToggle;
        spawnabilityToggle = buttons.spawnabilityToggle;
        chunkPerfToggle = buttons.chunkPerfToggle;
        heatmapButtons.addAll(buttons.heatmapButtons);
    }

    @Override
    protected void buildPanels() {
        // Header
        panelContainer.addPanel(new HeaderPanel(
            I18n.translate("devmod.testing.voxel_lab.debug.header").getString()));

        // Core Overlays Section
        panelContainer.addPanel(
            SectionPanel.builder("section-core",
                I18n.translate("devmod.testing.voxel_lab.debug.section.core").getString())
                .description(I18n.translate("devmod.testing.voxel_lab.debug.section.core_desc").getString())
                .addButton(debugMasterToggle)
                .addRow(lightLevelToggle, bodyPartBoxToggle)
                .build()
        );

        // Entity Debug Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-entity",
                I18n.translate("devmod.testing.voxel_lab.debug.section.entity").getString(),
                SectionPanel.builder("section-entity-content",
                        I18n.translate("devmod.testing.voxel_lab.debug.section.entity_desc").getString())
                    .addRow(lineOfSightToggle, aggroRangeToggle)
                    .addButton(safeSpotToggle)
                    .build(),
                0xFF00AAFF)
        );

        // Spatial Analysis Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-spatial",
                I18n.translate("devmod.testing.voxel_lab.debug.section.spatial").getString(),
                SectionPanel.builder("section-spatial-content",
                        I18n.translate("devmod.testing.voxel_lab.debug.section.spatial_desc").getString())
                    .addRow(roomBoundsToggle, verticalLevelsToggle)
                    .addRow(spawnabilityToggle, chunkPerfToggle)
                    .build(),
                0xFFFFAA00)
        );

        // Heatmaps Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-heatmaps",
                I18n.translate("devmod.testing.voxel_lab.debug.section.heatmaps").getString(),
                GridPanel.of("grid-heatmaps",
                    I18n.translate("devmod.testing.voxel_lab.debug.heatmap.types").getString(),
                    heatmapButtons, 2),
                0xFFFF5500)
        );

        // Render Distance Slider
        panelContainer.addPanel(
            SliderPanel.of("slider-render-distance",
                I18n.translate("devmod.testing.voxel_lab.debug.slider.render_distance").getString(),
                () -> (double) SettingsManager.INSTANCE.getSettings().visualizers.getRenderDistance(),
                v -> SettingsManager.INSTANCE.getSettings().visualizers.setRenderDistance(v.intValue()),
                4.0, 128.0, 4.0, "%.0f")
        );

        // Status
        panelContainer.addPanel(new SpacerPanel("spacer-status", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-debug")
                .addStatus(I18n.translate("devmod.testing.voxel_lab.debug.status.master").getString(),
                    () -> DebugRenderer.INSTANCE.isEnabled())
                .addStatus(I18n.translate("devmod.testing.voxel_lab.debug.status.light").getString(),
                    () -> LightLevelOverlay.INSTANCE.isEnabled())
                .addStatus(I18n.translate("devmod.testing.voxel_lab.debug.status.los").getString(),
                    () -> LineOfSightVisualizer.INSTANCE.isEnabled())
                .addStatus(I18n.translate("devmod.testing.voxel_lab.debug.status.aggro").getString(),
                    () -> AggroRangeVisualizer.INSTANCE.isEnabled())
                .build()
        );
    }

    private Buttons createButtons() {
        // Core toggles
        EditorButton debugMasterToggle = new EditorButton("toggle-debug-master",
            I18n.translate("devmod.testing.voxel_lab.debug.toggle.master").getString())
            .toggleable(true)
            .toggled(DebugRenderer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2699")
            .hotkeyHint("[G]")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_OVERLAY_TOGGLE,
                Boolean.TRUE.equals(v), DebugRenderer.INSTANCE.isEnabled()));

        EditorButton lightLevelToggle = new EditorButton("toggle-light",
            I18n.translate("devmod.testing.voxel_lab.debug.toggle.light_levels").getString())
            .toggleable(true)
            .toggled(LightLevelOverlay.INSTANCE.isEnabled())
            .style(EditorButton.Style.SUCCESS)
            .icon("\u2600")
            .hotkeyHint("[L]")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE,
                Boolean.TRUE.equals(v), LightLevelOverlay.INSTANCE.isEnabled()));

        EditorButton bodyPartBoxToggle = new EditorButton("toggle-bodypart",
            I18n.translate("devmod.testing.voxel_lab.debug.toggle.body_part_boxes").getString())
            .toggleable(true)
            .toggled(ModConfig.showBodyPartBoxes)
            .style(EditorButton.Style.SUCCESS)
            .icon("\u25A1")
            .hotkeyHint("[Shift+G]")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_BODY_PARTS_TOGGLE,
                Boolean.TRUE.equals(v), ModConfig.showBodyPartBoxes));

        // Entity debug
        EditorButton lineOfSightToggle = new EditorButton("toggle-los",
            I18n.translate("devmod.testing.voxel_lab.debug.toggle.line_of_sight").getString())
            .toggleable(true)
            .toggled(LineOfSightVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2192")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_LOS_TOGGLE,
                Boolean.TRUE.equals(v), LineOfSightVisualizer.INSTANCE.isEnabled()));

        EditorButton aggroRangeToggle = new EditorButton("toggle-aggro",
            I18n.translate("devmod.testing.voxel_lab.debug.toggle.aggro_range").getString())
            .toggleable(true)
            .toggled(AggroRangeVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.DANGER)
            .icon("\u26A0")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_AGGRO_RANGE_TOGGLE,
                Boolean.TRUE.equals(v), AggroRangeVisualizer.INSTANCE.isEnabled()));

        EditorButton safeSpotToggle = new EditorButton("toggle-safe",
            I18n.translate("devmod.testing.voxel_lab.debug.toggle.safe_spots").getString())
            .toggleable(true)
            .toggled(SafeSpotVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.SUCCESS)
            .icon("\u2713")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_SAFE_SPOTS_TOGGLE,
                Boolean.TRUE.equals(v), SafeSpotVisualizer.INSTANCE.isEnabled()));

        // Spatial debug
        EditorButton roomBoundsToggle = new EditorButton("toggle-room",
            I18n.translate("devmod.testing.voxel_lab.debug.toggle.room_bounds").getString())
            .toggleable(true)
            .toggled(RoomBoundsVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u25A0")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE,
                Boolean.TRUE.equals(v), RoomBoundsVisualizer.INSTANCE.isEnabled()));

        EditorButton verticalLevelsToggle = new EditorButton("toggle-vertical",
            I18n.translate("devmod.testing.voxel_lab.debug.toggle.vertical_levels").getString())
            .toggleable(true)
            .toggled(VerticalLevelsVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2195")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE,
                Boolean.TRUE.equals(v), VerticalLevelsVisualizer.INSTANCE.isEnabled()));

        EditorButton spawnabilityToggle = new EditorButton("toggle-spawn",
            I18n.translate("devmod.testing.voxel_lab.debug.toggle.spawnability").getString())
            .toggleable(true)
            .toggled(SpawnabilityOverlay.INSTANCE.isEnabled())
            .style(EditorButton.Style.DANGER)
            .icon("\u2605")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_SPAWNABILITY_TOGGLE,
                Boolean.TRUE.equals(v), SpawnabilityOverlay.INSTANCE.isEnabled()));

        EditorButton chunkPerfToggle = new EditorButton("toggle-chunk",
            I18n.translate("devmod.testing.voxel_lab.debug.toggle.chunk_perf").getString())
            .toggleable(true)
            .toggled(ChunkPerformanceVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2593")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_CHUNK_PERF_TOGGLE,
                Boolean.TRUE.equals(v), ChunkPerformanceVisualizer.INSTANCE.isEnabled()));

        // Heatmap buttons
        List<EditorButton> heatmapButtons = new ArrayList<>();
        for (HeatmapType type : HeatmapType.values()) {
            EditorButton btn = new EditorButton("heatmap-" + type.name().toLowerCase(Locale.ROOT), heatmapLabel(type))
                .toggleable(true)
                .toggled(HeatmapVisualizer.INSTANCE.isEnabled(type))
                .style(EditorButton.Style.GHOST)
                .size(EditorButton.Size.SMALL)
                .onToggle(v -> invokeHeatmapAction(type, Boolean.TRUE.equals(v)));
            heatmapButtons.add(btn);
        }

        return new Buttons(
            debugMasterToggle,
            lightLevelToggle,
            bodyPartBoxToggle,
            lineOfSightToggle,
            aggroRangeToggle,
            safeSpotToggle,
            roomBoundsToggle,
            verticalLevelsToggle,
            spawnabilityToggle,
            chunkPerfToggle,
            heatmapButtons
        );
    }

    private static final class Buttons {
        private final EditorButton debugMasterToggle;
        private final EditorButton lightLevelToggle;
        private final EditorButton bodyPartBoxToggle;
        private final EditorButton lineOfSightToggle;
        private final EditorButton aggroRangeToggle;
        private final EditorButton safeSpotToggle;
        private final EditorButton roomBoundsToggle;
        private final EditorButton verticalLevelsToggle;
        private final EditorButton spawnabilityToggle;
        private final EditorButton chunkPerfToggle;
        private final List<EditorButton> heatmapButtons;

        private Buttons(
            EditorButton debugMasterToggle,
            EditorButton lightLevelToggle,
            EditorButton bodyPartBoxToggle,
            EditorButton lineOfSightToggle,
            EditorButton aggroRangeToggle,
            EditorButton safeSpotToggle,
            EditorButton roomBoundsToggle,
            EditorButton verticalLevelsToggle,
            EditorButton spawnabilityToggle,
            EditorButton chunkPerfToggle,
            List<EditorButton> heatmapButtons
        ) {
            this.debugMasterToggle = debugMasterToggle;
            this.lightLevelToggle = lightLevelToggle;
            this.bodyPartBoxToggle = bodyPartBoxToggle;
            this.lineOfSightToggle = lineOfSightToggle;
            this.aggroRangeToggle = aggroRangeToggle;
            this.safeSpotToggle = safeSpotToggle;
            this.roomBoundsToggle = roomBoundsToggle;
            this.verticalLevelsToggle = verticalLevelsToggle;
            this.spawnabilityToggle = spawnabilityToggle;
            this.chunkPerfToggle = chunkPerfToggle;
            this.heatmapButtons = heatmapButtons;
        }
    }

    @Override
    protected void onTick() {
        syncButtonStates();
    }

    private void syncButtonStates() {
        debugMasterToggle.toggled(DebugRenderer.INSTANCE.isEnabled());
        lightLevelToggle.toggled(LightLevelOverlay.INSTANCE.isEnabled());
        bodyPartBoxToggle.toggled(ModConfig.showBodyPartBoxes);
        lineOfSightToggle.toggled(LineOfSightVisualizer.INSTANCE.isEnabled());
        aggroRangeToggle.toggled(AggroRangeVisualizer.INSTANCE.isEnabled());
        safeSpotToggle.toggled(SafeSpotVisualizer.INSTANCE.isEnabled());
        roomBoundsToggle.toggled(RoomBoundsVisualizer.INSTANCE.isEnabled());
        verticalLevelsToggle.toggled(VerticalLevelsVisualizer.INSTANCE.isEnabled());
        spawnabilityToggle.toggled(SpawnabilityOverlay.INSTANCE.isEnabled());
        chunkPerfToggle.toggled(ChunkPerformanceVisualizer.INSTANCE.isEnabled());

        // Sync heatmap buttons
        int i = 0;
        for (HeatmapType type : HeatmapType.values()) {
            if (i < heatmapButtons.size()) {
                heatmapButtons.get(i).toggled(HeatmapVisualizer.INSTANCE.isEnabled(type));
            }
            i++;
        }
    }

    private void invokeToggleAction(String actionId, boolean desired, boolean current) {
        if (desired == current) {
            return;
        }
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
    }

    private void invokeHeatmapAction(HeatmapType type, boolean desired) {
        String actionId = resolveHeatmapAction(type);
        if (actionId == null) {
            return;
        }
        boolean current = HeatmapVisualizer.INSTANCE.isEnabled(type);
        if (desired == current) {
            return;
        }
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
    }

    private static String resolveHeatmapAction(HeatmapType type) {
        return switch (type) {
            case DEATH -> ActionIds.DEBUG_HEATMAP_DEATH_TOGGLE;
            case MOVEMENT -> ActionIds.DEBUG_HEATMAP_MOVEMENT_TOGGLE;
            case CAMPING -> ActionIds.DEBUG_HEATMAP_CAMPING_TOGGLE;
            case STUCK -> ActionIds.DEBUG_HEATMAP_STUCK_TOGGLE;
            case AGGRO_DROP -> ActionIds.DEBUG_HEATMAP_AGGRO_DROP_TOGGLE;
            case KITING -> ActionIds.DEBUG_HEATMAP_KITING_TOGGLE;
            case LIGHT_SPAWNABLE -> ActionIds.DEBUG_HEATMAP_LIGHT_SPAWNABLE_TOGGLE;
            case LIGHT_DARK -> ActionIds.DEBUG_HEATMAP_LIGHT_DARK_TOGGLE;
        };
    }

    private static String heatmapLabel(HeatmapType type) {
        String key = switch (type) {
            case DEATH -> "death";
            case MOVEMENT -> "movement";
            case CAMPING -> "camping";
            case STUCK -> "stuck";
            case AGGRO_DROP -> "aggro_drop";
            case KITING -> "kiting";
            case LIGHT_SPAWNABLE -> "light_spawnable";
            case LIGHT_DARK -> "light_dark";
        };
        return I18n.translate("devmod.testing.voxel_lab.debug.heatmap." + key).getString();
    }
}
