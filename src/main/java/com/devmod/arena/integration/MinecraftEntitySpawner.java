package com.devmod.arena.integration;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;

import com.devmod.arena.builder.ArenaBuilder;
public class MinecraftEntitySpawner implements ArenaBuilder.EntitySpawner {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftEntitySpawner.class);

    private final ServerLevel level;
    private final Map<UUID, Entity> spawnedEntities;
    private final MobSpawnType spawnType;

    /**
     * Creates a new entity spawner for the given level.
     *
     * @param level The server level to spawn entities in
     */
    public MinecraftEntitySpawner(ServerLevel level) {
        this(level, MobSpawnType.COMMAND);
    }

    /**
     * Creates a new entity spawner with custom spawn type.
     *
     * @param level The server level to spawn entities in
     * @param spawnType The spawn reason for mobs
     */
    public MinecraftEntitySpawner(ServerLevel level, MobSpawnType spawnType) {
        this.level = Objects.requireNonNull(level, "level");
        this.spawnedEntities = new ConcurrentHashMap<>();
        this.spawnType = spawnType;
    }

    @Override
    public UUID spawnEntity(int x, int y, int z, String entityType) {
        // Resolve entity type
        EntityType<?> type = resolveEntityType(entityType);
        if (type == null) {
            LOGGER.warn("Unknown entity type '{}', spawn failed", entityType);
            return null;
        }

        // Create entity at spawn position
        BlockPos spawnPos = new BlockPos(x, y, z);
        Entity entity = type.create(Objects.requireNonNull(level), null, spawnPos, Objects.requireNonNull(spawnType), false, false);
        if (entity == null) {
            LOGGER.warn("Failed to create entity of type '{}'", entityType);
            return null;
        }

        // Fine-tune position (center of block)
        entity.setPos(x + 0.5, y, z + 0.5);
        entity.setYRot(level.random.nextFloat() * 360.0F);

        // Add to world
        if (!level.addFreshEntity(entity)) {
            LOGGER.warn("Failed to add entity '{}' to world at ({}, {}, {})", entityType, x, y, z);
            return null;
        }

        // Track for removal
        UUID entityId = entity.getUUID();
        spawnedEntities.put(entityId, entity);

        LOGGER.debug("Spawned entity '{}' with UUID {} at ({}, {}, {})", entityType, entityId, x, y, z);
        return entityId;
    }

    @Override
    public boolean removeEntity(UUID entityId) {
        if (entityId == null) {
            return false;
        }

        // Try cached entity first
        Entity entity = spawnedEntities.remove(entityId);
        if (entity != null) {
            entity.discard();
            return true;
        }

        // Fallback: search in level
        Entity levelEntity = level.getEntity(entityId);
        if (levelEntity != null) {
            levelEntity.discard();
            return true;
        }

        LOGGER.debug("Entity {} not found for removal", entityId);
        return false;
    }

    /**
     * Resolves an entity type string to an EntityType.
     *
     * <p>Supports formats:</p>
     * <ul>
     *   <li>"minecraft:zombie" - Full ResourceLocation</li>
     *   <li>"zombie" - Defaults to minecraft namespace</li>
     * </ul>
     */
    @Nullable
    private EntityType<?> resolveEntityType(String entityType) {
        if (entityType == null || entityType.isEmpty()) {
            return null;
        }

        // Handle namespace
        String fullName = entityType.contains(":") ? entityType : "minecraft:" + entityType;

        // Parse ResourceLocation
        ResourceLocation loc = ResourceLocation.tryParse(fullName);
        if (loc == null) {
            LOGGER.warn("Invalid entity type format: {}", entityType);
            return null;
        }

        // Get from registry
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(loc)) {
            return null;
        }

        return BuiltInRegistries.ENTITY_TYPE.get(loc);
    }

    /**
     * Removes all spawned entities.
     */
    public void removeAllSpawned() {
        for (Entity entity : spawnedEntities.values()) {
            if (entity != null && entity.isAlive()) {
                entity.discard();
            }
        }
        spawnedEntities.clear();
    }

    /**
     * Returns the number of tracked spawned entities.
     */
    public int getSpawnedEntityCount() {
        return spawnedEntities.size();
    }

    /**
     * Returns the server level this spawner operates on.
     */
    public ServerLevel getLevel() {
        return level;
    }

    /**
     * Searches for an entity by UUID in the level.
     */
    @Nullable
    public Entity findEntity(UUID entityId) {
        Entity cached = spawnedEntities.get(entityId);
        if (cached != null && cached.isAlive()) {
            return cached;
        }
        return level.getEntity(Objects.requireNonNull(entityId));
    }
}
