package com.devmod.network.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.arena.policy.PolicyResolver;
import com.devmod.arena.policy.TemplateSuggestion;
import com.devmod.endurance.ArenaSuggestionsPayload;
import com.devmod.endurance.BossAlertPayload;
import com.devmod.endurance.EnduranceLogger;
import com.devmod.endurance.EnduranceLogger.Phase;
import com.devmod.endurance.EnduranceQuest;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.EnduranceQuestState;
import com.devmod.endurance.InstanceLoadingPayload;
import com.devmod.endurance.KitManager;
import com.devmod.endurance.PartyStatsSyncPayload;
import com.devmod.endurance.PersonalRecordsSyncPayload;
import com.devmod.endurance.QuestActionPayload;
import com.devmod.endurance.QuestCompletionPayload;
import com.devmod.endurance.QuestDeathPayload;
import com.devmod.endurance.QuestSyncPayload;
import com.devmod.endurance.RequestArenaSuggestionsPayload;
import com.devmod.endurance.RequestPersonalRecordsPayload;
import com.devmod.endurance.RewardSystem;
import com.devmod.endurance.StartQuestPayload;
import com.devmod.endurance.TensionUpdatePayload;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.nutrition.NutritionSyncPayload;
import com.devmod.mob.MobRequirements;
import com.devmod.mob.MobRequirementsRegistry;
import com.devmod.network.NetworkHandler;
import com.devmod.network.PacketValidator;
import com.devmod.network.PacketValidator.ValidationResult;
import com.devmod.shared.SharedColorTokens;
import com.devmod.util.I18n;

/**
 * Handles quest lifecycle, arena suggestions, combat feedback, and instance loading payloads
 * for the endurance system. Delegated from {@link EnduranceNetworkHandler}.
 */
final class EnduranceQuestPacketHandler extends NetworkHandlerBase {

    private static final long ABANDON_CONFIRM_WINDOW_MS = 3_000L;

    private EnduranceQuestPacketHandler() {}

    // =================================================================================
    // START ENDURANCE QUEST
    // =================================================================================
    static void handleStartEnduranceQuest(StartQuestPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketValidator security = security();
                ValidationResult validation = security.validatePacket(player, "endurance_quest", false);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                    return;
                }

                String mobId = payload.mobId();
                if (mobId == null || mobId.isEmpty()) {
                    player.sendSystemMessage(I18n.translate("devmod.network.invalid_entity"));
                    return;
                }

                int waves = Math.max(1, Math.min(payload.totalWaves(), 100));
                int arenaSize = Math.max(32, Math.min(payload.arenaSize(), 128));
                String kitId = payload.kitId() != null ? payload.kitId() : "STARTER";

