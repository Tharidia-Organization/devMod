package com.devmod.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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
import com.devmod.endurance.lifecycle.WaveContext;
import com.devmod.endurance.nutrition.NutritionBridgeSystem;
import com.devmod.mailbox.template.MessageTemplateRegistry;
import com.devmod.notification.NotificationService;
import com.devmod.party.QuestStartSequence;
import com.devmod.telemetry.duckdb.aggregation.AggregationConfig;
import com.devmod.telemetry.duckdb.aggregation.TelemetryAggregatorRegistry;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;
import com.devmod.telemetry.player.PlayerAttributeTelemetryService;
@EventBusSubscriber(modid = "devmod")
public class EnduranceEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceEventHandler.class);

    // ==============================================================
    // QUEST LIFECYCLE
    // ==============================================================

    /*
     * Initialize all systems when a quest starts.
     * Called by EnduranceQuestManager.startQuest()
     */
    public static void onQuestStart(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session) {
        UUID playerId = player.getUUID();
        UUID questId = session.getQuest().getQuestId();
        boolean practice = session.isPracticeMode();
        ArenaPolicy policy = EnduranceQuestManager.INSTANCE.getPolicyForSession(session);

        // === PUBLISH QUEST START EVENT ===
        // This notifies all registered QuestLifecycleListeners (ComboSystem, MomentumTracker,
        // CombatTracker, TensionSystem, etc.) to initialize their sessions.
        QuestContext questContext = QuestContext.from(player, session, policy);
        QuestEventBus.INSTANCE.publish(QuestLifecycleEvent.QuestStarted.of(questContext));

        // Get mutator session (created by MutatorSystem listener via event bus)
        MutatorSystem.MutatorSession mutatorSession = MutatorSystem.INSTANCE.getSession(questId).orElse(null);

        // NOTE: PerkSystem session created by listener via event bus
        // NOTE: ComebackSystem cooldown reset by listener via event bus

        // Start live analytics session for real-time feedback hooks
        if (!practice) {
            LiveAnalyticsHookManager.INSTANCE.onQuestStart(questId, playerId);
        }

        // NOTE: DevilsBargainManager session created by listener via event bus
        // NOTE: ArenaHazardSystem session created by listener via event bus
        // NOTE: PerkSynergyWeb discoveries loaded by listener via event bus
        // NOTE: NutritionBridgeSystem session created by listener via event bus

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

    /*
     * Cleanup all systems when a quest ends.
     * Called when quest completes, fails, or is abandoned.
     */
    public static void onQuestEnd(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session,
                                   boolean completed) {
        onQuestEnd(player, session, completed, true);
    }

    public static void onQuestEnd(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session,
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

        // Send completion screen to client (only if quest completed or exited at checkpoint)
        if (rewards != null) {
            com.devmod.network.NetworkHandler.sendQuestCompletionScreen(
                player, session, rewards, comboSession, maxCombo);

            // Send mailbox notification with reward summary
            if (!practice) {
                sendQuestRewardMailbox(player, session, rewards, completed);
            }
        }

        // Get combat session data before cleanup (needed for analytics)
        CombatTracker.QuestCombatSession combatSessionData = CombatTracker.INSTANCE.getSession(questId).orElse(null);

        // === PUBLISH QUEST END EVENT ===
        // This notifies all registered QuestLifecycleListeners to cleanup their sessions.
        ArenaPolicy policy = EnduranceQuestManager.INSTANCE.getPolicyForSession(session);
        QuestContext questContext = QuestContext.from(player, session, policy);
        QuestLifecycleEvent.QuestEnded.EndReason endReason = completed
            ? QuestLifecycleEvent.QuestEnded.EndReason.COMPLETED
            : QuestLifecycleEvent.QuestEnded.EndReason.FAILED;
        QuestEventBus.INSTANCE.publish(
            new QuestLifecycleEvent.QuestEnded(questId, System.currentTimeMillis(), questContext, completed, endReason, cleanupShared)
        );

        // NOTE: MutatorSystem cleanup handled by listener via event bus
        // NOTE: PerkSystem cleanup handled by listener via event bus
        // NOTE: DevilsBargainManager cleanup handled by listener via event bus
        // NOTE: ArenaHazardSystem cleanup handled by listener via event bus
        // NOTE: PerkSynergyWeb discoveries saved by listener via event bus

        // NOTE: ExecutionSystem cleanup handled by listener via event bus
        // NOTE: NutritionBridgeSystem cleanup handled by listener via event bus
        // NOTE: ComebackSystem cleanup handled by listener via event bus

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
            // === THE TIDE - Record quest outcome for global threat level ===
            int waveReached = session.getQuest().getCurrentWave();
            boolean perfect = session.getQuest().getDeathsThisSession() == 0 &&
                              session.getQuest().getDamageTakenThisSession() == 0;
            UUID scopeId = tideScopeId != null ? tideScopeId : questId;
            if (completed) {
                com.devmod.endurance.tide.TideManager.INSTANCE.onQuestCompleted(scopeId, perfect);
            } else if (waveReached < 5) {
                // Early failure (before wave 5) increases tide more
                com.devmod.endurance.tide.TideManager.INSTANCE.onQuestFailedEarly(scopeId);
            }
        }

        // Trigger player attribute snapshot on quest end
        if (!practice) {
            PlayerAttributeTelemetryService.INSTANCE.recordSnapshot(player, "quest_end");
        }

        // === PARTY STATE TRANSITION ===
        // Finish party quest if this was a party quest and party is still in IN_QUEST state
        UUID partyId = session.getPartyId();
        if (partyId != null) {
            if (EnduranceQuestManager.INSTANCE.getPartySession(partyId).isEmpty()) {
                var party = com.devmod.party.PartyManager.INSTANCE.getParty(partyId);
                if (party != null && party.getState() == com.devmod.party.PartyData.PartyState.IN_QUEST) {
                    com.devmod.party.PartyManager.INSTANCE.finishQuest(partyId);
                    LOGGER.info("[EnduranceQuest] Party {} transitioned from IN_QUEST to FORMING", partyId);

                    // Sync party state to all members so clients see the updated state
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
    // WAVE EVENTS
    // ==============================================================

    /*
     * Called when a new wave starts.
     */
    public static void onWaveStart(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session, int waveNumber) {
        onWaveStart(player, session, waveNumber, true);
    }

    public static void onWaveStart(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session,
                                   int waveNumber, boolean applyShared) {
        UUID playerId = player.getUUID();
        EnduranceQuest quest = session.getQuest();

        // Reset combo for new wave (using Facade)
        IComboSession comboSession = ComboSystemFacade.get().getSession(playerId).orElse(null);
        if (comboSession != null) {
            comboSession.startNewWave();
        }

        // === BLOOD CONTRACTS - Signal wave start for violation tracking ===
        UUID questId = quest.getQuestId();
        if (applyShared) {
            com.devmod.endurance.contracts.ActiveContractManager.INSTANCE.onWaveStart(questId);
        }

        // Sync contracts to client for HUD
        com.devmod.endurance.contracts.ActiveContractManager.INSTANCE.getSession(questId, playerId)
            .ifPresent(contractSession -> {
                var payload = Objects.requireNonNull(
                    com.devmod.endurance.contracts.ContractSyncPayload.forSession(contractSession), "payload");
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
            });

        // Check if this is a boss wave (using tension system)
        boolean isBossWave = !session.isPracticeMode() && BossWaveSystem.INSTANCE.isBossWave(waveNumber, questId);

        // Gather wave info for notification
        int mobCount = quest.getCurrentWaveMobCount();
        String enemyType = quest.getMobConfig().getDisplayName();
        int totalWaves = quest.isEndlessMode() ? 0 : quest.getTotalWaves();
        String objective = null;
        String directive = null;

        ArenaContext arena = session.getArena();
        if (arena != null) {
            mobCount = WaveManager.INSTANCE.getWaveState(arena.getId())
                .map(WaveManager.WaveState::getTotalToSpawn)
                .orElse(mobCount);

            // Get objective and directive info
            var waveStateOpt = WaveManager.INSTANCE.getWaveState(arena.getId());
            if (waveStateOpt.isPresent()) {
                WaveManager.WaveState waveState = waveStateOpt.get();
                WaveObjectiveState objState = waveState.getObjective();
                objective = objState.getTitle();
                if (objState.getDescription() != null && !objState.getDescription().isBlank()) {
                    objective = objective + " (" + objState.getDescription() + ")";
                }

                if (waveState.getDirectiveId() != null) {
                    WaveDirective dir = WaveDirector.INSTANCE.findDirective(waveState.getDirectiveId());
                    if (dir != null) {
                        directive = dir.name() + " (x" + String.format("%.1f", waveState.getRewardMultiplier()) + ")";
                    }
                }
            }
        }

        // Boss wave: override enemy type with boss name
        if (isBossWave) {
            var bossFight = BossWaveSystem.INSTANCE.getBossFight(session.getArena().getId());
            enemyType = bossFight.map(bf -> bf.getArchetype().getDisplayName()).orElse("Champion");
        }

        // Send unified notification (replaces 5-10 chat lines with a single toast overlay)
        NotificationService.INSTANCE.notifyWaveStart(
            playerId, waveNumber, totalWaves, mobCount, enemyType,
            isBossWave, objective, directive
        );

        // === PUBLISH WAVE START EVENT ===
        // This notifies all registered QuestLifecycleListeners of the wave start.
        ArenaPolicy policy = EnduranceQuestManager.INSTANCE.getPolicyForSession(session);
        QuestContext questContext = QuestContext.from(player, session, policy);
        WaveContext waveContext = WaveContext.forWaveStart(
            questContext, waveNumber, isBossWave, mobCount, enemyType, comboSession);
        QuestEventBus.INSTANCE.publish(QuestLifecycleEvent.WaveStarted.of(waveContext, applyShared));

        LOGGER.debug("[EnduranceQuest] Wave {} started for {} (boss: {})",
            waveNumber, player.getName().getString(), isBossWave);
    }

    /*
     * Called when a wave is completed.
     */
    public static void onWaveComplete(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session, int waveNumber) {
        onWaveComplete(player, session, waveNumber, true);
    }

    public static void onWaveComplete(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session,
                                      int waveNumber, boolean applyShared) {
        // Heal player to full and clear negative effects at wave completion
        // This ensures player starts intermission in a safe state
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects(); // Clear poison, wither, etc.
        player.clearFire();
        LOGGER.debug("[EnduranceQuest] Healed player {} at wave {} completion", player.getName().getString(), waveNumber);

        UUID playerId = player.getUUID();
        UUID questId = session.getQuest().getQuestId();
        EnduranceQuest quest = session.getQuest();
        boolean practice = session.isPracticeMode();
        boolean signatureEnabled = isSignatureWeaponsEnabled(session);
        boolean tideEnabled = isTideEnabled(session);
        UUID tideScopeId = resolveDesignScopeId(session);

        // Get tracking sessions (using Facade for combo)
        IComboSession comboSession = ComboSystemFacade.get().getSession(playerId).orElse(null);
        MutatorSystem.MutatorSession mutatorSession = EnduranceEventCombat.getMutatorSession(questId);

        // Get combat stats for this wave
        CombatTracker.QuestCombatSession combatSession = CombatTracker.INSTANCE.getSession(questId).orElse(null);
        CombatTracker.WaveCombatStats waveStats = combatSession != null ? combatSession.getCurrentWaveStats() : null;

        // Calculate wave statistics
        String styleRank = comboSession != null ? comboSession.getCurrentRank().getDisplayName() : "D";
        int maxCombo = comboSession != null ? comboSession.getMaxCombo() : 0;
        float waveDamage = waveStats != null ? waveStats.damageDealt : 0;
        int waveKills = waveStats != null ? waveStats.kills : 0;
        float waveDamageTaken = waveStats != null ? waveStats.damageTaken : 0;

        var perkSynergyWeb = com.devmod.endurance.perk.PerkSynergyWeb.INSTANCE;

        // === SIGNATURE WEAPONS - Track exceptional wave performance ===
        if (signatureEnabled && "SSS".equals(styleRank)) {
            com.devmod.combat.signature.SoulImprintManager.INSTANCE.recordSSSWave(player);
            LOGGER.debug("[EnduranceQuest] Recorded SSS wave for signature weapon tracking");
        }
        if (signatureEnabled && waveDamageTaken == 0 && waveKills > 0) {
            com.devmod.combat.signature.SoulImprintManager.INSTANCE.recordNoHitWave(player);
            LOGGER.debug("[EnduranceQuest] Recorded no-hit wave for signature weapon tracking");
        }

        if (!practice) {
            if ("SSS".equals(styleRank)) {
                perkSynergyWeb.recordSSSRank(player);
            }

            // === PERK SYNERGY WEB - Record wave stats for discovery tracking ===
            perkSynergyWeb.recordWaveComplete(player, waveNumber);
            perkSynergyWeb.recordKills(player, waveKills);
        }

        // === THE TIDE - Global threat reduction for exceptional play ===
        if (tideEnabled) {
            UUID scopeId = tideScopeId != null ? tideScopeId : questId;
            if ("SSS".equals(styleRank)) {
                com.devmod.endurance.tide.TideManager.INSTANCE.onSSSWave(playerId, scopeId);
            }
            if (waveDamageTaken == 0 && waveKills > 0) {
                com.devmod.endurance.tide.TideManager.INSTANCE.onNoHitWave(playerId, scopeId);
            }
        }

        // NOTE: ArenaHazardSystem.checkWaveHazards handled by listener via event bus

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
        if (combatSession != null && applyShared) {
            // Finalize current wave stats and prepare for next wave
            combatSession.startNewWave(waveNumber + 1);
            LOGGER.debug("[EnduranceQuest] Combat tracker advanced to wave {}", waveNumber + 1);
        }

        // === TENSION SYSTEM - Dynamic boss spawning ===
        boolean nextWaveIsBoss = applyShared
            ? TensionSystem.INSTANCE.onWaveComplete(questId, waveNumber)
            : TensionSystem.INSTANCE.getTensionInfo(questId).bossImminent();
        if (practice) {
            nextWaveIsBoss = false;
        }
        TensionSystem.TensionInfo tensionInfo = TensionSystem.INSTANCE.getTensionInfo(questId);
        LOGGER.info("[EnduranceQuest]   Tension: {}% (level {}), Boss pending: {}",
            (int)(tensionInfo.percent() * 100), tensionInfo.level(), nextWaveIsBoss);

        // Send tension info to client for HUD display
        com.devmod.network.NetworkHandler.sendTensionUpdate(player, tensionInfo.percent(), tensionInfo.level(), nextWaveIsBoss);

        // Update weekly challenge progress for wave completion
        if (!practice) {
            com.devmod.endurance.challenges.WeeklyChallengeManager.INSTANCE.onWaveComplete(
                playerId, waveNumber, quest.isEndlessMode());
        }

        // If boss wave is coming, send alert
        if (!practice && nextWaveIsBoss && (quest.getCurrentWave() < quest.getTotalWaves() || quest.isEndlessMode())) {
            BossWaveSystem.INSTANCE.triggerBossAlert(player, "Champion");
        }

        // === LIVE ANALYTICS - Notify hooks of wave transition ===
        if (!practice && applyShared) {
            WaveSummary waveSummary = buildWaveSummary(waveNumber, waveStats, comboSession);
            LiveAnalyticsHookManager.INSTANCE.onWaveComplete(waveNumber, waveSummary, waveNumber + 1);
        }

        // === MUTATOR SYSTEM - Roll new mutators between waves ===
        if (applyShared && mutatorSession != null && waveNumber % 3 == 0) {
            // Every 3 waves, potentially add a new mutator
            int prevMutatorCount = mutatorSession.getActiveMutatorCount();
            MutatorSystem.INSTANCE.rollNewMutator(mutatorSession, waveNumber);
            LOGGER.info("[EnduranceQuest] New mutator rolled at wave {}", waveNumber);

            // Trigger player attribute snapshot if mutator was added
            if (mutatorSession.getActiveMutatorCount() > prevMutatorCount) {
                PlayerAttributeTelemetryService.INSTANCE.recordSnapshot(player, "mutator_added_wave_" + waveNumber);
            }
        }

        // === DEVIL'S BARGAIN - Spawn altar every 3 waves for curse selection ===
        if (applyShared && com.devmod.endurance.bargain.DevilsBargainManager.INSTANCE.shouldSpawnAltar(waveNumber)) {
            com.devmod.endurance.bargain.DevilsBargainManager.INSTANCE.spawnAltar(player, questId, waveNumber);
            LOGGER.info("[EnduranceQuest] Devil's Bargain altar spawned at wave {}", waveNumber);
        }

        // === PERK SYSTEM - Generate perk choices for player ===
        List<PerkSystem.Perk> perkChoices = PerkSystem.INSTANCE.generatePerkChoices(player, waveNumber);
        if (!perkChoices.isEmpty()) {
            LOGGER.info("[EnduranceQuest] Generated {} perk choices for player {}: {}",
                perkChoices.size(), player.getName().getString(),
                perkChoices.stream().map(p -> p.getName()).toList());
            // Send perk choices to client for UI display
            com.devmod.network.NetworkHandler.sendPerkChoices(player, waveNumber, perkChoices);
        }

        // === PERK SYNERGY WEB - Check for new hidden perk discoveries ===
        if (!practice) {
            PerkSystem.PerkSession perkSession = PerkSystem.INSTANCE.getSession(playerId).orElse(null);
            if (perkSession != null) {
                var discoveries = perkSynergyWeb.getDiscoveries(player);
                var discoveryContext = new com.devmod.endurance.perk.PerkSynergyWeb.DiscoveryContext(
                    playerId,
                    perkSession.getAcquiredPerkIds(),
                    discoveries.getDiscoveredPerks(),
                    waveNumber,
                    discoveries.getTotalKills(),
                    discoveries.getTotalWavesCompleted(),
                    styleRank,
                    Map.of()
                );
                List<String> newDiscoveries = perkSynergyWeb.checkDiscoveries(player, discoveryContext);
                if (!newDiscoveries.isEmpty()) {
                    LOGGER.info("[EnduranceQuest] Player {} discovered {} hidden perks: {}",
                        player.getName().getString(), newDiscoveries.size(), newDiscoveries);
                }
            }
        }

        // === DIRECTIVE CHAINS - Multi-wave narrative arcs ===
        // Check if we should advance an active chain or offer new chains
        boolean chainActive = DirectiveChainManager.INSTANCE.hasActiveChain(questId);
        if (applyShared && chainActive) {
            // Advance the active chain
            int waveDeaths = waveStats != null ? waveStats.deaths : 0;
            float damageTakenThisWave = waveStats != null ? waveStats.damageTaken : 0;
            boolean tookDamage = damageTakenThisWave > 0;
            int styleOrdinal = comboSession != null ? comboSession.getCurrentRank().ordinal() : 0;

            DirectiveChainManager.ChainAdvanceResult chainResult = DirectiveChainManager.INSTANCE.advanceChain(
                questId, waveKills, maxCombo, waveDeaths > 0, tookDamage, styleOrdinal);

            if (chainResult == DirectiveChainManager.ChainAdvanceResult.CHAIN_COMPLETED) {
                // Chain completed! Notify with reward summary
                DirectiveChainManager.INSTANCE.getActiveChain(questId).ifPresent(progress -> {
                    DirectiveChainManager.ChainRewards rewards = DirectiveChainManager.INSTANCE.calculateChainRewards(progress);
                    NotificationService.INSTANCE.notifyChainComplete(
                        playerId,
                        progress.getChain().name(),
                        rewards.bonusTokens(),
                        rewards.bonusPrestige()
                    );
                });
            } else if (chainResult == DirectiveChainManager.ChainAdvanceResult.CONDITION_FAILED) {
                // Chain failed condition
                DirectiveChainManager.INSTANCE.getActiveChain(questId).ifPresent(progress ->
                    NotificationService.INSTANCE.notifyChainFailed(playerId, progress.getChain().name()));
            } else if (chainResult == DirectiveChainManager.ChainAdvanceResult.STEP_COMPLETED) {
                // Show chain progress
                DirectiveChainManager.INSTANCE.getActiveChain(questId).ifPresent(progress -> {
                    NotificationService.INSTANCE.notifyChainProgress(
                        playerId,
                        progress.getChain().name(),
                        progress.getCurrentStep() + 1,
                        progress.getTotalSteps()
                    );
                });
            }
        }

        // === WAVE DIRECTIVES - Risk/Reward choices for next wave ===
        if (applyShared && (quest.getCurrentWave() < quest.getTotalWaves() || quest.isEndlessMode())) {
            int nextWave = waveNumber + 1;

            // If chain is active, use chain directive; otherwise offer choices
            if (DirectiveChainManager.INSTANCE.hasActiveChain(questId)) {
                DirectiveChainManager.INSTANCE.getCurrentChainDirective(questId).ifPresent(chainDirective -> {
                    session.setPendingDirectives(List.of(chainDirective), nextWave);
                    // Don't send choices UI - chain directive is automatic
                });
            } else {
                // Offer regular directives and potentially a chain
                List<WaveDirective> directives = WaveDirector.INSTANCE.rollDirectiveChoices(nextWave);
                session.setPendingDirectives(directives, nextWave);
                com.devmod.network.NetworkHandler.sendWaveDirectiveChoices(player, nextWave, directives);

                // Offer chain choice periodically (every 3 waves after wave 3, if no active chain)
                if (nextWave >= 3 && nextWave % 3 == 0) {
                    List<DirectiveChain> chainChoices = DirectiveChainManager.INSTANCE.rollChainChoices(nextWave, 2);
                    if (!chainChoices.isEmpty()) {
                        // Send chain offer notification (replaces 4 chat lines with toast overlay)
                        List<String> chainNames = chainChoices.stream()
                            .map(DirectiveChain::name)
                            .toList();
                        NotificationService.INSTANCE.notifyChainOffer(playerId, chainNames, nextWave);
                    }
                }
            }
        }

        float directiveMultiplier = 1.0f;
        ArenaContext arena = session.getArena();
        if (arena != null) {
            directiveMultiplier = WaveManager.INSTANCE.getWaveState(arena.getId())
                .map(WaveManager.WaveState::getRewardMultiplier)
                .orElse(1.0f);
        }

        // === BLOOD CONTRACTS - Check violations and apply reward multiplier ===
        var contractManager = com.devmod.endurance.contracts.ActiveContractManager.INSTANCE;
        contractManager.checkViolations(questId, player);
        float contractMultiplier = contractManager.getRewardMultiplier(questId, playerId);
        if (contractMultiplier > 1.0f) {
            LOGGER.info("[EnduranceQuest]   Contract Multiplier: x{}", String.format("%.1f", contractMultiplier));
        }

        float bargainMultiplier = com.devmod.endurance.bargain.DevilsBargainManager.INSTANCE.getRewardMultiplier(questId);
        if (bargainMultiplier > 1.0f) {
            LOGGER.info("[EnduranceQuest]   Bargain Multiplier: x{}", String.format("%.1f", bargainMultiplier));
        }

        // Apply contract/bargain multipliers to directive multiplier for final reward
        float totalMultiplier = directiveMultiplier * contractMultiplier * bargainMultiplier;

        RewardSystem.WaveReward waveReward = practice
            ? new RewardSystem.WaveReward(0, 0, 1.0f, 1.0f, 1.0f, 0)
            : RewardSystem.INSTANCE.calculateWaveReward(
                waveNumber, quest, comboSession, mutatorSession, totalMultiplier);
        if (!practice) {
            String rewardLine = String.format(
                "Reward: +%d tokens (base %d, style x%.1f, mutator x%.1f, directive x%.1f, bonus %d)",
                waveReward.tokensEarned(),
                waveReward.baseTokens(),
                waveReward.styleMultiplier(),
                waveReward.mutatorMultiplier(),
                waveReward.directiveMultiplier(),
                waveReward.bonusPoints());
            LOGGER.info("[EnduranceQuest]   {}", rewardLine);
        }

        // === NOTIFY PLAYER (Unified Notification Center) ===
        boolean hasMoreWaves = quest.getCurrentWave() < quest.getTotalWaves() || quest.isEndlessMode();
        boolean isFlawless = waveDamageTaken == 0 && waveKills > 0;
        NotificationService.INSTANCE.notifyWaveComplete(
            playerId,
            waveNumber,
            practice ? 0 : waveReward.tokensEarned(),
            styleRank,
            maxCombo,
            isFlawless,
            hasMoreWaves,
            waveKills,
            waveDamage,
            waveDamageTaken,
            practice ? null : waveReward
        );

        // === PUBLISH WAVE COMPLETE EVENT ===
        // This notifies all registered QuestLifecycleListeners of the wave completion.
        ArenaPolicy policy = EnduranceQuestManager.INSTANCE.getPolicyForSession(session);
        QuestContext questContext = QuestContext.from(player, session, policy);
        WaveManager.WaveState waveState = arena != null
            ? WaveManager.INSTANCE.getWaveState(arena.getId()).orElse(null)
            : null;
        WaveContext waveContext = WaveContext.forWaveComplete(
            questContext, waveNumber, comboSession, mutatorSession, waveStats, waveState);
        QuestEventBus.INSTANCE.publish(QuestLifecycleEvent.WaveCompleted.of(waveContext, applyShared));

        // === PARTY STATS SYNC (for debrief Party tab) ===
        if (applyShared) {
            sendPartyWaveStats(session, waveNumber);
        }
    }

    /*
     * Send aggregated party wave stats to all party members for the debrief Party tab.
     * Only sends if player is in a party run (2+ members).
     * FIX #4: Simplified signature - all stats come from CombatTracker now.
     */
    private static void sendPartyWaveStats(EnduranceQuestManager.ActiveQuestSession session,
                                           int waveNumber) {
        UUID partyId = session.getPartyId();
        if (partyId == null) {
            return; // Solo run, no party stats needed
        }

        PartyQuestSession partySession = EnduranceQuestManager.INSTANCE.getPartySession(partyId).orElse(null);
        if (partySession == null || !partySession.isActive()) {
            return;
        }

        // Only aggregate once per wave (first player to complete triggers aggregation)
        EnduranceQuest quest = partySession.getQuest();
        if (quest.getCurrentWave() != waveNumber) {
            return; // Already advanced, skip
        }

        // Get server for player lookup
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Get shared combat session for this quest
        CombatTracker.QuestCombatSession combatSession =
            CombatTracker.INSTANCE.getSession(partySession.getQuestId()).orElse(null);
        // FIX #1/#6: Use explicit wave number to avoid race condition
        CombatTracker.WaveCombatStats sharedWaveStats = combatSession != null
            ? combatSession.getWaveStats(waveNumber)
            : null;

        // FIX #3: Finalize duration BEFORE reading it (otherwise it's 0)
        if (sharedWaveStats != null) {
            sharedWaveStats.finalizeDuration();
        }

        // FIX #8: Use shared wave duration (not per-player)
        long waveDurationMs = sharedWaveStats != null ? sharedWaveStats.duration : 0;

        // Aggregate stats from all party members
        List<PartyWaveStats.PlayerWaveData> playerStats = new java.util.ArrayList<>();
        int partyTotalKills = 0;
        int partyEliteKills = 0;
        int partyTotalDamageDealt = 0;
        int partyTotalDamageTaken = 0;
        int partyDeaths = 0;
        int partyMaxCombo = 0;

        for (UUID memberId : partySession.getMembers()) {
            // FIX AUDIT #6: Use NullGuard for diagnostic logging on unexpected nulls
            if (memberId == null) {
                LOGGER.warn("[EnduranceQuest] Null member ID in party during stats collection");
                continue;
            }
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(memberId);
            if (memberPlayer == null) continue;

            String playerName = memberPlayer.getName().getString();
            boolean isSpectator = partySession.isSpectator(memberId);

            if (isSpectator) {
                playerStats.add(PartyWaveStats.PlayerWaveData.spectator(memberId, playerName));
                continue;
            }

            // Get member's session data
            EnduranceQuestManager.ActiveQuestSession memberSession =
                EnduranceQuestManager.INSTANCE.getActiveSession(memberPlayer).orElse(null);
            if (memberSession == null) continue;

            // FIX #1/#6: Get per-player stats using explicit wave number
            CombatTracker.PlayerWaveCombatStats playerWaveStats = combatSession != null
                ? combatSession.getPlayerWaveStats(memberId, waveNumber)
                : null;

            // FIX #3: Use all 0s as fallback if no tracking data (not session kills)
            int kills = playerWaveStats != null ? playerWaveStats.kills : 0;
            int damageDealt = playerWaveStats != null ? (int) playerWaveStats.damageDealt : 0;
            int damageTaken = playerWaveStats != null ? (int) playerWaveStats.damageTaken : 0;
            int deaths = playerWaveStats != null ? playerWaveStats.deaths : 0;
            // FIX #4: Use real eliteKills from per-player tracking
            int eliteKills = playerWaveStats != null ? playerWaveStats.eliteKills : 0;
            // FIX #5: Use per-wave maxCombo from CombatTracker (not session max)
            int maxCombo = playerWaveStats != null ? playerWaveStats.maxCombo : 0;

            float dps = waveDurationMs > 0 ? (damageDealt * 1000f / waveDurationMs) : 0;

            partyTotalKills += kills;
            partyEliteKills += eliteKills;
            partyTotalDamageDealt += damageDealt;
            partyTotalDamageTaken += damageTaken;
            partyDeaths += deaths;
            if (maxCombo > partyMaxCombo) partyMaxCombo = maxCombo;

            playerStats.add(new PartyWaveStats.PlayerWaveData(
                memberId.toString(),
                playerName,
                kills,
                eliteKills, // FIX #4: Now using real per-player elite kills
                damageDealt,
                damageTaken,
                deaths,
                maxCombo,
                dps,
                0f, // killPercent calculated below
                false
            ));
        }

        // Calculate kill percentages
        if (partyTotalKills > 0) {
            List<PartyWaveStats.PlayerWaveData> updatedStats = new java.util.ArrayList<>();
            for (PartyWaveStats.PlayerWaveData data : playerStats) {
                float killPercent = (data.kills() * 100f) / partyTotalKills;
                updatedStats.add(new PartyWaveStats.PlayerWaveData(
                    data.playerId(), data.playerName(), data.kills(), data.eliteKills(),
                    data.damageDealt(), data.damageTaken(), data.deaths(), data.maxCombo(),
                    data.dps(), killPercent, data.wasSpectator()
                ));
            }
            playerStats = updatedStats;
        }

        // Calculate party DPS and no-damage status
        float partyDPS = waveDurationMs > 0 ? (partyTotalDamageDealt * 1000f / waveDurationMs) : 0;
        boolean partyNoDamageWave = partyTotalDamageTaken == 0 && partyTotalKills > 0;

        // Determine MVP
        String mvpPlayerId = PartyWaveStats.determineMvp(playerStats);
        PartyWaveStats.PlayerWaveData mvpData = null;
        for (PartyWaveStats.PlayerWaveData data : playerStats) {
            if (data.playerId().equals(mvpPlayerId)) {
                mvpData = data;
                break;
            }
        }
        String mvpReason = PartyWaveStats.determineMvpReason(mvpData, playerStats, waveNumber);

        // Build and send payload to all party members
        PartyWaveStats stats = new PartyWaveStats(
            waveNumber,
            quest.getTotalWaves(),
            waveDurationMs,
            playerStats,
            partyTotalKills,
            partyEliteKills,
            partyTotalDamageDealt,
            partyTotalDamageTaken,
            partyDeaths,
            partyMaxCombo,
            partyDPS,
            partyNoDamageWave,
            mvpPlayerId,
            mvpReason
        );

        PartyStatsSyncPayload payload = PartyStatsSyncPayload.fromPartyWaveStats(stats);

        // FIX AUDIT #7: Validate payload before distribution loop
        if (payload == null) {
            LOGGER.warn("[EnduranceQuest] Failed to create PartyStatsSyncPayload for wave {}", waveNumber);
            return;
        }

        for (UUID memberId : partySession.getMembers()) {
            if (memberId == null) {
                LOGGER.warn("[EnduranceQuest] Null member ID in party session during stats sync");
                continue;
            }
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(memberId);
            if (memberPlayer != null) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(memberPlayer, payload);
            }
        }

        LOGGER.debug("[EnduranceQuest] Party stats synced for wave {}: {} members, {} total kills, MVP: {}",
            waveNumber, playerStats.size(), partyTotalKills, mvpPlayerId);
    }

    // ==============================================================
    // NEOFORGE EVENT SUBSCRIBERS
    // ==============================================================

    /*
     * Handle damage dealt by players to mobs in quests.
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        EnduranceEventCombat.handleDamagePost(event.getEntity(), event.getSource(), event.getNewDamage());
    }

    /*
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
                if (session.get().isLoadingProtection()) {
                    if (event.getNewDamage() > 0f) {
                        LOGGER.debug("[EnduranceQuest] Blocked damage during loading for {} (source={}, damage={}, pos={}, dim={})",
                            player.getName().getString(),
                            event.getSource().getMsgId(),
                            event.getNewDamage(),
                            player.blockPosition(),
                            player.level().dimension().location());
                    }
                    event.setNewDamage(0f);
                    return;
                }
                // IMPORTANT: Use getNewDamage() to respect armor reduction!
                // getOriginalDamage() returns pre-armor damage which would bypass armor
                float modifiedDamage = EnduranceEventCombat.handleDamagePre(player, event.getNewDamage());
                event.setNewDamage(modifiedDamage);
            }
        }
    }

    /*
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

    /*
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
            var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
            String damageType = event.getSource() != null ? event.getSource().type().msgId() : "unknown";
            if (sessionOpt.isPresent()) {
                var session = sessionOpt.get();
                LOGGER.info("[EnduranceQuest][DeathEvent] player={}, damageType={}, dimension={}, questState={}, awaitingRespawn={}, respawnRequested={}, wave={}, questId={}, instanceId={}",
                    player.getName().getString(),
                    damageType,
                    player.level().dimension().location(),
                    session.getQuest().getState(),
                    session.isAwaitingRespawnChoice(),
                    session.isRespawnRequested(),
                    session.getQuest().getCurrentWave(),
                    session.getQuest().getQuestId(),
                    session.getInstanceId());
            } else {
                boolean inInstance = com.devmod.runtime.InstanceManager.INSTANCE.isPlayerInInstance(player.getUUID());
                LOGGER.info("[EnduranceQuest][DeathEvent] player={}, damageType={}, dimension={}, hasSession=false, inInstance={}",
                    player.getName().getString(),
                    damageType,
                    player.level().dimension().location(),
                    inInstance);
            }
            EnduranceEventCombat.handlePlayerDeath(player);
        }
    }

    /*
     * Prevent quest gear from littering the arena on player death.
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (EnduranceQuestManager.INSTANCE.getActiveSession(player).isPresent()) {
                event.getDrops().clear();
            }
        }
    }

    /*
     * Server tick handler for wave management and quest updates.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        EnduranceEventTick.onServerTick();
    }

    /*
     * Restore per-player synced kit data on login.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KitManager.INSTANCE.restoreSyncedKits(player);
            var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
            if (sessionOpt.isPresent()) {
                var session = sessionOpt.get();
                if (session.getPartyId() != null && session.isPartySpectator()) {
                    EnduranceQuestManager.INSTANCE.rejoinPartyMember(player);
                }
            }

            var party = com.devmod.party.PartyManager.INSTANCE.getPlayerParty(player.getUUID());
            if (party != null && party.getState() != com.devmod.party.PartyData.PartyState.IN_QUEST) {
                UUID leaderId = party.getLeaderId();
                if (!leaderId.equals(player.getUUID())) {
                    MinecraftServer server = player.getServer();
                    if (server != null && server.getPlayerList().getPlayer(leaderId) == null) {
                        if (com.devmod.party.PartyManager.INSTANCE.transferLeadership(leaderId, player.getUUID())) {
                            com.devmod.network.handlers.PartyNetworkHandler.syncPartyToAllMembers(server, party.getPartyId());
                        }
                    }
                }
            }
        }
    }

    /*
     * Handle player logout - cleanup quest session.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();

            // Notify QuestStartSequence about disconnect (cancels sequence if needed)
            QuestStartSequence.INSTANCE.onPlayerDisconnect(playerId);

            // Clean up nutrition session to prevent memory leaks
            NutritionBridgeSystem.INSTANCE.onPlayerDisconnect(playerId);

            Optional<EnduranceQuestManager.ActiveQuestSession> sessionOpt =
                EnduranceQuestManager.INSTANCE.getActiveSession(player);

            if (sessionOpt.isPresent()) {
                LOGGER.info("[EnduranceQuest] Player {} logged out during quest, cleaning up...",
                    player.getName().getString());
                var session = sessionOpt.get();
                if (session.getPartyId() != null
                    && EnduranceQuestManager.INSTANCE.getPartySession(session.getPartyId())
                        .map(PartyQuestSession::isActive).orElse(false)) {
                    EnduranceQuestManager.INSTANCE.markPartyMemberInactive(playerId, "disconnect");
                } else {
                    // Treat as abandonment
                    EnduranceQuestManager.INSTANCE.abandonQuest(player);
                }
            }

            // Handle party disconnect - transfer leadership or leave party
            var party = com.devmod.party.PartyManager.INSTANCE.getPlayerParty(playerId);
            if (party != null) {
                UUID partyId = party.getPartyId();
                boolean wasLeader = party.isLeader(playerId);
                boolean partyRunActive = EnduranceQuestManager.INSTANCE.getPartySession(partyId)
                    .map(PartyQuestSession::isActive).orElse(false);

                if (!partyRunActive) {
                    // This will transfer leadership if player was leader, or just remove from party
                    com.devmod.party.PartyManager.INSTANCE.handlePlayerDisconnect(playerId);
                }

                // Sync party state to remaining members
                var server = player.getServer();
                if (server != null) {
                    // Party may have been disbanded if this was the only member
                    var remainingParty = com.devmod.party.PartyManager.INSTANCE.getParty(partyId);
                    if (remainingParty != null) {
                        com.devmod.network.handlers.PartyNetworkHandler.syncPartyToAllMembers(server, partyId);
                        if (wasLeader) {
                            LOGGER.info("[Party] Leadership transferred due to disconnect, new leader: {}",
                                remainingParty.getLeaderName());
                        }
                    }
                }
            }

            // Flush and cleanup telemetry aggregator for this player
            if (AggregationConfig.AGGREGATION_ENABLED) {
                TelemetryAggregatorRegistry.INSTANCE.onPlayerLeave(playerId);
            }

            // Clear per-player synced kit data
            KitManager.INSTANCE.clearSyncedKits(playerId);

            // Clear arena build rate limiter data to prevent memory leak
            com.devmod.arena.builder.ArenaBuilder.cleanupPlayerRateLimiter(playerId);
        }
    }

    // ==============================================================
    // PUBLIC GETTERS FOR UI
    // ==============================================================

    public static IComboSession getComboSession(UUID playerId) {
        return ComboSystemFacade.get().getSession(playerId).orElse(null);
    }

    public static MutatorSystem.MutatorSession getMutatorSession(UUID questId) {
        return EnduranceEventCombat.getMutatorSession(questId);
    }

    /*
     * Check if player is trying to leave the arena.
     */
    public static boolean canPlayerLeaveArena(ServerPlayer player) {
        return EnduranceEventTick.canPlayerLeaveArena(player);
    }

    /*
     * Teleport player back to arena if they try to escape.
     */
    public static void enforceArenaConfinement(ServerPlayer player) {
        EnduranceEventTick.enforceArenaConfinement(player);
    }

    // ==============================================================
    // MAILBOX INTEGRATION
    // ==============================================================

    /*
     * Send a mailbox notification with quest reward summary.
     */
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

            // Position description (wave reached / total or "Completed!")
            String position = completed
                ? "Completed!"
                : "Wave " + waveReached + "/" + totalWaves;

            // Send via template (fire-and-forget)
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
                null // No attachment - rewards already given
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

    /*
     * Builds a WaveSummary from combat and combo data.
     */
    private static WaveSummary buildWaveSummary(int waveNumber,
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
            "unknown", // primaryWeapon - could be tracked in future
            Map.of() // bodyPartHits - retrieved from CombatTracker if needed
        );
    }

    /*
     * Builds a QuestResult from all tracking data.
     */
    private static QuestResult buildQuestResult(UUID questId, UUID playerId,
            EnduranceQuest quest,
            CombatTracker.QuestCombatSession combatSession,
            IComboSession comboSession,
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
            0, // xpEarned - set by reward system
            0, // coinsEarned - set by reward system
            List.of() // achievementsUnlocked - set by gamification
        );
    }

    private static @javax.annotation.Nullable UUID resolveDesignScopeId(
            @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null) {
            return null;
        }
        UUID instanceId = session.getInstanceId();
        return instanceId != null ? instanceId : session.getQuest().getQuestId();
    }

    private static boolean isSignatureWeaponsEnabled(
            @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null) {
            return false;
        }
        return GameDesignConfigManager.INSTANCE.isSignatureWeaponsEnabled(
            resolveDesignScopeId(session), session.isPracticeMode());
    }

    private static boolean isTideEnabled(
            @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null) {
            return false;
        }
        return GameDesignConfigManager.INSTANCE.isTideEnabled(
            resolveDesignScopeId(session), session.isPracticeMode());
    }
}
