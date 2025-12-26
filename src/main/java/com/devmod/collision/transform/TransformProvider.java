package com.devmod.collision.transform;

import javax.annotation.Nonnull;

import net.minecraft.world.entity.LivingEntity;

public interface TransformProvider {

    /**
     * Extracts current animation transforms for an entity.
     *
     * @param entity      The entity
     * @param partialTick Partial tick for interpolation (0-1)
     * @param currentTick Current game tick
     * @return Snapshot of all bone transforms
     */
    @Nonnull
    AnimationSnapshot extractTransforms(@Nonnull LivingEntity entity,
                                        float partialTick,
                                        long currentTick);

    /**
     * Clears cached transforms for an entity.
     *
     * @param entityId The entity ID
     */
    void clearCache(int entityId);

    /**
     * Clears all cached transforms.
     */
    void clearAllCaches();

    /**
     * Gets the current cache size for diagnostics.
     *
     * @return Number of cached entries
     */
    int getCacheSize();
}
