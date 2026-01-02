package com.devmod.runtime.biome;

import java.util.Objects;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.BiomeSource;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;

/**
 * Registers custom BiomeSource codecs for the mod.
 * Required for proper serialization of custom biome sources in world saves.
 */
public class ModBiomeSources {
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
        DeferredRegister.create(Objects.requireNonNull(Registries.BIOME_SOURCE), DevMod.MODID);

    /** Zone-aware biome source for multi-biome arenas */
    public static final Supplier<MapCodec<? extends BiomeSource>> ZONE_BIOME_SOURCE =
        BIOME_SOURCES.register("zone_biome_source", () -> ZoneBiomeSource.CODEC);

    /**
     * Registers the biome sources with the event bus.
     * Call this from the mod constructor.
     */
    public static void register(IEventBus eventBus) {
        BIOME_SOURCES.register(Objects.requireNonNull(eventBus));
    }

    /**
     * Gets the resource location for the zone biome source.
     */
    public static ResourceLocation getZoneBiomeSourceId() {
        return ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "zone_biome_source");
    }
}
