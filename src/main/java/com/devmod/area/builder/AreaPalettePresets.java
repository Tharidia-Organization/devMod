package com.devmod.area.builder;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;

import com.devmod.area.data.AreaPalette;
import com.devmod.runtime.NexusPalette;

/**
 * Predefined palette presets for area construction.
 * Each preset maps all NexusPalette.Key IDs to block registry names.
 */
public final class AreaPalettePresets {
    private AreaPalettePresets() {}

    private static final Map<String, Map<String, String>> PRESETS = new LinkedHashMap<>();

    static {
        initializePresets();
    }

    private static void initializePresets() {
        // DEFAULT - Current Nexus style (Deepslate, Copper)
        PRESETS.put("default", createDefaultPreset());

        // INDUSTRIAL - Iron, Blackstone
        PRESETS.put("industrial", createIndustrialPreset());

        // FANTASY - Amethyst, Prismarine
        PRESETS.put("fantasy", createFantasyPreset());

        // MINIMAL - Quartz, White Concrete
        PRESETS.put("minimal", createMinimalPreset());

        // NETHER - Blackstone, Magma
        PRESETS.put("nether", createNetherPreset());

        // END - Purpur, End Stone
        PRESETS.put("end", createEndPreset());

        // NATURE - Moss, Oak Wood
        PRESETS.put("nature", createNaturePreset());

        // TECH - Copper, Light Blue Glass
        PRESETS.put("tech", createTechPreset());
    }

    /**
     * Gets the materials map for a preset ID.
     *
     * @param presetId The preset ID
     * @return The materials map, or default if not found
     */
    @Nonnull
    public static Map<String, String> getMaterials(@Nonnull String presetId) {
        Map<String, String> fallback = Objects.requireNonNull(PRESETS.get("default"), "default preset missing");
        return Objects.requireNonNull(PRESETS.getOrDefault(presetId, fallback));
    }

    /**
     * Creates an AreaPalette from a preset ID.
     */
    @Nonnull
    public static AreaPalette fromPreset(@Nonnull String presetId) {
        return new AreaPalette(
            new HashMap<>(getMaterials(presetId)),
            presetId,
            false
        );
    }

    /**
     * Gets all available preset IDs.
     */
    @Nonnull
    public static Set<String> getPresetIds() {
        return Objects.requireNonNull(Collections.unmodifiableSet(PRESETS.keySet()));
    }

    /**
     * Gets preset display information.
     */
    @Nonnull
    public static List<PresetInfo> getPresetInfoList() {
        return Objects.requireNonNull(List.of(
            new PresetInfo("default", "Default", "Classic Nexus style with deepslate and copper"),
            new PresetInfo("industrial", "Industrial", "Iron and blackstone industrial look"),
            new PresetInfo("fantasy", "Fantasy", "Magical amethyst and prismarine"),
            new PresetInfo("minimal", "Minimal", "Clean white quartz and concrete"),
            new PresetInfo("nether", "Nether", "Dark blackstone with magma accents"),
            new PresetInfo("end", "End", "Ethereal purpur and end stone"),
            new PresetInfo("nature", "Nature", "Organic moss and wood materials"),
            new PresetInfo("tech", "Tech", "Futuristic copper and glass")
        ));
    }

    public record PresetInfo(String id, String name, String description) {}

    // ========================================================================
    // Preset Definitions
    // ========================================================================

