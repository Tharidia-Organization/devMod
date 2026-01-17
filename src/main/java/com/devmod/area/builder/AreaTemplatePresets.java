package com.devmod.area.builder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.resources.ResourceLocation;

import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaGenerationType;
import com.devmod.area.data.AreaOptions;
import com.devmod.area.data.AreaOptions.WallStyle;
import com.devmod.area.data.AreaShape;
import com.devmod.area.data.BiomeGenerationConfig;
import com.devmod.area.data.BiomeGenerationConfig.TerrainStyle;
import com.devmod.area.template.TemplateCategory;

/**
 * Predefined area template presets for quick area creation.
 * UX-03 fix: Provides ready-to-use configurations for common use cases.
 */
public final class AreaTemplatePresets {
    private AreaTemplatePresets() {}

    private static final Map<String, AreaTemplatePreset> PRESETS = new LinkedHashMap<>();

    static {
        initializePresets();
    }

    private static void initializePresets() {
        // ========================================================================
        // Arena Presets
        // ========================================================================

        PRESETS.put("arena_small", new AreaTemplatePreset(
            "arena_small",
            "Small Arena",
            "Compact PvP arena for 1v1 or 2v2 combat",
            TemplateCategory.ARENA,
            AreaShape.CIRCULAR,
            new AreaDimensions(32, 32, 16, 64),
            AreaGenerationType.CUSTOM,
            "industrial",
            AreaOptions.DEFAULT,
            null
        ));

        PRESETS.put("arena_medium", new AreaTemplatePreset(
            "arena_medium",
            "Medium Arena",
            "Standard arena for team battles (4v4)",
            TemplateCategory.ARENA,
            AreaShape.OCTAGONAL,
            new AreaDimensions(48, 48, 20, 64),
            AreaGenerationType.CUSTOM,
            "default",
            AreaOptions.DEFAULT,
            null
        ));

        PRESETS.put("arena_large", new AreaTemplatePreset(
            "arena_large",
            "Large Arena",
            "Grand arena for large-scale combat events",
            TemplateCategory.ARENA,
            AreaShape.CIRCULAR,
            new AreaDimensions(64, 64, 24, 64),
            AreaGenerationType.CUSTOM,
            "fantasy",
            AreaOptions.DEFAULT,
            null
        ));

        PRESETS.put("arena_boss", new AreaTemplatePreset(
            "arena_boss",
            "Boss Arena",
            "Large circular arena designed for boss encounters",
            TemplateCategory.ARENA,
            AreaShape.CIRCULAR,
            new AreaDimensions(80, 80, 32, 64),
            AreaGenerationType.CUSTOM,
            "nether",
            new AreaOptions(true, true, true, WallStyle.SOLID, true, false, null),
            null
        ));

        // ========================================================================
        // Dungeon Presets
        // ========================================================================

        PRESETS.put("dungeon_room", new AreaTemplatePreset(
            "dungeon_room",
            "Dungeon Room",
            "Single dungeon room with stone brick walls",
            TemplateCategory.DUNGEON,
            AreaShape.RECTANGULAR,
            new AreaDimensions(16, 16, 8, 64),
            AreaGenerationType.CUSTOM,
            "industrial",
            new AreaOptions(true, true, true, WallStyle.SOLID, false, false, null),
            null
        ));

        PRESETS.put("dungeon_corridor", new AreaTemplatePreset(
            "dungeon_corridor",
            "Dungeon Corridor",
            "Long narrow corridor for connecting rooms",
            TemplateCategory.DUNGEON,
            AreaShape.RECTANGULAR,
            new AreaDimensions(5, 32, 6, 64),
            AreaGenerationType.CUSTOM,
            "industrial",
            new AreaOptions(true, true, true, WallStyle.SOLID, false, false, null),
            null
        ));

        PRESETS.put("dungeon_cave", new AreaTemplatePreset(
            "dungeon_cave",
            "Cave Dungeon",
            "Natural cave-style dungeon with terrain generation",
            TemplateCategory.DUNGEON,
            AreaShape.CIRCULAR,
            new AreaDimensions(40, 40, 24, 32),
            AreaGenerationType.BIOME,
            "default",
            AreaOptions.DEFAULT,
            new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("dripstone_caves")),
                0L, // Random seed
                true, // Generate structures (caves)
                false, // No features
                true, // Ores
                32,
                TerrainStyle.NATURAL
            )
        ));

        // ========================================================================
        // Hub Presets
        // ========================================================================

        PRESETS.put("hub_spawn", new AreaTemplatePreset(
            "hub_spawn",
            "Spawn Hub",
            "Central spawn point with portals",
            TemplateCategory.HUB,
            AreaShape.OCTAGONAL,
            new AreaDimensions(32, 32, 12, 64),
            AreaGenerationType.CUSTOM,
            "default",
            new AreaOptions(true, true, true, WallStyle.SOLID, true, true, null),
            null
        ));

        PRESETS.put("hub_large", new AreaTemplatePreset(
            "hub_large",
            "Large Hub",
            "Expansive hub area for multiple destinations",
            TemplateCategory.HUB,
            AreaShape.HEXAGONAL,
            new AreaDimensions(64, 64, 20, 64),
            AreaGenerationType.CUSTOM,
            "tech",
            new AreaOptions(true, true, true, WallStyle.SOLID, true, true, null),
            null
        ));

        // ========================================================================
        // Landscape Presets
        // ========================================================================

        PRESETS.put("landscape_plains", new AreaTemplatePreset(
            "landscape_plains",
            "Plains Landscape",
            "Flat grassy plains with natural features",
            TemplateCategory.LANDSCAPE,
            AreaShape.RECTANGULAR,
            new AreaDimensions(64, 64, 32, 64),
            AreaGenerationType.BIOME,
            "nature",
            AreaOptions.DEFAULT,
            new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                0L,
                false,
                true, // Generate features (trees, grass)
                false,
                63,
                TerrainStyle.NATURAL
            )
        ));

        PRESETS.put("landscape_forest", new AreaTemplatePreset(
            "landscape_forest",
            "Forest Landscape",
            "Dense forest with trees and vegetation",
            TemplateCategory.LANDSCAPE,
            AreaShape.RECTANGULAR,
            new AreaDimensions(64, 64, 48, 64),
            AreaGenerationType.BIOME,
            "nature",
            AreaOptions.DEFAULT,
            new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("forest")),
                0L,
                false,
                true,
                false,
                63,
                TerrainStyle.NATURAL
            )
        ));

        PRESETS.put("landscape_desert", new AreaTemplatePreset(
            "landscape_desert",
            "Desert Landscape",
            "Sandy desert terrain",
            TemplateCategory.LANDSCAPE,
            AreaShape.RECTANGULAR,
            new AreaDimensions(64, 64, 32, 64),
            AreaGenerationType.BIOME,
            "default",
            AreaOptions.DEFAULT,
            new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("desert")),
                0L,
                false,
                true,
                false,
                63,
                TerrainStyle.NATURAL
            )
        ));

        PRESETS.put("landscape_floating", new AreaTemplatePreset(
            "landscape_floating",
            "Floating Islands",
            "Ethereal floating island terrain",
            TemplateCategory.LANDSCAPE,
            AreaShape.CIRCULAR,
            new AreaDimensions(48, 48, 64, 64),
            AreaGenerationType.BIOME,
            "end",
            AreaOptions.DEFAULT,
            new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                0L,
                false,
                true,
                false,
                0, // No sea level
                TerrainStyle.FLOATING
            )
        ));

        // ========================================================================
        // Housing Presets
        // ========================================================================

        PRESETS.put("housing_plot", new AreaTemplatePreset(
            "housing_plot",
            "Housing Plot",
            "Standard-sized player housing plot",
            TemplateCategory.HOUSING,
            AreaShape.RECTANGULAR,
            new AreaDimensions(16, 16, 16, 64),
            AreaGenerationType.CUSTOM,
            "minimal",
            new AreaOptions(true, true, false, WallStyle.SOLID, false, false, null),
            null
        ));

        PRESETS.put("housing_large", new AreaTemplatePreset(
            "housing_large",
            "Large Housing Plot",
            "Extended plot for larger builds",
            TemplateCategory.HOUSING,
            AreaShape.RECTANGULAR,
            new AreaDimensions(32, 32, 24, 64),
            AreaGenerationType.CUSTOM,
            "minimal",
            new AreaOptions(true, true, false, WallStyle.SOLID, false, false, null),
            null
        ));

        // ========================================================================
        // Utility Presets
        // ========================================================================

        PRESETS.put("utility_teleport", new AreaTemplatePreset(
            "utility_teleport",
            "Teleport Platform",
            "Small platform for teleportation points",
            TemplateCategory.UTILITY,
            AreaShape.CIRCULAR,
            new AreaDimensions(8, 8, 4, 64),
            AreaGenerationType.CUSTOM,
            "tech",
            new AreaOptions(true, true, false, WallStyle.SOLID, true, false, null),
            null
        ));

        PRESETS.put("utility_observation", new AreaTemplatePreset(
            "utility_observation",
            "Observation Deck",
            "Elevated platform for viewing areas",
            TemplateCategory.UTILITY,
            AreaShape.OCTAGONAL,
            new AreaDimensions(12, 12, 2, 100),
            AreaGenerationType.CUSTOM,
            "minimal",
            new AreaOptions(true, true, false, WallStyle.SOLID, false, false, null),
            null
        ));
    }

    /**
     * Gets a preset by ID.
     *
     * @param presetId The preset ID
     * @return The preset, or null if not found
     */
    @Nonnull
    public static AreaTemplatePreset getPreset(@Nonnull String presetId) {
        AreaTemplatePreset preset = PRESETS.get(presetId);
        if (preset == null) {
            throw new IllegalArgumentException("Unknown preset: " + presetId);
        }
        return preset;
    }

    /**
     * Checks if a preset exists.
     */
    public static boolean hasPreset(@Nonnull String presetId) {
        return PRESETS.containsKey(presetId);
    }

    /**
     * Gets all preset IDs.
     */
    @Nonnull
    public static List<String> getPresetIds() {
        return Objects.requireNonNull(List.copyOf(PRESETS.keySet()));
    }

    /**
     * Gets presets filtered by category.
     */
    @Nonnull
    public static List<AreaTemplatePreset> getPresetsByCategory(@Nonnull TemplateCategory category) {
        return Objects.requireNonNull(PRESETS.values().stream()
            .filter(p -> p.category() == category)
            .toList());
    }

    /**
     * Gets all presets as a list.
     */
    @Nonnull
    public static List<AreaTemplatePreset> getAllPresets() {
        return Objects.requireNonNull(List.copyOf(PRESETS.values()));
    }

    /**
     * A predefined area template configuration.
     *
     * @param id             Unique identifier
     * @param name           Display name
     * @param description    Description of the preset
     * @param category       Template category
     * @param shape          Area shape
     * @param dimensions     Default dimensions
     * @param generationType CUSTOM or BIOME
     * @param palettePreset  Palette preset ID
     * @param options        Area options
     * @param biomeConfig    Biome config (null for CUSTOM generation)
     */
    public record AreaTemplatePreset(
        @Nonnull String id,
        @Nonnull String name,
        @Nonnull String description,
        @Nonnull TemplateCategory category,
        @Nonnull AreaShape shape,
        @Nonnull AreaDimensions dimensions,
        @Nonnull AreaGenerationType generationType,
        @Nonnull String palettePreset,
        @Nonnull AreaOptions options,
        @javax.annotation.Nullable BiomeGenerationConfig biomeConfig
    ) {
        public AreaTemplatePreset {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(dimensions, "dimensions");
            Objects.requireNonNull(generationType, "generationType");
            Objects.requireNonNull(palettePreset, "palettePreset");
            Objects.requireNonNull(options, "options");
        }
    }
}
