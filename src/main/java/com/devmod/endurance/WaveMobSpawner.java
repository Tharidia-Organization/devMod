package com.devmod.endurance;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.arena.spawn.SpawnOccupancyTracker;
import com.devmod.compat.mods.dummmmmmy.DummmmmmyCompat;
import com.devmod.endurance.EnduranceLogger.Phase;
import com.devmod.endurance.config.EffectiveConfig;
import com.devmod.endurance.spawn.MobDrivePolicy;
import com.devmod.mob.MobRequirements;
import com.devmod.mob.MobRequirementsRegistry;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;

/**
 * Handles mob spawning, stat scaling, affix application, and AI awakening
 * for endurance wave mobs. Extracted from WaveManager to isolate
 * spawning/configuration concerns.
 */
final class WaveMobSpawner {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaveMobSpawner.class);

    static final WaveMobSpawner INSTANCE = new WaveMobSpawner();

    private final Random random = new Random();
    private final WaveSpawnPositionResolver positionResolver = WaveSpawnPositionResolver.INSTANCE;

    private WaveMobSpawner() {}

    /**
     * Spawn mobs for a scheduled wave batch.
     */
    void spawnWaveBatch(WaveManager.WaveState waveState,
                        ArenaContext arena,
                        @Nullable ArenaHandle handle,
                        WaveDirector.SpawnBatch batch) {
        if (batch == null) {
            return;
        }
        spawnWaveMobs(waveState, arena, handle, batch.count(), batch.affix(), batch.role(), batch.objectiveTarget());
    }

    /**
     * Spawn mobs for a wave. Verifies each spawn and logs failures for debugging.
     */
    void spawnWaveMobs(WaveManager.WaveState waveState,
                       ArenaContext arena,
                       @Nullable ArenaHandle handle,
                       int count,
                       SpawnAffix affix,
                       WaveDirector.SpawnRole role,
                       boolean objectiveTarget) {
        if (count <= 0 || arena == null) {
            return;
        }

        // Practice mode: spawn training dummies instead of real mobs
        if (waveState.isPracticeMode() && DummmmmmyCompat.isAvailable()) {
            spawnPracticeDummies(waveState, arena, count);
            return;
        }

        SpawnAffix safeAffix = affix != null ? affix : SpawnAffix.BASE;
        WaveManager.SpawnContext spawnContext = waveState.getSpawnContext();
        if (spawnContext == null || spawnContext.positions().isEmpty()) {
            LOGGER.error("[EnduranceQuest] No spawn positions available for wave {}", waveState.getWaveNumber());
            EnduranceTelemetryService.INSTANCE.recordWaveBlocked(waveState.getQuest().getQuestId());
            return;
        }

        ServerLevel level = arena.getLevel();
        EnduranceQuestRegistry.MobQuestConfig mobConfig = waveState.getQuest().getMobConfig();
        EntityType<?> entityType = mobConfig.getEntityType();

        // Check if this mob type is enabled in session's mob pool config
        EnduranceQuestManager.ActiveQuestSession session = waveState.getSession();
        if (!EffectiveConfig.isMobEnabled(session, mobConfig.getMobId())) {
            LOGGER.info("[EnduranceQuest] Mob {} is disabled by session config, skipping spawn", mobConfig.getMobId());
            return;
        }

        // Get mob requirements for spawn validation
        MobRequirements mobReqs = MobRequirementsRegistry.INSTANCE.get(entityType);

        // Log if time requirements are not met (non-blocking - arena should control time)
        if (!mobReqs.time().isValidAt(level.getDayTime())) {
            LOGGER.debug("[EnduranceQuest] Time requirement ({}) not optimal for {} at dayTime={}, spawning anyway",
                mobReqs.time(), entityType.getDescriptionId(), level.getDayTime() % 24000);
        }

        List<BlockPos> spawnPositions = spawnContext.positions();
        SpawnOccupancyTracker occupied = new SpawnOccupancyTracker();
        boolean allowReuse = spawnPositions.size() < count;
        WaveManager.SpawnPools pools = spawnContext.pools();

        int successfulSpawns = 0;
        int failedSpawns = 0;

        if (spawnPositions.size() < count) {
            LOGGER.warn("[EnduranceQuest] Only {} valid spawn positions found for {} mobs",
                spawnPositions.size(), count);
        }

        for (int i = 0; i < count; i++) {
            // Safety check: prevent entity overload
            if (!spawnContext.canSpawnMore(successfulSpawns)) {
                LOGGER.warn("[EnduranceQuest] Entity limit reached, stopping spawn at {}/{}",
                    successfulSpawns, count);
                break;
            }

            Entity entity = entityType.create(Objects.requireNonNull(level));
            if (entity instanceof Mob mob) {
                List<BlockPos> candidatePool = positionResolver.chooseSpawnPool(role, pools, mob);
                BlockPos spawnPos = positionResolver.pickValidatedSpawnPosition(
                    candidatePool, i, occupied,
                    spawnContext.runtimeValidator(), spawnContext.slotMap(),
                    spawnContext.template(), level, allowReuse
                );
                if (spawnPos == null && candidatePool != pools.all()) {
                    EnduranceTelemetryService.INSTANCE.recordSpawnFallback(
                        waveState.getQuest().getQuestId(), waveState.getWaveNumber(),
                        positionResolver.resolvePoolTag(candidatePool, pools),
                        handle != null ? handle.templateId() : null, "pool_exhausted"
                    );
                    spawnPos = positionResolver.pickValidatedSpawnPosition(
                        pools.all(), i, occupied,
                        spawnContext.runtimeValidator(), spawnContext.slotMap(),
                        spawnContext.template(), level, allowReuse
                    );
                }
                if (spawnPos == null) {
                    failedSpawns++;
                    EnduranceTelemetryService.INSTANCE.recordSpawnFailure(
                        waveState.getQuest().getQuestId(), waveState.getWaveNumber(),
                        "no_valid_spawn", handle != null ? handle.templateId() : null
                    );
                    continue;
                }

                mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

                // finalizeSpawn FIRST, before any scaling. It used to run after, and for a modded
                // mob that is destructive: finalizeSpawn is where a mob decides its own statistics.
                // Age of Fight's Ashen Court entities allocate a hundred-point stat budget there and
                // write it over MAX_HEALTH, ATTACK_DAMAGE, ATTACK_SPEED, MOVEMENT_SPEED, ARMOR,
                // ARMOR_TOUGHNESS and KNOCKBACK_RESISTANCE, plus setHealth(getMaxHealth()) -- so
                // every affix, multiplayer scale and elite buff was silently discarded, and the
                // "Boss HP final" log below reported values that were about to be replaced.
                //
                // Those particular mobs are no longer scaled at all (see MobDrivePolicy: their own
                // runtime reads those base values back and refuses to move an entity whose budget
                // no longer adds up). The ordering still matters for every other modded mob that
                // sets its stats here and that we do scale.
                finalizeMobSpawn(mob, level, spawnPos);

                applyMobModifiers(mob, waveState);
                applyMultiplayerHPScaling(mob, waveState);
                applySpawnAffix(mob, safeAffix);

                SpawnAffix appliedAffix = safeAffix;
                // FIX #9B: Check ELITE_HUNTER curse - forces all mobs to be elite
                boolean forcedElite = com.devmod.endurance.bargain.DevilsBargainManager.INSTANCE
                    .shouldSpawnElite(waveState.getQuest().getQuestId());
                if (!safeAffix.isElite() && (forcedElite || random.nextFloat() < getEliteChance(mobConfig.getEliteChance(), waveState.getWaveNumber(), waveState.getSession()))) {
                    applyEliteBuffs(mob, waveState.getWaveNumber());
                    appliedAffix = SpawnAffix.ELITE;
                } else if (safeAffix.isElite()) {
                    applyEliteBuffs(mob, waveState.getWaveNumber());
                }

                if (shouldLogBossHp(mobConfig)) {
                    LOGGER.info("[EnduranceQuest] Boss HP final for {}: maxHp={}, currentHp={}, affix={}, wave={}, questId={}",
                        mobConfig.getMobId(), mob.getMaxHealth(), mob.getHealth(), appliedAffix.name(),
                        waveState.getWaveNumber(), waveState.getQuest().getQuestId());
                }

                tagSpawnedMob(mob, waveState, arena, handle, appliedAffix, objectiveTarget);

                boolean added = level.addFreshEntity(mob);
                if (added && mob.isAlive()) {
                    waveState.addSpawnedMob(mob.getUUID(), appliedAffix, objectiveTarget);
                    successfulSpawns++;
                    awakeMobAI(mob, level);
                    EnduranceLogger.mob(Phase.MOB_SPAWN, waveState.getQuest().getQuestId(), waveState.getWaveNumber(),
                        mob.getUUID(), mobConfig.getMobId().getPath(), "Spawned at pos=%s, affix=%s",
                        spawnPos, appliedAffix);
                    if (handle != null) {
                        EnduranceTelemetryService.INSTANCE.recordSpawnHeatmap(
                            waveState.getQuest().getQuestId(), handle, spawnPos);
                    }
                } else {
                    failedSpawns++;
                    LOGGER.warn("[EnduranceQuest] Failed to spawn mob at {} (added={}, alive={})",
                        spawnPos, added, mob.isAlive());
                }
            } else {
                failedSpawns++;
                LOGGER.error("[EnduranceQuest] Failed to create entity of type {}", entityType);
            }
        }

        if (failedSpawns > 0) {
            LOGGER.warn("[EnduranceQuest] Wave {} spawn summary: {} successful, {} failed. Adjusting wave target.",
                waveState.getWaveNumber(), successfulSpawns, failedSpawns);
            waveState.adjustTotalToSpawn(Math.max(1, waveState.getSpawned()));
            waveState.adjustKillTarget(waveState.getTotalToSpawn());
        }

        LOGGER.info("[EnduranceQuest] Wave {} spawned {}/{} mobs successfully",
            waveState.getWaveNumber(), successfulSpawns, count);
    }

    /**
     * Spawn training dummies for practice mode.
     */
    void spawnPracticeDummies(WaveManager.WaveState waveState, ArenaContext arena, int count) {
        if (!DummmmmmyCompat.isAvailable()) {
            LOGGER.warn("[EnduranceQuest] Practice mode requested but Dummmmmmy mod not available");
            return;
        }

        WaveManager.SpawnContext spawnContext = waveState.getSpawnContext();
        if (spawnContext == null || spawnContext.positions().isEmpty()) {
            LOGGER.error("[EnduranceQuest] No spawn positions available for practice dummies");
            return;
        }

        ServerLevel level = arena.getLevel();
        List<BlockPos> positions = spawnContext.positions();

        // Limit dummies to avoid overwhelming the player
        int dummyCount = Math.min(count, Math.min(5, positions.size()));
        boolean objectiveTarget = waveState.getObjective().getType() == WaveObjectiveState.Type.ELITE_HUNT;

        int successfulSpawns = 0;
        for (int i = 0; i < dummyCount; i++) {
            BlockPos pos = positions.get(i % positions.size());
            int dummyIndex = waveState.nextPracticeDummyIndex();
            String dummyId = String.format("practice_w%d_%d", waveState.getWaveNumber(), dummyIndex);

            UUID uuid = DummmmmmyCompat.spawnDummy(level, pos, dummyId,
                dummy -> tagPracticeDummy(dummy, waveState, arena));
            if (uuid != null) {
                waveState.addSpawnedMob(uuid, SpawnAffix.BASE, objectiveTarget);
                successfulSpawns++;
            }
        }

        // Adjust wave target to match spawned dummies
        if (successfulSpawns > 0) {
            waveState.adjustTotalToSpawn(successfulSpawns);
            waveState.adjustKillTarget(successfulSpawns);
            waveState.getObjective().adjustEliteTargetCount(successfulSpawns);
        }

        LOGGER.info("[EnduranceQuest] Practice mode: spawned {} dummies for wave {}",
            successfulSpawns, waveState.getWaveNumber());
    }

    /**
     * Respawn missing mobs (real mobs path).
     */
    int respawnMissingMobs(EnduranceQuestManager.ActiveQuestSession session,
                           WaveManager.WaveState waveState,
                           int allowed,
                           List<UUID> deadMobIds) {
        ArenaContext arena = session.getArena();
        ArenaHandle handle = session.getArenaHandle();
        if (arena == null || handle == null) {
            EnduranceTelemetryService.INSTANCE.recordWaveBlocked(waveState.getQuest().getQuestId());
            return 0;
        }

        WaveManager.SpawnContext spawnContext = waveState.getSpawnContext();
        if (spawnContext == null || spawnContext.positions().isEmpty()) {
            EnduranceTelemetryService.INSTANCE.recordWaveBlocked(waveState.getQuest().getQuestId());
            return 0;
        }

        ServerLevel level = arena.getLevel();
        EnduranceQuestRegistry.MobQuestConfig mobConfig = waveState.getQuest().getMobConfig();
        EntityType<?> entityType = mobConfig.getEntityType();

        SpawnOccupancyTracker occupied = new SpawnOccupancyTracker();
        boolean allowReuse = spawnContext.positions().size() < allowed;
        WaveManager.SpawnPools pools = spawnContext.pools();

        int successfulRespawns = 0;
        int failedRespawns = 0;

        for (int i = 0; i < allowed; i++) {
            if (!spawnContext.canSpawnMore(successfulRespawns)) {
                LOGGER.warn("[EnduranceQuest] Entity limit reached, stopping respawn at {}/{}",
                    successfulRespawns, allowed);
                break;
            }

            Entity entity = entityType.create(Objects.requireNonNull(level));
            if (!(entity instanceof Mob mob)) {
                failedRespawns++;
                LOGGER.error("[EnduranceQuest] Failed to create entity of type {}", entityType);
                continue;
            }

            UUID deadId = i < deadMobIds.size() ? deadMobIds.get(i) : null;
            SpawnAffix affix = deadId != null ? waveState.getAffixForMob(deadId) : SpawnAffix.BASE;
            boolean objectiveTarget = deadId != null && waveState.isObjectiveTarget(deadId);

            List<BlockPos> candidatePool = positionResolver.chooseSpawnPool(
                positionResolver.resolveSpawnRole(affix), pools, mob);
            BlockPos spawnPos = positionResolver.pickValidatedSpawnPosition(
                candidatePool, i, occupied,
                spawnContext.runtimeValidator(), spawnContext.slotMap(),
                spawnContext.template(), level, allowReuse
            );
            if (spawnPos == null && candidatePool != pools.all()) {
                EnduranceTelemetryService.INSTANCE.recordSpawnFallback(
                    waveState.getQuest().getQuestId(), waveState.getWaveNumber(),
                    positionResolver.resolvePoolTag(candidatePool, pools),
                    handle.templateId(), "pool_exhausted"
                );
                spawnPos = positionResolver.pickValidatedSpawnPosition(
                    pools.all(), i, occupied,
                    spawnContext.runtimeValidator(), spawnContext.slotMap(),
                    spawnContext.template(), level, allowReuse
                );
            }
            if (spawnPos == null) {
                failedRespawns++;
                EnduranceTelemetryService.INSTANCE.recordSpawnFailure(
                    waveState.getQuest().getQuestId(), waveState.getWaveNumber(),
                    "no_valid_spawn", handle.templateId()
                );
                continue;
            }

            mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            // Same ordering as the initial spawn: finalizeSpawn is where a mob decides its own
            // statistics, so it must run before ours are layered on top rather than after, or the
            // scaling below is silently discarded.
            finalizeMobSpawn(mob, level, spawnPos);

            applyMobModifiers(mob, waveState);
            applyMultiplayerHPScaling(mob, waveState);
            applySpawnAffix(mob, affix);
            if (affix.isElite()) {
                applyEliteBuffs(mob, waveState.getWaveNumber());
            }

            CompoundTag tag = mob.getPersistentData();
            tag.putUUID(EnduranceTags.QUEST_ID, Objects.requireNonNull(waveState.getQuest().getQuestId()));
            tag.putUUID(EnduranceTags.ARENA_ID, Objects.requireNonNull(arena.getId()));
            tag.putString(EnduranceTags.MOB_ID, Objects.requireNonNull(waveState.getQuest().getMobId().toString()));
            tag.putBoolean("endurance_respawned", true);
            tag.putString(EnduranceTags.AFFIX, Objects.requireNonNull(affix.name(), "affixName"));
            if (handle.templateId() != null) {
                tag.putString("endurance_template_id",
                    Objects.requireNonNull(handle.templateId(), "templateId"));
            }
            tag.putInt("endurance_template_version", handle.templateVersion());
            if (handle.policyId() != null) {
                tag.putString("endurance_policy_id",
                    Objects.requireNonNull(handle.policyId(), "policyId"));
            }
            tag.putInt("endurance_policy_version", handle.policyVersion());
            if (objectiveTarget) {
                tag.putBoolean("endurance_objective_target", true);
            }

            boolean added = level.addFreshEntity(mob);
            if (added && mob.isAlive()) {
                if (deadId != null) {
                    waveState.getSpawnedMobs().remove(deadId);
                    waveState.removeSpawnAffix(deadId);
                }
                if (objectiveTarget && deadId != null) {
                    waveState.replaceObjectiveTarget(deadId, mob.getUUID());
                }
                waveState.getSpawnedMobs().add(Objects.requireNonNull(mob.getUUID()));
                waveState.putSpawnAffix(mob.getUUID(), affix);
                successfulRespawns++;
                awakeMobAI(mob, level);
                EnduranceTelemetryService.INSTANCE.recordSpawnHeatmap(
                    waveState.getQuest().getQuestId(), handle, spawnPos);
            } else {
                failedRespawns++;
                LOGGER.warn("[EnduranceQuest] Failed to respawn mob at {}", spawnPos);
            }
        }

        if (failedRespawns > 0) {
            LOGGER.warn("[EnduranceQuest] External respawn summary: {} successful, {} failed (wave {})",
                successfulRespawns, failedRespawns, waveState.getWaveNumber());
        }

        return successfulRespawns;
    }

    /**
     * Respawn practice dummies for practice mode.
     */
    int respawnPracticeDummies(EnduranceQuestManager.ActiveQuestSession session,
                               WaveManager.WaveState waveState,
                               int missingCount,
                               List<UUID> deadMobIds) {
        if (session == null || waveState == null || missingCount <= 0) {
            return 0;
        }
        if (!DummmmmmyCompat.isAvailable()) {
            LOGGER.warn("[EnduranceQuest] Practice mode respawn requested but Dummmmmmy mod not available");
            return 0;
        }

        ArenaContext arena = session.getArena();
        if (arena == null) {
            return 0;
        }

        WaveManager.SpawnContext spawnContext = waveState.getSpawnContext();
        if (spawnContext == null || spawnContext.positions().isEmpty()) {
            LOGGER.error("[EnduranceQuest] No spawn positions available for practice dummy respawn");
            return 0;
        }

        ServerLevel level = arena.getLevel();
        List<BlockPos> positions = spawnContext.positions();
        List<UUID> safeDeadMobIds = deadMobIds != null ? deadMobIds : List.of();
        boolean objectiveTarget = waveState.getObjective().getType() == WaveObjectiveState.Type.ELITE_HUNT;

        int spawnLimit = Math.min(missingCount, positions.size());
        int successfulRespawns = 0;
        int deadIndex = 0;

        for (int i = 0; i < spawnLimit; i++) {
            if (!spawnContext.canSpawnMore(waveState.getSpawned() + successfulRespawns)) {
                LOGGER.warn("[EnduranceQuest] Entity limit reached, stopping practice respawn at {}/{}",
                    successfulRespawns, spawnLimit);
                break;
            }
            BlockPos pos = positions.get(i % positions.size());
            int dummyIndex = waveState.nextPracticeDummyIndex();
            String dummyId = String.format("practice_w%d_%d", waveState.getWaveNumber(), dummyIndex);
            UUID uuid = DummmmmmyCompat.spawnDummy(level, pos, dummyId,
                dummy -> tagPracticeDummy(dummy, waveState, arena));
            if (uuid != null) {
                UUID deadId = deadIndex < safeDeadMobIds.size() ? safeDeadMobIds.get(deadIndex) : null;
                deadIndex++;
                if (deadId != null) {
                    waveState.getSpawnedMobs().remove(deadId);
                    waveState.removeSpawnAffix(deadId);
                    if (objectiveTarget) {
                        waveState.replaceObjectiveTarget(deadId, uuid);
                    }
                } else if (objectiveTarget) {
                    waveState.getObjective().registerObjectiveTarget(uuid);
                }
                waveState.getSpawnedMobs().add(uuid);
                waveState.putSpawnAffix(uuid, SpawnAffix.BASE);
                successfulRespawns++;
            }
        }

        if (successfulRespawns > 0) {
            LOGGER.info("[EnduranceQuest] Practice mode: respawned {} dummies for wave {}",
                successfulRespawns, waveState.getWaveNumber());
        }

        return successfulRespawns;
    }

    // ========== Mob Configuration ==========

    /**
     * Ask whether DevMod may write attribute base values on this mob, logging the refusal.
     *
     * <p>Every stat-writing helper below goes through here rather than each checking the policy
     * itself, so a new one cannot be added without the guard: the four that exist today all wrote
     * MAX_HEALTH, and any single one of them was enough to freeze an Ashen Court entity forever.
     *
     * @param mob the mob about to be modified
     * @param what what was going to be applied, for the log line
     * @return true when scaling is permitted
     */
    static boolean allowedToScale(Mob mob, String what) {
        return MobDrivePolicy.allowScaling(mob, what);
    }

    /**
     * Apply wave modifiers to a mob.
     */
    void applyMobModifiers(Mob mob, WaveManager.WaveState waveState) {
        // Resolved once, applied per case. The four attribute modifiers are the unsafe ones; the
        // fire-aspect tag, the two potion effects and DOUBLE_SPAWN touch nothing another mod reads
        // back, and dropping those too would leave a wave labelled INVISIBILITY whose mobs were
        // plainly visible.
        boolean mayScale = allowedToScale(mob, "wave modifiers");
        for (WaveManager.WaveModifier modifier : waveState.getModifiers()) {
            switch (modifier) {
                case SPEED_BOOST -> {
                    var speedAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
                    if (speedAttr != null && mayScale) {
                        speedAttr.setBaseValue(speedAttr.getBaseValue() * 1.25);
                    }
                }
                case DAMAGE_BOOST -> {
                    var attackAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
                    if (attackAttr != null && mayScale) {
                        attackAttr.setBaseValue(attackAttr.getBaseValue() * 1.25);
                    }
                }
                case HEALTH_BOOST -> {
                    var healthAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
                    if (healthAttr != null && mayScale) {
                        healthAttr.setBaseValue(healthAttr.getBaseValue() * 1.5);
                        mob.setHealth(mob.getMaxHealth());
                    }
                }
                case ARMOR_BOOST -> {
                    var armorAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ARMOR));
                    if (armorAttr != null && mayScale) {
                        armorAttr.setBaseValue(armorAttr.getBaseValue() + 8);
                    }
                }
                case FIRE_ASPECT -> {
                    mob.getPersistentData().putBoolean("endurance_fire_aspect", true);
                }
                case INVISIBILITY -> {
                    mob.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.INVISIBILITY), Integer.MAX_VALUE, 0, false, false));
                }
                case REGEN -> {
                    mob.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.REGENERATION), Integer.MAX_VALUE, 0, false, false));
                }
                case DOUBLE_SPAWN -> {
                    // Handled in wave setup
                }
            }
        }
    }

    void applySpawnAffix(Mob mob, SpawnAffix affix) {
        if (mob == null || affix == null) {
            return;
        }
        if (affix == SpawnAffix.BASE) {
            return;
        }
        if (!allowedToScale(mob, "affix " + affix.name())) {
            return;
        }
        var healthAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
        if (healthAttr != null) {
            healthAttr.setBaseValue(healthAttr.getBaseValue() * affix.getHpMultiplier());
            mob.setHealth(mob.getMaxHealth());
        }
        var attackAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        if (attackAttr != null) {
            attackAttr.setBaseValue(attackAttr.getBaseValue() * affix.getDamageMultiplier());
        }
        var speedAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * affix.getSpeedMultiplier());
        }
    }

    /**
     * Apply HP and damage scaling based on player count, quest type, AND mob difficulty preset.
     */
    void applyMultiplayerHPScaling(Mob mob, WaveManager.WaveState waveState) {
        if (!allowedToScale(mob, "multiplayer/difficulty scaling")) {
            return;
        }
        EnduranceQuestRegistry.MobQuestConfig mobConfig = waveState.getQuest().getMobConfig();
        EnduranceQuestManager.ActiveQuestSession session = waveState.getSession();
        int playerCount = waveState.getPlayerCount();
        QuestType questType = waveState.getQuestType();
        float waveScale = DifficultyScaler.INSTANCE.getWaveMultiplier(waveState.getWaveNumber(), waveState.getQuest().getTotalWaves());

        float globalHealthMult = EffectiveConfig.getGlobalHealthMult(session);
        float globalDamageMult = EffectiveConfig.getGlobalDamageMult(session);
        float globalSpeedMult = EffectiveConfig.getGlobalSpeedMult(session);

        // Apply HP scaling using MobQuestConfig (includes difficultyPreset.hpMultiplier)
        var healthAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
        if (healthAttr != null) {
            float baseHP = (float) healthAttr.getBaseValue();
            float scaledHP = mobConfig.getScaledHealth(playerCount, questType);
            float ratio = 1.0f;
            if (Math.abs(baseHP - mobConfig.getBaseHealth()) > 0.1f && mobConfig.getBaseHealth() > 0) {
                ratio = baseHP / mobConfig.getBaseHealth();
                scaledHP = scaledHP * ratio;
            }
            scaledHP *= waveScale * globalHealthMult;
            healthAttr.setBaseValue(scaledHP);
            mob.setHealth(mob.getMaxHealth());

            LOGGER.debug("[EnduranceQuest] Mob HP scaled: {} -> {} (players={}, preset={}, type={}, waveScale={}, globalMult={})",
                baseHP, scaledHP, playerCount, mobConfig.getDifficultyPreset().getDisplayName(), questType, waveScale, globalHealthMult);
            if (shouldLogBossHp(mobConfig)) {
                LOGGER.info("[EnduranceQuest] Boss HP scaling (pre-affix) for {}: baseAttr={}, baseEstimated={}, ratio={}, scaled={}, maxHp={}, wave={}, players={}, type={}, waveScale={}, globalMult={}, questId={}",
                    mobConfig.getMobId(), baseHP, mobConfig.getBaseHealth(), ratio, scaledHP, mob.getMaxHealth(),
                    waveState.getWaveNumber(), playerCount, questType, waveScale, globalHealthMult,
                    waveState.getQuest().getQuestId());
            }
        }

        // Apply damage scaling
        var attackAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        if (attackAttr != null) {
            float baseDamage = (float) attackAttr.getBaseValue();
            float scaledDamage = mobConfig.getScaledDamage(playerCount);
            if (Math.abs(baseDamage - mobConfig.getBaseDamage()) > 0.1f && mobConfig.getBaseDamage() > 0) {
                float ratio = baseDamage / mobConfig.getBaseDamage();
                scaledDamage = scaledDamage * ratio;
            }
            scaledDamage *= waveScale * globalDamageMult;
            attackAttr.setBaseValue(scaledDamage);

            LOGGER.debug("[EnduranceQuest] Mob DMG scaled: {} -> {} (players={}, preset={}, waveScale={}, globalMult={})",
                baseDamage, scaledDamage, playerCount, mobConfig.getDifficultyPreset().getDisplayName(), waveScale, globalDamageMult);
        }

        // Apply speed multiplier if session has global speed override
        if (globalSpeedMult != 1.0f) {
            var speedAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
            if (speedAttr != null) {
                float baseSpeed = (float) speedAttr.getBaseValue();
                float scaledSpeed = baseSpeed * globalSpeedMult;
                speedAttr.setBaseValue(scaledSpeed);
                LOGGER.debug("[EnduranceQuest] Mob SPEED scaled: {} -> {} (globalMult={})",
                    baseSpeed, scaledSpeed, globalSpeedMult);
            }
        }
    }

    /**
     * Apply elite buffs to a mob (special stronger variant).
     */
    void applyEliteBuffs(Mob mob, int waveNumber) {
        // The tag is written either way: elite is also a loot, score and HUD fact, and only part of
        // it is unsafe on a mob whose own mod reads its statistics back.
        mob.getPersistentData().putBoolean("endurance_elite", true);
        boolean mayScale = allowedToScale(mob, "elite buffs");

        if (mayScale) {
            float scaleFactor = 1.0f + (waveNumber * 0.1f);

            var healthAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
            if (healthAttr != null) {
                healthAttr.setBaseValue(healthAttr.getBaseValue() * scaleFactor * 1.5);
                mob.setHealth(mob.getMaxHealth());
            }

            var attackAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
            if (attackAttr != null) {
                attackAttr.setBaseValue(attackAttr.getBaseValue() * scaleFactor);
            }

            // Armour is gated with the attributes and not separately: a worn piece installs
            // AttributeModifiers on ARMOR and ARMOR_TOUGHNESS, and a guarded mob's own mod refuses
            // any modifier on those when the entity is read back from disk -- the elite would
            // vanish after a restart. Its own model already carries its armour anyway.
            if (random.nextBoolean()) {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Objects.requireNonNull(Items.IRON_HELMET)));
            }
            if (random.nextBoolean()) {
                mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Objects.requireNonNull(Items.IRON_CHESTPLATE)));
            }
        }

        // These two run for every elite. The glow is how a player tells an elite apart, and
        // resistance is applied in the damage calculation rather than through an attribute, so
        // neither can make a guarded mob unreadable to its own runtime. Dropping them would have
        // left a guarded elite indistinguishable from a normal mob while still being scored,
        // looted and announced as one.
        mob.setGlowingTag(true);
        mob.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.DAMAGE_RESISTANCE), Integer.MAX_VALUE, 0, false, false));
    }

    float getEliteChance(float baseChance, int waveNumber,
                         @Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (baseChance <= 0f || waveNumber < 3) {
            return 0f;
        }

        float configBaseChance = (float) EffectiveConfig.getEliteChanceBase(session);
        float configScaling = (float) EffectiveConfig.getEliteChanceScaling(session);

        float rampChance;
        if (waveNumber < 5) {
            rampChance = configBaseChance;
        } else if (waveNumber < 8) {
            rampChance = configBaseChance + configScaling * (waveNumber - 4);
        } else {
            rampChance = configBaseChance + configScaling * 4 + configScaling * 0.5f * (waveNumber - 8);
        }

        return Math.min(baseChance, rampChance);
    }

    // ========== Helpers ==========

    private void tagSpawnedMob(Mob mob, WaveManager.WaveState waveState,
                               ArenaContext arena, @Nullable ArenaHandle handle,
                               SpawnAffix appliedAffix, boolean objectiveTarget) {
        CompoundTag tag = mob.getPersistentData();
        tag.putUUID(EnduranceTags.QUEST_ID, Objects.requireNonNull(waveState.getQuest().getQuestId()));
        tag.putUUID(EnduranceTags.ARENA_ID, Objects.requireNonNull(arena.getId()));
        tag.putString(EnduranceTags.MOB_ID, Objects.requireNonNull(waveState.getQuest().getMobId().toString()));
        tag.putString(EnduranceTags.AFFIX,
            Objects.requireNonNull(appliedAffix.name(), "appliedAffixName"));
        if (handle != null) {
            if (handle.templateId() != null) {
                tag.putString("endurance_template_id",
                    Objects.requireNonNull(handle.templateId(), "templateId"));
            }
            tag.putInt("endurance_template_version", handle.templateVersion());
            if (handle.policyId() != null) {
                tag.putString("endurance_policy_id",
                    Objects.requireNonNull(handle.policyId(), "policyId"));
            }
            tag.putInt("endurance_policy_version", handle.policyVersion());
        }
        if (objectiveTarget) {
            tag.putBoolean("endurance_objective_target", true);
        }
    }

    private void tagPracticeDummy(Entity dummy, WaveManager.WaveState waveState, ArenaContext arena) {
        if (dummy == null || waveState == null || arena == null) {
            return;
        }
        CompoundTag tag = dummy.getPersistentData();
        tag.putUUID(EnduranceTags.QUEST_ID, Objects.requireNonNull(waveState.getQuest().getQuestId(), "questId"));
        tag.putUUID(EnduranceTags.ARENA_ID, Objects.requireNonNull(arena.getId(), "arenaId"));
        tag.putString(EnduranceTags.MOB_ID, Objects.requireNonNull(waveState.getQuest().getMobId().toString()));
        tag.putString(EnduranceTags.MOB_ID_OVERRIDE, Objects.requireNonNull(waveState.getQuest().getMobId().toString()));
        tag.putString(EnduranceTags.AFFIX, Objects.requireNonNull(SpawnAffix.BASE.name(), "affix"));
        tag.putBoolean(EnduranceTags.PRACTICE_DUMMY, true);
    }

    static boolean shouldLogBossHp(EnduranceQuestRegistry.MobQuestConfig mobConfig) {
        if (mobConfig == null || mobConfig.getMobId() == null) {
            return false;
        }
        return "ender_dragon".equals(mobConfig.getMobId().getPath());
    }

    /**
     * Run the mob's own spawn finalisation.
     *
     * <p>Package-visible because BossWaveSystem needs it too: that path called addFreshEntity
     * directly and never finalised at all, which for a modded mob that allocates its statistics in
     * finalizeSpawn means it is never authorised to fight.
     *
     * @param mob the mob being spawned
     * @param level the level it is being spawned into
     * @param spawnPos where it is being spawned
     */
    @SuppressWarnings("deprecation")
    static void finalizeMobSpawn(Mob mob, ServerLevel level, BlockPos spawnPos) {
        mob.finalizeSpawn(level, Objects.requireNonNull(level.getCurrentDifficultyAt(Objects.requireNonNull(spawnPos))),
            MobSpawnType.MOB_SUMMONED, null);
    }

    /**
     * Give a freshly spawned mob the goals that make it hunt the player.
     *
     * <p>Package-visible and static because {@link BossWaveSystem} needs it too. It used to be
     * private here, so the boss and minion spawns in that class -- which call addFreshEntity
     * directly -- produced mobs with no targeting goal and no attack goal at all. They stood
     * still. Regular waves worked because this path called it; nothing else did.
     *
     * <p>The goals deliberately extend plain {@code Goal} and {@code TargetGoal} rather than
     * vanilla's melee goals: Epic Fight's MobPatch.selectGoalToRemove strips vanilla melee goals
     * from patched mobs to install its own animated attacks, and these have to survive that.
     *
     * @param mob the mob just added to the level
     * @param level the level it was added to
     */
    @SuppressWarnings("unchecked")
    static void awakeMobAI(Mob mob, ServerLevel level) {
        try {
            MobDrivePolicy policy = MobDrivePolicy.resolve(mob);
            // deferToOwner on a self-driven mob: bootstrap a target only when it has none, and back
            // off when the mob refuses the write. Its own runtime owns target selection, and Age of
            // Fight's pilot silently ignores setTarget while it holds an interdiction lease.
            mob.targetSelector.addGoal(1,
                new com.devmod.endurance.ai.EnduranceTargetPlayerGoal(mob, policy.selfDriven()));
            if (policy.selfDriven()) {
                // The targeting goal only, and on purpose. A self-driven mob's own mod owns its
                // PathNavigation; adding our melee goal would give that navigation two writers, and
                // whichever wrote last each tick would win -- which reads as stuttering, not as a
                // bug, and would be blamed on the other mod. Setting the target is still ours to do
                // and is what those runtimes wait for: Age of Fight only treats a player as hostile
                // once a member already targets it or has been hurt by it, so an arena mob spawned
                // to fight a player it has never met would otherwise never acquire one.
                mob.getSensing().tick();
                mob.targetSelector.tick();
                LOGGER.info("[AIDebug] awakeMobAI: {} is self-driven ({}), installed the targeting "
                        + "goal only. target={}, targetGoals={}",
                    mob.getType().toString(),
                    policy.reason(),
                    mob.getTarget() != null ? mob.getTarget().getName().getString() : "null",
                    mob.targetSelector.getAvailableGoals().size());
                return;
            }
            // Priority 1, not 2. Combat Evolution patches these mobs with
            // net.shelmarow.combat_evolution.stealth.CEInvestigationGoal at priority 2, declaring
            // the same [MOVE, LOOK] flags as ours. At equal priority with conflicting flags the
            // goal that claimed the flag first keeps it, and CE's is registered before ours -- so
            // GoalSelector never even called canUse() on the attack goal. The mob acquired its
            // target (the TARGET-flagged goal runs fine) and then stood still while a stealth
            // investigation goal held the movement. Measured in a live run via the goal dump
            // below. Attacking has to outrank investigating for the duration of a wave.
            mob.goalSelector.addGoal(1, new com.devmod.endurance.ai.EnduranceMeleeAttackGoal(mob, 1.0, true));

            mob.getSensing().tick();
            mob.targetSelector.tick();
            mob.goalSelector.tick();

            net.minecraft.world.entity.ai.Brain<Mob> brain =
                (net.minecraft.world.entity.ai.Brain<Mob>) mob.getBrain();
            brain.tick(Objects.requireNonNull(level, "level"), mob);

            mob.setAggressive(true);

            int targetGoalCount = mob.targetSelector.getAvailableGoals().size();
            int behaviorGoalCount = mob.goalSelector.getAvailableGoals().size();
            net.minecraft.world.entity.LivingEntity target = mob.getTarget();
            boolean noAI = mob.isNoAi();
            // Dump WHICH goals are present, not just how many. A count cannot answer the question
            // that matters here: Minecraft's GoalSelector never calls canUse() on a goal whose
            // Flags are held by a running higher-priority goal, so our attack goal (MOVE + LOOK,
            // priority 2) can be silently skipped -- no start, no diagnostic, mob standing still.
            // Epic Fight strips most vanilla goals from patched mobs and drives movement from its
            // own patch tick, so knowing what it leaves behind, and at what priority, is the whole
            // diagnosis.
            LOGGER.info("[AIDebug] goals for {}: behaviour={}",
                mob.getUUID().toString().substring(0, 8),
                describeGoals(mob.goalSelector));
            LOGGER.info("[AIDebug] goals for {}: target={}",
                mob.getUUID().toString().substring(0, 8),
                describeGoals(mob.targetSelector));

            LOGGER.info("[AIDebug] awakeMobAI: mob={}, mobId={}, target={}, noAI={}, targetGoals={}, behaviorGoals={}, pos={}",
                mob.getType().toString(),
                mob.getUUID(),
                target != null ? target.getName().getString() : "null",
                noAI,
                targetGoalCount,
                behaviorGoalCount,
                mob.blockPosition());

        } catch (Exception e) {
            LOGGER.warn("[EnduranceQuest] Failed to awaken AI for {}: {}",
                mob.getType().toString(), e.getMessage());
        }
    }

    /**
     * Describe a goal selector's contents: priority, class and flags for each entry.
     *
     * @param selector the selector to describe
     * @return a compact one-line description, safe to log
     */
    private static String describeGoals(net.minecraft.world.entity.ai.goal.GoalSelector selector) {
        StringBuilder sb = new StringBuilder();
        for (net.minecraft.world.entity.ai.goal.WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('p').append(wrapped.getPriority()).append(':')
              .append(wrapped.getGoal().getClass().getName())
              .append(wrapped.isRunning() ? "(running)" : "")
              .append(wrapped.getGoal().getFlags());
        }
        return sb.length() == 0 ? "<none>" : sb.toString();
    }
}