    private static Map<String, String> createDefaultPreset() {
        Map<String, String> m = new HashMap<>();

        // Flooring
        m.put(NexusPalette.Key.FLOOR.id(), "minecraft:smooth_stone");
        m.put(NexusPalette.Key.FLOOR_ALT.id(), "minecraft:polished_andesite");
        m.put(NexusPalette.Key.FLOOR_HUB.id(), "minecraft:calcite");
        m.put(NexusPalette.Key.GRID.id(), "minecraft:polished_andesite");

        // Walls and structure
        m.put(NexusPalette.Key.WALL.id(), "minecraft:smooth_sandstone");
        m.put(NexusPalette.Key.WALL_FRAME.id(), "minecraft:tuff_bricks");
        m.put(NexusPalette.Key.BORDER.id(), "minecraft:smooth_quartz");
        m.put(NexusPalette.Key.TRIM.id(), "minecraft:quartz_pillar");

        // Accents
        m.put(NexusPalette.Key.ACCENT.id(), "minecraft:waxed_exposed_copper");
        m.put(NexusPalette.Key.RING.id(), "minecraft:polished_tuff");
        m.put(NexusPalette.Key.CORE.id(), "minecraft:calcite");
        m.put(NexusPalette.Key.CORE_GLASS.id(), "minecraft:light_gray_stained_glass");

        // Ceiling (FIX: was missing, causing ceiling to be built with air)
        m.put(NexusPalette.Key.CEILING.id(), "minecraft:smooth_stone_slab");

        // Lighting
        m.put(NexusPalette.Key.LIGHT.id(), "minecraft:sea_lantern");
        m.put(NexusPalette.Key.LIGHT_WARM.id(), "minecraft:ochre_froglight");

        // Screens and windows
        m.put(NexusPalette.Key.SCREEN.id(), "minecraft:light_gray_stained_glass");
        m.put(NexusPalette.Key.SCREEN_ALT.id(), "minecraft:white_stained_glass");
        m.put(NexusPalette.Key.SCREEN_FRAME.id(), "minecraft:polished_andesite");
        m.put(NexusPalette.Key.WINDOW.id(), "minecraft:light_gray_stained_glass");
        m.put(NexusPalette.Key.WINDOW_DARK.id(), "minecraft:gray_stained_glass");

        // Infrastructure
        m.put(NexusPalette.Key.RAIL.id(), "minecraft:iron_bars");
        m.put(NexusPalette.Key.TRUSS.id(), "minecraft:iron_bars");
        m.put(NexusPalette.Key.METAL.id(), "minecraft:iron_block");
        m.put(NexusPalette.Key.DATA_LINE.id(), "minecraft:waxed_exposed_cut_copper");

        // Console
        m.put(NexusPalette.Key.CONSOLE.id(), "minecraft:smooth_stone");
        m.put(NexusPalette.Key.CONSOLE_TOP.id(), "minecraft:calcite");

        // Combat zone
        m.put(NexusPalette.Key.COMBAT_RING.id(), "minecraft:tuff_bricks");
        m.put(NexusPalette.Key.COMBAT_RING_INNER.id(), "minecraft:polished_tuff");
        m.put(NexusPalette.Key.COMBAT_POST.id(), "minecraft:polished_basalt");
        m.put(NexusPalette.Key.COMBAT_TARGET.id(), "minecraft:target");

        // UI and telemetry zones
        m.put(NexusPalette.Key.UI_ACCENT.id(), "minecraft:smooth_quartz");
        m.put(NexusPalette.Key.TELEMETRY_ACCENT.id(), "minecraft:waxed_exposed_cut_copper");
        m.put(NexusPalette.Key.TELEMETRY_GLASS.id(), "minecraft:light_gray_stained_glass");

        // Arena zone
        m.put(NexusPalette.Key.ARENA_RING.id(), "minecraft:polished_tuff");
        m.put(NexusPalette.Key.ARENA_RING_INNER.id(), "minecraft:tuff_bricks");
        m.put(NexusPalette.Key.ARENA_BARRIER.id(), "minecraft:blackstone_wall");

        // Portal
        m.put(NexusPalette.Key.PORTAL_FRAME.id(), "minecraft:obsidian");
        m.put(NexusPalette.Key.PORTAL_GLASS.id(), "minecraft:light_gray_stained_glass");

        // Colored pads
        m.put(NexusPalette.Key.PAD_BLUE.id(), "minecraft:light_blue_concrete");
        m.put(NexusPalette.Key.PAD_GREEN.id(), "minecraft:lime_concrete");
        m.put(NexusPalette.Key.PAD_ORANGE.id(), "minecraft:orange_concrete");
        m.put(NexusPalette.Key.PAD_RED.id(), "minecraft:red_concrete");
        m.put(NexusPalette.Key.PAD_YELLOW.id(), "minecraft:yellow_concrete");
        m.put(NexusPalette.Key.PAD_PURPLE.id(), "minecraft:purple_concrete");

        // Air
        m.put(NexusPalette.Key.AIR.id(), "minecraft:air");

        return m;
    }

    private static Map<String, String> createIndustrialPreset() {
        Map<String, String> m = new HashMap<>(createDefaultPreset());

        m.put(NexusPalette.Key.FLOOR.id(), "minecraft:iron_block");
        m.put(NexusPalette.Key.FLOOR_ALT.id(), "minecraft:raw_iron_block");
        m.put(NexusPalette.Key.FLOOR_HUB.id(), "minecraft:polished_blackstone");
        m.put(NexusPalette.Key.WALL.id(), "minecraft:polished_blackstone_bricks");
        m.put(NexusPalette.Key.WALL_FRAME.id(), "minecraft:chiseled_polished_blackstone");
        m.put(NexusPalette.Key.CEILING.id(), "minecraft:polished_blackstone");
        m.put(NexusPalette.Key.ACCENT.id(), "minecraft:iron_block");
        m.put(NexusPalette.Key.CORE.id(), "minecraft:polished_blackstone");
        m.put(NexusPalette.Key.METAL.id(), "minecraft:iron_block");
        m.put(NexusPalette.Key.LIGHT.id(), "minecraft:redstone_lamp");
        m.put(NexusPalette.Key.SCREEN.id(), "minecraft:gray_stained_glass");
        m.put(NexusPalette.Key.WINDOW.id(), "minecraft:gray_stained_glass");

        return m;
    }

