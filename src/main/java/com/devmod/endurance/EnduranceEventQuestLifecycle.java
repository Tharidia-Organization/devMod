package com.devmod.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.arena.policy.ArenaPolicy;
import com.devmod.config.gamedesign.GameDesignConfigManager;
import com.devmod.endurance.analytics.LiveAnalyticsHookManager;
import com.devmod.endurance.analytics.QuestResult;
import com.devmod.endurance.analytics.WaveSummary;
import com.devmod.endurance.combat.ComboSystemFacade;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.lifecycle.QuestContext;
import com.devmod.endurance.lifecycle.QuestEventBus;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent;
import com.devmod.mailbox.template.MessageTemplateRegistry;
import com.devmod.notification.NotificationService;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;
import com.devmod.telemetry.player.PlayerAttributeTelemetryService;

/**
 * Quest lifecycle event handling: quest start, quest end, reward mailbox, and analytics helpers.
 * Extracted from EnduranceEventHandler to keep each class focused.
 */
final class EnduranceEventQuestLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceEventQuestLifecycle.class);

    private EnduranceEventQuestLifecycle() {}

    // ==============================================================
    // QUEST START
    // ==============================================================

    static void onQuestStart(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session) {
        UUID playerId = player.getUUID();
        UUID questId = session.getQuest().getQuestId();
        boolean practice = session.isPracticeMode();
        ArenaPolicy policy = EnduranceQuestManager.INSTANCE.getPolicyForSession(session);

        // === PUBLISH QUEST START EVENT ===
        QuestContext questContext = QuestContext.from(player, session, policy);
        QuestEventBus.INSTANCE.publish(QuestLifecycleEvent.QuestStarted.of(questContext));

        // Get mutator session (created by MutatorSystem listener via event bus)
        MutatorSystem.MutatorSession mutatorSession = MutatorSystem.INSTANCE.getSession(questId).orElse(null);

        // Start live analytics session for real-time feedback hooks
        if (!practice) {
            LiveAnalyticsHookManager.INSTANCE.onQuestStart(questId, playerId);
        }

        if (!practice) {
            // Record telemetry for quest start
            int playerCount = session.getPlayerCount();
            QuestType questType = session.getQuestType();
            String templateId = session.getTemplateId();
            Integer templateVersion = session.getTemplateVersion();
            String policyId = session.getPolicyId();
            Integer policyVersion = session.getPolicyVersion();
            UUID instanceId = session.getInstanceId();
            UUID arenaId = session.getArena() != null ? session.getArena().getId() : null;

            EnduranceTelemetryService.INSTANCE.recordQuestStart(
                questId,
                playerId,
                session.getQuest().getDisplayName(),
                session.getQuest().getTotalWaves(),
                session.getQuest().isEndlessMode(),
                playerCount,
                questType,
                templateId,
                templateVersion,
                policyId,
                policyVersion,
                instanceId,
                arenaId
            );

            // Trigger player attribute snapshot on quest start
            PlayerAttributeTelemetryService.INSTANCE.recordSnapshot(player, "quest_start");
        }

        LOGGER.info("[EnduranceQuest] Quest started for {} with {} mutators",
            player.getName().getString(), mutatorSession.getActiveMutatorCount());
    }

    // ==============================================================
    // QUEST END
    // ==============================================================

    static void onQuestEnd(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session,
                           boolean completed, boolean cleanupShared) {
        UUID playerId = player.getUUID();
        UUID questId = session.getQuest().getQuestId();
        boolean practice = session.isPracticeMode();
        boolean tideEnabled = isTideEnabled(session);
        UUID tideScopeId = resolveDesignScopeId(session);

        // Get combo session from Facade (single source of truth)
        IComboSession comboSession = ComboSystemFacade.get().getSession(playerId).orElse(null);
        MutatorSystem.MutatorSession mutatorSession = cleanupShared
            ? EnduranceEventCombat.removeMutatorSession(questId)
            : EnduranceEventCombat.getMutatorSession(questId);

        // Get max combo before cleanup
        int maxCombo = comboSession != null ? comboSession.getMaxCombo() : 0;

        RewardSystem.QuestRewards rewards;
        if (practice) {
            rewards = new RewardSystem.QuestRewards();
            rewards.styleRank = comboSession != null ? comboSession.getHighestRank() : null;
            rewards.activeMutators = mutatorSession != null ? mutatorSession.getActiveMutatorCount() : 0;
        } else {
            // Award rewards based on performance
            ArenaPolicy policy = EnduranceQuestManager.INSTANCE.getPolicyForSession(session);
            rewards = RewardSystem.INSTANCE.calculateQuestRewards(
                player, session.getQuest(), comboSession, mutatorSession, policy, session);
        }

        // Send completion screen to client AFTER a short delay to allow teleport to complete
        if (rewards != null) {
            final RewardSystem.QuestRewards finalRewards = rewards;
            final IComboSession finalComboSession = comboSession;
            final int finalMaxCombo = maxCombo;
            final UUID finalPlayerId = playerId;
            final EnduranceQuestManager.ActiveQuestSession finalSession = session;
            final boolean finalPractice = practice;

            // Schedule completion screen to be sent after 10 ticks (500ms) to allow teleport
            var server = player.getServer();
            if (server != null) {
                server.execute(() -> {
                    server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 10, () -> {
                        ServerPlayer currentPlayer = server.getPlayerList().getPlayer(finalPlayerId);
                        if (currentPlayer != null) {
                            com.devmod.network.NetworkHandler.sendQuestCompletionScreen(
                                currentPlayer, finalSession, finalRewards, finalComboSession, finalMaxCombo);

                            if (!finalPractice) {
                                sendQuestRewardMailbox(currentPlayer, finalSession, finalRewards, completed);
                            }
                        }
                    }));
                });
            }
        }

        // Get combat session data before cleanup (needed for analytics)
        CombatTracker.QuestCombatSession combatSessionData = CombatTracker.INSTANCE.getSession(questId).orElse(null);

        // === PUBLISH QUEST END EVENT ===
        ArenaPolicy policy = EnduranceQuestManager.INSTANCE.getPolicyForSession(session);
        QuestContext questContext = QuestContext.from(player, session, policy);
        QuestLifecycleEvent.QuestEnded.EndReason endReason = completed
            ? QuestLifecycleEvent.QuestEnded.EndReason.COMPLETED
            : QuestLifecycleEvent.QuestEnded.EndReason.FAILED;
        QuestEventBus.INSTANCE.publish(
            new QuestLifecycleEvent.QuestEnded(questId, System.currentTimeMillis(), questContext, completed, endReason, cleanupShared)
        );

        // Process chain rewards if chain was completed
        if (!practice) {
            DirectiveChainManager.INSTANCE.getActiveChain(questId).ifPresent(chainProgress -> {
                if (chainProgress.isCompleted()) {
                    DirectiveChainManager.ChainRewards chainRewards = DirectiveChainManager.INSTANCE.calculateChainRewards(chainProgress);
                    RewardSystem.INSTANCE.getWallet(playerId).addCurrency(RewardSystem.Currency.TOKENS, chainRewards.bonusTokens());
                    RewardSystem.INSTANCE.getWallet(playerId).addCurrency(RewardSystem.Currency.PRESTIGE, chainRewards.bonusPrestige());
                    LOGGER.info("[EnduranceQuest] Chain '{}' rewards awarded to {}: {} tokens, {} prestige",
                        chainProgress.getChain().name(), player.getName().getString(),
                        chainRewards.bonusTokens(), chainRewards.bonusPrestige());
                }
            });
        }

        // Cleanup directive chain tracking (shared per questId)
        if (cleanupShared) {
            DirectiveChainManager.INSTANCE.endChain(questId);
        }

        // Record gamification stats (leaderboards, badges, challenges)
        if (!practice && completed && combatSessionData != null && EnduranceQuestManager.INSTANCE.isGamificationEnabled()) {
            GamificationManager.QuestCompletionResult gamificationResult =
                GamificationManager.INSTANCE.recordQuestCompletion(
                    playerId,
                    player.getName().getString(),
                    session.getQuest(),
                    combatSessionData,
                    session
                );

            // Send badge unlock notifications for newly earned badges
            for (GamificationManager.Badge badge : gamificationResult.getNewBadges()) {
                String rewardDescription = "+" + badge.getBonusPoints() + " bonus points (" + badge.getRarity().getDisplayName() + ")";
                NotificationService.INSTANCE.notifyBadgeUnlock(
                    playerId, badge.getName(), badge.getRarity().getDisplayName(), badge.getDescription(), rewardDescription);
            }

            // Send record banner for new personal records
            if (gamificationResult.isNewWaveRecord()) {
                String waveValue = "Wave " + session.getQuest().getCurrentWave();
                NotificationService.INSTANCE.notifyRecord(playerId, "BEST WAVE", waveValue);
            }
            if (gamificationResult.isNewHighScore()) {
                String scoreValue = String.format("%,d pts", session.getQuest().getPointsEarnedThisSession());
                NotificationService.INSTANCE.notifyRecord(playerId, "HIGH SCORE", scoreValue);
            }
        }

        if (!practice) {
            // Submit to global leaderboard system
            String arenaId = session.getTemplateId();
            LeaderboardSystem.INSTANCE.submitQuestResult(player, session.getQuest(), comboSession, arenaId);

            // Update weekly challenge progress for quest completion
            int bossesKilledThisRun = combatSessionData != null ?
                (int) combatSessionData.getWaveStats().stream()
                    .filter(w -> BossWaveSystem.INSTANCE.isBossWave(w.getWaveNumber(), questId))
                    .count() : 0;
            com.devmod.endurance.challenges.WeeklyChallengeManager.INSTANCE.onQuestComplete(
                playerId, session.getQuest(), bossesKilledThisRun);

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

            if (combatSessionData != null) {
                EnduranceTelemetryService.INSTANCE.recordQuestPerformance(
                    questId,
                    playerId,
                    session.getQuestType(),
                    combatSessionData,
                    session.getQuest().getCurrentWave()
                );
            }

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
        }

        if (tideEnabled) {
            int waveReached = session.getQuest().getCurrentWave();
            boolean perfect = session.getQuest().getDeathsThisSession() == 0 &&
                              session.getQuest().getDamageTakenThisSession() == 0;
            UUID scopeId = tideScopeId != null ? tideScopeId : questId;
            if (completed) {
                com.devmod.endurance.tide.TideManager.INSTANCE.onQuestCompleted(scopeId, perfect);
            } else if (waveReached < 5) {
                com.devmod.endurance.tide.TideManager.INSTANCE.onQuestFailedEarly(scopeId);
            }
        }

        // Trigger player attribute snapshot on quest end
        if (!practice) {
            PlayerAttributeTelemetryService.INSTANCE.recordSnapshot(player, "quest_end");
        }

        // === PARTY STATE TRANSITION ===
        UUID partyId = session.getPartyId();
        if (partyId != null) {
            if (EnduranceQuestManager.INSTANCE.getPartySession(partyId).isEmpty()) {
                var party = com.devmod.party.PartyManager.INSTANCE.getParty(partyId);
                if (party != null && party.getState() == com.devmod.party.PartyData.PartyState.IN_QUEST) {
                    com.devmod.party.PartyManager.INSTANCE.finishQuest(partyId);
                    LOGGER.info("[EnduranceQuest] Party {} transitioned from IN_QUEST to FORMING", partyId);

                    var server = player.getServer();
                    if (server != null) {
                        com.devmod.network.handlers.PartyNetworkHandler.syncPartyToAllMembers(server, partyId);
                    }
                }
            }
        }

        LOGGER.info("[EnduranceQuest] Quest ended for {} - Completed: {}, Style Rank: {}",
            player.getName().getString(), completed,
            comboSession != null ? comboSession.getHighestRank().getDisplayName() : "N/A");
    }

    // ==============================================================
    // MAILBOX INTEGRATION
    // ==============================================================

    private static void sendQuestRewardMailbox(
            ServerPlayer player,
            EnduranceQuestManager.ActiveQuestSession session,
            RewardSystem.QuestRewards rewards,
            boolean completed) {

        try {
            String playerName = player.getName().getString();
            String questName = session.getQuest().getDisplayName();
            int waveReached = session.getQuest().getCurrentWave();
            int totalWaves = session.getQuest().getTotalWaves();

            // Build reward description
            StringBuilder rewardDesc = new StringBuilder();
            if (rewards.tokensEarned > 0) {
                rewardDesc.append(rewards.tokensEarned).append(" tokens");
            }
            if (rewards.prestigeEarned > 0) {
                if (rewardDesc.length() > 0) rewardDesc.append(", ");
                rewardDesc.append(rewards.prestigeEarned).append(" prestige");
            }
            if (rewardDesc.length() == 0) {
                rewardDesc.append("No rewards");
            }

            String position = completed
                ? "Completed!"
                : "Wave " + waveReached + "/" + totalWaves;

            MessageTemplateRegistry.INSTANCE.sendFromTemplate(
                "reward.event_participation",
                player.getUUID(),
                Map.of(
                    "player_name", playerName,
                    "event_name", questName,
                    "position", position,
                    "points", String.valueOf(waveReached),
                    "reward_description", rewardDesc.toString()
                ),
                null
            ).exceptionally(ex -> {
                LOGGER.error("[EnduranceQuest] Failed to deliver mailbox reward notification", ex);
                return Optional.empty();
            });

            LOGGER.debug("[EnduranceQuest] Sent mailbox reward notification to {}", playerName);
        } catch (Exception e) {
            LOGGER.error("[EnduranceQuest] Failed to send mailbox reward notification", e);
        }
    }

    // ==============================================================
    // ANALYTICS HELPER METHODS
    // ==============================================================

    static WaveSummary buildWaveSummary(int waveNumber,
            CombatTracker.WaveCombatStats waveStats,
            IComboSession comboSession) {

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
            "unknown",
            Map.of()
        );
    }

    static QuestResult buildQuestResult(UUID questId, UUID playerId,
            EnduranceQuest quest,
            CombatTracker.QuestCombatSession combatSession,
            IComboSession comboSession,
            boolean completed) {

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

        Map<String, Integer> bodyPartHits = combatSession != null ?
            combatSession.getBodyPartHits() : Map.of();
        int headshots = bodyPartHits.getOrDefault("HEAD", 0);
        float headshotRate = totalHits > 0 ? (float) headshots / totalHits : 0;

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

        List<Long> waveTimes = new ArrayList<>();
        List<WaveSummary> waveSummaries = new ArrayList<>();
        if (combatSession != null) {
            for (CombatTracker.WaveCombatStats ws : combatSession.getWaveStats()) {
                waveTimes.add(ws.duration);
                waveSummaries.add(buildWaveSummary(ws.getWaveNumber(), ws, comboSession));
            }
        }

        return new QuestResult(
            questId,
            playerId,
            quest.getQuestId().toString(),
            quest.getMobConfig().getMobId().toString(),
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
            0,
            0,
            List.of()
        );
    }

    // ==============================================================
    // CONFIG HELPERS
    // ==============================================================

    static @Nullable UUID resolveDesignScopeId(
            @Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null) {
            return null;
        }
        UUID instanceId = session.getInstanceId();
        return instanceId != null ? instanceId : session.getQuest().getQuestId();
    }

    static boolean isSignatureWeaponsEnabled(
            @Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null) {
            return false;
        }
        return GameDesignConfigManager.INSTANCE.isSignatureWeaponsEnabled(
            resolveDesignScopeId(session), session.isPracticeMode());
    }

    static boolean isTideEnabled(
            @Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null) {
            return false;
        }
        return GameDesignConfigManager.INSTANCE.isTideEnabled(
            resolveDesignScopeId(session), session.isPracticeMode());
    }
}
