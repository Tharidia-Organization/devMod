package com.devmod.runtime;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.config.Config;
import com.devmod.util.ConfigPaths;

/**
 * Palette provider for Nexus hub materials.
 */
public final class NexusPalette {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusPalette.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PALETTE_FILE = "nexus_palette.json";

    public enum Key {
        FLOOR("floor"),
        FLOOR_ALT("floor_alt"),
        FLOOR_HUB("floor_hub"),
        /** H-13 fix: Dedicated ceiling key for proper ceiling material */
        CEILING("ceiling"),
        GRID("grid"),
        ACCENT("accent"),
        WALL("wall"),
        WALL_FRAME("wall_frame"),
        BORDER("border"),
        TRIM("trim"),
        RING("ring"),
        CORE("core"),
        CORE_GLASS("core_glass"),
        LIGHT("light"),
        LIGHT_WARM("light_warm"),
        SCREEN("screen"),
        SCREEN_ALT("screen_alt"),
        SCREEN_FRAME("screen_frame"),
        WINDOW("window"),
        WINDOW_DARK("window_dark"),
        RAIL("rail"),
        TRUSS("truss"),
        METAL("metal"),
        DATA_LINE("data_line"),
        CONSOLE("console"),
        CONSOLE_TOP("console_top"),
        COMBAT_RING("combat_ring"),
        COMBAT_RING_INNER("combat_ring_inner"),
        COMBAT_POST("combat_post"),
        COMBAT_TARGET("combat_target"),
        UI_ACCENT("ui_accent"),
        TELEMETRY_ACCENT("telemetry_accent"),
        TELEMETRY_GLASS("telemetry_glass"),
        ARENA_RING("arena_ring"),
        ARENA_RING_INNER("arena_ring_inner"),
        ARENA_BARRIER("arena_barrier"),
        PORTAL_FRAME("portal_frame"),
        PORTAL_GLASS("portal_glass"),
        PAD_BLUE("pad_blue"),
        PAD_GREEN("pad_green"),
        PAD_ORANGE("pad_orange"),
        PAD_RED("pad_red"),
        PAD_YELLOW("pad_yellow"),
        PAD_PURPLE("pad_purple"),
        AIR("air");

        private final String id;

        Key(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Key fromId(String id) {
            if (id == null) {
                return null;
            }
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            for (Key key : values()) {
                if (key.id.equals(normalized)) {
                    return key;
                }
            }
            return null;
        }
    }

    private final EnumMap<Key, BlockState> states;

    private NexusPalette(EnumMap<Key, BlockState> states) {
        this.states = states;
    }

    public BlockState get(Key key) {
        return states.get(key);
    }

    public static NexusPalette load() {
        Config.NexusPaletteProfile profile = Config.NEXUS_PALETTE_PROFILE.get();
        NexusPalette base = switch (profile) {
            case PERFORMANCE -> performancePalette();
            default -> defaultPalette();
        };
        if (profile == Config.NexusPaletteProfile.CUSTOM) {
            return loadCustom(base);
        }
        return base;
    }

