package com.frenkvs.devmod.network.handlers;

import com.frenkvs.devmod.endurance.*;
import com.frenkvs.devmod.hud.InstanceLoadingOverlay;
import com.frenkvs.devmod.network.PacketSecurityService;
import com.frenkvs.devmod.network.PacketSecurityService.ValidationResult;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Network handler for Endurance Quest system packets.
 * Extracted from NetworkHandler for single responsibility.
 */
public final class EnduranceNetworkHandler extends NetworkHandlerBase {

    private EnduranceNetworkHandler() {}

    // =================================================================================
    // START ENDURANCE QUEST
    // =================================================================================
    public static void handleStartEnduranceQuest(StartQuestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketSecurityService security = security();
                ValidationResult validation = security.validatePacket(player, "endurance_quest", true);
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

                try {
                    ResourceLocation mobLocation = ResourceLocation.parse(mobId);

                    EnduranceQuestManager.QuestSettings settings = new EnduranceQuestManager.QuestSettings();
                    settings.totalWaves = waves;
                    settings.endlessMode = payload.endlessMode();
                    settings.arenaSize = arenaSize;

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
    // QUEST ACTIONS (respawn, checkpoint, abandon)
    // =================================================================================
    public static void handleQuestAction(QuestActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
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
                                EnduranceQuestManager.INSTANCE.abandonQuest(player);
                                LOGGER.info("[EnduranceQuest] Player {} abandoned quest",
                                    player.getName().getString());
                            }
                        }
                        case CONTINUE_TO_NEXT_WAVE -> {
                            EnduranceQuestManager.INSTANCE.continueToNextWave(player);
                            LOGGER.info("[EnduranceQuest] Player {} continuing to next wave",
                                player.getName().getString());
                        }
                        case EXIT_AT_CHECKPOINT -> {
                            EnduranceQuestManager.INSTANCE.exitAtCheckpoint(player);
                            LOGGER.info("[EnduranceQuest] Player {} exited at checkpoint",
                                player.getName().getString());
                        }
                        case ABANDON_QUEST -> {
                            EnduranceQuestManager.INSTANCE.abandonQuest(player);
                            LOGGER.info("[EnduranceQuest] Player {} abandoned quest",
                                player.getName().getString());
                        }
                    }
                } catch (Exception e) {
                    player.sendSystemMessage(I18n.translate("devmod.network.quest_action_failed", e.getMessage()));
                    LOGGER.error("[EnduranceQuest] Quest action failed", e);
                }
            }
        });
    }

    // =================================================================================
    // QUEST SYNC (client-side)
    // =================================================================================
    public static void handleQuestSync(QuestSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientQuestCache.update(payload));
    }

    // =================================================================================
    // SHOP PURCHASE
    // =================================================================================
    public static void handleShopPurchase(ShopPurchasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                String itemId = payload.itemId();

                if (itemId == null || itemId.isEmpty()) {
                    player.sendSystemMessage(I18n.translate("devmod.network.invalid_item"));
                    return;
                }

                RewardSystem.PurchaseResult result = RewardSystem.INSTANCE.purchaseItem(player, itemId);

                if (!result.success()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", result.message()));
                }

                sendShopSync(player);

                LOGGER.info("[Shop] Player {} attempted purchase of {}: {}",
                    player.getName().getString(), itemId, result.success() ? "SUCCESS" : result.message());
            }
        });
    }

    // =================================================================================
    // SHOP SYNC (client-side)
    // =================================================================================
    public static void handleShopSync(ShopSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientShopCache.update(payload));
    }

    public static void sendShopSync(ServerPlayer player) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(player.getUUID());
        ShopSyncPayload payload = ShopSyncPayload.fromWallet(wallet);
        sendPacket(player, payload);
    }

    // =================================================================================
    // REQUEST SHOP SYNC (server-side)
    // =================================================================================
    public static void handleRequestShopSync(RequestShopSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                sendShopSync(player);
            }
        });
    }

    // =================================================================================
    // QUEST DEATH SCREEN (client-side)
    // =================================================================================
    public static void handleQuestDeath(QuestDeathPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> mc.setScreen(new QuestDeathScreen()));
            }
        });
    }

    public static void sendQuestDeathScreen(ServerPlayer player, int currentWave, int totalWaves,
            boolean endlessMode, int pointsEarned, int deathsThisRun, int respawnCost) {
        QuestDeathPayload payload = new QuestDeathPayload(
            currentWave, totalWaves, endlessMode, pointsEarned, deathsThisRun, respawnCost);
        sendPacket(player, payload);
    }

    // =================================================================================
    // PERK CHOICES (client-side)
    // =================================================================================
    public static void handlePerkChoices(PerkChoicesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> mc.setScreen(new PerkSelectionScreen(payload.waveNumber(), payload.choices())));
            }
        });
    }

    public static void sendPerkChoices(ServerPlayer player, int waveNumber, List<PerkSystem.Perk> perks) {
        List<PerkChoicesPayload.PerkChoice> choices = new ArrayList<>();
        var sessionOpt = PerkSystem.INSTANCE.getSession(player.getUUID());

        for (PerkSystem.Perk perk : perks) {
            int currentStacks = sessionOpt.map(s -> s.getPerkStacks(perk.id)).orElse(0);
            choices.add(PerkChoicesPayload.PerkChoice.from(perk, currentStacks));
        }

        PerkChoicesPayload payload = new PerkChoicesPayload(waveNumber, choices);
        sendPacket(player, payload);
    }

    // =================================================================================
    // PERK SELECTION (server-side)
    // =================================================================================
    public static void handlePerkSelection(PerkSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                String perkId = payload.perkId();

                if (payload.isSkip()) {
                    player.sendSystemMessage(nn(I18n.translate("devmod.network.perk_skipped")
                        .withStyle(net.minecraft.ChatFormatting.GRAY)));
                    LOGGER.info("[Perk] Player {} skipped perk selection", player.getName().getString());

                    PerkSystem.INSTANCE.getSession(player.getUUID())
                        .ifPresent(PerkSystem.PerkSession::clearPendingChoices);
                } else {
                    var sessionOpt = PerkSystem.INSTANCE.getSession(player.getUUID());
                    if (sessionOpt.isEmpty()) {
                        player.sendSystemMessage(I18n.translate("devmod.network.no_perk_session"));
                        return;
                    }

                    PerkSystem.PerkSession session = sessionOpt.get();
                    List<PerkSystem.Perk> pendingChoices = session.getPendingChoices();

                    int choiceIndex = -1;
                    for (int i = 0; i < pendingChoices.size(); i++) {
                        if (pendingChoices.get(i).id.equals(perkId)) {
                            choiceIndex = i;
                            break;
                        }
                    }

                    if (choiceIndex >= 0) {
                        boolean success = PerkSystem.INSTANCE.selectPerk(player, choiceIndex);
                        if (success) {
                            LOGGER.info("[Perk] Player {} selected perk: {}", player.getName().getString(), perkId);
                        } else {
                            player.sendSystemMessage(I18n.translate("devmod.network.perk_failed", perkId));
                            LOGGER.warn("[Perk] Failed to apply perk {} for player {}", perkId, player.getName().getString());
                        }
                    } else {
                        player.sendSystemMessage(I18n.translate("devmod.network.perk_invalid", perkId));
                        LOGGER.warn("[Perk] Perk {} not found in pending choices for player {}", perkId, player.getName().getString());
                    }
                }
            }
        });
    }

    // =================================================================================
    // QUEST COMPLETION (client-side)
    // =================================================================================
    public static void handleQuestCompletion(QuestCompletionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> mc.setScreen(new QuestCompletionScreen(payload)));
            }
        });
    }

    public static void sendQuestCompletionScreen(ServerPlayer player,
            EnduranceQuest quest,
            RewardSystem.QuestRewards rewards,
            ComboSystem.ComboSession comboSession,
            int maxCombo) {

        List<String> achievementNames = new ArrayList<>();
        if (rewards.achievementsUnlocked != null) {
            for (RewardSystem.Achievement achievement : rewards.achievementsUnlocked) {
                achievementNames.add(achievement.displayName);
            }
        }

        QuestCompletionPayload payload = new QuestCompletionPayload(
            quest.getDisplayName(),
            quest.getCurrentWave(),
            quest.getTotalWaves(),
            quest.isEndlessMode(),
            quest.getSessionDuration(),
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

        sendPacket(player, payload);
    }

    // =================================================================================
    // INSTANCE LOADING OVERLAY (client-side)
    // =================================================================================
    public static void handleInstanceLoading(InstanceLoadingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.show()) {
                String translatedStatus = I18n.translate(payload.status()).getString();
                InstanceLoadingOverlay.show(translatedStatus);
            } else {
                InstanceLoadingOverlay.hide();
            }
        });
    }

    public static void sendInstanceLoadingShow(ServerPlayer player, String status) {
        InstanceLoadingPayload payload = new InstanceLoadingPayload(true, status);
        sendPacket(player, payload);
    }

    public static void sendInstanceLoadingHide(ServerPlayer player) {
        InstanceLoadingPayload payload = InstanceLoadingPayload.hide();
        sendPacket(player, payload);
    }

    // =================================================================================
    // PERSONAL RECORDS (client-side)
    // =================================================================================
    public static void handlePersonalRecordsSync(PersonalRecordsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPersonalRecordsCache.update(payload));
    }

    public static void handleRequestPersonalRecords(RequestPersonalRecordsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                sendPersonalRecordsSync(player);
            }
        });
    }

    public static void sendPersonalRecordsSync(ServerPlayer player) {
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
    public static void sendBossAlert(ServerPlayer player, long durationMs, String bossType) {
        BossAlertPayload payload = new BossAlertPayload(durationMs, bossType);
        sendPacket(player, payload);
    }

    // =================================================================================
    // BADGE UNLOCK
    // =================================================================================
    public static void sendBadgeUnlock(ServerPlayer player, String badgeName, String rarity) {
        BadgeUnlockPayload payload = new BadgeUnlockPayload(badgeName, rarity);
        sendPacket(player, payload);
    }

    // =================================================================================
    // TOKEN GAIN ANIMATION
    // =================================================================================
    public static void sendTokenGain(ServerPlayer player, int amount) {
        if (amount > 0) {
            TokenGainPayload payload = new TokenGainPayload(amount);
            sendPacket(player, payload);
        }
    }

    // =================================================================================
    // RECORD BANNER
    // =================================================================================
    public static void sendRecordBanner(ServerPlayer player, String recordType, String recordValue) {
        RecordBannerPayload payload = new RecordBannerPayload(recordType, recordValue);
        sendPacket(player, payload);
    }

    // =================================================================================
    // COMBO DECAY FEEDBACK
    // =================================================================================
    public static void sendComboDecay(ServerPlayer player, int lostCombo, int previousRank, int newRank) {
        if (lostCombo >= 3 || newRank < previousRank) {
            ComboDecayPayload payload = new ComboDecayPayload(lostCombo, previousRank, newRank);
            sendPacket(player, payload);
        }
    }
}
