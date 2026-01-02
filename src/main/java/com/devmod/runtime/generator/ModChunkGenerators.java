package com.devmod.runtime.generator;

import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkGenerator;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;

/**
 * Registers custom ChunkGenerator codecs for the mod.
 * Required for proper serialization of arena dimensions in world saves.
 */
public class ModChunkGenerators {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Objects.requireNonNull(Registries.CHUNK_GENERATOR, "CHUNK_GENERATOR registry key"), DevMod.MODID);

    /** Arena flat chunk generator for flat terrain with zone biomes */
    public static final Supplier<MapCodec<? extends ChunkGenerator>> ARENA_FLAT =
            CHUNK_GENERATORS.register("arena_flat", () -> ArenaFlatChunkGenerator.CODEC);

    // Future: Arena natural chunk generator
    // public static final Supplier<MapCodec<? extends ChunkGenerator>> ARENA_NATURAL =
    //         CHUNK_GENERATORS.register("arena_natural", () -> ArenaNaturalChunkGenerator.CODEC);

    /**
     * Registers the chunk generators with the event bus.
     * Call this from the mod constructor.
     */
    public static void register(@Nonnull IEventBus eventBus) {
        CHUNK_GENERATORS.register(Objects.requireNonNull(eventBus, "eventBus cannot be null"));
    }

    /**
     * Gets the resource location for the arena flat generator.
     */
    public static ResourceLocation getArenaFlatId() {
        return ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "arena_flat");
    }
}
