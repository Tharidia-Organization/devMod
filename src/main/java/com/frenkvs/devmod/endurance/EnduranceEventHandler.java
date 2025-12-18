package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.util.I18n;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.frenkvs.devmod.endurance.analytics.LiveAnalyticsHookManager;
import com.frenkvs.devmod.endurance.analytics.WaveSummary;
import com.frenkvs.devmod.endurance.analytics.QuestResult;
import com.frenkvs.devmod.party.QuestStartSequence;
import com.frenkvs.devmod.telemetry.endurance.EnduranceTelemetryService;
import com.frenkvs.devmod.telemetry.player.PlayerAttributeTelemetryService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Central Event Handler for the Endurance Quest system.
 *
 * This handler integrates all the subsystems:
 * - EnduranceQuestManager: Core quest logic
 * - ComboSystem: Style scoring (DMC-style)
 * - PerkSystem: Roguelike upgrades
 * - BossWaveSystem: Boss mechanics
 * - MutatorSystem: Dynamic modifiers
 * - RewardSystem: Loot and currency
 * - CombatTracker: Stats tracking
 *
 * Delegates to:
 * - EnduranceEventCombat: Combat event processing (damage, death, critical hits)
 * - EnduranceEventTick: Server tick handlers, wave sync, arena cleanup
 */

