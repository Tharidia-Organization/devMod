package com.devmod.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.compat.mods.easydiet.EasyDietCompat;
import com.devmod.endurance.analytics.LiveAnalyticsHookManager;
import com.devmod.endurance.combat.ComboSystemFacade;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.nutrition.NutritionBridgeSystem;
import com.devmod.endurance.services.InstanceServicesFacade;
import com.devmod.party.QuestSequencePayload;
import com.devmod.party.QuestStartSequence;
import com.devmod.runtime.DynamicDimensionManager;
import com.devmod.runtime.environment.DimensionEnvironmentManager;
import com.devmod.shared.SharedColorTokens;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;
import com.devmod.util.I18n;

public class EnduranceEventTick {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceEventTick.class);

    private static int tickCounter = 0;
    private static int gamificationTickCounter = 0;
    private static int overrideCleanupTickCounter = 0;

    private static final int WAVE_CHECK_INTERVAL = 20; // Check every second
    private static final int ARENA_CLEANUP_INTERVAL = 40; // Check every 2 seconds
    private static final int MOB_VALIDATION_INTERVAL = 60; // Check every 3 seconds
    private static final int MOB_AI_DEBUG_INTERVAL = 100; // Debug AI state every 5 seconds (just for monitoring)
    private static final int OVERRIDE_CLEANUP_INTERVAL = 300; // Clean up overrides every 5 minutes (300 seconds)
    private static final long DIMENSION_RECOVERY_COOLDOWN_MS = 2000;
    private static final long CONFINEMENT_LOG_COOLDOWN_MS = 1000;

    // ═══════════════════════════════════════════════════════════════
    // MAIN TICK HANDLER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Server tick handler for wave management and quest updates.
     */
    public static void onServerTick() {
        tickCounter++;

        // Update combo decay every tick (via facade)
        if (ComboSystemFacade.isInitialized()) {
            ComboSystemFacade.get().tick();
        }

        // Update momentum decay every tick
        MomentumTracker.INSTANCE.tick();

        // Tick execution system
        com.devmod.combat.bridge.CombatEnduranceBridge.get().tickExecutionSystem();

        // Tick live analytics hooks (throttled internally to 1/sec)
        LiveAnalyticsHookManager.INSTANCE.tick();

        // Prune resonance hit records. ResonanceChainSystem was written with two cleanup mechanisms
        // -- this periodic prune, which drops hits older than HIT_EXPIRY_MS and removes the emptied
        // entries, and a clear() for quest end -- and NEITHER had a caller. This fixes the leak:
        // recentHits is keyed by entity id and no other code path ever removes an entry, so the map
        // grew for the life of the server.
        //
        // It does NOT fix cross-quest contamination, and an earlier version of this comment claimed
        // it did. checkResonance already skips records from another quest, and recordHit prunes
        // expired ones on every hit against a one-second expiry, so a recycled entity id could not
        // carry hits between quests. The defect was memory, not correctness. clear() still has no
        // caller and quest end still does not drop that quest's records eagerly.
        //
        // The method throttles itself to once a second internally and tolerates the tickCounter
        // wrap below.
        com.devmod.endurance.resonance.ResonanceChainSystem.INSTANCE.tick(tickCounter);

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

            // Tick dimension environment manager (enforces frozen time per-dimension)
            DimensionEnvironmentManager.INSTANCE.tick(server);

            tickPendingInstanceStarts(server);
            tickPendingWaveStarts(server);

            // Tick perk effects and enforce arena confinement for all players in active quests
            java.util.Set<UUID> tickedArenas = new java.util.HashSet<>();
            for (Map.Entry<UUID, EnduranceQuestManager.ActiveQuestSession> entry :
                EnduranceQuestManager.INSTANCE.getActiveSessions().entrySet()) {
                UUID playerId = entry.getKey();
                EnduranceQuestManager.ActiveQuestSession session = entry.getValue();
                ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(playerId));
                if (player != null) {
                    if (!session.shouldProcessGameplay()) {
                        continue;
                    }
                    boolean spectator = session.isPartySpectator();
                    ArenaContext arena = session.getArena();
                    if (!spectator && arena != null) {
                        UUID arenaId = arena.getId();
                        if (arenaId != null && tickedArenas.add(arenaId)) {
                            WaveManager.INSTANCE.tickWave(session, player);
                        }
                    }
                    if (!spectator) {
                        PerkSystem.INSTANCE.tick(player);

                        // Tick nutrition system for Easy-Diet integration
                        if (EasyDietCompat.isAvailable()) {
                            NutritionBridgeSystem.INSTANCE.tick(player);
                        }

                        com.devmod.combat.bridge.CombatEnduranceBridge.get().tickExecutionPlayer(player);

                        UUID questId = session.getQuest().getQuestId();
                        com.devmod.endurance.bargain.DevilsBargainManager.INSTANCE.tickPlayer(player, questId);

                        // Only tick hazards during active combat (not during intermission)
                        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                                && session.getQuest().getState() == EnduranceQuestState.IN_PROGRESS) {
                            com.devmod.endurance.hazard.ArenaHazardSystem.INSTANCE.tick(serverLevel, questId, player);
                        }

                        if (tickCounter % 10 == 0) {
                            ensurePlayerInQuestDimension(player, session);
                            enforceArenaConfinement(player);
                        }
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

        // Debug mob AI state (every 2 seconds) - helps diagnose attack issues
        if (tickCounter % MOB_AI_DEBUG_INTERVAL == 0) {
            debugMobAttackState();
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

            // Clean up expired overrides every 5 minutes (300 seconds)
            overrideCleanupTickCounter++;
            if (overrideCleanupTickCounter >= OVERRIDE_CLEANUP_INTERVAL) {
                overrideCleanupTickCounter = 0;
                EnduranceQuestManager.INSTANCE.cleanupExpiredOverrides();
                com.devmod.arena.override.TemplateOverrideManager.getInstance().cleanupExpired();
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
        java.util.Set<UUID> startedArenas = new java.util.HashSet<>();
        for (EnduranceQuestManager.ActiveQuestSession session :
                EnduranceQuestManager.INSTANCE.getActiveSessions().values()) {
            // Check if any countdown is pending (wave, safe window, or boss intro)
            // We need to process all of these, not just wave start
            if (!session.isWaveStartPending() && !session.isSafeWindowPending() && !session.isBossIntroPending()) {
                continue;
            }
            if (session.getQuest().getState() != EnduranceQuestState.IN_PROGRESS) {
                session.clearPendingWaveStart();
                session.clearPendingBossIntro();
                session.clearPendingSafeWindow();
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
                if (session.isPracticeMode()) {
                    session.clearPendingBossIntro();
                    if (!session.isWaveStartPending()) {
                        session.scheduleWaveStart(EnduranceQuestManager.WAVE_START_COUNTDOWN_TICKS);
                        session.setLastWaveCountdownSeconds(-1);
                    }
                    continue;
                }
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
                        List.of("Boss: " + session.getQuest().getMobConfig().getDisplayName()));
                }
                if (ticksRemaining <= 0) {
                    int bossWaveNumber = session.getQuest().getCurrentWave();
                    session.clearPendingBossIntro();
                    session.setRespawnCountdownActive(false);
                    UUID arenaId = arena.getId();

                    // Clear completed wave state before boss spawn (same fix as continueToNextWave)
                    if (arenaId != null) {
                        boolean cleared = WaveManager.INSTANCE.clearCompletedWaveState(arenaId);
                        if (cleared) {
                            LOGGER.debug("[BossDebug] Cleared completed wave state for arena {} before boss spawn", arenaId);
                        }
                    }

                    boolean hasExistingWaveState = arenaId != null && WaveManager.INSTANCE.getWaveState(arenaId).isPresent();
                    if (hasExistingWaveState) {
                        LOGGER.warn("[BossDebug] Boss intro finished for wave {} but BLOCKED by existing wave state",
                            bossWaveNumber);
                        session.clearPendingWaveStart();
                        continue;
                    }
                    PartyQuestSession partySession = session.getPartyId() != null
                        ? EnduranceQuestManager.INSTANCE.getPartySession(session.getPartyId()).orElse(null)
                        : null;
                    if (partySession != null && partySession.isActive()) {
                        if (arenaId != null && !startedArenas.add(arenaId)) {
                            session.clearPendingWaveStart();
                            continue;
                        }
                        EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                            QuestSequencePayload.Phase.STARTED, 0);
                        WaveManager.INSTANCE.startWave(session);
                        LOGGER.info("[BossDebug] Boss wave {} started successfully (party mode)", bossWaveNumber);
                        boolean applyShared = true;
                        for (UUID memberId : partySession.getMembers()) {
                            EnduranceQuestManager.ActiveQuestSession memberSession =
                                EnduranceQuestManager.INSTANCE.getActiveSession(memberId).orElse(null);
                            if (memberSession == null) continue;
                            ServerPlayer member = server.getPlayerList().getPlayer(
                                Objects.requireNonNull(memberId, "memberId cannot be null"));
                            if (member != null) {
                                EnduranceEventHandler.onWaveStart(member, memberSession,
                                    memberSession.getQuest().getCurrentWave(), applyShared);
                                applyShared = false;
                            }
                        }
                        continue;
                    }
                    EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                        QuestSequencePayload.Phase.STARTED, 0);
                    WaveManager.INSTANCE.startWave(session);
                    LOGGER.info("[BossDebug] Boss wave {} started successfully", bossWaveNumber);
                    EnduranceEventHandler.onWaveStart(player, session, session.getQuest().getCurrentWave());
                }
                continue;
            }

            int ticksRemaining = session.tickWaveStartCountdown();
            int secondsRemaining = (int) Math.ceil(ticksRemaining / 20.0);

            if (secondsRemaining > 0 && secondsRemaining != session.getLastWaveCountdownSeconds()) {
                if (session.getLastWaveCountdownSeconds() < 0) {
                    boolean shouldRecord = true;
                    if (session.getPartyId() != null) {
                        PartyQuestSession partySession = EnduranceQuestManager.INSTANCE
                            .getPartySession(session.getPartyId())
                            .orElse(null);
                        if (partySession != null && partySession.isActive()) {
                            shouldRecord = partySession.markCountdownWave(session.getQuest().getCurrentWave());
                        }
                    }
                    if (shouldRecord) {
                        if (!session.isPracticeMode()) {
                            EnduranceTelemetryService.INSTANCE.recordCountdownStarted(session.getQuest().getQuestId());
                        }
                    }
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
                    .withStyle(SharedColorTokens.Chat.YELLOW)));
            }

            if (ticksRemaining <= 0) {
                session.clearPendingWaveStart();
                session.setRespawnCountdownActive(false);
                UUID questId = session.getQuest().getQuestId();
                int currentWave = session.getQuest().getCurrentWave();
                boolean isBoss = !session.isPracticeMode()
                    && BossWaveSystem.INSTANCE.isBossWave(currentWave, questId);
                LOGGER.info("[BossDebug] Wave countdown finished. Wave={}, isBossWave={}, questId={}",
                    currentWave, isBoss, questId);
                if (isBoss) {
                    LOGGER.info("[BossDebug] Scheduling boss intro for wave {}", currentWave);
                    session.scheduleBossIntro(EnduranceQuestManager.BOSS_INTRO_TICKS);
                    session.setLastBossIntroSeconds(-1);
                    BossWaveSystem.INSTANCE.triggerBossAlert(player, session.getQuest().getDisplayName());
                    continue;
                }
                UUID arenaId = arena.getId();
                if (arenaId != null && WaveManager.INSTANCE.getWaveState(arenaId).isPresent()) {
                    continue;
                }
                PartyQuestSession partySession = session.getPartyId() != null
                    ? EnduranceQuestManager.INSTANCE.getPartySession(session.getPartyId()).orElse(null)
                    : null;
                if (partySession != null && partySession.isActive()) {
                    if (arenaId != null && !startedArenas.add(arenaId)) {
                        continue;
                    }
                    EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                        QuestSequencePayload.Phase.STARTED, 0);
                    player.playSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_PLING.value()), 0.9f, 1.1f);
                    WaveManager.INSTANCE.startWave(session);
                    boolean applyShared = true;
                    for (UUID memberId : partySession.getMembers()) {
                        EnduranceQuestManager.ActiveQuestSession memberSession =
                            EnduranceQuestManager.INSTANCE.getActiveSession(memberId).orElse(null);
                        if (memberSession == null) continue;
                        ServerPlayer member = server.getPlayerList().getPlayer(
                            Objects.requireNonNull(memberId, "memberId cannot be null"));
                        if (member != null) {
                            EnduranceEventHandler.onWaveStart(member, memberSession,
                                memberSession.getQuest().getCurrentWave(), applyShared);
                            applyShared = false;
                        }
                    }
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
                    modifiers.add(mod.getDisplayName());
                }
            }

            // Get combo session data (via facade)
            IComboSession comboSession = ComboSystemFacade.isInitialized()
                ? ComboSystemFacade.get().getSession(playerId).orElse(null)
                : null;
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

            String questId = quest.getQuestId().toString();
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
                questId,
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

            // Also send LVC (Last Value Cache) telemetry sync
            com.devmod.telemetry.network.LVCSyncPayload lvcPayload =
                com.devmod.telemetry.duckdb.lvc.TelemetryLVC.INSTANCE.buildSyncPayload(playerId);
            if (lvcPayload.hasData()) {
                com.devmod.network.NetworkHandler.sendLvcSync(player, lvcPayload);
            }
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
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            boolean outsideXZ = x < bounds.minX || x > bounds.maxX || z < bounds.minZ || z > bounds.maxZ;
            double verticalSpan = bounds.maxY - bounds.minY;
            boolean enforceY = verticalSpan >= 4.0;
            boolean outsideY = enforceY && (y < (bounds.minY - 2.0) || y > (bounds.maxY + 2.0));

            if (outsideXZ || outsideY) {
                ArenaHandle handle = activeSession.getArenaHandle();
                net.minecraft.core.BlockPos targetPos = handle != null && handle.playerSpawnPositions() != null
                    && !handle.playerSpawnPositions().isEmpty()
                    ? new net.minecraft.core.BlockPos(handle.primaryPlayerSpawn().x(),
                        handle.primaryPlayerSpawn().y(),
                        handle.primaryPlayerSpawn().z())
                    : arena.getCenter();
                // If outsideY, use the center of the arena bounds for Y, not the spawn pos
                // This fixes issues where spawn positions are incorrectly stored outside bounds
                double targetY;
                if (outsideY) {
                    // Place player at center Y of the arena bounds
                    targetY = (bounds.minY + bounds.maxY) / 2.0;
                } else {
                    targetY = enforceY ? targetPos.getY() : Math.max(targetPos.getY(), bounds.minY + 1.0);
                }
                long now = System.currentTimeMillis();
                if (activeSession.canLogConfinement(now, CONFINEMENT_LOG_COOLDOWN_MS)) {
                    activeSession.markConfinementLog(now);
                    LOGGER.warn("[EnduranceQuest] Arena confinement teleport for {} (pos=({}, {}, {}), outsideXZ={}, outsideY={}, bounds=({}, {}, {})..({}, {}, {}), target=({}, {}, {}), wave={}, questId={})",
                        player.getName().getString(),
                        x, y, z,
                        outsideXZ, outsideY,
                        bounds.minX, bounds.minY, bounds.minZ,
                        bounds.maxX, bounds.maxY, bounds.maxZ,
                        targetPos.getX(), targetY, targetPos.getZ(),
                        activeSession.getQuest().getCurrentWave(),
                        activeSession.getQuest().getQuestId());
                }
                // Use aggressive teleport that forces client sync
                net.minecraft.server.level.ServerLevel level = arena.getLevel();
                level.getChunkAt(targetPos); // Ensure chunk is loaded on server

                // Stop any movement and reset player state before teleporting
                player.setDeltaMovement(0, 0, 0);
                player.fallDistance = 0;

                // Force position update using connection for reliable client sync
                double tx = targetPos.getX() + 0.5;
                double tz = targetPos.getZ() + 0.5;
                player.connection.teleport(tx, targetY, tz, player.getYRot(), player.getXRot());

                // Also set the position directly as fallback
                player.moveTo(tx, targetY, tz, player.getYRot(), player.getXRot());
            }
        }
    }

    private static void ensurePlayerInQuestDimension(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session) {
        if (player == null || session == null || !session.isInInstanceDimension()) {
            return;
        }
        ArenaContext arena = session.getArena();
        if (arena == null || arena.getLevel() == null) {
            return;
        }
        if (player.level().dimension().equals(arena.getLevel().dimension())) {
            return;
        }
        if (player.isDeadOrDying()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!session.canAttemptDimensionRecovery(now, DIMENSION_RECOVERY_COOLDOWN_MS)) {
            return;
        }
        session.markDimensionRecoveryAttempt(now);

        boolean awaitingRespawn = session.isAwaitingRespawnChoice() || session.isRespawnRequested();
        LOGGER.warn("[EnduranceQuest] Player {} out of arena dimension during quest (state={}, awaitingRespawn={})",
            player.getName().getString(), session.getQuest().getState(), awaitingRespawn);

        if (awaitingRespawn) {
            EnduranceQuestManager.INSTANCE.handleVanillaRespawn(player);
            return;
        }

        UUID instanceId = session.getInstanceId();
        if (instanceId == null) {
            LOGGER.error("[EnduranceQuest] Cannot recover player {} - missing instance id",
                player.getName().getString());
            return;
        }
        boolean teleported = EnduranceQuestManager.INSTANCE.teleportPlayerToArena(
            player,
            session,
            true,
            false
        );
        if (teleported) {
            EndurancePlayerStateManager.INSTANCE.applySafeWindowEffects(player, EnduranceQuestManager.SAFE_WINDOW_TICKS);
            LOGGER.info("[EnduranceQuest] Recovered player {} to instance {}",
                player.getName().getString(), instanceId);
        } else {
            boolean instanceAlive = InstanceServicesFacade.INSTANCE.getInstance(instanceId)
                .map(instance -> instance.getState().isAlive())
                .orElse(false);
            boolean dimensionAlive = DynamicDimensionManager.INSTANCE.hasDimension(instanceId);
            if (!instanceAlive || !dimensionAlive) {
                LOGGER.error("[EnduranceQuest] Recovery failed for {} - instance unavailable (instanceAlive={}, dimensionAlive={})",
                    player.getName().getString(), instanceAlive, dimensionAlive);
                EnduranceQuestManager.INSTANCE.handleCriticalTeleportFailure(
                    player,
                    session,
                    "instance_unavailable"
                );
            } else {
                LOGGER.warn("[EnduranceQuest] Failed to recover player {} to instance {} (instance alive, will retry)",
                    player.getName().getString(), instanceId);
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
        // Early exit if no active sessions - avoids server lookup
        var sessions = EnduranceQuestManager.INSTANCE.getActiveSessions();
        if (sessions.isEmpty()) {
            LOGGER.trace("[EnduranceQuest] cleanupExternalMobsInArenas: skipped (no active sessions)");
            return;
        }
        LOGGER.trace("[EnduranceQuest] cleanupExternalMobsInArenas: processing {} sessions", sessions.size());

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (EnduranceQuestManager.ActiveQuestSession session : sessions.values()) {
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
                    boolean isQuestMob = data.contains(EnduranceTags.QUEST_ID) &&
                        data.contains(EnduranceTags.ARENA_ID) &&
                        Objects.requireNonNull(data.getUUID(EnduranceTags.ARENA_ID)).equals(arena.getId());

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
        // Early exit if no active sessions - avoids server lookup
        var sessions = EnduranceQuestManager.INSTANCE.getActiveSessions();
        if (sessions.isEmpty()) {
            LOGGER.trace("[EnduranceQuest] validateAndRespawnWaveMobs: skipped (no active sessions)");
            return;
        }
        LOGGER.trace("[EnduranceQuest] validateAndRespawnWaveMobs: checking {} sessions", sessions.size());

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (Map.Entry<UUID, EnduranceQuestManager.ActiveQuestSession> entry : sessions.entrySet()) {
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
                    // Log dead mob info at debug level (mobs will be cleaned up by respawnMissingMobs)
                    if (entity == null) {
                        LOGGER.debug("[EnduranceQuest] Mob {} not found via getEntity() - removed from level", mobId);
                    } else {
                        Entity.RemovalReason removalReason = entity.getRemovalReason();
                        LOGGER.debug("[EnduranceQuest] Mob {} exists but isAlive()={}, isRemoved()={}, removalReason={}",
                            mobId, entity.isAlive(), entity.isRemoved(),
                            removalReason != null ? removalReason.name() : "null");
                    }
                    deadMobIds.add(mobId);
                }
            }

            // Calculate expected alive mobs (spawned - killed by player)
            int expectedAlive = waveState.getSpawned() - waveState.getKilled();

            // If we have fewer alive mobs than expected, some died from external causes
            int missingMobs = expectedAlive - aliveMobs;

            if (missingMobs > 0) {
                LOGGER.info("[EnduranceQuest] Wave {} has {} missing mobs (external deaths). Cleaning up and respawning...",
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

        // Log respawn budget usage for telemetry/debug
        LOGGER.debug("[EnduranceQuest] External respawn: {}/{} used (wave {})",
            waveState.getExternalRespawnCount(),
            waveState.getExternalRespawnLimit(),
            waveState.getWaveNumber());

        // Notify player about respawned mobs
        UUID playerId = session.getPlayerId();
        if (playerId == null) {
            // Party session without primary player - skip notification
            return;
        }
        var serverInstance = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerPlayer player = serverInstance != null ? serverInstance.getPlayerList().getPlayer(playerId) : null;
        if (player != null) {
            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.endurance.mobs_respawned", successfulRespawns)
                .withStyle(SharedColorTokens.Chat.YELLOW)));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MOB AI DEBUG
    // ═══════════════════════════════════════════════════════════════

    /**
     * Debug mob attack state and apply fallback targeting to all endurance mobs.
     * This fixes modded mobs that have custom dimension checks that prevent targeting in dynamic dimensions.
     */
    private static void debugMobAttackState() {
        // Skip entirely if debug logging is disabled - saves iteration overhead
        if (!LOGGER.isDebugEnabled()) return;

        // Early exit if no active sessions
        var sessions = EnduranceQuestManager.INSTANCE.getActiveSessions();
        if (sessions.isEmpty()) return;

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (EnduranceQuestManager.ActiveQuestSession session : sessions.values()) {
            ArenaContext arena = session.getArena();
            if (arena == null) continue;

            Optional<WaveManager.WaveState> waveStateOpt = WaveManager.INSTANCE.getWaveState(arena.getId());
            if (waveStateOpt.isEmpty()) continue;

            WaveManager.WaveState waveState = waveStateOpt.get();
            net.minecraft.server.level.ServerLevel level = arena.getLevel();

            ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(session.getPlayerId()));
            if (player == null) continue;

            // Debug only first mob to monitor AI state (targeting handled by EnduranceTargetPlayerGoal)
            for (UUID mobId : waveState.getSpawnedMobs()) {
                Entity entity = level.getEntity(Objects.requireNonNull(mobId));
                if (entity instanceof Mob mob && entity.isAlive()) {
                    debugSingleMobAttackState(mob, player);
                    break; // Only debug first mob
                }
            }
        }
    }

    // NOTE: Fallback targeting is now handled by EnduranceTargetPlayerGoal
    // registered at spawn time in WaveManager.awakeMobAI()

    /**
     * Debug a single mob's attack state in detail.
     */
    private static void debugSingleMobAttackState(Mob mob, ServerPlayer player) {
        var target = mob.getTarget();
        Entity safePlayer = Objects.requireNonNull(player, "player");
        double distToPlayer = mob.distanceTo(safePlayer);
        boolean canSee = mob.getSensing().hasLineOfSight(safePlayer);

        // Default melee attack range
        double attackRange = 2.0;

        // Check navigation state
        var navigation = mob.getNavigation();
        boolean isNavigating = navigation.isInProgress();
        var currentPath = navigation.getPath();
        boolean hasPath = currentPath != null && !currentPath.isDone();

        // Log running goals - iterate through available goals and check which are running
        StringBuilder runningGoals = new StringBuilder();
        StringBuilder runningTargetGoals = new StringBuilder();

        for (var wrappedGoal : mob.goalSelector.getAvailableGoals()) {
            if (wrappedGoal.isRunning()) {
                if (runningGoals.length() > 0) runningGoals.append(", ");
                runningGoals.append(wrappedGoal.getGoal().getClass().getSimpleName());
            }
        }

        for (var wrappedGoal : mob.targetSelector.getAvailableGoals()) {
            if (wrappedGoal.isRunning()) {
                if (runningTargetGoals.length() > 0) runningTargetGoals.append(", ");
                runningTargetGoals.append(wrappedGoal.getGoal().getClass().getSimpleName());
            }
        }

        // Check if mob is in attack range
        boolean inAttackRange = distToPlayer <= attackRange + 1.0; // +1 for player hitbox

        // Only log if debug is enabled to avoid string building overhead
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[MobAI Debug] {} -> target={}, dist={}, inRange={}, canSee={}, nav={}, hasPath={}, goals=[{}], targetGoals=[{}]",
                mob.getType().toString(),
                target != null ? target.getName().getString() : "NONE",
                String.format("%.1f", distToPlayer),
                inAttackRange,
                canSee,
                isNavigating,
                hasPath,
                runningGoals.length() > 0 ? runningGoals.toString() : "none",
                runningTargetGoals.length() > 0 ? runningTargetGoals.toString() : "none"
            );

            // If mob has target but isn't attacking when in range, log more details
            if (target != null && inAttackRange && canSee) {
                for (var wrappedGoal : mob.goalSelector.getAvailableGoals()) {
                    var goal = wrappedGoal.getGoal();
                    String goalName = goal.getClass().getSimpleName();
                    if (goalName.contains("Attack") || goalName.contains("Melee")) {
                        boolean canUse = goal.canUse();
                        boolean isRunning = wrappedGoal.isRunning();
                        LOGGER.debug("[MobAI Debug]   Attack goal '{}': canUse={}, isRunning={}, priority={}",
                            goalName, canUse, isRunning, wrappedGoal.getPriority());
                    }
                }
            }
        }
    }
}
