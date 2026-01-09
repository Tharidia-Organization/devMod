package com.devmod.clone.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.devmod.clone.data.BioscanData;
import com.devmod.clone.entity.PlayerCloneEntity;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.entity.ModEntities;

/**
 * Integration for spawning player clones in Endurance quests.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Boss wave with player clone as opponent</li>
 *   <li>Practice mode with clone targets</li>
 *   <li>Special wave types featuring clones</li>
 * </ul>
 */
public final class EnduranceCloneWave {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceCloneWave.class);

    public static final EnduranceCloneWave INSTANCE = new EnduranceCloneWave();

    private EnduranceCloneWave() {}

    /**
     * Spawn player clones as wave enemies.
     *
     * @param session     The active quest session
     * @param positions   Available spawn positions
     * @param count       Number of clones to spawn
     * @param difficulty  Difficulty multiplier (1.0 = normal)
     * @return List of spawned clone UUIDs
     */
    @Nonnull
    public List<UUID> spawnCloneWave(
            @Nonnull EnduranceQuestManager.ActiveQuestSession session,
            @Nonnull List<BlockPos> positions,
            int count,
            float difficulty) {

        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(positions, "positions");

        if (positions.isEmpty() || count <= 0) {
            return List.of();
        }

        ServerLevel level = session.getArena() != null ? session.getArena().getLevel() : null;
        if (level == null) {
            LOGGER.warn("Cannot spawn clone wave: no level available");
            return List.of();
        }

        List<UUID> spawnedClones = new ArrayList<>();
        int spawnLimit = Math.min(count, positions.size());

        for (int i = 0; i < spawnLimit; i++) {
            BlockPos pos = positions.get(i);
            UUID cloneId = spawnEnemyClone(level, pos, difficulty, session.getPlayerId());
            if (cloneId != null) {
                spawnedClones.add(cloneId);
            }
        }

        LOGGER.info("[EnduranceClone] Spawned {} enemy clones for wave (difficulty: {})",
            spawnedClones.size(), difficulty);

        return spawnedClones;
    }

    /**
     * Spawn a single enemy clone at the given position.
     */
    @Nullable
    private UUID spawnEnemyClone(
            @Nonnull ServerLevel level,
            @Nonnull BlockPos pos,
            float difficulty,
            @Nullable UUID targetPlayerId) {

        PlayerCloneEntity clone = ModEntities.PLAYER_CLONE.get().create(level);
        if (clone == null) {
            return null;
        }

        // Position
        clone.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        clone.setYRot(level.random.nextFloat() * 360.0F);

        // Enemy clone - no owner, aggressive mode
        clone.setTame(false, false);
        clone.setBehaviorMode(PlayerCloneEntity.MODE_ATTACK);

        // Scale stats based on difficulty
        applyDifficultyScaling(clone, difficulty);

        // Tag for endurance tracking
        clone.getPersistentData().putBoolean("endurance_clone", true);
        clone.getPersistentData().putFloat("endurance_difficulty", difficulty);

        // Set target player if available
        if (targetPlayerId != null) {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(targetPlayerId);
            if (target != null) {
                clone.setTarget(target);
                // Use player skin for the clone
                clone.setOriginalPlayer(targetPlayerId, target.getName().getString());
            }
        }

        // Add to world
        if (!level.addFreshEntity(clone)) {
            LOGGER.warn("Failed to add enemy clone to world at {}", pos);
            return null;
        }

        return clone.getUUID();
    }

    /**
     * Spawn a boss clone (stronger, larger, glowing).
     */
    @Nullable
    public UUID spawnBossClone(
            @Nonnull ServerLevel level,
            @Nonnull BlockPos pos,
            @Nullable UUID originalPlayerUUID,
            @Nullable String playerName,
            int waveNumber) {

        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");

        PlayerCloneEntity boss = ModEntities.PLAYER_CLONE.get().create(level);
        if (boss == null) {
            return null;
        }

        // Position
        boss.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        // Boss configuration
        boss.setTame(false, false);
        boss.setBehaviorMode(PlayerCloneEntity.MODE_ATTACK);

        // Set player skin
        if (originalPlayerUUID != null || playerName != null) {
            boss.setOriginalPlayer(originalPlayerUUID, playerName);
        }

        // Boss stats scaling (much stronger than normal clones)
        float bossMultiplier = 2.0f + (waveNumber * 0.2f);
        applyDifficultyScaling(boss, bossMultiplier);

        // Visual indicators
        boss.setGlowingTag(true);
        boss.setCustomName(net.minecraft.network.chat.Component.literal(
            (playerName != null ? playerName : "Shadow") + " (Boss)"
        ));
        boss.setCustomNameVisible(true);

        // Tag for endurance boss tracking
        boss.getPersistentData().putBoolean("endurance_clone", true);
        boss.getPersistentData().putBoolean("endurance_boss", true);
        boss.getPersistentData().putInt("endurance_wave", waveNumber);

        // Add to world
        if (!level.addFreshEntity(boss)) {
            LOGGER.warn("Failed to add boss clone to world at {}", pos);
            return null;
        }

        LOGGER.info("[EnduranceClone] Spawned boss clone '{}' at {} (wave: {}, mult: {})",
            playerName, pos, waveNumber, bossMultiplier);

        return boss.getUUID();
    }

    /**
     * Spawn clones from BioscanData for a custom wave.
     */
    @Nonnull
    public List<UUID> spawnFromBioscanWave(
            @Nonnull ServerLevel level,
            @Nonnull List<BlockPos> positions,
            @Nonnull BioscanData bioscanData,
            int count,
            float difficulty) {

        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(bioscanData, "bioscanData");

        if (positions.isEmpty() || count <= 0) {
            return List.of();
        }

        CloneEntitySpawner spawner = new CloneEntitySpawner(level);
        List<UUID> spawnedIds = new ArrayList<>();

        int spawnLimit = Math.min(count, positions.size());
        for (int i = 0; i < spawnLimit; i++) {
            BlockPos pos = positions.get(i);
            UUID entityId = spawner.spawnFromBioscan(pos, bioscanData);
            if (entityId != null) {
                // Apply difficulty scaling if the entity is alive
                var entity = level.getEntity(entityId);
                if (entity instanceof net.minecraft.world.entity.Mob mob) {
                    applyDifficultyScaling(mob, difficulty);
                    // Tag for endurance tracking
                    mob.getPersistentData().putBoolean("endurance_clone", true);
                    mob.getPersistentData().putString("endurance_clone_source",
                        bioscanData.entityTypeId().toString());
                }
                spawnedIds.add(entityId);
            }
        }

        LOGGER.info("[EnduranceClone] Spawned {} clones from bioscan {} (difficulty: {})",
            spawnedIds.size(), bioscanData.entityTypeId(), difficulty);

        return spawnedIds;
    }

    /**
     * Apply difficulty scaling to a mob.
     */
    private void applyDifficultyScaling(net.minecraft.world.entity.Mob mob, float multiplier) {
        if (mob == null || multiplier <= 0) {
            return;
        }

        // Scale health
        var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            double baseHealth = healthAttr.getBaseValue();
            healthAttr.setBaseValue(baseHealth * multiplier);
            mob.setHealth(mob.getMaxHealth());
        }

        // Scale attack damage
        var attackAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            double baseDamage = attackAttr.getBaseValue();
            attackAttr.setBaseValue(baseDamage * Math.sqrt(multiplier));
        }

        // Slight speed boost for higher difficulties
        if (multiplier > 1.5f) {
            var speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                double baseSpeed = speedAttr.getBaseValue();
                speedAttr.setBaseValue(baseSpeed * (1.0 + (multiplier - 1.0) * 0.1));
            }
        }
    }

    /**
     * Check if an entity is an endurance clone.
     */
    public boolean isEnduranceClone(@Nullable net.minecraft.world.entity.Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity.getPersistentData().getBoolean("endurance_clone");
    }

    /**
     * Check if an entity is an endurance boss clone.
     */
    public boolean isEnduranceBossClone(@Nullable net.minecraft.world.entity.Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity.getPersistentData().getBoolean("endurance_clone")
            && entity.getPersistentData().getBoolean("endurance_boss");
    }
}