    public static NexusPalette defaultPalette() {
        EnumMap<Key, BlockState> map = new EnumMap<>(Key.class);
        map.put(Key.FLOOR, Blocks.SMOOTH_STONE.defaultBlockState());
        map.put(Key.FLOOR_ALT, Blocks.POLISHED_ANDESITE.defaultBlockState());
        map.put(Key.FLOOR_HUB, Blocks.CALCITE.defaultBlockState());
        // H-13 fix: Dedicated ceiling material (defaults to same as floor for backward compat)
        map.put(Key.CEILING, Blocks.SMOOTH_STONE.defaultBlockState());
        map.put(Key.GRID, Blocks.POLISHED_ANDESITE.defaultBlockState());
        map.put(Key.ACCENT, Blocks.WAXED_EXPOSED_COPPER.defaultBlockState());
        map.put(Key.WALL, Blocks.SMOOTH_SANDSTONE.defaultBlockState());
        map.put(Key.WALL_FRAME, Blocks.TUFF_BRICKS.defaultBlockState());
        map.put(Key.BORDER, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        map.put(Key.TRIM, Blocks.QUARTZ_PILLAR.defaultBlockState());
        map.put(Key.RING, Blocks.POLISHED_TUFF.defaultBlockState());
        map.put(Key.CORE, Blocks.CALCITE.defaultBlockState());
        map.put(Key.CORE_GLASS, Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState());
        map.put(Key.LIGHT, Blocks.SEA_LANTERN.defaultBlockState());
        map.put(Key.LIGHT_WARM, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
        map.put(Key.SCREEN, Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState());
        map.put(Key.SCREEN_ALT, Blocks.WHITE_STAINED_GLASS.defaultBlockState());
        map.put(Key.SCREEN_FRAME, Blocks.POLISHED_ANDESITE.defaultBlockState());
        map.put(Key.WINDOW, Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState());
        map.put(Key.WINDOW_DARK, Blocks.GRAY_STAINED_GLASS.defaultBlockState());
        map.put(Key.RAIL, Blocks.IRON_BARS.defaultBlockState());
        map.put(Key.TRUSS, Blocks.IRON_BARS.defaultBlockState());
        map.put(Key.METAL, Blocks.IRON_BLOCK.defaultBlockState());
        map.put(Key.DATA_LINE, Blocks.WAXED_EXPOSED_CUT_COPPER.defaultBlockState());
        map.put(Key.CONSOLE, Blocks.SMOOTH_STONE.defaultBlockState());
        map.put(Key.CONSOLE_TOP, Blocks.CALCITE.defaultBlockState());
        map.put(Key.COMBAT_RING, Blocks.TUFF_BRICKS.defaultBlockState());
        map.put(Key.COMBAT_RING_INNER, Blocks.POLISHED_TUFF.defaultBlockState());
        map.put(Key.COMBAT_POST, Blocks.POLISHED_BASALT.defaultBlockState());
        map.put(Key.COMBAT_TARGET, Blocks.TARGET.defaultBlockState());
        map.put(Key.UI_ACCENT, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        map.put(Key.TELEMETRY_ACCENT, Blocks.WAXED_EXPOSED_CUT_COPPER.defaultBlockState());
        map.put(Key.TELEMETRY_GLASS, Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState());
        map.put(Key.ARENA_RING, Blocks.POLISHED_TUFF.defaultBlockState());
        map.put(Key.ARENA_RING_INNER, Blocks.TUFF_BRICKS.defaultBlockState());
        map.put(Key.ARENA_BARRIER, Blocks.BLACKSTONE_WALL.defaultBlockState());
        map.put(Key.PORTAL_FRAME, Blocks.OBSIDIAN.defaultBlockState());
        map.put(Key.PORTAL_GLASS, Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState());
        map.put(Key.PAD_BLUE, Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState());
        map.put(Key.PAD_GREEN, Blocks.LIME_CONCRETE.defaultBlockState());
        map.put(Key.PAD_ORANGE, Blocks.ORANGE_CONCRETE.defaultBlockState());
        map.put(Key.PAD_RED, Blocks.RED_CONCRETE.defaultBlockState());
        map.put(Key.PAD_YELLOW, Blocks.YELLOW_CONCRETE.defaultBlockState());
        map.put(Key.PAD_PURPLE, Blocks.PURPLE_CONCRETE.defaultBlockState());
        map.put(Key.AIR, Blocks.AIR.defaultBlockState());
        return new NexusPalette(map);
    }

    public static NexusPalette performancePalette() {
        EnumMap<Key, BlockState> map = new EnumMap<>(defaultPalette().states);
        map.put(Key.WALL, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        map.put(Key.BORDER, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        map.put(Key.WINDOW, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        map.put(Key.WINDOW_DARK, Blocks.GRAY_CONCRETE.defaultBlockState());
        map.put(Key.CORE_GLASS, Blocks.SEA_LANTERN.defaultBlockState());
        map.put(Key.SCREEN, Blocks.GRAY_CONCRETE.defaultBlockState());
        map.put(Key.SCREEN_ALT, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        map.put(Key.RAIL, Blocks.IRON_BARS.defaultBlockState());
        map.put(Key.DATA_LINE, Blocks.WAXED_EXPOSED_CUT_COPPER.defaultBlockState());
        map.put(Key.TELEMETRY_GLASS, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        map.put(Key.LIGHT_WARM, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
        map.put(Key.PAD_YELLOW, Blocks.CALCITE.defaultBlockState());
        map.put(Key.PAD_PURPLE, Blocks.TUFF_BRICKS.defaultBlockState());
        return new NexusPalette(map);
    }

    private static NexusPalette loadCustom(NexusPalette fallback) {
        Path palettePath = ConfigPaths.getConfigDir().resolve(PALETTE_FILE);
        if (!Files.exists(palettePath)) {
            writePalette(palettePath, fallback);
            return fallback;
        }

        Map<String, String> raw = null;
        try (Reader reader = Files.newBufferedReader(palettePath, StandardCharsets.UTF_8)) {
            raw = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warn("[Nexus] Failed to read palette file {}, using defaults: {}", palettePath, e.getMessage());
            return fallback;
        }

        if (raw == null || raw.isEmpty()) {
            return fallback;
        }

        EnumMap<Key, BlockState> map = new EnumMap<>(fallback.states);
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            Key key = Key.fromId(entry.getKey());
            if (key == null) {
                LOGGER.warn("[Nexus] Unknown palette key '{}'", entry.getKey());
                continue;
            }
            BlockState resolved = resolveBlock(entry.getValue(), map.get(key));
            map.put(key, resolved);
        }
        return new NexusPalette(map);
    }

    private static void writePalette(Path path, NexusPalette palette) {
        Path parent = path.getParent();
        if (parent == null) {
            LOGGER.warn("[Nexus] Palette path has no parent directory: {}", path);
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            LOGGER.warn("[Nexus] Failed to create palette directory {}: {}", parent, e.getMessage());
            return;
        }

        Map<String, String> data = new HashMap<>();
        for (Key key : Key.values()) {
            BlockState state = palette.get(key);
            if (state == null) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(state.getBlock()));
            data.put(key.id(), id.toString());
        }

        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.warn("[Nexus] Failed to write palette file {}: {}", path, e.getMessage());
        }
    }

    private static BlockState resolveBlock(String value, BlockState fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        ResourceLocation location = ResourceLocation.tryParse(Objects.requireNonNull(value.trim()));
        if (location == null) {
            LOGGER.warn("[Nexus] Invalid block id '{}' in palette", value);
            return fallback;
        }
        Block block = BuiltInRegistries.BLOCK.get(location);
        if (block == Blocks.AIR && !Objects.equals(value.trim(), "minecraft:air")) {
            LOGGER.warn("[Nexus] Unknown block '{}' in palette", value);
            return fallback;
        }
        return block.defaultBlockState();
    }
}