                if ("TEMPORARY".equals(kitId)) {
                    if (!KitManager.INSTANCE.hasTemporaryKit(player.getUUID())
                        && !KitManager.INSTANCE.hasTemporaryKit()) {
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", "Temporary kit not synced"));
                        return;
                    }
                } else if (kitId.length() == 8) {
                    boolean hasSynced = KitManager.INSTANCE.getSyncedCustomKit(player.getUUID(), kitId).isPresent();
                    boolean hasSaved = KitManager.INSTANCE.getCustomKit(kitId).isPresent();
                    if (!hasSynced && !hasSaved) {
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", "Custom kit not found"));
                        return;
                    }
                } else if (KitManager.INSTANCE.getKitById(kitId) == null) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", "Unknown kit"));
                    return;
                }

                try {
                    ResourceLocation mobLocation = ResourceLocation.parse(mobId);

                    EnduranceQuestManager.QuestSettings settings = new EnduranceQuestManager.QuestSettings();
                    settings.totalWaves = waves;
                    settings.endlessMode = payload.endlessMode();
                    settings.arenaSize = arenaSize;
                    settings.kitId = kitId;
                    settings.forceTemplateId = payload.forceTemplateId();
                    settings.practiceMode = payload.practiceMode();

                    EnduranceQuestManager.StartQuestResult result = EnduranceQuestManager.INSTANCE.startQuest(
                        player, mobLocation, settings);

                    if (result.success()) {
                        player.sendSystemMessage(I18n.translate("devmod.network.quest_started_msg", mobId));
                        LOGGER.info("[EnduranceQuest] Player {} started quest for {} ({} waves, endless={})",
                            player.getName().getString(), mobId, waves, payload.endlessMode());
                    } else {
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", result.message()));
                    }

                } catch (Exception e) {
                    player.sendSystemMessage(I18n.translate("devmod.network.failed_start_quest", e.getMessage()));
                    LOGGER.error("[EnduranceQuest] Failed to start quest", e);
                }
            }
        });
    }

    // =================================================================================
    // ARENA SUGGESTIONS
    // =================================================================================
    static void handleRequestArenaSuggestions(RequestArenaSuggestionsPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "arena_suggestions", false);
            if (!validation.isSuccess()) {
                return;
            }

            try {
                ResourceLocation mobId = payload.getMobResourceLocation();
                MobRequirements mobReqs = MobRequirementsRegistry.INSTANCE.get(mobId);

                PolicyResolver resolver = EnduranceQuestManager.INSTANCE.getPolicyResolver();
                if (resolver == null) {
                    LOGGER.warn("[ArenaSuggestions] PolicyResolver not available");
                    return;
                }

                List<TemplateSuggestion> suggestions = resolver.getTemplateSuggestions(mobReqs);
                String selectedTemplateId = resolver.getAutoSelectedTemplateId(mobReqs);

                ArenaSuggestionsPayload response = new ArenaSuggestionsPayload(
                    mobId.toString(),
                    suggestions,
                    selectedTemplateId
                );
                player.connection.send(response);

            } catch (Exception e) {
                LOGGER.error("[ArenaSuggestions] Failed to calculate suggestions", e);
            }
        });
    }

    static void handleArenaSuggestions(ArenaSuggestionsPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        com.devmod.client.endurance.ClientArenaSuggestionsCache.INSTANCE.receiveSuggestions(payload);
    }

    // =================================================================================
    // QUEST ACTIONS (respawn, checkpoint, abandon)
    // =================================================================================
    static void handleQuestAction(QuestActionPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "quest_action", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("quest_action", player.getName().getString());
                return;
            }

            QuestActionPayload.Action action = payload.action();

            var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
            if (sessionOpt.isEmpty()) {
                player.sendSystemMessage(I18n.translate("devmod.network.no_active_quest"));
                return;
            }
            var session = sessionOpt.get();
            boolean awaitingRespawn = session.isAwaitingRespawnChoice();
            boolean atCheckpoint = session.getQuest().getState() == EnduranceQuestState.WAVE_COMPLETE;

            try {
                switch (action) {
                    case CONTINUE_AFTER_DEATH -> {
                        session.clearAbandonConfirm();
                        if (awaitingRespawn) {
                            EnduranceQuestManager.INSTANCE.handleRespawnChoice(player, true);
                            LOGGER.info("[EnduranceQuest] Player {} respawning after death",
                                player.getName().getString());
                        } else if (atCheckpoint) {
                            EnduranceQuestManager.INSTANCE.continueToNextWave(player);
                            LOGGER.info("[EnduranceQuest] Player {} continuing to next wave",
                                player.getName().getString());
                        } else {
                            player.sendSystemMessage(I18n.translate("devmod.network.cannot_continue"));
                        }
                    }
                    case GIVE_UP_AFTER_DEATH -> {
                        if (awaitingRespawn) {
                            EnduranceQuestManager.INSTANCE.handleRespawnChoice(player, false);
                            LOGGER.info("[EnduranceQuest] Player {} gave up after death",
                                player.getName().getString());
                        } else if (atCheckpoint) {
                            EnduranceQuestManager.INSTANCE.exitAtCheckpoint(player);
                            LOGGER.info("[EnduranceQuest] Player {} exited at checkpoint",
                                player.getName().getString());
                        } else {
                            if (!confirmAbandon(player, session)) {
                                return;
                            }
                            EnduranceQuestManager.INSTANCE.abandonQuest(player);
                            LOGGER.info("[EnduranceQuest] Player {} abandoned quest",
                                player.getName().getString());
                        }
                    }
                    case CONTINUE_TO_NEXT_WAVE -> {
                        session.clearAbandonConfirm();
                        EnduranceQuestManager.INSTANCE.continueToNextWave(player);
                        LOGGER.info("[EnduranceQuest] Player {} continuing to next wave",
                            player.getName().getString());
                    }
                    case EXIT_AT_CHECKPOINT -> {
                        session.clearAbandonConfirm();
                        EnduranceQuestManager.INSTANCE.exitAtCheckpoint(player);
                        LOGGER.info("[EnduranceQuest] Player {} exited at checkpoint",
                            player.getName().getString());
                    }
                    case ABANDON_QUEST -> {
                        if (!confirmAbandon(player, session)) {
                            return;
                        }
                        EnduranceQuestManager.INSTANCE.abandonQuest(player);
                        LOGGER.info("[EnduranceQuest] Player {} abandoned quest",
                            player.getName().getString());
                    }
                }
            } catch (Exception e) {
                player.sendSystemMessage(I18n.translate("devmod.network.quest_action_failed", e.getMessage()));
                LOGGER.error("[EnduranceQuest] Quest action failed", e);
            }
        });
    }

    private static boolean confirmAbandon(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session) {
        if (session.confirmAbandonRequest(ABANDON_CONFIRM_WINDOW_MS)) {
            return true;
        }
        player.sendSystemMessage(Objects.requireNonNull(Component.literal("[DevMod] Press exit again to confirm.")
            .withStyle(SharedColorTokens.Chat.YELLOW)));
        LOGGER.info("[EnduranceQuest] Player {} requested quest abandon; awaiting confirmation",
            player.getName().getString());
        return false;
    }

    // =================================================================================
    // QUEST SYNC (client-side)
    // =================================================================================
    static void handleQuestSync(QuestSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleQuestSync(payload)));
        }
    }

    // =================================================================================
    // QUEST DEATH SCREEN (client-side)
    // =================================================================================
    static void handleQuestDeath(QuestDeathPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleQuestDeath(payload)));
        }
    }

    static void sendQuestDeathScreen(ServerPlayer player, int currentWave, int totalWaves,
            boolean endlessMode, int pointsEarned, int deathsThisRun, int respawnCost) {
        EnduranceLogger.phase(Phase.DEATH_SCREEN, player, null,
            "Showing death screen: wave=%d/%d, points=%d, deaths=%d, respawnCost=%d",
            currentWave, totalWaves, pointsEarned, deathsThisRun, respawnCost);
        QuestDeathPayload payload = new QuestDeathPayload(
            currentWave, totalWaves, endlessMode, pointsEarned, deathsThisRun, respawnCost);
        sendPacket(player, payload);
    }

    // =================================================================================
    // QUEST COMPLETION (client-side)
    // =================================================================================
    static void handleQuestCompletion(QuestCompletionPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleQuestCompletion(payload)));
        }
    }

    static void sendQuestCompletionScreen(ServerPlayer player,
            EnduranceQuestManager.ActiveQuestSession session,
            RewardSystem.QuestRewards rewards,
            IComboSession comboSession,
            int maxCombo) {
        EnduranceQuest quest = session.getQuest();

        List<String> achievementNames = new ArrayList<>();
        if (rewards.achievementsUnlocked != null) {
            for (RewardSystem.Achievement achievement : rewards.achievementsUnlocked) {
                achievementNames.add(achievement.getDisplayName());
            }
        }

        String templateId = session.getTemplateId() != null ? session.getTemplateId() : "";
        Integer templateVersionValue = session.getTemplateVersion();
        int templateVersion = templateVersionValue != null ? templateVersionValue.intValue() : 0;
        String policyId = session.getPolicyId() != null ? session.getPolicyId() : "";
        Integer policyVersionValue = session.getPolicyVersion();
        int policyVersion = policyVersionValue != null ? policyVersionValue.intValue() : 0;
        String instanceId = session.getInstanceId() != null ? session.getInstanceId().toString() : "";
        String arenaId = session.getArena() != null ? session.getArena().getId().toString() : "";
        String difficultyLabel = session.getDifficultyLabel() != null ? session.getDifficultyLabel() : "";
        String questTypeLabel = session.getQuestTypeLabel() != null ? session.getQuestTypeLabel() : "";

        QuestCompletionPayload payload = new QuestCompletionPayload(
            quest.getDisplayName(),
            quest.getCurrentWave(),
            quest.getTotalWaves(),
            quest.isEndlessMode(),
            quest.getSessionDuration(),
            templateId,
            templateVersion,
            policyId,
            policyVersion,
            instanceId,
            arenaId,
            difficultyLabel,
            questTypeLabel,
            rewards.tokensEarned,
            rewards.baseTokens,
            rewards.prestigeEarned,
            rewards.bloodGemsEarned,
            rewards.styleMultiplier,
            rewards.mutatorMultiplier,
            rewards.noHitBonus,
            rewards.speedBonus,
            rewards.styleRank != null ? rewards.styleRank.ordinal() : 0,
            rewards.activeMutators,
            quest.getMobsKilledThisSession(),
            quest.getTotalDamageDealtThisSession(),
            quest.getDamageTakenThisSession(),
            quest.getDeathsThisSession(),
            maxCombo,
            achievementNames
        );

        EnduranceLogger.phase(Phase.RESULTS_SCREEN, player, quest.getQuestId(),
            "Showing results: wave=%d/%d, duration=%dms, tokens=%d, kills=%d, deaths=%d, maxCombo=%d",
            quest.getCurrentWave(), quest.getTotalWaves(), quest.getSessionDuration(),
            rewards.tokensEarned, quest.getMobsKilledThisSession(), quest.getDeathsThisSession(), maxCombo);
        sendPacket(player, payload);
    }

    // =================================================================================
    // INSTANCE LOADING OVERLAY (client-side)
    // =================================================================================
    static void handleInstanceLoading(InstanceLoadingPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleInstanceLoading(payload)));
        }
    }

    static void sendInstanceLoadingShow(ServerPlayer player, String status) {
        InstanceLoadingPayload payload = new InstanceLoadingPayload(true, status);
        sendPacket(player, payload);
    }

    static void sendInstanceLoadingHide(ServerPlayer player) {
        InstanceLoadingPayload payload = InstanceLoadingPayload.hide();
        sendPacket(player, payload);
    }

    // =================================================================================
    // PERSONAL RECORDS
    // =================================================================================
    static void handlePersonalRecordsSync(PersonalRecordsSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handlePersonalRecordsSync(payload)));
        }
    }

    static void handleRequestPersonalRecords(RequestPersonalRecordsPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer player) {
                sendPersonalRecordsSync(player);
            }
        });
    }

    static void sendPersonalRecordsSync(ServerPlayer player) {
        EnduranceQuestManager.PlayerQuestStats stats = EnduranceQuestManager.INSTANCE.getPlayerStats(player.getUUID());

        Map<String, PersonalRecordsSyncPayload.MobRecord> mobRecords = new HashMap<>();
        for (Map.Entry<String, EnduranceQuestManager.MobQuestRecord> entry : stats.getMobRecords().entrySet()) {
            EnduranceQuestManager.MobQuestRecord record = entry.getValue();
            mobRecords.put(entry.getKey(), new PersonalRecordsSyncPayload.MobRecord(
                record.attempts,
                record.completions,
                record.bestScore,
                record.highestWave
            ));
        }

        PersonalRecordsSyncPayload syncPayload = new PersonalRecordsSyncPayload(
            stats.getTotalQuestsAttempted(),
            stats.getTotalQuestsCompleted(),
            stats.getTotalPointsEarned(),
            mobRecords
        );

        sendPacket(player, syncPayload);
    }

    // =================================================================================
    // BOSS ALERT
    // =================================================================================
    static void handleBossAlert(BossAlertPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleBossAlert(payload))), "boss alert");
        }
    }

    static void sendBossAlert(ServerPlayer player, long durationMs, String bossType) {
        BossAlertPayload payload = new BossAlertPayload(durationMs, bossType);
        sendPacket(player, payload);
    }

    // =================================================================================
    // TENSION SYSTEM UPDATE
    // =================================================================================
    static void handleTensionUpdate(TensionUpdatePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleTensionUpdate(payload))), "tension update");
        }
    }

    static void sendTensionUpdate(ServerPlayer player, float tensionPercent, int tensionLevel, boolean bossImminent) {
        TensionUpdatePayload payload = new TensionUpdatePayload(tensionPercent, tensionLevel, bossImminent);
        sendPacket(player, payload);
    }

    // =================================================================================
    // PARTY STATS SYNC (client-side)
    // =================================================================================
    static void handlePartyStatsSync(PartyStatsSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                com.devmod.client.endurance.ClientPartyStatsCache.update(payload));
        }
    }

    // =================================================================================
    // NUTRITION SYNC (client-side, Easy-Diet integration)
    // =================================================================================
    static void handleNutritionSync(NutritionSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                com.devmod.client.endurance.ClientNutritionCache.update(payload));
        }
    }

    private static void enqueueWork(IPayloadContext context, Runnable work) {
        var future = context.enqueueWork(java.util.Objects.requireNonNull(work));
        if (future.isCancelled()) {
            LOGGER.debug("[EnduranceNetwork] Enqueued work cancelled");
        }
    }
}