@EventBusSubscriber(modid = "devmod")
public class EnduranceEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceEventHandler.class);

    // ═══════════════════════════════════════════════════════════════
    // QUEST LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Initialize all systems when a quest starts.
     * Called by EnduranceQuestManager.startQuest()
     */
    public static void onQuestStart(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session) {
        UUID playerId = player.getUUID();
        UUID questId = session.getQuest().getQuestId();

        // Create combo session for DMC-style scoring
        ComboSystem.ComboSession comboSession = ComboSystem.INSTANCE.startSession(playerId, questId);
        EnduranceEventCombat.putComboSession(playerId, comboSession);

        // Create mutator session with random mutators
        MutatorSystem.MutatorSession mutatorSession = MutatorSystem.INSTANCE.createSession(questId, 3, 1);
        EnduranceEventCombat.putMutatorSession(questId, mutatorSession);

        // Create perk session for roguelike upgrades
        PerkSystem.INSTANCE.startSession(playerId, questId);

        // Create combat tracking session
        CombatTracker.INSTANCE.startTracking(questId, playerId, session.getQuest().getMobConfig().mobId);

        // Start live analytics session for real-time feedback hooks
        LiveAnalyticsHookManager.INSTANCE.onQuestStart(questId, playerId);

        // Record telemetry for quest start
        EnduranceTelemetryService.INSTANCE.recordQuestStart(
            questId,
            playerId,
            session.getQuest().getDisplayName(),
            session.getQuest().getTotalWaves(),
            session.getQuest().isEndlessMode(),
            1, // party size - single player for now
            QuestType.PVE_COOP // default quest type
        );

        // Trigger player attribute snapshot on quest start
        PlayerAttributeTelemetryService.INSTANCE.recordSnapshot(player, "quest_start");

        LOGGER.info("[EnduranceQuest] Quest started for {} with {} mutators",
            player.getName().getString(), mutatorSession.getActiveMutatorCount());
    }

    /**
     * Cleanup all systems when a quest ends.
     * Called when quest completes, fails, or is abandoned.
     */
    public static void onQuestEnd(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session,
                                   boolean completed) {
        UUID playerId = player.getUUID();
        UUID questId = session.getQuest().getQuestId();

        // Get sessions before cleanup
        ComboSystem.ComboSession comboSession = EnduranceEventCombat.removeComboSession(playerId);
        MutatorSystem.MutatorSession mutatorSession = EnduranceEventCombat.removeMutatorSession(questId);

        // Get max combo before cleanup
        int maxCombo = comboSession != null ? comboSession.getMaxCombo() : 0;

        // Award rewards based on performance
        RewardSystem.QuestRewards rewards = RewardSystem.INSTANCE.calculateQuestRewards(
            player, session.getQuest(), comboSession, mutatorSession);

        // Send completion screen to client (only if quest completed or exited at checkpoint)
        if (rewards != null) {
            com.frenkvs.devmod.NetworkHandler.sendQuestCompletionScreen(
                player, session.getQuest(), rewards, comboSession, maxCombo);

            // Send token gain overlay animation
            if (rewards.tokensEarned > 0) {
                com.frenkvs.devmod.NetworkHandler.sendTokenGain(player, rewards.tokensEarned);
            }
        }

        // Cleanup combo system
        ComboSystem.INSTANCE.endSession(playerId);

        // Cleanup mutator system
        MutatorSystem.INSTANCE.endSession(questId);

        // Cleanup perk system (removes applied attribute modifiers)
        PerkSystem.INSTANCE.endSession(player);

        // Finalize and stop combat tracking
        CombatTracker.QuestCombatSession combatSessionData = CombatTracker.INSTANCE.getSession(questId).orElse(null);
        CombatTracker.INSTANCE.stopTracking(questId);

        // Record gamification stats (leaderboards, badges, challenges)
        if (completed && combatSessionData != null) {
            GamificationManager.QuestCompletionResult gamificationResult =
                GamificationManager.INSTANCE.recordQuestCompletion(
                    playerId,
                    player.getName().getString(),
                    session.getQuest(),
                    combatSessionData
                );

            // Send badge unlock notifications for newly earned badges
            for (GamificationManager.Badge badge : gamificationResult.newBadges) {
                com.frenkvs.devmod.NetworkHandler.sendBadgeUnlock(
                    player, badge.name, badge.rarity.displayName);
            }

            // Send record banner for new personal records
            if (gamificationResult.isNewWaveRecord) {
                com.frenkvs.devmod.NetworkHandler.sendRecordBanner(
                    player, "BEST WAVE", "Wave " + session.getQuest().getCurrentWave());
            }
            if (gamificationResult.isNewHighScore) {
                com.frenkvs.devmod.NetworkHandler.sendRecordBanner(
                    player, "HIGH SCORE", String.format("%,d pts", session.getQuest().getPointsEarnedThisSession()));
            }
        }

        // Record analytics session for detailed tracking
        if (combatSessionData != null) {
            EnduranceAnalytics.INSTANCE.recordSession(
                combatSessionData,
                session.getQuest(),
                player.getName().getString()
            );
        }

        // Notify live analytics hooks with quest result
        QuestResult result = buildQuestResult(
            questId, playerId, session.getQuest(), combatSessionData, comboSession, completed
        );
        LiveAnalyticsHookManager.INSTANCE.onQuestEnd(result);

        // Record telemetry for quest end
        EnduranceTelemetryService.INSTANCE.recordQuestEnd(
            questId,
            completed ? EnduranceQuestState.COMPLETED : EnduranceQuestState.FAILED,
            session.getQuest().getCurrentWave(),
            session.getQuest().getSessionDuration(),
            session.getQuest().getMobsKilledThisSession(),
            session.getQuest().getTotalDamageDealtThisSession(),
            session.getQuest().getDamageTakenThisSession()
        );

        // Trigger player attribute snapshot on quest end
        PlayerAttributeTelemetryService.INSTANCE.recordSnapshot(player, "quest_end");

        LOGGER.info("[EnduranceQuest] Quest ended for {} - Completed: {}, Style Rank: {}",
            player.getName().getString(), completed,
            comboSession != null ? comboSession.getHighestRank().displayName : "N/A");
    }

    // ═══════════════════════════════════════════════════════════════
    // WAVE EVENTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called when a new wave starts.
     */
    public static void onWaveStart(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session, int waveNumber) {
        UUID playerId = player.getUUID();
        EnduranceQuest quest = session.getQuest();

        // Reset combo for new wave
        ComboSystem.ComboSession comboSession = EnduranceEventCombat.getComboSession(playerId);
        if (comboSession != null) {
            comboSession.startNewWave();
        }

        // Check if this is a boss wave
        boolean isBossWave = BossWaveSystem.INSTANCE.isBossWave(waveNumber);

        if (isBossWave) {
            // Special boss wave announcement
            var bossFight = BossWaveSystem.INSTANCE.getBossFight(session.getArena().getId());
            String bossType = bossFight.map(bf -> bf.getArchetype().displayName).orElse("Champion");

            player.sendSystemMessage(I18n.translate("devmod.wave.boss_wave_divider")
                .withStyle(ChatFormatting.DARK_PURPLE));
            player.sendSystemMessage(I18n.translate("devmod.wave.boss_wave_title", waveNumber)
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            player.sendSystemMessage(I18n.translate("devmod.wave.boss_appeared", bossType, quest.getMobConfig().displayName)
                .withStyle(ChatFormatting.RED));
            player.sendSystemMessage(I18n.translate("devmod.wave.defeat_boss")
                .withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(I18n.translate("devmod.wave.boss_wave_divider")
                .withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            // Normal wave announcement
            int mobCount = quest.getCurrentWaveMobCount();
            player.sendSystemMessage(I18n.translate("devmod.wave.normal_divider")
                .withStyle(ChatFormatting.DARK_RED));
            player.sendSystemMessage(I18n.translate("devmod.wave.wave_title", waveNumber, quest.getTotalWaves())
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            player.sendSystemMessage(I18n.translate("devmod.wave.enemies_count", mobCount, quest.getMobConfig().displayName)
                .withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(I18n.translate("devmod.wave.normal_divider")
                .withStyle(ChatFormatting.DARK_RED));
        }

        LOGGER.debug("[EnduranceQuest] Wave {} started for {} (boss: {})",
            waveNumber, player.getName().getString(), isBossWave);
    }

    /**
     * Called when a wave is completed.
     */
    public static void onWaveComplete(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session, int waveNumber) {
        UUID playerId = player.getUUID();
        UUID questId = session.getQuest().getQuestId();
        EnduranceQuest quest = session.getQuest();

        // Get tracking sessions
        ComboSystem.ComboSession comboSession = EnduranceEventCombat.getComboSession(playerId);
        MutatorSystem.MutatorSession mutatorSession = EnduranceEventCombat.getMutatorSession(questId);

        // Get combat stats for this wave
        CombatTracker.QuestCombatSession combatSession = CombatTracker.INSTANCE.getSession(questId).orElse(null);
        CombatTracker.WaveCombatStats waveStats = combatSession != null ? combatSession.getCurrentWaveStats() : null;

        // Calculate wave statistics
        String styleRank = comboSession != null ? comboSession.getCurrentRank().displayName : "D";
        int maxCombo = comboSession != null ? comboSession.getMaxCombo() : 0;
        float waveDamage = waveStats != null ? waveStats.damageDealt : 0;
        int waveKills = waveStats != null ? waveStats.kills : 0;
        float waveDamageTaken = waveStats != null ? waveStats.damageTaken : 0;

        // === DETAILED LOGGING ===
        LOGGER.info("[EnduranceQuest] Wave {} completed for quest {} by player {}",
            waveNumber, questId, player.getName().getString());
        LOGGER.info("[EnduranceQuest]   Combat Stats: {} damage dealt, {} kills, {} damage taken",
            String.format("%.1f", waveDamage), waveKills, String.format("%.1f", waveDamageTaken));
        LOGGER.info("[EnduranceQuest]   Style Stats: Rank {}, Max Combo {}", styleRank, maxCombo);
        if (mutatorSession != null) {
            LOGGER.info("[EnduranceQuest]   Active Mutators: {}", mutatorSession.getActiveMutatorCount());
        }

        // === COMBAT TRACKER - Prepare for next wave ===
        if (combatSession != null) {
            // Finalize current wave stats and prepare for next wave
            combatSession.startNewWave(waveNumber + 1);
            LOGGER.debug("[EnduranceQuest] Combat tracker advanced to wave {}", waveNumber + 1);
        }

        // === LIVE ANALYTICS - Notify hooks of wave transition ===
        WaveSummary waveSummary = buildWaveSummary(waveNumber, waveStats, comboSession);
        LiveAnalyticsHookManager.INSTANCE.onWaveComplete(waveNumber, waveSummary, waveNumber + 1);

        // === MUTATOR SYSTEM - Roll new mutators between waves ===
        if (mutatorSession != null && waveNumber % 3 == 0) {
            // Every 3 waves, potentially add a new mutator
            int prevMutatorCount = mutatorSession.getActiveMutatorCount();
            MutatorSystem.INSTANCE.rollNewMutator(mutatorSession, waveNumber);
            LOGGER.info("[EnduranceQuest] New mutator rolled at wave {}", waveNumber);

            // Trigger player attribute snapshot if mutator was added
            if (mutatorSession.getActiveMutatorCount() > prevMutatorCount) {
                PlayerAttributeTelemetryService.INSTANCE.recordSnapshot(player, "mutator_added_wave_" + waveNumber);
            }
        }

        // === PERK SYSTEM - Generate perk choices for player ===
        List<PerkSystem.Perk> perkChoices = PerkSystem.INSTANCE.generatePerkChoices(player, waveNumber);
        if (!perkChoices.isEmpty()) {
            LOGGER.info("[EnduranceQuest] Generated {} perk choices for player {}: {}",
                perkChoices.size(), player.getName().getString(),
                perkChoices.stream().map(p -> p.name).toList());
            // Send perk choices to client for UI display
            com.frenkvs.devmod.NetworkHandler.sendPerkChoices(player, waveNumber, perkChoices);
        }

        // === NOTIFY PLAYER ===
        player.sendSystemMessage(I18n.translate("devmod.wave.complete_divider")
            .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(I18n.translate("devmod.wave.complete_title", waveNumber)
            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        player.sendSystemMessage(I18n.translate("devmod.wave.style_rank_combo", styleRank, maxCombo)
            .withStyle(ChatFormatting.YELLOW));

        // Show wave combat summary
        player.sendSystemMessage(I18n.translate("devmod.wave.combat_divider")
            .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(I18n.translate("devmod.wave.damage_kills", String.format("%.0f", waveDamage), waveKills)
            .withStyle(ChatFormatting.WHITE));
        if (waveDamageTaken > 0) {
            player.sendSystemMessage(I18n.translate("devmod.wave.damage_taken", String.format("%.0f", waveDamageTaken))
                .withStyle(ChatFormatting.RED));
        } else {
            player.sendSystemMessage(I18n.translate("devmod.wave.flawless")
                .withStyle(ChatFormatting.AQUA));
        }


        if (quest.getCurrentWave() < quest.getTotalWaves() || quest.isEndlessMode()) {
            player.sendSystemMessage(I18n.translate("devmod.wave.checkpoint")
                .withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(I18n.translate("devmod.wave.continue_option", waveNumber + 1)
                .withStyle(ChatFormatting.WHITE));
            player.sendSystemMessage(I18n.translate("devmod.wave.exit_option")
                .withStyle(ChatFormatting.WHITE));
        } else {
            player.sendSystemMessage(I18n.translate("devmod.wave.quest_complete_final")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        player.sendSystemMessage(I18n.translate("devmod.wave.complete_divider")
            .withStyle(ChatFormatting.GOLD));
    }

    // ═══════════════════════════════════════════════════════════════
    // NEOFORGE EVENT SUBSCRIBERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle damage dealt by players to mobs in quests.
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        EnduranceEventCombat.handleDamagePost(event.getEntity(), event.getSource(), event.getNewDamage());
    }

    /**
     * Handle damage reduction from perks (Pre event to modify damage).
     */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();

        // Only process for players in quests
        if (target instanceof ServerPlayer player) {
            Optional<EnduranceQuestManager.ActiveQuestSession> session =
                EnduranceQuestManager.INSTANCE.getActiveSession(player);

            if (session.isPresent()) {
                float modifiedDamage = EnduranceEventCombat.handleDamagePre(player, event.getOriginalDamage());
                event.setNewDamage(modifiedDamage);
            }
        }
    }

    /**
     * Handle critical hits.
     */
    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.isVanillaCritical()) {
            Entity target = event.getTarget();
            float damage = event.getDamageMultiplier() * player.getAttackStrengthScale(0.5f);
            EnduranceEventCombat.handleCriticalHit(player, target, damage);
        }
    }

    /**
     * Handle mob deaths in quests.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Check if this is a quest mob
        if (EnduranceEventCombat.isQuestMob(entity)) {
            EnduranceEventCombat.handleMobDeath(entity, event.getSource());
        }

        // Check if a player in a quest died
        if (entity instanceof ServerPlayer player) {
            EnduranceEventCombat.handlePlayerDeath(player);
        }
    }

    /**
     * Server tick handler for wave management and quest updates.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        EnduranceEventTick.onServerTick();
    }

    /**
     * Handle player logout - cleanup quest session.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Notify QuestStartSequence about disconnect (cancels sequence if needed)
            QuestStartSequence.INSTANCE.onPlayerDisconnect(player.getUUID());

            Optional<EnduranceQuestManager.ActiveQuestSession> sessionOpt =
                EnduranceQuestManager.INSTANCE.getActiveSession(player);

            if (sessionOpt.isPresent()) {
                LOGGER.info("[EnduranceQuest] Player {} logged out during quest, cleaning up...",
                    player.getName().getString());

                // Treat as abandonment
                EnduranceQuestManager.INSTANCE.abandonQuest(player);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC GETTERS FOR UI
    // ═══════════════════════════════════════════════════════════════

    public static ComboSystem.ComboSession getComboSession(UUID playerId) {
        return EnduranceEventCombat.getComboSession(playerId);
    }

    public static MutatorSystem.MutatorSession getMutatorSession(UUID questId) {
        return EnduranceEventCombat.getMutatorSession(questId);
    }

    /**
     * Check if player is trying to leave the arena.
     */
    public static boolean canPlayerLeaveArena(ServerPlayer player) {
        return EnduranceEventTick.canPlayerLeaveArena(player);
    }

    /**
     * Teleport player back to arena if they try to escape.
     */
    public static void enforceArenaConfinement(ServerPlayer player) {
        EnduranceEventTick.enforceArenaConfinement(player);
    }

    // ═══════════════════════════════════════════════════════════════
    // ANALYTICS HELPER METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Builds a WaveSummary from combat and combo data.
     */
    private static WaveSummary buildWaveSummary(int waveNumber,
            CombatTracker.WaveCombatStats waveStats,
            ComboSystem.ComboSession comboSession) {

        if (waveStats == null) {
            return WaveSummary.empty(waveNumber);
        }

        return new WaveSummary(
            waveNumber,
            waveStats.duration,
            waveStats.damageDealt,
            waveStats.damageTaken,
            waveStats.kills,
            waveStats.deaths,
            waveStats.criticalHits,
            waveStats.hitsLanded,
            comboSession != null ? comboSession.getMaxCombo() : 0,
            comboSession != null ? comboSession.getHighestRank().name() : "D",
            "unknown", // primaryWeapon - could be tracked in future
            Map.of() // bodyPartHits - retrieved from CombatTracker if needed
        );
    }

    /**
     * Builds a QuestResult from all tracking data.
     */
    private static QuestResult buildQuestResult(UUID questId, UUID playerId,
            EnduranceQuest quest,
            CombatTracker.QuestCombatSession combatSession,
            ComboSystem.ComboSession comboSession,
            boolean completed) {

        // Extract data with null safety
        float totalDamageDealt = combatSession != null ? combatSession.getTotalDamageDealt() : 0;
        float totalDamageTaken = combatSession != null ? combatSession.getTotalDamageTaken() : 0;
        int totalKills = combatSession != null ? combatSession.getKills() : 0;
        int deaths = combatSession != null ? combatSession.getDeaths() : 0;
        int totalHits = combatSession != null ? combatSession.getTotalHitsLanded() : 0;
        int criticalHits = combatSession != null ? combatSession.getCriticalHits() : 0;
        long duration = combatSession != null ? combatSession.getSessionDuration() : 0;
        float dps = combatSession != null ? combatSession.getDPS() : 0;
        float critRate = combatSession != null ? combatSession.getCriticalHitRate() : 0;

        int maxCombo = comboSession != null ? comboSession.getMaxCombo() : 0;
        String maxRank = comboSession != null ? comboSession.getHighestRank().name() : "D";

        // Body part stats
        Map<String, Integer> bodyPartHits = combatSession != null ?
            combatSession.getBodyPartHits() : Map.of();
        int headshots = bodyPartHits.getOrDefault("HEAD", 0);
        float headshotRate = totalHits > 0 ? (float) headshots / totalHits : 0;

        // Weapon stats
        Map<String, Float> weaponDamageMap = new java.util.HashMap<>();
        String bestWeapon = "unknown";
        float bestDamage = 0;
        if (combatSession != null) {
            for (var entry : combatSession.getWeaponStats().entrySet()) {
                weaponDamageMap.put(entry.getKey(), entry.getValue().totalDamage);
                if (entry.getValue().totalDamage > bestDamage) {
                    bestDamage = entry.getValue().totalDamage;
                    bestWeapon = entry.getKey();
                }
            }
        }

        // Wave times
        List<Long> waveTimes = new ArrayList<>();
        List<WaveSummary> waveSummaries = new ArrayList<>();
        if (combatSession != null) {
            for (CombatTracker.WaveCombatStats ws : combatSession.getWaveStats()) {
                waveTimes.add(ws.duration);
                waveSummaries.add(buildWaveSummary(ws.waveNumber, ws, comboSession));
            }
        }

        return new QuestResult(
            questId,
            playerId,
            quest.getQuestId().toString(),
            quest.getMobConfig().mobId.toString(),
            completed,
            quest.getCurrentWave(),
            quest.getTotalWaves(),
            completed ? null : "Quest not completed",
            duration,
            waveTimes,
            totalDamageDealt,
            totalDamageTaken,
            totalKills,
            deaths,
            totalHits,
            criticalHits,
            dps,
            critRate,
            headshotRate,
            maxCombo,
            maxRank,
            bestWeapon,
            bestWeapon,
            weaponDamageMap,
            waveSummaries,
            0, // xpEarned - set by reward system
            0, // coinsEarned - set by reward system
            List.of() // achievementsUnlocked - set by gamification
        );
    }
}
