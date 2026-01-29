package com.devmod.area.data;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents an entity spawn point within an area.
 * Used to configure where mobs or entities should spawn.
 */
public record EntitySpawnPoint(
    @Nonnull UUID id,
    @Nonnull ResourceLocation entityType,
    @Nonnull BlockPos position,
    int spawnCount,
    int respawnDelayTicks,
    int spawnRadius,
    boolean enabled
) {
    /**
     * Minimum spawn count.
     */
    public static final int MIN_SPAWN_COUNT = 1;

    /**
     * Maximum spawn count.
     */
    public static final int MAX_SPAWN_COUNT = 64;

    /**
     * Default spawn count.
     */
    public static final int DEFAULT_SPAWN_COUNT = 1;

    /**
     * Minimum respawn delay in ticks (0 = no respawn).
     */
    public static final int MIN_RESPAWN_DELAY = 0;

    /**
     * Maximum respawn delay in ticks (1 hour).
     */
    public static final int MAX_RESPAWN_DELAY = 72000;

    /**
     * Default respawn delay (5 minutes = 6000 ticks).
     */
    public static final int DEFAULT_RESPAWN_DELAY = 6000;

    /**
     * Minimum spawn radius.
     */
    public static final int MIN_SPAWN_RADIUS = 0;

    /**
     * Maximum spawn radius.
     */
    public static final int MAX_SPAWN_RADIUS = 32;

    /**
     * Default spawn radius.
     */
    public static final int DEFAULT_SPAWN_RADIUS = 2;

    public static final Codec<EntitySpawnPoint> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(EntitySpawnPoint::id),
            ResourceLocation.CODEC.fieldOf("entityType").forGetter(EntitySpawnPoint::entityType),
            BlockPos.CODEC.fieldOf("position").forGetter(EntitySpawnPoint::position),
            Codec.intRange(MIN_SPAWN_COUNT, MAX_SPAWN_COUNT).fieldOf("spawnCount").forGetter(EntitySpawnPoint::spawnCount),
            Codec.intRange(MIN_RESPAWN_DELAY, MAX_RESPAWN_DELAY).fieldOf("respawnDelayTicks").forGetter(EntitySpawnPoint::respawnDelayTicks),
            Codec.intRange(MIN_SPAWN_RADIUS, MAX_SPAWN_RADIUS).fieldOf("spawnRadius").forGetter(EntitySpawnPoint::spawnRadius),
            Codec.BOOL.fieldOf("enabled").forGetter(EntitySpawnPoint::enabled)
        ).apply(instance, EntitySpawnPoint::new)
    );

    public EntitySpawnPoint {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(entityType, "entityType cannot be null");
        Objects.requireNonNull(position, "position cannot be null");
        spawnCount = Math.max(MIN_SPAWN_COUNT, Math.min(MAX_SPAWN_COUNT, spawnCount));
        respawnDelayTicks = Math.max(MIN_RESPAWN_DELAY, Math.min(MAX_RESPAWN_DELAY, respawnDelayTicks));
        spawnRadius = Math.max(MIN_SPAWN_RADIUS, Math.min(MAX_SPAWN_RADIUS, spawnRadius));
    }

    /**
     * Creates a new spawn point with default values.
     */
    public static EntitySpawnPoint create(@Nonnull ResourceLocation entityType, @Nonnull BlockPos position) {
        return new EntitySpawnPoint(
            UUID.randomUUID(),
            Objects.requireNonNull(entityType),
            Objects.requireNonNull(position),
            DEFAULT_SPAWN_COUNT,
            DEFAULT_RESPAWN_DELAY,
            DEFAULT_SPAWN_RADIUS,
            true
        );
    }

    /**
     * Creates a zombie spawn point at the given position.
     */
    public static EntitySpawnPoint zombie(@Nonnull BlockPos position) {
        return create(ResourceLocation.withDefaultNamespace("zombie"), position);
    }

    /**
     * Creates a skeleton spawn point at the given position.
     */
    public static EntitySpawnPoint skeleton(@Nonnull BlockPos position) {
        return create(ResourceLocation.withDefaultNamespace("skeleton"), position);
    }

    /**
     * Gets the display name of the entity type.
     */
    public String getEntityDisplayName() {
        String path = entityType.getPath();
        // Convert snake_case to Title Case
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Gets respawn delay formatted as time string.
     */
    public String getFormattedRespawnDelay() {
        if (respawnDelayTicks == 0) {
            return "No respawn";
        }
        int seconds = respawnDelayTicks / 20;
        if (seconds < 60) {
            return seconds + "s";
        }
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        if (minutes < 60) {
            return remainingSeconds > 0 ? String.format("%dm %ds", minutes, remainingSeconds) : minutes + "m";
        }
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        return remainingMinutes > 0 ? String.format("%dh %dm", hours, remainingMinutes) : hours + "h";
    }

    /**
     * Creates a copy with a different entity type.
     */
    public EntitySpawnPoint withEntityType(@Nonnull ResourceLocation entityType) {
        return new EntitySpawnPoint(id, Objects.requireNonNull(entityType), position, spawnCount, respawnDelayTicks, spawnRadius, enabled);
    }

    /**
     * Creates a copy with a different position.
     */
    public EntitySpawnPoint withPosition(@Nonnull BlockPos position) {
        return new EntitySpawnPoint(id, entityType, Objects.requireNonNull(position), spawnCount, respawnDelayTicks, spawnRadius, enabled);
    }

    /**
     * Creates a copy with a different spawn count.
     */
    public EntitySpawnPoint withSpawnCount(int spawnCount) {
        return new EntitySpawnPoint(id, entityType, position, spawnCount, respawnDelayTicks, spawnRadius, enabled);
    }

    /**
     * Creates a copy with a different respawn delay.
     */
    public EntitySpawnPoint withRespawnDelay(int respawnDelayTicks) {
        return new EntitySpawnPoint(id, entityType, position, spawnCount, respawnDelayTicks, spawnRadius, enabled);
    }

    /**
     * Creates a copy with a different spawn radius.
     */
    public EntitySpawnPoint withSpawnRadius(int spawnRadius) {
        return new EntitySpawnPoint(id, entityType, position, spawnCount, respawnDelayTicks, spawnRadius, enabled);
    }

    /**
     * Creates a copy with enabled state changed.
     */
    public EntitySpawnPoint withEnabled(boolean enabled) {
        return new EntitySpawnPoint(id, entityType, position, spawnCount, respawnDelayTicks, spawnRadius, enabled);
    }
}
