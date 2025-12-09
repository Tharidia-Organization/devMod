package com.frenkvs.devmod.ui.hub;

import com.frenkvs.devmod.rendering.DebugRenderer;
import com.frenkvs.devmod.rendering.HeatmapVisualizer;
import com.frenkvs.devmod.rendering.LightLevelOverlay;
import com.frenkvs.devmod.rendering.LineOfSightVisualizer;
import com.frenkvs.devmod.rendering.PathfindingDebugger;
import com.frenkvs.devmod.rendering.RoomBoundsVisualizer;
import com.frenkvs.devmod.rendering.SafeSpotVisualizer;
import com.frenkvs.devmod.rendering.VerticalLevelsVisualizer;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Enum che rappresenta i tool/overlay disponibili nel TestingHub.
 * Ogni tool ha un hotkey, un metodo per verificare se è attivo,
 * e un metodo per togglarlo.
 */
public enum ToolType {
    DEBUG("Debug Overlay", "G",
        () -> DebugRenderer.INSTANCE.isEnabled(),
        enabled -> { if (enabled) DebugRenderer.INSTANCE.enable(); else DebugRenderer.INSTANCE.disable(); }),

    LIGHT_LEVEL("Light Levels", "L",
        () -> LightLevelOverlay.INSTANCE.isEnabled(),
        enabled -> LightLevelOverlay.INSTANCE.setEnabled(enabled)),

    HEATMAP("Heatmap", "H",
        () -> HeatmapVisualizer.INSTANCE.hasActiveHeatmaps(),
        enabled -> {
            for (HeatmapVisualizer.HeatmapType type : HeatmapVisualizer.HeatmapType.values()) {
                HeatmapVisualizer.INSTANCE.setEnabled(type, enabled);
            }
        }),

    ROOM_BOUNDS("Room Bounds", "R",
        () -> RoomBoundsVisualizer.INSTANCE.isEnabled(),
        enabled -> RoomBoundsVisualizer.INSTANCE.setEnabled(enabled)),

    PATHFINDING("Pathfinding", "P",
        () -> PathfindingDebugger.INSTANCE.isEnabled(),
        enabled -> PathfindingDebugger.INSTANCE.setEnabled(enabled)),

    LINE_OF_SIGHT("Line of Sight", "V",
        () -> LineOfSightVisualizer.INSTANCE.isEnabled(),
        enabled -> LineOfSightVisualizer.INSTANCE.setEnabled(enabled)),

    VERTICAL_LEVELS("Vertical Levels", "Y",
        () -> VerticalLevelsVisualizer.INSTANCE.isEnabled(),
        enabled -> VerticalLevelsVisualizer.INSTANCE.setEnabled(enabled)),

    SAFE_SPOTS("Safe Spots", "C",
        () -> SafeSpotVisualizer.INSTANCE.isEnabled(),
        enabled -> SafeSpotVisualizer.INSTANCE.setEnabled(enabled));

    private final String label;
    private final String hotkey;
    private final Supplier<Boolean> isEnabledSupplier;
    private final Consumer<Boolean> setEnabledConsumer;

    ToolType(String label, String hotkey,
             Supplier<Boolean> isEnabledSupplier,
             Consumer<Boolean> setEnabledConsumer) {
        this.label = label;
        this.hotkey = hotkey;
        this.isEnabledSupplier = isEnabledSupplier;
        this.setEnabledConsumer = setEnabledConsumer;
    }

    public String getLabel() {
        return label;
    }

    public String getHotkey() {
        return hotkey;
    }

    public boolean isEnabled() {
        return isEnabledSupplier.get();
    }

    public void setEnabled(boolean enabled) {
        setEnabledConsumer.accept(enabled);
    }

    public void toggle() {
        setEnabled(!isEnabled());
    }
}