    private static Map<String, String> createFantasyPreset() {
        Map<String, String> m = new HashMap<>(createDefaultPreset());

        m.put(NexusPalette.Key.FLOOR.id(), "minecraft:amethyst_block");
        m.put(NexusPalette.Key.FLOOR_ALT.id(), "minecraft:prismarine_bricks");
        m.put(NexusPalette.Key.FLOOR_HUB.id(), "minecraft:budding_amethyst");
        m.put(NexusPalette.Key.WALL.id(), "minecraft:prismarine");
        m.put(NexusPalette.Key.WALL_FRAME.id(), "minecraft:dark_prismarine");
        m.put(NexusPalette.Key.CEILING.id(), "minecraft:dark_prismarine");
        m.put(NexusPalette.Key.ACCENT.id(), "minecraft:amethyst_block");
        m.put(NexusPalette.Key.CORE.id(), "minecraft:sea_lantern");
        m.put(NexusPalette.Key.CORE_GLASS.id(), "minecraft:magenta_stained_glass");
        m.put(NexusPalette.Key.LIGHT.id(), "minecraft:sea_lantern");
        m.put(NexusPalette.Key.SCREEN.id(), "minecraft:magenta_stained_glass");
        m.put(NexusPalette.Key.WINDOW.id(), "minecraft:cyan_stained_glass");

        return m;
    }

    private static Map<String, String> createMinimalPreset() {
        Map<String, String> m = new HashMap<>(createDefaultPreset());

        m.put(NexusPalette.Key.FLOOR.id(), "minecraft:quartz_block");
        m.put(NexusPalette.Key.FLOOR_ALT.id(), "minecraft:smooth_quartz");
        m.put(NexusPalette.Key.FLOOR_HUB.id(), "minecraft:white_concrete");
        m.put(NexusPalette.Key.WALL.id(), "minecraft:white_concrete");
        m.put(NexusPalette.Key.WALL_FRAME.id(), "minecraft:quartz_pillar");
        m.put(NexusPalette.Key.CEILING.id(), "minecraft:smooth_quartz");
        m.put(NexusPalette.Key.ACCENT.id(), "minecraft:light_gray_concrete");
        m.put(NexusPalette.Key.CORE.id(), "minecraft:white_concrete");
        m.put(NexusPalette.Key.CORE_GLASS.id(), "minecraft:white_stained_glass");
        m.put(NexusPalette.Key.LIGHT.id(), "minecraft:glowstone");
        m.put(NexusPalette.Key.SCREEN.id(), "minecraft:white_stained_glass");
        m.put(NexusPalette.Key.WINDOW.id(), "minecraft:glass");

        return m;
    }

    private static Map<String, String> createNetherPreset() {
        Map<String, String> m = new HashMap<>(createDefaultPreset());

        m.put(NexusPalette.Key.FLOOR.id(), "minecraft:polished_blackstone");
        m.put(NexusPalette.Key.FLOOR_ALT.id(), "minecraft:polished_blackstone_bricks");
        m.put(NexusPalette.Key.FLOOR_HUB.id(), "minecraft:gilded_blackstone");
        m.put(NexusPalette.Key.WALL.id(), "minecraft:blackstone");
        m.put(NexusPalette.Key.WALL_FRAME.id(), "minecraft:polished_blackstone_bricks");
        m.put(NexusPalette.Key.CEILING.id(), "minecraft:blackstone");
        m.put(NexusPalette.Key.ACCENT.id(), "minecraft:magma_block");
        m.put(NexusPalette.Key.CORE.id(), "minecraft:crying_obsidian");
        m.put(NexusPalette.Key.CORE_GLASS.id(), "minecraft:red_stained_glass");
        m.put(NexusPalette.Key.LIGHT.id(), "minecraft:shroomlight");
        m.put(NexusPalette.Key.LIGHT_WARM.id(), "minecraft:magma_block");
        m.put(NexusPalette.Key.SCREEN.id(), "minecraft:red_stained_glass");
        m.put(NexusPalette.Key.WINDOW.id(), "minecraft:orange_stained_glass");

        return m;
    }

