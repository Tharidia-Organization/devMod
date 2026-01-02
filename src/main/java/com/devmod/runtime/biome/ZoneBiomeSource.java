package com.devmod.runtime.biome;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneLayout;

/**
 * Custom BiomeSource that resolves biomes based on zone layout.
 * Each zone can have its own biome, allowing multi-biome arenas.
 *
 * This enables proper biome-specific rendering and effects per zone,
 * including modded biomes from Biomes O' Plenty, Terralith, etc.
 */
public class ZoneBiomeSource extends BiomeSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneBiomeSource.class);

    /**
     * Codec for serialization.
     * <p>
     * <b>DESIGN DECISION:</b> Uses {@code MapCodec.unit()} for placeholder-based serialization because:
     * <ol>
     *   <li>ZoneLayout contains runtime references and cannot be directly serialized</li>
     *   <li>Arena dimensions are ephemeral - created on-demand and destroyed after sessions</li>
     *   <li>The JSON template is the source of truth for recreation</li>
     *   <li>Placeholder protects against improper use before initialization</li>
     * </ol>
     * <p>
     * The placeholder is replaced with a properly configured instance in
     * {@link com.devmod.runtime.DynamicDimensionManager#createDimensionSync}.
     * <p>
     * If persistence were needed in future:
     * <ul>
     *   <li>Store templateId + configuration hash in the codec</li>
     *   <li>Rebuild from template in {@code LevelEvent.Load} handler</li>
     * </ul>
     *
     * @see com.devmod.runtime.DynamicDimensionManager#createDimensionSync
     */
    public static final MapCodec<ZoneBiomeSource> CODEC = MapCodec.unit(ZoneBiomeSource::createPlaceholder);

    /**
     * Creates a placeholder instance for codec deserialization.
     * This will be replaced with a properly configured instance when the dimension is accessed.
     * Also used by ArenaFlatChunkGenerator for its placeholder.
     */
    public static ZoneBiomeSource createPlaceholder() {
        // Return a minimal valid instance - will be replaced at runtime
        return new ZoneBiomeSource();
    }

    /**
     * Private constructor for placeholder instances.
     * Creates a minimal valid biome source with no zones.
     */
    private ZoneBiomeSource() {
        this.zoneLayout = null;
        this.defaultBiome = null; // Will be set when dimension is accessed
        this.zoneBiomes = Map.of();
    }

    private final @Nullable ZoneLayout zoneLayout;
    private final @Nullable Holder<Biome> defaultBiome;
    private final Map<String, Holder<Biome>> zoneBiomes;

    /**
     * Creates a zone-aware biome source.
     *
     * @param zoneLayout The zone layout defining zone boundaries
     * @param biomeRegistry The biome registry for looking up biomes
     * @param defaultBiome The fallback biome for areas outside zones
     */
    public ZoneBiomeSource(
            @Nullable ZoneLayout zoneLayout,
            @Nullable Registry<Biome> biomeRegistry,
            @Nullable Holder<Biome> defaultBiome
    ) {
        this.zoneLayout = zoneLayout;
        this.defaultBiome = defaultBiome != null ? defaultBiome : getVoidBiome(biomeRegistry);
        this.zoneBiomes = resolveZoneBiomes(zoneLayout, biomeRegistry, this.defaultBiome);
    }

    /**
     * Resolves biome holders for each zone based on their environment configuration.
     */
    private Map<String, Holder<Biome>> resolveZoneBiomes(
            @Nullable ZoneLayout layout,
            @Nullable Registry<Biome> registry,
            Holder<Biome> fallback
    ) {
        Map<String, Holder<Biome>> result = new HashMap<>();

        if (layout == null || registry == null) {
            return result;
        }

        for (ArenaZone zone : layout.zones()) {
            var env = zone.environment();
            if (env.biome().isPresent()) {
                ResourceLocation biomeId = env.biome().get();
                Holder<Biome> biomeHolder = resolveBiomeSafe(biomeId, registry, fallback);
                result.put(zone.name(), biomeHolder);
                LOGGER.debug("Zone '{}' mapped to biome: {}", zone.name(), biomeId);
            } else {
                result.put(zone.name(), fallback);
            }
        }

        return Map.copyOf(result);
    }

    /**
     * Safely resolves a biome by ResourceLocation, with fallback for missing biomes.
     * This is critical for modded biome compatibility - if a mod's biome isn't loaded,
     * we gracefully fall back to the default biome.
     */
    private Holder<Biome> resolveBiomeSafe(
            ResourceLocation biomeId,
            Registry<Biome> registry,
            Holder<Biome> fallback
    ) {
        try {
            ResourceKey<Biome> key = ResourceKey.create(
                Objects.requireNonNull(Registries.BIOME),
                Objects.requireNonNull(biomeId)
            );
            Optional<Holder.Reference<Biome>> holder = registry.getHolder(Objects.requireNonNull(key));
            if (holder.isPresent()) {
                return holder.get();
            }
            LOGGER.warn("Biome {} not found in registry, using fallback", biomeId);
        } catch (Exception e) {
            LOGGER.warn("Error resolving biome {}: {}", biomeId, e.getMessage());
        }
        return fallback;
    }

    /**
     * Gets the void biome as ultimate fallback.
     * Returns null if registry is not available (placeholder mode).
     */
    @Nullable
    private Holder<Biome> getVoidBiome(@Nullable Registry<Biome> registry) {
        if (registry == null) {
            // Placeholder mode - will be replaced at runtime
            return null;
        }
        try {
            ResourceKey<Biome> voidKey = ResourceKey.create(
                Objects.requireNonNull(Registries.BIOME),
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("the_void"))
            );
            return registry.getHolderOrThrow(Objects.requireNonNull(voidKey));
        } catch (Exception e) {
            // Return any available biome as fallback
            Optional<Holder.Reference<Biome>> fallback = registry.holders().findFirst();
            if (fallback.isPresent()) {
                return fallback.get();
            }
            LOGGER.error("No biomes available in registry!");
            return null;
        }
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        Set<Holder<Biome>> biomes = new HashSet<>();
        if (defaultBiome != null) {
            biomes.add(defaultBiome);
        }
        biomes.addAll(zoneBiomes.values());

        // Fallback for placeholder mode - return void biome to avoid empty stream
        if (biomes.isEmpty()) {
            Holder<Biome> voidBiome = getVoidBiome(null);
            if (voidBiome != null) {
                biomes.add(voidBiome);
            }
        }

        return biomes.stream();
    }

    /**
     * Returns the biome at the given quart position.
     * Quart positions are 1/4 of a block, used for biome blending.
     *
     * Note: In placeholder mode (defaultBiome is null), this will throw.
     * The placeholder should be replaced before the dimension is actually used.
     */
    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, @Nonnull Climate.Sampler sampler) {
        // Check for placeholder mode
        if (defaultBiome == null) {
            throw new IllegalStateException(
                "ZoneBiomeSource is in placeholder mode - dimension was not properly initialized. " +
                "This should be replaced with a configured instance before use."
            );
        }

        // Capture to local variable for null safety after check
        final ZoneLayout layout = zoneLayout;
        if (layout == null) {
            return defaultBiome;
        }

        // Convert quart positions to block positions
        int blockX = QuartPos.toBlock(quartX);
        int blockZ = QuartPos.toBlock(quartZ);

        // Find which zone contains this position
        Optional<ArenaZone> zone = layout.getZoneAt(blockX, blockZ);

        if (zone.isPresent()) {
            String zoneName = zone.get().name();
            Holder<Biome> zoneBiome = zoneBiomes.get(zoneName);
            if (zoneBiome != null) {
                return zoneBiome;
            }
        }

        return defaultBiome;
    }

    /**
     * Checks if this is a placeholder instance (not properly configured).
     */
    public boolean isPlaceholder() {
        return defaultBiome == null;
    }

    /**
     * Checks if this biome source has multiple biomes.
     */
    public boolean isMultiZone() {
        return zoneLayout != null && zoneBiomes.size() > 1;
    }

    /**
     * Gets the zone layout used by this biome source.
     */
    @Nullable
    public ZoneLayout getZoneLayout() {
        return zoneLayout;
    }

    /**
     * Gets the default biome.
     * May be null in placeholder mode.
     */
    @Nullable
    public Holder<Biome> getDefaultBiome() {
        return defaultBiome;
    }

    /**
     * Creates a single-biome source (equivalent to vanilla flat world behavior).
     */
    public static ZoneBiomeSource singleBiome(Holder<Biome> biome) {
        return new ZoneBiomeSource(null, null, biome);
    }

    /**
     * Creates a zone-aware biome source from layout and registry.
     */
    public static ZoneBiomeSource fromZoneLayout(
            ZoneLayout layout,
            Registry<Biome> biomeRegistry,
            Holder<Biome> defaultBiome
    ) {
        return new ZoneBiomeSource(layout, biomeRegistry, defaultBiome);
    }
}
