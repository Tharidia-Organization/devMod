package com.frenkvs.devmod.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Mixin accessor for MinecraftServer to access internal fields.
 * This is required for dynamic dimension creation/destruction.
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {

    /**
     * Get the levels map for reading/writing dimensions.
     */
    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> getLevels();

    /**
     * Get the executor for async operations.
     */
    @Accessor("executor")
    Executor getExecutor();

    /**
     * Get the storage source for dimension data.
     */
    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess getStorageSource();
}
