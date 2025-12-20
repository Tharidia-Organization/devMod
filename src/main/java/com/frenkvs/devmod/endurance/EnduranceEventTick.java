package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.util.I18n;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.frenkvs.devmod.endurance.analytics.LiveAnalyticsHookManager;
import com.frenkvs.devmod.party.QuestStartSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Server tick handlers for EnduranceQuest system.
 * Handles wave sync, arena cleanup, mob validation, and periodic updates.
 */
public class EnduranceEventTick {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceEventTick.class);

    private static int tickCounter = 0;
    private static int gamificationTickCounter = 0;

    private static final int WAVE_CHECK_INTERVAL = 20; // Check every second
    private static final int ARENA_CLEANUP_INTERVAL = 40; // Check every 2 seconds
    private static final int MOB_VALIDATION_INTERVAL = 60; // Check every 3 seconds

    // ═══════════════════════════════════════════════════════════════
    // MAIN TICK HANDLER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Server tick handler for wave management and quest updates.
     */
    public static void onServerTick() {
        tickCounter++;

        // Update combo decay every tick
        EnduranceEventCombat.comboSessions.values().forEach(ComboSystem.ComboSession::tick);

        // Tick live analytics hooks (throttled internally to 1/sec)
        LiveAnalyticsHookManager.INSTANCE.tick();

        // Tick quest start sequences (countdown, validation, teleport) and quest-related updates
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            QuestStartSequence.INSTANCE.tick(server);

            // Tick perk effects and enforce arena confinement for all players in active quests
            for (UUID playerId : EnduranceQuestManager.INSTANCE.getActiveSessions().keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(playerId));
                if (player != null) {
                    PerkSystem.INSTANCE.tick(player);

                    // Check arena boundaries every 10 ticks (0.5 seconds)
                    if (tickCounter % 10 == 0) {
                        enforceArenaConfinement(player);
                    }
                }
            }
        }

        // Arena cleanup - remove external mobs (every 2 seconds)
        if (tickCounter % ARENA_CLEANUP_INTERVAL == 0) {
            cleanupExternalMobsInArenas();
        }

        // Mob validation - check wave mobs are alive (every 3 seconds)
        if (tickCounter % MOB_VALIDATION_INTERVAL == 0) {
            validateAndRespawnWaveMobs();
        }

        // Periodic checks (every second)
        if (tickCounter >= WAVE_CHECK_INTERVAL) {
            tickCounter = 0;

            // Sync quest state to all players with active quests
            syncQuestStateToClients();

            // Check gamification resets every minute (60 ticks * 20 = 1200 ticks)
            gamificationTickCounter++;
            if (gamificationTickCounter >= 60) {
                gamificationTickCounter = 0;
                GamificationManager.INSTANCE.tickResets();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // QUEST STATE SYNC
    // ═══════════════════════════════════════════════════════════════

    /**
     * Send quest state sync packets to all players with active quests.
     */
    private static void syncQuestStateToClients() {
        for (Map.Entry<UUID, EnduranceQuestManager.ActiveQuestSession> entry :
                EnduranceQuestManager.INSTANCE.getActiveSessions().entrySet()) {

            UUID playerId = entry.getKey();
            EnduranceQuestManager.ActiveQuestSession session = entry.getValue();
            EnduranceQuest quest = session.getQuest();

            // Find the player
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) continue;

            ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(playerId));
            if (player == null) continue;

            // Build modifier list
            List<String> modifiers = new ArrayList<>();
            var waveStateOpt = WaveManager.INSTANCE.getWaveState(session.getArena().getId());
            if (waveStateOpt.isPresent()) {
                for (WaveManager.WaveModifier mod : waveStateOpt.get().getModifiers()) {
                    modifiers.add(mod.displayName);
                }
            }

            // Get combo session data
            ComboSystem.ComboSession comboSession = EnduranceEventCombat.comboSessions.get(playerId);
            int currentCombo = comboSession != null ? comboSession.getCurrentCombo() : 0;
            int maxCombo = comboSession != null ? comboSession.getMaxCombo() : 0;
            int styleScore = comboSession != null ? comboSession.getStyleScore() : 0;
            int styleRankOrdinal = comboSession != null ? comboSession.getCurrentRank().ordinal() : 0;

            // Get wave progress
            int mobsKilledInWave = waveStateOpt.map(WaveManager.WaveState::getKilled).orElse(0);
            int totalMobsInWave = waveStateOpt.map(WaveManager.WaveState::getTotalToSpawn).orElse(quest.getCurrentWaveMobCount());

            // Create sync payload
            QuestSyncPayload payload = new QuestSyncPayload(
                true,
                quest.getDisplayName(),
                quest.getCurrentWave(),
                quest.getTotalWaves(),
                quest.isEndlessMode(),
                quest.getPointsEarnedThisSession(),
                quest.getMobsKilledThisSession(),
                mobsKilledInWave,
                totalMobsInWave,
                quest.getTotalDamageDealtThisSession(),
                (int) quest.getDamageTakenThisSession(),
                quest.getDeathsThisSession(),
                quest.getSessionDuration(),
                quest.getState().ordinal(),
                modifiers,
                currentCombo,
                maxCombo,
                styleScore,
                styleRankOrdinal
            );

            // Send to player
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ARENA CONFINEMENT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Teleport player back to arena if they try to escape.
     */
    public static void enforceArenaConfinement(ServerPlayer player) {
        Optional<EnduranceQuestManager.ActiveQuestSession> session =
            EnduranceQuestManager.INSTANCE.getActiveSession(player);

        if (session.isPresent()) {
            ArenaManager.Arena arena = session.get().getArena();
            if (arena == null) {
                // Arena not yet assigned, skip confinement check
                return;
            }
            if (!arena.contains(Objects.requireNonNull(player.position()))) {
                // Teleport back to center
                player.teleportTo(
                    arena.getCenter().getX() + 0.5,
                    arena.getCenter().getY(),
                    arena.getCenter().getZ() + 0.5
                );
            }
        }
    }

    /**
     * Check if player is trying to leave the arena.
     */
    public static boolean canPlayerLeaveArena(ServerPlayer player) {
        Optional<EnduranceQuestManager.ActiveQuestSession> session =
            EnduranceQuestManager.INSTANCE.getActiveSession(player);

        if (session.isPresent()) {
            EnduranceQuestState state = session.get().getQuest().getState();
            // Can only leave during checkpoint (between waves)
            return state == EnduranceQuestState.WAVE_COMPLETE;
        }

        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // ARENA CLEANUP
    // ═══════════════════════════════════════════════════════════════

    /**
     * Remove any non-quest mobs that spawn inside active arenas.
     * This prevents external mobs from interfering with the quest.
     */
    private static void cleanupExternalMobsInArenas() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (EnduranceQuestManager.ActiveQuestSession session :
                EnduranceQuestManager.INSTANCE.getActiveSessions().values()) {

            ArenaManager.Arena arena = session.getArena();
            if (arena == null) {
                // Arena not yet assigned, skip cleanup for this session
                continue;
            }
            net.minecraft.server.level.ServerLevel level = arena.getLevel();

            // Get all entities in arena bounds
            List<Entity> entitiesInArena = level.getEntities(
                (Entity) null,
                Objects.requireNonNull(arena.getBounds()),
                entity -> entity instanceof Mob
            );

            for (Entity entity : entitiesInArena) {
                if (entity instanceof Mob mob) {
                    CompoundTag data = mob.getPersistentData();

                    // Check if this mob belongs to our quest
                    boolean isQuestMob = data.contains("endurance_quest_id") &&
                        data.contains("endurance_arena_id") &&
                        Objects.requireNonNull(data.getUUID("endurance_arena_id")).equals(arena.getId());

                    if (!isQuestMob) {
                        // This is an external mob - remove it
                        mob.discard();
                        LOGGER.debug("[EnduranceQuest] Removed external mob {} from arena {}",
                            mob.getType().getDescriptionId(), arena.getId());
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MOB VALIDATION & RESPAWN
    // ═══════════════════════════════════════════════════════════════

    /**
     * Validate that wave mobs are still alive. If mobs died from external causes
     * (not player kills), respawn them to ensure wave can be completed.
     */
    private static void validateAndRespawnWaveMobs() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (Map.Entry<UUID, EnduranceQuestManager.ActiveQuestSession> entry :
                EnduranceQuestManager.INSTANCE.getActiveSessions().entrySet()) {

            EnduranceQuestManager.ActiveQuestSession session = entry.getValue();
            EnduranceQuest quest = session.getQuest();

            // Only validate during active wave combat
            if (quest.getState() != EnduranceQuestState.IN_PROGRESS) continue;

            ArenaManager.Arena arena = session.getArena();
            Optional<WaveManager.WaveState> waveStateOpt = WaveManager.INSTANCE.getWaveState(arena.getId());

            if (waveStateOpt.isEmpty()) continue;

            WaveManager.WaveState waveState = waveStateOpt.get();
            net.minecraft.server.level.ServerLevel level = arena.getLevel();

            // Count how many spawned mobs are still alive
            int aliveMobs = 0;
            List<UUID> deadMobIds = new ArrayList<>();

            for (UUID mobId : waveState.getSpawnedMobs()) {
                Entity entity = level.getEntity(Objects.requireNonNull(mobId));
                if (entity != null && entity.isAlive()) {
                    aliveMobs++;
                } else {
                    deadMobIds.add(mobId);
                }
            }

            // Calculate expected alive mobs (spawned - killed by player)
            int expectedAlive = waveState.getSpawned() - waveState.getKilled();

            // If we have fewer alive mobs than expected, some died from external causes
            int missingMobs = expectedAlive - aliveMobs;

            if (missingMobs > 0) {
                LOGGER.warn("[EnduranceQuest] Wave {} has {} missing mobs (external deaths). Respawning...",
                    waveState.getWaveNumber(), missingMobs);

                // Respawn the missing mobs
                respawnMissingWaveMobs(session, waveState, missingMobs, deadMobIds);
            }
        }
    }

    /**
     * Respawn mobs that died from external causes (not player kills).
     * Applies the same wave modifiers as original spawns.
     */
    private static void respawnMissingWaveMobs(
            EnduranceQuestManager.ActiveQuestSession session,
            WaveManager.WaveState waveState,
            int count,
            List<UUID> deadMobIds) {

        ArenaManager.Arena arena = session.getArena();
        net.minecraft.server.level.ServerLevel level = arena.getLevel();
        EnduranceQuestRegistry.MobQuestConfig mobConfig = waveState.getQuest().getMobConfig();
        net.minecraft.world.entity.EntityType<?> entityType = mobConfig.entityType;

        List<net.minecraft.core.BlockPos> spawnPositions = arena.getDistributedSpawnPositions(count);
        int successfulRespawns = 0;

        for (int i = 0; i < count; i++) {
            // Use modulo to reuse positions if we don't have enough
            net.minecraft.core.BlockPos spawnPos = spawnPositions.get(i % Math.max(1, spawnPositions.size()));

            Entity entity = entityType.create(Objects.requireNonNull(level));
            if (entity instanceof Mob mob) {
                // Position the mob
                mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

                // Apply wave modifiers (same as original spawns)
                applyWaveModifiersToMob(mob, waveState);

                // Finalize spawn (triggers mob initialization)
                finalizeSpawnDeprecated(mob, level, spawnPos);

                // Tag mob as quest mob BEFORE adding to world
                CompoundTag tag = mob.getPersistentData();
                tag.putUUID("endurance_quest_id", Objects.requireNonNull(waveState.getQuest().getQuestId()));
                tag.putUUID("endurance_arena_id", Objects.requireNonNull(arena.getId()));
                tag.putBoolean("endurance_respawned", true); // Mark as respawned

                // Add to world and verify success
                boolean added = level.addFreshEntity(mob);

                if (added && mob.isAlive()) {
                    // Replace dead mob UUID with new one in wave state tracking
                    if (i < deadMobIds.size()) {
                        waveState.getSpawnedMobs().remove(deadMobIds.get(i));
                    }
                    waveState.getSpawnedMobs().add(Objects.requireNonNull(mob.getUUID()));
                    successfulRespawns++;

                    LOGGER.info("[EnduranceQuest] Respawned {} at {} for wave {}",
                        entityType.getDescriptionId(), spawnPos, waveState.getWaveNumber());
                } else {
                    LOGGER.warn("[EnduranceQuest] Failed to respawn mob at {}", spawnPos);
                }
            }
        }

        // Notify player about respawned mobs
        if (successfulRespawns > 0) {
            UUID playerId = session.getPlayerId();
            var serverInstance = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            ServerPlayer player = serverInstance != null ? serverInstance.getPlayerList().getPlayer(Objects.requireNonNull(playerId)) : null;
            if (player != null) {
                player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.endurance.mobs_respawned", successfulRespawns)
                    .withStyle(ChatFormatting.YELLOW)));
            }
        }
    }

    /**
     * Calls the deprecated finalizeSpawn method (required for mob initialization).
     * Isolated in a helper method to contain the deprecation warning.
     */
    @SuppressWarnings("deprecation")
    private static void finalizeSpawnDeprecated(Mob mob, net.minecraft.server.level.ServerLevel level,
                                                 net.minecraft.core.BlockPos spawnPos) {
        mob.finalizeSpawn(level, Objects.requireNonNull(level.getCurrentDifficultyAt(Objects.requireNonNull(spawnPos))),
            net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null);
    }

    /**
     * Apply wave modifiers to a respawned mob (mirrors WaveManager.applyMobModifiers).
     */
    private static void applyWaveModifiersToMob(Mob mob, WaveManager.WaveState waveState) {
        for (WaveManager.WaveModifier modifier : waveState.getModifiers()) {
            switch (modifier) {
                case SPEED_BOOST -> {
                    var speedAttr = mob.getAttribute(Objects.requireNonNull(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
                    if (speedAttr != null) {
                        speedAttr.setBaseValue(speedAttr.getBaseValue() * 1.25);
                    }
                }
                case DAMAGE_BOOST -> {
                    var attackAttr = mob.getAttribute(Objects.requireNonNull(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
                    if (attackAttr != null) {
                        attackAttr.setBaseValue(attackAttr.getBaseValue() * 1.25);
                    }
                }
                case HEALTH_BOOST -> {
                    var healthAttr = mob.getAttribute(Objects.requireNonNull(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH));
                    if (healthAttr != null) {
                        healthAttr.setBaseValue(healthAttr.getBaseValue() * 1.5);
                        mob.setHealth(mob.getMaxHealth());
                    }
                }
                case ARMOR_BOOST -> {
                    var armorAttr = mob.getAttribute(Objects.requireNonNull(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR));
                    if (armorAttr != null) {
                        armorAttr.setBaseValue(armorAttr.getBaseValue() + 8);
                    }
                }
                case FIRE_ASPECT -> {
                    mob.getPersistentData().putBoolean("endurance_fire_aspect", true);
                }
                case INVISIBILITY -> {
                    mob.addEffect(new MobEffectInstance(
                        Objects.requireNonNull(MobEffects.INVISIBILITY), Integer.MAX_VALUE, 0, false, false));
                }
                case REGEN -> {
                    mob.addEffect(new MobEffectInstance(
                        Objects.requireNonNull(MobEffects.REGENERATION), Integer.MAX_VALUE, 0, false, false));
                }
                case DOUBLE_SPAWN -> {
                    // Not applicable for individual mob spawns
                }
            }
        }
    }
}
