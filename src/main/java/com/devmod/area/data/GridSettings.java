package com.devmod.area.data;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Grid snap settings for area positioning and dimensions.
 * Used to align areas to a world grid for Plot Systems and organized layouts.
 */
public record GridSettings(
    boolean enabled,
    @Nonnull GridSize size,
    boolean snapPosition,
    boolean snapDimensions,
    boolean showGridLines,
    @Nonnull GridAlignment alignment
) {
    /**
     * Predefined grid sizes.
     */
    public enum GridSize {
        SMALL(8, "small"),
        MEDIUM(16, "medium"),
        LARGE(32, "large"),
        CUSTOM(16, "custom"); // Default for custom

        private final int defaultBlocks;
        private final String id;

        GridSize(int defaultBlocks, String id) {
            this.defaultBlocks = defaultBlocks;
            this.id = id;
        }

        public int getDefaultBlocks() {
            return defaultBlocks;
        }

        public String getId() {
            return id;
        }

        public static GridSize fromId(String id) {
            for (GridSize size : values()) {
                if (size.id.equals(id)) {
                    return size;
                }
            }
            return MEDIUM;
        }

        public static final Codec<GridSize> CODEC = Codec.STRING.xmap(
            GridSize::fromId,
            GridSize::getId
        );
    }

    /**
     * Grid alignment mode.
     */
    public enum GridAlignment {
        CENTER("center"),
        CORNER("corner");

        private final String id;

        GridAlignment(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static GridAlignment fromId(String id) {
            for (GridAlignment alignment : values()) {
                if (alignment.id.equals(id)) {
                    return alignment;
                }
            }
            return CENTER;
        }

        public static final Codec<GridAlignment> CODEC = Codec.STRING.xmap(
            GridAlignment::fromId,
            GridAlignment::getId
        );
    }

    /**
     * Default disabled grid settings.
     */
    public static final GridSettings DISABLED = new GridSettings(
        false, GridSize.MEDIUM, false, false, false, GridAlignment.CENTER
    );

    /**
     * Default enabled grid settings for plot system.
     */
    public static final GridSettings PLOT_DEFAULT = new GridSettings(
        true, GridSize.MEDIUM, true, true, true, GridAlignment.CORNER
    );

    public static final Codec<GridSettings> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.BOOL.fieldOf("enabled").forGetter(GridSettings::enabled),
            GridSize.CODEC.fieldOf("size").forGetter(GridSettings::size),
            Codec.BOOL.fieldOf("snapPosition").forGetter(GridSettings::snapPosition),
            Codec.BOOL.fieldOf("snapDimensions").forGetter(GridSettings::snapDimensions),
            Codec.BOOL.fieldOf("showGridLines").forGetter(GridSettings::showGridLines),
            GridAlignment.CODEC.fieldOf("alignment").forGetter(GridSettings::alignment)
        ).apply(instance, GridSettings::new)
    );

    // Custom grid size for CUSTOM mode
    private static int customGridSize = 16;

    /**
     * Gets the effective grid size in blocks.
     */
    public int getGridSizeBlocks() {
        if (size == GridSize.CUSTOM) {
            return customGridSize;
        }
        return size.getDefaultBlocks();
    }

    /**
     * Sets the custom grid size (only used when size is CUSTOM).
     */
    public static void setCustomGridSize(int blocks) {
        customGridSize = Math.max(1, Math.min(128, blocks));
    }

    /**
     * Gets the current custom grid size.
     */
    public static int getCustomGridSize() {
        return customGridSize;
    }

    /**
     * Snaps a value to the grid.
     *
     * @param value The value to snap
     * @return The snapped value
     */
    public int snap(int value) {
        if (!enabled) {
            return value;
        }
        int gridSize = getGridSizeBlocks();
        return Math.round(value / (float) gridSize) * gridSize;
    }

    /**
     * Snaps a position coordinate to the grid.
     *
     * @param value The coordinate to snap
     * @return The snapped coordinate
     */
    public int snapPosition(int value) {
        if (!enabled || !snapPosition) {
            return value;
        }
        int gridSize = getGridSizeBlocks();
        if (alignment == GridAlignment.CENTER) {
            // Snap to grid center
            return Math.round(value / (float) gridSize) * gridSize + gridSize / 2;
        } else {
            // Snap to grid corner
            return Math.round(value / (float) gridSize) * gridSize;
        }
    }

    /**
     * Snaps a dimension value to the grid.
     *
     * @param value The dimension to snap
     * @param min   Minimum allowed value
     * @param max   Maximum allowed value
     * @return The snapped dimension
     */
    public int snapDimension(int value, int min, int max) {
        if (!enabled || !snapDimensions) {
            return value;
        }
        int gridSize = getGridSizeBlocks();
        int snapped = Math.round(value / (float) gridSize) * gridSize;
        // Ensure at least one grid cell
        if (snapped < gridSize) {
            snapped = gridSize;
        }
        return Math.max(min, Math.min(max, snapped));
    }

    /**
     * Creates a copy with enabled state changed.
     */
    public GridSettings withEnabled(boolean enabled) {
        return new GridSettings(enabled, size, snapPosition, snapDimensions, showGridLines, alignment);
    }

    /**
     * Creates a copy with grid size changed.
     */
    public GridSettings withSize(@Nonnull GridSize size) {
        return new GridSettings(enabled, Objects.requireNonNull(size), snapPosition, snapDimensions, showGridLines, alignment);
    }

    /**
     * Creates a copy with snap position changed.
     */
    public GridSettings withSnapPosition(boolean snapPosition) {
        return new GridSettings(enabled, size, snapPosition, snapDimensions, showGridLines, alignment);
    }

    /**
     * Creates a copy with snap dimensions changed.
     */
    public GridSettings withSnapDimensions(boolean snapDimensions) {
        return new GridSettings(enabled, size, snapPosition, snapDimensions, showGridLines, alignment);
    }

    /**
     * Creates a copy with show grid lines changed.
     */
    public GridSettings withShowGridLines(boolean showGridLines) {
        return new GridSettings(enabled, size, snapPosition, snapDimensions, showGridLines, alignment);
    }

    /**
     * Creates a copy with alignment changed.
     */
    public GridSettings withAlignment(@Nonnull GridAlignment alignment) {
        return new GridSettings(enabled, size, snapPosition, snapDimensions, showGridLines, Objects.requireNonNull(alignment));
    }
}
