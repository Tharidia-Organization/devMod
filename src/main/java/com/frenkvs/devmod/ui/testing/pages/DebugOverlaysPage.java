package com.frenkvs.devmod.ui.testing.pages;

import com.frenkvs.devmod.Config;
import com.frenkvs.devmod.rendering.*;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.testing.VoxelLabTab;
import com.frenkvs.devmod.ui.testing.panel.*;
import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;

import java.util.ArrayList;
import java.util.List;

import static com.frenkvs.devmod.ui.testing.pages.PageUtils.safeGetBool;

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
            .onToggle(v -> {
                if (Boolean.TRUE.equals(v)) {
                    DebugRenderer.INSTANCE.toggle();
                    if (!DebugRenderer.INSTANCE.isEnabled()) {
                        DebugRenderer.INSTANCE.toggle();
                    }
                } else {
                    if (DebugRenderer.INSTANCE.isEnabled()) {
                        DebugRenderer.INSTANCE.toggle();
                    }
                }
            });

        lightLevelToggle = new EditorButton("toggle-light", "Light Levels")
            .toggleable(true)
            .toggled(LightLevelOverlay.INSTANCE.isEnabled())
            .style(EditorButton.Style.SUCCESS)
            .icon("\u2600")
            .hotkeyHint("[L]")
            .onToggle(v -> LightLevelOverlay.INSTANCE.setEnabled(Boolean.TRUE.equals(v)));

        bodyPartBoxToggle = new EditorButton("toggle-bodypart", "Body Part Boxes")
            .toggleable(true)
            .toggled(safeGetBool(Config.SHOW_BODY_PART_BOXES))
            .style(EditorButton.Style.SUCCESS)
            .icon("\u25A1")
            .hotkeyHint("[Shift+G]")
            .onToggle(v -> Config.SHOW_BODY_PART_BOXES.set(Boolean.TRUE.equals(v)));

        // Entity debug
        lineOfSightToggle = new EditorButton("toggle-los", "Line of Sight")
            .toggleable(true)
            .toggled(LineOfSightVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2192")
            .onToggle(v -> LineOfSightVisualizer.INSTANCE.setEnabled(Boolean.TRUE.equals(v)));

        aggroRangeToggle = new EditorButton("toggle-aggro", "Aggro Range")
            .toggleable(true)
            .toggled(AggroRangeVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.DANGER)
            .icon("\u26A0")
            .onToggle(v -> AggroRangeVisualizer.INSTANCE.setEnabled(Boolean.TRUE.equals(v)));

        safeSpotToggle = new EditorButton("toggle-safe", "Safe Spots")
            .toggleable(true)
            .toggled(SafeSpotVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.SUCCESS)
            .icon("\u2713")
            .onToggle(v -> SafeSpotVisualizer.INSTANCE.setEnabled(Boolean.TRUE.equals(v)));

        // Spatial debug
        roomBoundsToggle = new EditorButton("toggle-room", "Room Bounds")
            .toggleable(true)
            .toggled(RoomBoundsVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u25A0")
            .onToggle(v -> RoomBoundsVisualizer.INSTANCE.setEnabled(Boolean.TRUE.equals(v)));

        verticalLevelsToggle = new EditorButton("toggle-vertical", "Vertical Levels")
            .toggleable(true)
            .toggled(VerticalLevelsVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2195")
            .onToggle(v -> VerticalLevelsVisualizer.INSTANCE.setEnabled(Boolean.TRUE.equals(v)));

        spawnabilityToggle = new EditorButton("toggle-spawn", "Spawnability")
            .toggleable(true)
            .toggled(SpawnabilityOverlay.INSTANCE.isEnabled())
            .style(EditorButton.Style.DANGER)
            .icon("\u2605")
            .onToggle(v -> SpawnabilityOverlay.INSTANCE.setEnabled(Boolean.TRUE.equals(v)));

        chunkPerfToggle = new EditorButton("toggle-chunk", "Chunk Perf")
            .toggleable(true)
            .toggled(ChunkPerformanceVisualizer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2593")
            .onToggle(v -> ChunkPerformanceVisualizer.INSTANCE.setEnabled(Boolean.TRUE.equals(v)));

        // Heatmap buttons
        heatmapButtons.clear();
        for (HeatmapVisualizer.HeatmapType type : HeatmapVisualizer.HeatmapType.values()) {
            EditorButton btn = new EditorButton("heatmap-" + type.name().toLowerCase(), type.name())
                .toggleable(true)
                .toggled(HeatmapVisualizer.INSTANCE.isEnabled(type))
                .style(EditorButton.Style.GHOST)
                .size(EditorButton.Size.SMALL)
                .onToggle(v -> HeatmapVisualizer.INSTANCE.setEnabled(type, Boolean.TRUE.equals(v)));
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
        bodyPartBoxToggle.toggled(safeGetBool(Config.SHOW_BODY_PART_BOXES));
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
}
