package com.devmod.endurance;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.arena.policy.ArenaPolicy;
import com.devmod.endurance.analytics.LiveAnalyticsHookManager;
import com.devmod.endurance.analytics.WaveSummary;
import com.devmod.endurance.combat.ComboSystemFacade;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.lifecycle.QuestContext;
import com.devmod.endurance.lifecycle.QuestEventBus;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent;
import com.devmod.endurance.lifecycle.WaveContext;
import com.devmod.notification.NotificationService;
import com.devmod.telemetry.player.PlayerAttributeTelemetryService;

/**
 * Wave lifecycle event handling: wave start, wave complete, and party wave stats sync.
 * Extracted from EnduranceEventHandler to keep each class focused.
 */
final class EnduranceEventWave {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceEventWave.class);

    private EnduranceEventWave() {}

    // ==============================================================
    // WAVE START
    // ==============================================================

    static void onWaveStart(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session,
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

        // Send unified notification
        NotificationService.INSTANCE.notifyWaveStart(
            playerId, waveNumber, totalWaves, mobCount, enemyType,
            isBossWave, objective, directive
        );

        // === PUBLISH WAVE START EVENT ===
        ArenaPolicy policy = EnduranceQuestManager.INSTANCE.getPolicyForSession(session);
        QuestContext questContext = QuestContext.from(player, session, policy);
        WaveContext waveContext = WaveContext.forWaveStart(
            questContext, waveNumber, isBossWave, mobCount, enemyType, comboSession);
        QuestEventBus.INSTANCE.publish(QuestLifecycleEvent.WaveStarted.of(waveContext, applyShared));

        LOGGER.debug("[EnduranceQuest] Wave {} started for {} (boss: {})",
            waveNumber, player.getName().getString(), isBossWave);
    }

    // ==============================================================
    // WAVE COMPLETE
    // ==============================================================

    static void onWaveComplete(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session,
                               int waveNumber, boolean applyShared) {
        // Heal player to full and clear negative effects at wave completion
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.clearFire();
        LOGGER.debug("[EnduranceQuest] Healed player {} at wave {} completion", player.getName().getString(), waveNumber);

        UUID playerId = player.getUUID();
        UUID questId = session.getQuest().getQuestId();
        EnduranceQuest quest = session.getQuest();
        boolean practice = session.isPracticeMode();
        boolean signatureEnabled = EnduranceEventQuestLifecycle.isSignatureWeaponsEnabled(session);
        boolean tideEnabled = EnduranceEventQuestLifecycle.isTideEnabled(session);
        UUID tideScopeId = EnduranceEventQuestLifecycle.resolveDesignScopeId(session);

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
            com.devmod.combat.bridge.CombatEnduranceBridge.get().recordSoulSSSWave(player);
            LOGGER.debug("[EnduranceQuest] Recorded SSS wave for signature weapon tracking");
        }
        if (signatureEnabled && waveDamageTaken == 0 && waveKills > 0) {
            com.devmod.combat.bridge.CombatEnduranceBridge.get().recordSoulNoHitWave(player);
            LOGGER.debug("[EnduranceQuest] Recorded no-hit wave for signature weapon tracking");
        }

        if (!practice) {
            if ("SSS".equals(styleRank)) {
                perkSynergyWeb.recordSSSRank(player);
            }

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

        // === DETAILED LOGGING ===
        LOGGER.info("[EnduranceQuest] Wave {} completed for quest {} by player {}",
            waveNumber, questId, player.getName().getString());
        LOGGER.info("[EnduranceQuest]   Combat Stats: {} damage dealt, {} kills, {} damage taken",
            String.format("%.1f", waveDamage), waveKills, String.format("%.1f", waveDamageTaken));
        LOGGER.info("[EnduranceQuest]   Style Stats: Rank {}, Max Combo {}", styleRank, maxCombo);
        if (mutatorSession != null) {
            LOGGER.info("[EnduranceQuest]   Active Mutators: {}", mutatorSession.getActiveMutatorCount());
        }

        // Combat tracker wave advance is driven by CombatTracker.onWaveCompleted,
        // triggered by the WaveCompleted event published at the end of this method.

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
            WaveSummary waveSummary = EnduranceEventQuestLifecycle.buildWaveSummary(waveNumber, waveStats, comboSession);
            LiveAnalyticsHookManager.INSTANCE.onWaveComplete(waveNumber, waveSummary, waveNumber + 1);
        }

        // === MUTATOR SYSTEM - Roll new mutators between waves ===
        if (applyShared && mutatorSession != null && waveNumber % 3 == 0) {
            int prevMutatorCount = mutatorSession.getActiveMutatorCount();
            MutatorSystem.INSTANCE.rollNewMutator(mutatorSession, waveNumber);
            LOGGER.info("[EnduranceQuest] New mutator rolled at wave {}", waveNumber);

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
        boolean chainActive = DirectiveChainManager.INSTANCE.hasActiveChain(questId);
        if (applyShared && chainActive) {
            int waveDeaths = waveStats != null ? waveStats.deaths : 0;
            float damageTakenThisWave = waveStats != null ? waveStats.damageTaken : 0;
            boolean tookDamage = damageTakenThisWave > 0;
            int styleOrdinal = comboSession != null ? comboSession.getCurrentRank().getNetworkId() : 0;

            DirectiveChainManager.ChainAdvanceResult chainResult = DirectiveChainManager.INSTANCE.advanceChain(
                questId, waveKills, maxCombo, waveDeaths > 0, tookDamage, styleOrdinal);

            if (chainResult == DirectiveChainManager.ChainAdvanceResult.CHAIN_COMPLETED) {
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
                DirectiveChainManager.INSTANCE.getActiveChain(questId).ifPresent(progress ->
                    NotificationService.INSTANCE.notifyChainFailed(playerId, progress.getChain().name()));
            } else if (chainResult == DirectiveChainManager.ChainAdvanceResult.STEP_COMPLETED) {
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

            if (DirectiveChainManager.INSTANCE.hasActiveChain(questId)) {
                DirectiveChainManager.INSTANCE.getCurrentChainDirective(questId).ifPresent(chainDirective -> {
                    session.setPendingDirectives(List.of(chainDirective), nextWave);
                });
            } else {
                List<WaveDirective> directives = WaveDirector.INSTANCE.rollDirectiveChoices(nextWave);
                session.setPendingDirectives(directives, nextWave);
                com.devmod.network.NetworkHandler.sendWaveDirectiveChoices(player, nextWave, directives);

                if (nextWave >= 3 && nextWave % 3 == 0) {
                    List<DirectiveChain> chainChoices = DirectiveChainManager.INSTANCE.rollChainChoices(nextWave, 2);
                    if (!chainChoices.isEmpty()) {
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
            practice ? null : Map.of(
                "tokens", String.valueOf(waveReward.tokensEarned()),
                "base", String.valueOf(waveReward.baseTokens()),
                "style_mult", String.valueOf(waveReward.styleMultiplier()),
                "bonus", String.valueOf(waveReward.bonusPoints())
            )
        );

        // === PUBLISH WAVE COMPLETE EVENT ===
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

    // ==============================================================
    // PARTY WAVE STATS
    // ==============================================================

    private static void sendPartyWaveStats(EnduranceQuestManager.ActiveQuestSession session,
                                           int waveNumber) {
        UUID partyId = session.getPartyId();
        if (partyId == null) {
            return;
        }

        PartyQuestSession partySession = EnduranceQuestManager.INSTANCE.getPartySession(partyId).orElse(null);
        if (partySession == null || !partySession.isActive()) {
            return;
        }

        EnduranceQuest quest = partySession.getQuest();
        if (quest.getCurrentWave() != waveNumber) {
            return;
        }

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        CombatTracker.QuestCombatSession combatSession =
            CombatTracker.INSTANCE.getSession(partySession.getQuestId()).orElse(null);
        CombatTracker.WaveCombatStats sharedWaveStats = combatSession != null
            ? combatSession.getWaveStats(waveNumber)
            : null;

        if (sharedWaveStats != null) {
            sharedWaveStats.finalizeDuration();
        }

        long waveDurationMs = sharedWaveStats != null ? sharedWaveStats.duration : 0;

        List<PartyWaveStats.PlayerWaveData> playerStats = new java.util.ArrayList<>();
        int partyTotalKills = 0;
        int partyEliteKills = 0;
        int partyTotalDamageDealt = 0;
        int partyTotalDamageTaken = 0;
        int partyDeaths = 0;
        int partyMaxCombo = 0;

        for (UUID memberId : partySession.getMembers()) {
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

            EnduranceQuestManager.ActiveQuestSession memberSession =
                EnduranceQuestManager.INSTANCE.getActiveSession(memberPlayer).orElse(null);
            if (memberSession == null) continue;

            CombatTracker.PlayerWaveCombatStats playerWaveStats = combatSession != null
                ? combatSession.getPlayerWaveStats(memberId, waveNumber)
                : null;

            int kills = playerWaveStats != null ? playerWaveStats.kills : 0;
            int damageDealt = playerWaveStats != null ? (int) playerWaveStats.damageDealt : 0;
            int damageTaken = playerWaveStats != null ? (int) playerWaveStats.damageTaken : 0;
            int deaths = playerWaveStats != null ? playerWaveStats.deaths : 0;
            int eliteKills = playerWaveStats != null ? playerWaveStats.eliteKills : 0;
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
                eliteKills,
                damageDealt,
                damageTaken,
                deaths,
                maxCombo,
                dps,
                0f,
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

        float partyDPS = waveDurationMs > 0 ? (partyTotalDamageDealt * 1000f / waveDurationMs) : 0;
        boolean partyNoDamageWave = partyTotalDamageTaken == 0 && partyTotalKills > 0;

        String mvpPlayerId = PartyWaveStats.determineMvp(playerStats);
        PartyWaveStats.PlayerWaveData mvpData = null;
        for (PartyWaveStats.PlayerWaveData data : playerStats) {
            if (data.playerId().equals(mvpPlayerId)) {
                mvpData = data;
                break;
            }
        }
        String mvpReason = PartyWaveStats.determineMvpReason(mvpData, playerStats, waveNumber);

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
}