    private static Map<String, String> createEndPreset() {
        Map<String, String> m = new HashMap<>(createDefaultPreset());

        m.put(NexusPalette.Key.FLOOR.id(), "minecraft:purpur_block");
        m.put(NexusPalette.Key.FLOOR_ALT.id(), "minecraft:purpur_pillar");
        m.put(NexusPalette.Key.FLOOR_HUB.id(), "minecraft:end_stone_bricks");
        m.put(NexusPalette.Key.WALL.id(), "minecraft:end_stone_bricks");
        m.put(NexusPalette.Key.WALL_FRAME.id(), "minecraft:purpur_pillar");
        m.put(NexusPalette.Key.CEILING.id(), "minecraft:purpur_slab");
        m.put(NexusPalette.Key.ACCENT.id(), "minecraft:purpur_block");
        m.put(NexusPalette.Key.CORE.id(), "minecraft:end_rod");
        m.put(NexusPalette.Key.CORE_GLASS.id(), "minecraft:magenta_stained_glass");
        m.put(NexusPalette.Key.LIGHT.id(), "minecraft:end_rod");
        m.put(NexusPalette.Key.SCREEN.id(), "minecraft:magenta_stained_glass");
        m.put(NexusPalette.Key.WINDOW.id(), "minecraft:purple_stained_glass");

        return m;
    }

    private static Map<String, String> createNaturePreset() {
        Map<String, String> m = new HashMap<>(createDefaultPreset());

        m.put(NexusPalette.Key.FLOOR.id(), "minecraft:moss_block");
        m.put(NexusPalette.Key.FLOOR_ALT.id(), "minecraft:rooted_dirt");
        m.put(NexusPalette.Key.FLOOR_HUB.id(), "minecraft:grass_block");
        m.put(NexusPalette.Key.WALL.id(), "minecraft:oak_planks");
        m.put(NexusPalette.Key.WALL_FRAME.id(), "minecraft:oak_log");
        m.put(NexusPalette.Key.CEILING.id(), "minecraft:oak_planks");
        m.put(NexusPalette.Key.ACCENT.id(), "minecraft:moss_block");
        m.put(NexusPalette.Key.CORE.id(), "minecraft:flowering_azalea_leaves");
        m.put(NexusPalette.Key.CORE_GLASS.id(), "minecraft:green_stained_glass");
        m.put(NexusPalette.Key.LIGHT.id(), "minecraft:verdant_froglight");
        m.put(NexusPalette.Key.LIGHT_WARM.id(), "minecraft:pearlescent_froglight");
        m.put(NexusPalette.Key.SCREEN.id(), "minecraft:green_stained_glass");
        m.put(NexusPalette.Key.WINDOW.id(), "minecraft:lime_stained_glass");
        m.put(NexusPalette.Key.RAIL.id(), "minecraft:oak_fence");
        m.put(NexusPalette.Key.TRUSS.id(), "minecraft:oak_fence");

        return m;
    }

    private static Map<String, String> createTechPreset() {
        Map<String, String> m = new HashMap<>(createDefaultPreset());

        m.put(NexusPalette.Key.FLOOR.id(), "minecraft:waxed_copper_block");
        m.put(NexusPalette.Key.FLOOR_ALT.id(), "minecraft:waxed_cut_copper");
        m.put(NexusPalette.Key.FLOOR_HUB.id(), "minecraft:waxed_oxidized_copper");
        m.put(NexusPalette.Key.WALL.id(), "minecraft:waxed_exposed_copper");
        m.put(NexusPalette.Key.WALL_FRAME.id(), "minecraft:waxed_cut_copper");
        m.put(NexusPalette.Key.CEILING.id(), "minecraft:waxed_cut_copper");
        m.put(NexusPalette.Key.ACCENT.id(), "minecraft:lightning_rod");
        m.put(NexusPalette.Key.CORE.id(), "minecraft:waxed_copper_block");
        m.put(NexusPalette.Key.CORE_GLASS.id(), "minecraft:light_blue_stained_glass");
        m.put(NexusPalette.Key.LIGHT.id(), "minecraft:sea_lantern");
        m.put(NexusPalette.Key.SCREEN.id(), "minecraft:light_blue_stained_glass");
        m.put(NexusPalette.Key.WINDOW.id(), "minecraft:cyan_stained_glass");
        m.put(NexusPalette.Key.DATA_LINE.id(), "minecraft:waxed_copper_grate");

        return m;
    }
}
