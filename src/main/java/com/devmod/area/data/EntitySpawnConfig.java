package com.devmod.area.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Configuration for entity spawn points within an area.
 * Manages a list of spawn points with operations for adding, removing, and updating.
 */
public record EntitySpawnConfig(
    @Nonnull List<EntitySpawnPoint> spawnPoints,
    boolean globalEnabled
) {
    /**
     * Maximum number of spawn points per area.
     */
    public static final int MAX_SPAWN_POINTS = 32;

    public static final Codec<EntitySpawnConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            EntitySpawnPoint.CODEC.listOf().fieldOf("spawnPoints").forGetter(EntitySpawnConfig::spawnPoints),
            Codec.BOOL.fieldOf("globalEnabled").forGetter(EntitySpawnConfig::globalEnabled)
        ).apply(instance, EntitySpawnConfig::new)
    );

    public EntitySpawnConfig {
        Objects.requireNonNull(spawnPoints, "spawnPoints cannot be null");
        spawnPoints = List.copyOf(spawnPoints); // Defensive copy
    }

    /**
     * Creates an empty spawn config.
     */
    public static EntitySpawnConfig empty() {
        return new EntitySpawnConfig(Collections.emptyList(), true);
    }

    /**
     * Gets the number of spawn points.
     */
    public int getSpawnPointCount() {
        return spawnPoints.size();
    }

    /**
     * Checks if spawn points can be added.
     */
    public boolean canAddSpawnPoint() {
        return spawnPoints.size() < MAX_SPAWN_POINTS;
    }

    /**
     * Gets a spawn point by index.
     */
    @Nonnull
    public EntitySpawnPoint getSpawnPoint(int index) {
        if (index < 0 || index >= spawnPoints.size()) {
            throw new IndexOutOfBoundsException("Spawn point index " + index + " out of bounds for " + spawnPoints.size() + " points");
        }
        return spawnPoints.get(index);
    }

    /**
     * Finds a spawn point by ID.
     */
    @Nullable
    public EntitySpawnPoint findById(@Nonnull UUID id) {
        return spawnPoints.stream()
            .filter(sp -> sp.id().equals(id))
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets the index of a spawn point by ID.
     */
    public int indexOfId(@Nonnull UUID id) {
        for (int i = 0; i < spawnPoints.size(); i++) {
            if (spawnPoints.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Creates a copy with a spawn point added.
     */
    public EntitySpawnConfig withSpawnPointAdded(@Nonnull EntitySpawnPoint spawnPoint) {
        if (spawnPoints.size() >= MAX_SPAWN_POINTS) {
            return this;
        }
        List<EntitySpawnPoint> newPoints = new ArrayList<>(spawnPoints);
        newPoints.add(Objects.requireNonNull(spawnPoint));
        return new EntitySpawnConfig(newPoints, globalEnabled);
    }

    /**
     * Creates a copy with a new spawn point at the given position.
     */
    public EntitySpawnConfig withNewSpawnPoint(@Nonnull ResourceLocation entityType, @Nonnull BlockPos position) {
        return withSpawnPointAdded(EntitySpawnPoint.create(entityType, position));
    }

    /**
     * Creates a copy with a spawn point removed by index.
     */
    public EntitySpawnConfig withSpawnPointRemoved(int index) {
        if (index < 0 || index >= spawnPoints.size()) {
            return this;
        }
        List<EntitySpawnPoint> newPoints = new ArrayList<>(spawnPoints);
        newPoints.remove(index);
        return new EntitySpawnConfig(newPoints, globalEnabled);
    }

    /**
     * Creates a copy with a spawn point removed by ID.
     */
    public EntitySpawnConfig withSpawnPointRemoved(@Nonnull UUID id) {
        int index = indexOfId(id);
        if (index < 0) {
            return this;
        }
        return withSpawnPointRemoved(index);
    }

    /**
     * Creates a copy with a spawn point updated.
     */
    public EntitySpawnConfig withSpawnPointUpdated(int index, @Nonnull EntitySpawnPoint spawnPoint) {
        if (index < 0 || index >= spawnPoints.size()) {
            return this;
        }
        List<EntitySpawnPoint> newPoints = new ArrayList<>(spawnPoints);
        newPoints.set(index, Objects.requireNonNull(spawnPoint));
        return new EntitySpawnConfig(newPoints, globalEnabled);
    }

    /**
     * Creates a copy with a spawn point updated by ID.
     */
    public EntitySpawnConfig withSpawnPointUpdated(@Nonnull UUID id, @Nonnull EntitySpawnPoint spawnPoint) {
        int index = indexOfId(id);
        if (index < 0) {
            return this;
        }
        return withSpawnPointUpdated(index, spawnPoint);
    }

    /**
     * Creates a copy with global enabled state changed.
     */
    public EntitySpawnConfig withGlobalEnabled(boolean enabled) {
        return new EntitySpawnConfig(spawnPoints, enabled);
    }

    /**
     * Gets the total number of entities that would be spawned.
     */
    public int getTotalSpawnCount() {
        return spawnPoints.stream()
            .filter(EntitySpawnPoint::enabled)
            .mapToInt(EntitySpawnPoint::spawnCount)
            .sum();
    }

    /**
     * Gets all enabled spawn points.
     */
    @Nonnull
    public List<EntitySpawnPoint> getEnabledSpawnPoints() {
        if (!globalEnabled) {
            return Collections.emptyList();
        }
        return spawnPoints.stream()
            .filter(EntitySpawnPoint::enabled)
            .toList();
    }
}
