package com.devmod.arena.policy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import com.devmod.mob.MobRequirements;

/**
 * Dynamic biome-to-floor material mapper using biome tags from the registry.
 *
 * <p>This system supports modded biomes (Terralith, BiomesOPlenty, etc.) by:
 * <ul>
 *   <li>Querying biome tags at runtime from the registry</li>
 *   <li>Using vanilla BiomeTags for categorization</li>
 *   <li>Supporting mod-specific tags via conventional naming</li>
 * </ul>
 *
 * <p>Priority for floor material selection:
 * <ol>
 *   <li>MobRequirements.floor().preferredBlock() - explicit floor preference</li>
 *   <li>MobRequirements.biome().biomeTag() - tag-based lookup</li>
 *   <li>Biome registry lookup - check tags for specific biome</li>
 *   <li>Fallback to stone_bricks</li>
 * </ol>
 */
public final class BiomeFloorMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(BiomeFloorMapper.class);

    /** Default floor material when no specific mapping is found */
    private static final String DEFAULT_FLOOR = "minecraft:stone_bricks";

    // Tag → Floor material mappings (ordered by specificity)
    private static final Map<TagKey<Biome>, String> TAG_FLOOR_MAP = createTagFloorMap();

    private BiomeFloorMapper() {}

    /**
     * Creates the tag-to-floor mapping with vanilla BiomeTags.
     * Modded tags can be added dynamically via {@link #registerTagFloorMapping}.
     */
    private static Map<TagKey<Biome>, String> createTagFloorMap() {
        // LinkedHashMap preserves insertion order - more specific tags first
        Map<TagKey<Biome>, String> map = new LinkedHashMap<>();

        // Nether biomes (most specific first)
        map.put(BiomeTags.IS_NETHER, "minecraft:netherrack");
        // End biomes
        map.put(BiomeTags.IS_END, "minecraft:end_stone_bricks");
        // Water biomes
        map.put(BiomeTags.IS_OCEAN, "minecraft:prismarine_bricks");
        map.put(BiomeTags.IS_DEEP_OCEAN, "minecraft:dark_prismarine");
        map.put(BiomeTags.IS_RIVER, "minecraft:prismarine");
        map.put(BiomeTags.IS_BEACH, "minecraft:sandstone");
        // Mountain biomes
        map.put(BiomeTags.IS_MOUNTAIN, "minecraft:stone");
        map.put(BiomeTags.IS_HILL, "minecraft:cobblestone");
        // Forest biomes
        map.put(BiomeTags.IS_FOREST, "minecraft:moss_block");
        map.put(BiomeTags.IS_JUNGLE, "minecraft:jungle_planks");
        map.put(BiomeTags.IS_TAIGA, "minecraft:spruce_planks");
        // Temperature-based
        map.put(BiomeTags.IS_BADLANDS, "minecraft:red_sandstone");
        map.put(BiomeTags.IS_SAVANNA, "minecraft:acacia_planks");

        return map;
    }

    /**
     * Resolves floor material for a mob based on its requirements.
     * Uses the full priority chain: preferredBlock → biomeTag → registry lookup → fallback.
     *
     * @param mobReqs The mob requirements
     * @param server  The server (for registry access), may be null for offline resolution
     * @return The floor material ResourceLocation string
     */
    public static String resolveFloorMaterial(MobRequirements mobReqs, @Nullable MinecraftServer server) {
        // Priority 1: Explicit floor preference from mob requirements
        Optional<ResourceLocation> preferredBlock = mobReqs.floor().preferredBlock();
        if (preferredBlock.isPresent()) {
            String floor = preferredBlock.get().toString();
            LOGGER.debug("[BiomeFloorMapper] Using preferred block: {}", floor);
            return floor;
        }

        // Priority 2: Liquid floor for water mobs
        if (mobReqs.floor().canSpawnOnLiquid() && !mobReqs.floor().requiresSolid()) {
            LOGGER.debug("[BiomeFloorMapper] Mob can spawn on liquid, using water");
            return "minecraft:water";
        }

        // Priority 3: Use biome tag from MobRequirements if available
        Optional<TagKey<Biome>> biomeTag = mobReqs.biome().biomeTag();
        if (biomeTag.isPresent()) {
            String floor = getFloorFromTag(biomeTag.get());
            if (floor != null) {
                LOGGER.debug("[BiomeFloorMapper] Using floor '{}' from biome tag '{}'",
                    floor, biomeTag.get().location());
                return floor;
            }
        }

        // Priority 4: Look up biome in registry and check its tags
        Optional<ResourceLocation> preferredBiome = mobReqs.biome().preferredBiome();
        if (preferredBiome.isPresent() && server != null) {
            String floor = getFloorFromBiomeRegistry(preferredBiome.get(), server);
            if (floor != null) {
                LOGGER.debug("[BiomeFloorMapper] Using floor '{}' from biome registry lookup for '{}'",
                    floor, preferredBiome.get());
                return floor;
            }
        }

        // Priority 5: Check validBiomes list
        if (!mobReqs.biome().validBiomes().isEmpty() && server != null) {
            for (ResourceLocation validBiome : mobReqs.biome().validBiomes()) {
                String floor = getFloorFromBiomeRegistry(validBiome, server);
                if (floor != null) {
                    LOGGER.debug("[BiomeFloorMapper] Using floor '{}' from valid biome '{}'",
                        floor, validBiome);
                    return floor;
                }
            }
        }

        // Priority 6: Fallback pattern matching for biome ID (supports mods without tags)
        if (preferredBiome.isPresent()) {
            String floor = getFloorFromBiomeIdPattern(preferredBiome.get().toString());
            if (!DEFAULT_FLOOR.equals(floor)) {
                LOGGER.debug("[BiomeFloorMapper] Using floor '{}' from biome ID pattern '{}'",
                    floor, preferredBiome.get());
                return floor;
            }
        }

        LOGGER.debug("[BiomeFloorMapper] Using default floor: {}", DEFAULT_FLOOR);
        return DEFAULT_FLOOR;
    }

    /**
     * Gets floor material from a biome tag directly.
     *
     * @param tag The biome tag
     * @return Floor material or null if no mapping exists
     */
    @Nullable
    public static String getFloorFromTag(TagKey<Biome> tag) {
        // Direct lookup in our tag map
        String direct = TAG_FLOOR_MAP.get(tag);
        if (direct != null) {
            return direct;
        }

        // Check if tag location suggests a category (for modded tags)
        String tagPath = tag.location().getPath();
        return inferFloorFromTagPath(tagPath);
    }

    /**
     * Looks up a biome in the registry and determines floor material from its tags.
     *
     * @param biomeId The biome ResourceLocation
     * @param server  The server for registry access
     * @return Floor material or null if biome not found or no tags match
     */
    @Nullable
    public static String getFloorFromBiomeRegistry(ResourceLocation biomeId, MinecraftServer server) {
        try {
            Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
            Optional<Holder.Reference<Biome>> holderOpt = biomeRegistry.getHolder(
                net.minecraft.resources.ResourceKey.create(Registries.BIOME, Objects.requireNonNull(biomeId))
            );

            if (holderOpt.isEmpty()) {
                LOGGER.debug("[BiomeFloorMapper] Biome '{}' not found in registry", biomeId);
                return null;
            }

            Holder<Biome> holder = holderOpt.get();

            // Check each tag in priority order
            for (Map.Entry<TagKey<Biome>, String> entry : TAG_FLOOR_MAP.entrySet()) {
                if (holder.is(entry.getKey())) {
                    return entry.getValue();
                }
            }

            // No matching tag found
            return null;

        } catch (Exception e) {
            LOGGER.warn("[BiomeFloorMapper] Error looking up biome '{}': {}", biomeId, e.getMessage());
            return null;
        }
    }

    /**
     * Infers floor material from tag path string for modded tags.
     * Supports common mod conventions like "is_nether", "is_ocean", etc.
     */
    @Nullable
    private static String inferFloorFromTagPath(String tagPath) {
        String lower = tagPath.toLowerCase();

        // Nether variants
        if (lower.contains("nether")) {
            if (lower.contains("soul")) return "minecraft:soul_sand";
            if (lower.contains("basalt")) return "minecraft:blackstone";
            if (lower.contains("crimson")) return "minecraft:crimson_nylium";
            if (lower.contains("warped")) return "minecraft:warped_nylium";
            return "minecraft:netherrack";
        }

        // End variants
        if (lower.contains("end") || lower.contains("void")) {
            return "minecraft:end_stone_bricks";
        }

        // Water variants
        if (lower.contains("ocean") || lower.contains("aquatic")) {
            if (lower.contains("deep")) return "minecraft:dark_prismarine";
            return "minecraft:prismarine_bricks";
        }
        if (lower.contains("river")) return "minecraft:prismarine";
        if (lower.contains("beach")) return "minecraft:sandstone";

        // Cold/frozen
        if (lower.contains("frozen") || lower.contains("ice") || lower.contains("snowy") || lower.contains("cold")) {
            return "minecraft:packed_ice";
        }

        // Hot/dry
        if (lower.contains("desert")) return "minecraft:sandstone";
        if (lower.contains("badlands") || lower.contains("mesa")) return "minecraft:red_sandstone";

        // Forest types
        if (lower.contains("jungle")) return "minecraft:jungle_planks";
        if (lower.contains("forest")) return "minecraft:moss_block";
        if (lower.contains("taiga")) return "minecraft:spruce_planks";
        if (lower.contains("savanna")) return "minecraft:acacia_planks";
        if (lower.contains("cherry")) return "minecraft:cherry_planks";
        if (lower.contains("dark_forest") || lower.contains("dark_oak")) return "minecraft:dark_oak_planks";
        if (lower.contains("birch")) return "minecraft:birch_planks";

        // Swamp/marsh
        if (lower.contains("swamp") || lower.contains("mangrove") || lower.contains("marsh")) {
            return "minecraft:mud_bricks";
        }

        // Cave biomes
        if (lower.contains("deep_dark") || lower.contains("sculk")) return "minecraft:deepslate_tiles";
        if (lower.contains("dripstone")) return "minecraft:dripstone_block";
        if (lower.contains("lush")) return "minecraft:moss_block";

        // Mountain
        if (lower.contains("mountain") || lower.contains("peak") || lower.contains("highland")) {
            return "minecraft:stone";
        }
        if (lower.contains("hill")) return "minecraft:cobblestone";

        // Mushroom
        if (lower.contains("mushroom")) return "minecraft:mycelium";

        return null;
    }

    /**
     * Pattern matching fallback for biome IDs.
     * Used when registry lookup is not available or returns null.
     *
     * @param biomeId The full biome ID string (e.g., "minecraft:nether_wastes")
     * @return Floor material, or DEFAULT_FLOOR if no pattern matches
     */
    public static String getFloorFromBiomeIdPattern(String biomeId) {
        if (biomeId == null) {
            return DEFAULT_FLOOR;
        }

        // Extract path from resource location if full ID
        String path = biomeId.contains(":") ? biomeId.substring(biomeId.indexOf(':') + 1) : biomeId;
        String result = inferFloorFromTagPath(path);
        return result != null ? result : DEFAULT_FLOOR;
    }

    /**
     * Registers a custom tag-to-floor mapping for mod support.
     * Call during mod initialization to add mod-specific biome tags.
     *
     * @param tag   The biome tag to register
     * @param floor The floor material ResourceLocation string
     */
    public static void registerTagFloorMapping(TagKey<Biome> tag, String floor) {
        // Insert at the beginning for higher priority (LinkedHashMap)
        Map<TagKey<Biome>, String> newMap = new LinkedHashMap<>();
        newMap.put(tag, floor);
        newMap.putAll(TAG_FLOOR_MAP);
        TAG_FLOOR_MAP.clear();
        TAG_FLOOR_MAP.putAll(newMap);
        LOGGER.info("[BiomeFloorMapper] Registered tag floor mapping: {} -> {}", tag.location(), floor);
    }

    /**
     * Creates a custom biome tag for a mod.
     * Helper for mods that want to register their own biome categories.
     *
     * @param namespace The mod namespace
     * @param path      The tag path (e.g., "is_volcanic")
     * @return The created TagKey
     */
    public static TagKey<Biome> createModBiomeTag(String namespace, String path) {
        return TagKey.create(
            Registries.BIOME,
            Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(namespace, path))
        );
    }
}
