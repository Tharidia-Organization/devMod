package com.devmod.clone.integration;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;

import com.devmod.arena.builder.ArenaBuilder;
import com.devmod.clone.data.BioscanData;
import com.devmod.clone.entity.PlayerCloneEntity;
import com.devmod.entity.ModEntities;

/**
 * Entity spawner that creates clones from BioscanData.
 * Integrates the Clone system with Arena/Endurance spawning.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li>Clone from BioscanData - spawns entity matching scanned data</li>
 *   <li>Player Clone - spawns PlayerCloneEntity companion</li>
 * </ul>
 */
public class CloneEntitySpawner implements ArenaBuilder.EntitySpawner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloneEntitySpawner.class);

    private final ServerLevel level;
    private final Map<UUID, Entity> spawnedClones;
    private final @Nullable UUID ownerId;

    /**
     * Creates a clone spawner without an owner (arena mode).
     */
    public CloneEntitySpawner(@Nonnull ServerLevel level) {
        this(level, null);
    }

    /**
     * Creates a clone spawner with an owner (companion mode).
     */
    public CloneEntitySpawner(@Nonnull ServerLevel level, @Nullable UUID ownerId) {
        this.level = Objects.requireNonNull(level, "level");
        this.spawnedClones = new ConcurrentHashMap<>();
        this.ownerId = ownerId;
    }

    @Override
    public UUID spawnEntity(int x, int y, int z, String entityType) {
        // For standard entity types, delegate to normal spawning
        if (entityType == null || !entityType.startsWith("clone:")) {
            return spawnStandardEntity(x, y, z, entityType);
        }

        // Parse clone type
        String cloneSpec = entityType.substring(6);
        if ("player".equals(cloneSpec)) {
            return spawnPlayerClone(x, y, z, null, null);
        }

        LOGGER.warn("Unknown clone specification: {}", cloneSpec);
        return null;
    }

    /**
     * Spawn a clone from BioscanData.
     *
     * @param pos       Spawn position
     * @param bioscanData The scanned entity data
     * @return UUID of spawned entity, or null if failed
     */
    @Nullable
    public UUID spawnFromBioscan(@Nonnull BlockPos pos, @Nonnull BioscanData bioscanData) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(bioscanData, "bioscanData");

        // Resolve entity type from bioscan data
        EntityType<?> entityType = bioscanData.getEntityType();
        if (entityType == null) {
            LOGGER.warn("Cannot resolve entity type from bioscan: {}", bioscanData.entityTypeId());
            return null;
        }

        // If it's a player, spawn a PlayerCloneEntity instead
        if (entityType == EntityType.PLAYER) {
            return spawnPlayerClone(
                pos.getX(), pos.getY(), pos.getZ(),
                bioscanData.playerUUID(),
                bioscanData.entityName()
            );
        }

        // Spawn the entity from bioscan data
        Entity entity = entityType.create(level);
        if (entity == null) {
            LOGGER.warn("Failed to create entity from bioscan: {}", bioscanData.entityTypeId());
            return null;
        }

        // Apply stored NBT if available
        CompoundTag nbt = bioscanData.entityNBT();
        if (nbt != null && entity instanceof LivingEntity) {
            try {
                // Create a copy to avoid modifying the original
                CompoundTag loadTag = nbt.copy();
                // Remove position/rotation to avoid teleporting
                loadTag.remove("Pos");
                loadTag.remove("Rotation");
                loadTag.remove("UUID");
                entity.load(loadTag);
            } catch (Exception e) {
                LOGGER.warn("Failed to apply NBT to cloned entity: {}", e.getMessage());
            }
        }

        // Position the entity
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        entity.setYRot(level.random.nextFloat() * 360.0F);

        // Finalize spawn for mobs
        if (entity instanceof Mob mob) {
            finalizeMobSpawn(mob, pos);
        }

        // Tag as clone
        entity.getPersistentData().putBoolean("devmod_clone", true);
        entity.getPersistentData().putString("devmod_clone_source", bioscanData.entityTypeId().toString());
        entity.getPersistentData().putLong("devmod_clone_scan_time", bioscanData.scanTime());

        // Add to world
        if (!level.addFreshEntity(entity)) {
            LOGGER.warn("Failed to add cloned entity to world");
            return null;
        }

        UUID entityId = entity.getUUID();
        spawnedClones.put(entityId, entity);

        LOGGER.debug("Spawned clone of {} at {}", bioscanData.entityTypeId(), pos);
        return entityId;
    }

    /**
     * Spawn a PlayerCloneEntity companion.
     */
    @Nullable
    public UUID spawnPlayerClone(int x, int y, int z,
                                  @Nullable UUID originalUUID,
                                  @Nullable String skinName) {
        PlayerCloneEntity clone = ModEntities.PLAYER_CLONE.get().create(level);
        if (clone == null) {
            LOGGER.warn("Failed to create PlayerCloneEntity");
            return null;
        }

        // Set position
        clone.setPos(x + 0.5, y, z + 0.5);
        clone.setYRot(level.random.nextFloat() * 360.0F);

        // Set owner if available
        if (ownerId != null) {
            Player owner = level.getPlayerByUUID(ownerId);
            if (owner != null) {
                clone.setOwnerUUID(ownerId);
                clone.setTame(true, false);
            }
        }

        // Set original player data for skin
        if (originalUUID != null || skinName != null) {
            clone.setOriginalPlayer(originalUUID, skinName);
        }

        // Tag as arena clone if no owner
        if (ownerId == null) {
            clone.getPersistentData().putBoolean("devmod_arena_clone", true);
        }

        // Add to world
        if (!level.addFreshEntity(clone)) {
            LOGGER.warn("Failed to add PlayerCloneEntity to world");
            return null;
        }

        UUID entityId = clone.getUUID();
        spawnedClones.put(entityId, clone);

        LOGGER.debug("Spawned PlayerCloneEntity at ({}, {}, {})", x, y, z);
        return entityId;
    }

    /**
     * Spawn a standard entity (non-clone).
     */
    @Nullable
    private UUID spawnStandardEntity(int x, int y, int z, String entityType) {
        if (entityType == null || entityType.isEmpty()) {
            return null;
        }

        var loc = net.minecraft.resources.ResourceLocation.tryParse(
            entityType.contains(":") ? entityType : "minecraft:" + entityType
        );
        if (loc == null) {
            return null;
        }

        if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(loc)) {
            return null;
        }

        EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(loc);
        BlockPos pos = new BlockPos(x, y, z);
        Entity entity = type.create(level, null, pos, MobSpawnType.COMMAND, false, false);
        if (entity == null) {
            return null;
        }

        entity.setPos(x + 0.5, y, z + 0.5);

        if (!level.addFreshEntity(entity)) {
            return null;
        }

        UUID entityId = entity.getUUID();
        spawnedClones.put(entityId, entity);
        return entityId;
    }

    @SuppressWarnings("deprecation")
    private void finalizeMobSpawn(Mob mob, BlockPos pos) {
        mob.finalizeSpawn(level,
            Objects.requireNonNull(level.getCurrentDifficultyAt(pos)),
            MobSpawnType.MOB_SUMMONED, null);
    }

    @Override
    public boolean removeEntity(UUID entityId) {
        if (entityId == null) {
            return false;
        }

        Entity entity = spawnedClones.remove(entityId);
        if (entity != null) {
            entity.discard();
            return true;
        }

        Entity levelEntity = level.getEntity(entityId);
        if (levelEntity != null) {
            levelEntity.discard();
            return true;
        }

        return false;
    }

    /**
     * Remove all spawned clones.
     */
    public void removeAllClones() {
        for (Entity entity : spawnedClones.values()) {
            if (entity != null && entity.isAlive()) {
                entity.discard();
            }
        }
        spawnedClones.clear();
    }

    /**
     * Get count of spawned clones.
     */
    public int getSpawnedCount() {
        return spawnedClones.size();
    }

    /**
     * Get the server level.
     */
    public ServerLevel getLevel() {
        return level;
    }
}
