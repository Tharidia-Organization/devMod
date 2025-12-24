package com.devmod.ui.testing.pages;

import com.devmod.ModConfig;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.rendering.*;
import com.devmod.ui.editor.components.EditorButton;
import com.devmod.ui.testing.VoxelLabTab;
import com.devmod.ui.testing.panel.*;
import com.devmod.ui.unified.persistence.SettingsManager;

import java.util.ArrayList;
import java.util.List;


/**
 * Debug Overlays Page - Configuration for all debug visualizers.
 * Controls 15+ debug rendering systems.
 */
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
    }

    @Override
    protected void buildPanels() {
        createButtons();

        // Header
        panelContainer.addPanel(new HeaderPanel("DEBUG OVERLAYS"));

        // Core Overlays Section
        panelContainer.addPanel(
            SectionPanel.builder("section-core", "Core Overlays")
                .description("Main debug rendering systems")
                .addButton(debugMasterToggle)
                .addRow(lightLevelToggle, bodyPartBoxToggle)
                .build()
        );

        // Entity Debug Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-entity", "Entity Debug",
                SectionPanel.builder("section-entity-content", "Entity Analysis")
                    .addRow(lineOfSightToggle, aggroRangeToggle)
                    .addButton(safeSpotToggle)
                    .build(),
                0xFF00AAFF)
        );

        // Spatial Analysis Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-spatial", "Spatial Analysis",
                SectionPanel.builder("section-spatial-content", "World Analysis")
                    .addRow(roomBoundsToggle, verticalLevelsToggle)
                    .addRow(spawnabilityToggle, chunkPerfToggle)
                    .build(),
                0xFFFFAA00)
        );

        // Heatmaps Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-heatmaps", "Heatmaps",
                GridPanel.of("grid-heatmaps", "Heatmap Types", heatmapButtons, 2),
                0xFFFF5500)
        );

        // Render Distance Slider
        panelContainer.addPanel(
            SliderPanel.of("slider-render-distance", "Render Distance (blocks)",
                () -> (double) SettingsManager.INSTANCE.getSettings().visualizers.getRenderDistance(),
                v -> SettingsManager.INSTANCE.getSettings().visualizers.setRenderDistance(v.intValue()),
                4.0, 128.0, 4.0, "%.0f")
        );

        // Status
        panelContainer.addPanel(new SpacerPanel("spacer-status", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-debug")
                .addStatus("Master", () -> DebugRenderer.INSTANCE.isEnabled())
                .addStatus("Light", () -> LightLevelOverlay.INSTANCE.isEnabled())
                .addStatus("LoS", () -> LineOfSightVisualizer.INSTANCE.isEnabled())
                .addStatus("Aggro", () -> AggroRangeVisualizer.INSTANCE.isEnabled())
                .build()
        );
    }

    private void createButtons() {
        // Core toggles
        debugMasterToggle = new EditorButton("toggle-debug-master", "Debug Master")
            .toggleable(true)
            .toggled(DebugRenderer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2699")
            .hotkeyHint("[G]")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_OVERLAY_TOGGLE,
                Boolean.TRUE.equals(v), DebugRenderer.INSTANCE.isEnabled()));

        lightLevelToggle = new EditorButton("toggle-light", "Light Levels")
            .toggleable(true)
            .toggled(LightLevelOverlay.INSTANCE.isEnabled())
            .style(EditorButton.Style.SUCCESS)
            .icon("\u2600")
            .hotkeyHint("[L]")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE,
                Boolean.TRUE.equals(v), LightLevelOverlay.INSTANCE.isEnabled()));

        bodyPartBoxToggle = new EditorButton("toggle-bodypart", "Body Part Boxes")
            .toggleable(true)
            .toggled(ModConfig.showBodyPartBoxes)
            .style(EditorButton.Style.SUCCESS)
            .icon("\u25A1")
            .hotkeyHint("[Shift+G]")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_BODY_PARTS_TOGGLE,
                Boolean.TRUE.equals(v), ModConfig.showBodyPartBoxes));

        // Entity debug
        lineOfSightToggle = new EditorButton("toggle-los", "Line of Sight")
            .toggleable(true)
            .toggled(LineOfSightVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2192")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_LOS_TOGGLE,
                Boolean.TRUE.equals(v), LineOfSightVisualizer.INSTANCE.isEnabled()));

        aggroRangeToggle = new EditorButton("toggle-aggro", "Aggro Range")
            .toggleable(true)
            .toggled(AggroRangeVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.DANGER)
            .icon("\u26A0")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_AGGRO_RANGE_TOGGLE,
                Boolean.TRUE.equals(v), AggroRangeVisualizer.INSTANCE.isEnabled()));

        safeSpotToggle = new EditorButton("toggle-safe", "Safe Spots")
            .toggleable(true)
            .toggled(SafeSpotVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.SUCCESS)
            .icon("\u2713")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_SAFE_SPOTS_TOGGLE,
                Boolean.TRUE.equals(v), SafeSpotVisualizer.INSTANCE.isEnabled()));

        // Spatial debug
        roomBoundsToggle = new EditorButton("toggle-room", "Room Bounds")
            .toggleable(true)
            .toggled(RoomBoundsVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u25A0")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE,
                Boolean.TRUE.equals(v), RoomBoundsVisualizer.INSTANCE.isEnabled()));

        verticalLevelsToggle = new EditorButton("toggle-vertical", "Vertical Levels")
            .toggleable(true)
            .toggled(VerticalLevelsVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2195")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE,
                Boolean.TRUE.equals(v), VerticalLevelsVisualizer.INSTANCE.isEnabled()));

        spawnabilityToggle = new EditorButton("toggle-spawn", "Spawnability")
            .toggleable(true)
            .toggled(SpawnabilityOverlay.INSTANCE.isEnabled())
            .style(EditorButton.Style.DANGER)
            .icon("\u2605")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_SPAWNABILITY_TOGGLE,
                Boolean.TRUE.equals(v), SpawnabilityOverlay.INSTANCE.isEnabled()));

        chunkPerfToggle = new EditorButton("toggle-chunk", "Chunk Perf")
            .toggleable(true)
            .toggled(ChunkPerformanceVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2593")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_CHUNK_PERF_TOGGLE,
                Boolean.TRUE.equals(v), ChunkPerformanceVisualizer.INSTANCE.isEnabled()));

        // Heatmap buttons
        heatmapButtons.clear();
        for (HeatmapVisualizer.HeatmapType type : HeatmapVisualizer.HeatmapType.values()) {
            EditorButton btn = new EditorButton("heatmap-" + type.name().toLowerCase(), type.name())
                .toggleable(true)
                .toggled(HeatmapVisualizer.INSTANCE.isEnabled(type))
                .style(EditorButton.Style.GHOST)
                .size(EditorButton.Size.SMALL)
                .onToggle(v -> invokeHeatmapAction(type, Boolean.TRUE.equals(v)));
            heatmapButtons.add(btn);
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
        for (HeatmapVisualizer.HeatmapType type : HeatmapVisualizer.HeatmapType.values()) {
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

    private void invokeHeatmapAction(HeatmapVisualizer.HeatmapType type, boolean desired) {
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

    private static String resolveHeatmapAction(HeatmapVisualizer.HeatmapType type) {
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
}
