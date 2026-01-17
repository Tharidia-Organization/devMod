package com.devmod.area.builder;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

import com.devmod.area.data.AreaDefinition;

/**
 * Represents a build request waiting in the queue.
 *
 * @param areaId        The area UUID
 * @param definition    The area definition to build
 * @param levelId       The dimension resource location
 * @param playerId      The requesting player's UUID (null for server-initiated)
 * @param clearFirst    Whether to clear the area before building
 * @param forceMultiTick Whether to force multi-tick building
 * @param queuedAt      Timestamp when this was queued
 */
public record QueuedBuild(
    @Nonnull UUID areaId,
    @Nonnull AreaDefinition definition,
    @Nonnull ResourceLocation levelId,
    @Nullable UUID playerId,
    boolean clearFirst,
    boolean forceMultiTick,
    long queuedAt
) {
    public QueuedBuild {
        java.util.Objects.requireNonNull(areaId, "areaId");
        java.util.Objects.requireNonNull(definition, "definition");
        java.util.Objects.requireNonNull(levelId, "levelId");
    }
}
