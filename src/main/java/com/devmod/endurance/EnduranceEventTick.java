package com.devmod.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.endurance.analytics.LiveAnalyticsHookManager;
import com.devmod.party.QuestSequencePayload;
import com.devmod.party.QuestStartSequence;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;
import com.devmod.util.I18n;

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

        // Update momentum decay every tick
        MomentumTracker.INSTANCE.tick();

        // Tick execution system
        com.devmod.combat.ExecutionSystem.INSTANCE.tick();

        // Tick live analytics hooks (throttled internally to 1/sec)
        LiveAnalyticsHookManager.INSTANCE.tick();

        // Tick quest start sequences (countdown, validation, teleport) and quest-related updates
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Refine mob tiers with actual attributes (runs once on first server tick)
            if (!EnduranceQuestRegistry.INSTANCE.isAttributesRefined()) {
                var overworld = server.overworld();
                if (overworld != null) {
                    EnduranceQuestRegistry.INSTANCE.refineWithAttributes(overworld);
                }
            }
            QuestStartSequence.INSTANCE.tick(server);
            EnduranceQuestManager.INSTANCE.tickAsyncBuilds(server);
            tickPendingInstanceStarts(server);
            tickPendingWaveStarts(server);

            // Tick perk effects and enforce arena confinement for all players in active quests
            for (Map.Entry<UUID, EnduranceQuestManager.ActiveQuestSession> entry :
                EnduranceQuestManager.INSTANCE.getActiveSessions().entrySet()) {
                UUID playerId = entry.getKey();
                EnduranceQuestManager.ActiveQuestSession session = entry.getValue();
                ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(playerId));
                if (player != null) {
                    PerkSystem.INSTANCE.tick(player);
                    WaveManager.INSTANCE.tickWave(session, player);

                    // Tick execution system for this player
                    com.devmod.combat.ExecutionSystem.INSTANCE.tickPlayer(player);

                    // Tick Devil's Bargain curse effects
                    UUID questId = session.getQuest().getQuestId();
                    com.devmod.endurance.bargain.DevilsBargainManager.INSTANCE.tickPlayer(player, questId);

                    // Tick Arena Hazards (environmental effects)
                    if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        com.devmod.endurance.hazard.ArenaHazardSystem.INSTANCE.tick(serverLevel, questId, player);
                    }

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
        if (tickCounter % WAVE_CHECK_INTERVAL == 0) {

            // Sync quest state to all players with active quests
            syncQuestStateToClients();

            // Check gamification resets every minute (60 ticks * 20 = 1200 ticks)
            gamificationTickCounter++;
            if (gamificationTickCounter >= 60) {
                gamificationTickCounter = 0;
                GamificationManager.INSTANCE.tickResets();
            }
        }

        if (tickCounter >= 1_000_000) {
            tickCounter = 0;
        }
    }

    /**
     * Handle pre-teleport countdowns for solo instance starts.
     */
    private static void tickPendingInstanceStarts(MinecraftServer server) {
        for (EnduranceQuestManager.ActiveQuestSession session :
                EnduranceQuestManager.INSTANCE.getActiveSessions().values()) {
            if (!session.isInstanceStartPending() && !session.isBriefingPending()) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(session.getPlayerId()));
            if (player == null) {
                continue;
            }

            if (session.isBriefingPending()) {
                int ticksRemaining = session.tickBriefingCountdown();
                int secondsRemaining = (int) Math.ceil(ticksRemaining / 20.0);

                if (secondsRemaining > 0 && secondsRemaining != session.getLastBriefingSeconds()) {
                    session.setLastBriefingSeconds(secondsRemaining);
                    EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(
                        player,
                        session,
                        QuestSequencePayload.Phase.BRIEFING,
                        secondsRemaining,
                        session.getQuest().getDisplayName(),
                        "Endurance briefing",
                        session.getBriefingLines()
                    );
                }

                if (ticksRemaining <= 0) {
                    session.setLastTeleportCountdownSeconds(-1);
                }
                continue;
            }

            int ticksRemaining = session.tickInstanceStartCountdown();
            int secondsRemaining = (int) Math.ceil(ticksRemaining / 20.0);

            if (secondsRemaining > 0 && secondsRemaining != session.getLastTeleportCountdownSeconds()) {
                if (session.getLastTeleportCountdownSeconds() < 0) {
                    EnduranceTelemetryService.INSTANCE.recordCountdownStarted(session.getQuest().getQuestId());
                }
                session.setLastTeleportCountdownSeconds(secondsRemaining);
                EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                    QuestSequencePayload.Phase.COUNTDOWN_START, secondsRemaining,
                    session.getQuest().getDisplayName(),
                    "Preparing instance...",
                    List.of());
                if (secondsRemaining <= 3) {
                    float pitch = 1.2f + (3 - secondsRemaining) * 0.1f;
                    player.playSound(Objects.requireNonNull(SoundEvents.UI_BUTTON_CLICK.value()), 0.7f, pitch);
                }
            }

            if (ticksRemaining <= 0) {
                EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                    QuestSequencePayload.Phase.TELEPORTING, 0,
                    session.getQuest().getDisplayName(),
                    "Loading arena...",
                    List.of());
                EnduranceQuestManager.INSTANCE.startPendingInstanceQuest(player, session);
            }
        }
    }

    /**
     * Handle delayed wave starts (solo start + respawn).
     * Only counts down once the player is in the instance dimension.
     */
    private static void tickPendingWaveStarts(MinecraftServer server) {
        for (EnduranceQuestManager.ActiveQuestSession session :
                EnduranceQuestManager.INSTANCE.getActiveSessions().values()) {
            if (!session.isWaveStartPending()) {
                continue;
            }
            if (session.getQuest().getState() != EnduranceQuestState.IN_PROGRESS) {
                session.clearPendingWaveStart();
                session.setRespawnCountdownActive(false);
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(session.getPlayerId()));
            if (player == null) {
                continue;
            }

            ArenaContext arena = session.getArena();
            if (arena == null) {
                session.clearPendingWaveStart();
                session.setRespawnCountdownActive(false);
                LOGGER.warn("[EnduranceQuest] Pending wave start cancelled - arena missing for {}",
                    player.getName().getString());
                continue;
            }

            // Wait until the player is actually inside the arena before counting down.
            if (!player.level().dimension().equals(arena.getLevel().dimension())) {
                continue;
            }

            if (session.isSafeWindowPending()) {
                int ticksRemaining = session.tickSafeWindowCountdown();
                int secondsRemaining = (int) Math.ceil(ticksRemaining / 20.0);

                if (secondsRemaining > 0 && secondsRemaining != session.getLastSafeWindowSeconds()) {
                    if (session.getLastSafeWindowSeconds() < 0) {
                        EndurancePlayerStateManager.INSTANCE.applySafeWindowEffects(
                            player,
                            EnduranceQuestManager.SAFE_WINDOW_TICKS
                        );
                    }
                    session.setLastSafeWindowSeconds(secondsRemaining);
                    EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                        QuestSequencePayload.Phase.SAFE_WINDOW, secondsRemaining,
                        session.getQuest().getDisplayName(),
                        "Safe window",
                        List.of("Invulnerability active"));
                }

                if (ticksRemaining <= 0) {
                    session.clearPendingSafeWindow();
                }
                continue;
            }

                if (session.isBossIntroPending()) {
                    int ticksRemaining = session.tickBossIntroCountdown();
                    int secondsRemaining = (int) Math.ceil(ticksRemaining / 20.0);
                    if (secondsRemaining > 0 && secondsRemaining != session.getLastBossIntroSeconds()) {
                        if (session.getLastBossIntroSeconds() < 0) {
                            EndurancePlayerStateManager.INSTANCE.applySafeWindowEffects(
                                player,
                                EnduranceQuestManager.BOSS_INTRO_TICKS
                            );
                            player.playSound(Objects.requireNonNull(SoundEvents.WITHER_SPAWN), 0.9f, 0.8f);
                        }
                        session.setLastBossIntroSeconds(secondsRemaining);
                        EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                            QuestSequencePayload.Phase.BOSS_INTRO, secondsRemaining,
                            session.getQuest().getDisplayName(),
                            "Boss incoming",
                            List.of("Boss: " + session.getQuest().getMobConfig().displayName));
                    }
                if (ticksRemaining <= 0) {
                    session.clearPendingBossIntro();
                    session.setRespawnCountdownActive(false);
                    EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                        QuestSequencePayload.Phase.STARTED, 0);
                    WaveManager.INSTANCE.startWave(session);
                    EnduranceEventHandler.onWaveStart(player, session, session.getQuest().getCurrentWave());
                }
                continue;
            }

            int ticksRemaining = session.tickWaveStartCountdown();
            int secondsRemaining = (int) Math.ceil(ticksRemaining / 20.0);

            if (secondsRemaining > 0 && secondsRemaining != session.getLastWaveCountdownSeconds()) {
                if (session.getLastWaveCountdownSeconds() < 0) {
                    EnduranceTelemetryService.INSTANCE.recordCountdownStarted(session.getQuest().getQuestId());
                }
                session.setLastWaveCountdownSeconds(secondsRemaining);
                EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                    QuestSequencePayload.Phase.WAVE_INCOMING, secondsRemaining,
                    session.getQuest().getDisplayName(),
                    "Wave " + session.getQuest().getCurrentWave() + " incoming",
                    List.of());
                if (secondsRemaining <= 3) {
                    float pitch = 1.1f + (3 - secondsRemaining) * 0.1f;
                    player.playSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_HAT.value()), 0.7f, pitch);
                }
                player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.endurance.wave_starting_in", secondsRemaining)
                    .withStyle(ChatFormatting.YELLOW)));
            }

            if (ticksRemaining <= 0) {
                session.clearPendingWaveStart();
                session.setRespawnCountdownActive(false);
                UUID questId = session.getQuest().getQuestId();
                if (BossWaveSystem.INSTANCE.isBossWave(session.getQuest().getCurrentWave(), questId)) {
                    session.scheduleBossIntro(EnduranceQuestManager.BOSS_INTRO_TICKS);
                    session.setLastBossIntroSeconds(-1);
                    BossWaveSystem.INSTANCE.triggerBossAlert(player, session.getQuest().getDisplayName());
                    continue;
                }
                EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                    QuestSequencePayload.Phase.STARTED, 0);
                player.playSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_PLING.value()), 0.9f, 1.1f);
                WaveManager.INSTANCE.startWave(session);
                EnduranceEventHandler.onWaveStart(player, session, session.getQuest().getCurrentWave());
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
            ArenaContext arena = session.getArena();
            if (arena == null) {
                // Session is still pending (instance not ready) or arena setup failed.
                continue;
            }

            // Find the player
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) continue;

            ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(playerId));
            if (player == null) continue;

            // Build modifier list
            List<String> modifiers = new ArrayList<>();
            var waveStateOpt = WaveManager.INSTANCE.getWaveState(arena.getId());
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

            // Get flow state data
            int flowStateOrdinal = comboSession != null ? comboSession.getFlowState().ordinal() : 1; // NEUTRAL = 1
            float virtuosoProgress = comboSession != null ? comboSession.getVirtuosoProgress() : 0f;
            float staleRisk = comboSession != null ? comboSession.getStaleRisk() : 0f;
            int uniqueActionCount = comboSession != null ? comboSession.getUniqueActionCount() : 0;

            // Get momentum data
            MomentumTracker.MomentumSession momentumSession = MomentumTracker.INSTANCE.getSession(playerId);
            int momentumPercent = momentumSession != null ? momentumSession.getMomentumPercent() : 50;
            int momentumStateOrdinal = momentumSession != null ? momentumSession.getState().ordinal() : 1; // BUILDING = 1
            boolean isOverdrive = momentumSession != null && momentumSession.isInOverdrive();
            long overdriveRemaining = momentumSession != null ? momentumSession.getOverdriveRemaining() : 0;

            // Get wave progress
            int mobsKilledInWave = waveStateOpt.map(WaveManager.WaveState::getKilled).orElse(0);
            int totalMobsInWave = waveStateOpt.map(WaveManager.WaveState::getTotalToSpawn).orElse(quest.getCurrentWaveMobCount());

            WaveObjectiveState objective = waveStateOpt.map(WaveManager.WaveState::getObjective).orElse(null);
            int objectiveTypeOrdinal = objective != null ? objective.getType().ordinal() : WaveObjectiveState.Type.KILL_ALL.ordinal();
            String objectiveTitle = objective != null ? objective.getTitle() : "";
            String objectiveDescription = objective != null ? objective.getDescription() : "";
            int objectiveProgress = objective != null ? objective.getProgressForUi() : 0;
            int objectiveTarget = objective != null ? objective.getTargetForUi() : 0;
            boolean objectiveComplete = objective != null && objective.isComplete();
            boolean objectiveFailed = objective != null && objective.isFailed();

            String templateId = session.getTemplateId() != null ? session.getTemplateId() : "";
            Integer templateVersion = session.getTemplateVersion();
            int templateVersionValue = templateVersion != null ? templateVersion.intValue() : 0;
            String policyId = session.getPolicyId() != null ? session.getPolicyId() : "";
            Integer policyVersion = session.getPolicyVersion();
            int policyVersionValue = policyVersion != null ? policyVersion.intValue() : 0;
            String instanceId = session.getInstanceId() != null ? session.getInstanceId().toString() : "";
            String arenaId = arena.getId() != null ? arena.getId().toString() : "";
            String difficultyLabel = session.getDifficultyLabel() != null ? session.getDifficultyLabel() : "";
            String questTypeLabel = session.getQuestTypeLabel() != null ? session.getQuestTypeLabel() : "";

            // Create sync payload
            QuestSyncPayload payload = new QuestSyncPayload(
                true,
                quest.getDisplayName(),
                templateId,
                templateVersionValue,
                policyId,
                policyVersionValue,
                instanceId,
                arenaId,
                difficultyLabel,
                questTypeLabel,
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
                objectiveTypeOrdinal,
                objectiveTitle,
                objectiveDescription,
                objectiveProgress,
                objectiveTarget,
                objectiveComplete,
                objectiveFailed,
                modifiers,
                currentCombo,
                maxCombo,
                styleScore,
                styleRankOrdinal,
                flowStateOrdinal,
                virtuosoProgress,
                staleRisk,
                uniqueActionCount,
                momentumPercent,
                momentumStateOrdinal,
                isOverdrive,
                overdriveRemaining
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
            EnduranceQuestManager.ActiveQuestSession activeSession = session.get();
            if (activeSession.isAwaitingRespawnChoice()
                || activeSession.isRespawnRequested()
                || activeSession.getQuest().getState() != EnduranceQuestState.IN_PROGRESS) {
                return;
            }

            ArenaContext arena = activeSession.getArena();
            if (arena == null) {
                // Arena not yet assigned, skip confinement check
                return;
            }
            if (!player.level().dimension().equals(arena.getLevel().dimension())) {
                return;
            }
            net.minecraft.world.phys.AABB bounds = resolveArenaBounds(activeSession);
            if (bounds == null) {
                return;
            }
            if (!bounds.contains(Objects.requireNonNull(player.position()))) {
                ArenaHandle handle = activeSession.getArenaHandle();
                net.minecraft.core.BlockPos center = handle != null
                    ? new net.minecraft.core.BlockPos(handle.center().x(), handle.center().y(), handle.center().z())
                    : arena.getCenter();
                player.teleportTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
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

    @javax.annotation.Nullable
    private static net.minecraft.world.phys.AABB resolveArenaBounds(EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null) {
            return null;
        }
        ArenaHandle handle = session.getArenaHandle();
        if (handle != null && handle.bounds() != null) {
            ArenaHandle.AABB bounds = handle.bounds();
            return new net.minecraft.world.phys.AABB(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0
            );
        }
        ArenaContext arena = session.getArena();
        return arena != null ? arena.getBounds() : null;
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

            ArenaContext arena = session.getArena();
            if (arena == null) {
                // Arena not yet assigned, skip cleanup for this session
                continue;
            }
            net.minecraft.server.level.ServerLevel level = arena.getLevel();

            net.minecraft.world.phys.AABB bounds = resolveArenaBounds(session);
            if (bounds == null) {
                continue;
            }

            // Get all entities in arena bounds
            List<Entity> entitiesInArena = level.getEntities(
                (Entity) null,
                bounds,
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

            ArenaContext arena = session.getArena();
            if (arena == null || session.getArenaHandle() == null) {
                continue;
            }
            Optional<WaveManager.WaveState> waveStateOpt = WaveManager.INSTANCE.getWaveState(arena.getId());

            if (waveStateOpt.isEmpty()) continue;

            WaveManager.WaveState waveState = waveStateOpt.get();
            net.minecraft.server.level.ServerLevel level = arena.getLevel();

            if (!waveState.getObjective().shouldRespawnExternalDeaths()) {
                continue;
            }

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
        int successfulRespawns = WaveManager.INSTANCE.respawnMissingMobs(session, waveState, count, deadMobIds);
        if (successfulRespawns <= 0) {
            return;
        }

        EnduranceTelemetryService.INSTANCE.recordExternalDeathRespawn(waveState.getQuest().getQuestId(), successfulRespawns);

        // Notify player about respawned mobs
        UUID playerId = session.getPlayerId();
        var serverInstance = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerPlayer player = serverInstance != null ? serverInstance.getPlayerList().getPlayer(Objects.requireNonNull(playerId)) : null;
        if (player != null) {
            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.endurance.mobs_respawned", successfulRespawns)
                .withStyle(ChatFormatting.YELLOW)));
        }
    }
}
