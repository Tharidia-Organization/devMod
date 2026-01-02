package com.devmod.network.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.endurance.BossAlertPayload;
import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceQuest;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.EnduranceQuestState;
import com.devmod.endurance.InstanceLoadingPayload;
import com.devmod.endurance.PerkChoicesPayload;
import com.devmod.endurance.PerkSelectionPayload;
import com.devmod.endurance.PerkSynergySystem;
import com.devmod.endurance.PerkSystem;
import com.devmod.endurance.PersonalRecordsSyncPayload;
import com.devmod.endurance.QuestActionPayload;
import com.devmod.endurance.QuestCompletionPayload;
import com.devmod.endurance.QuestDeathPayload;
import com.devmod.endurance.QuestSyncPayload;
import com.devmod.endurance.RequestPersonalRecordsPayload;
import com.devmod.endurance.RequestShopSyncPayload;
import com.devmod.endurance.RewardSystem;
import com.devmod.endurance.ShopPurchasePayload;
import com.devmod.endurance.ShopSyncPayload;
import com.devmod.endurance.StartQuestPayload;
import com.devmod.endurance.TensionUpdatePayload;
import com.devmod.endurance.WaveDirective;
import com.devmod.endurance.WaveDirectiveChoicesPayload;
import com.devmod.endurance.WaveDirectiveSelectionPayload;
import com.devmod.network.ChannelId;
import com.devmod.network.NetworkHandler;
import com.devmod.network.PacketValidator;
import com.devmod.network.PacketValidator.ValidationResult;
import com.devmod.network.PayloadValidation.PayloadLimits;
import com.devmod.util.I18n;

import static com.devmod.network.PayloadValidation.validated;

/**
 * P2: Domain-specific network handler for endurance quest system payloads.
 * Handles quest lifecycle, perks, shop, and wave directives.
 */
public final class EnduranceNetworkHandler extends NetworkHandlerBase implements PayloadRegistrar {

    public static final EnduranceNetworkHandler INSTANCE = new EnduranceNetworkHandler();

    private EnduranceNetworkHandler() {}

    private static final long PERK_SELECTION_TIMEOUT_MS = 20_000L;
    private static final long DIRECTIVE_SELECTION_TIMEOUT_MS = 15_000L;

    // =================================================================================
    // PAYLOAD REGISTRATION (P2: Domain-specific registration)
    // =================================================================================

    @Override
    public String getDomainName() {
        return "endurance";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // START_QUEST: Client -> Server quest start request
        event.registrar(ChannelId.START_QUEST.asString()).playToServer(
            nn(StartQuestPayload.TYPE),
            nn(StartQuestPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleStartEnduranceQuest, PayloadLimits.SMALL)
        );

        // QUEST_ACTION: Client -> Server quest actions (respawn, checkpoint, abandon)
        event.registrar(ChannelId.QUEST_ACTION.asString()).playToServer(
            nn(QuestActionPayload.TYPE),
            nn(QuestActionPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleQuestAction, PayloadLimits.QUEST_ACTION)
        );

        // QUEST_SYNC: Server -> Client quest state sync
        event.registrar(ChannelId.QUEST_SYNC.asString()).playToClient(
            nn(QuestSyncPayload.TYPE),
            nn(QuestSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleQuestSync, PayloadLimits.LARGE)
        );

        // SHOP_PURCHASE: Client -> Server shop purchase request
        event.registrar(ChannelId.SHOP_PURCHASE.asString()).playToServer(
            nn(ShopPurchasePayload.TYPE),
            nn(ShopPurchasePayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleShopPurchase, PayloadLimits.SMALL)
        );

        // SHOP_SYNC: Server -> Client shop state sync
        event.registrar(ChannelId.SHOP_SYNC.asString()).playToClient(
            nn(ShopSyncPayload.TYPE),
            nn(ShopSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleShopSync, PayloadLimits.SYNC_MEDIUM)
        );

        // REQUEST_SHOP_SYNC: Client -> Server request for shop sync
        event.registrar(ChannelId.REQUEST_SHOP_SYNC.asString()).playToServer(
            nn(RequestShopSyncPayload.TYPE),
            nn(RequestShopSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleRequestShopSync, PayloadLimits.SMALL)
        );

        // QUEST_DEATH: Server -> Client death screen
        event.registrar(ChannelId.QUEST_DEATH.asString()).playToClient(
            nn(QuestDeathPayload.TYPE),
            nn(QuestDeathPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleQuestDeath, PayloadLimits.SMALL)
        );

        // PERK_CHOICES: Server -> Client perk selection options
        event.registrar(ChannelId.PERK_CHOICES.asString()).playToClient(
            nn(PerkChoicesPayload.TYPE),
            nn(PerkChoicesPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handlePerkChoices, PayloadLimits.SMALL)
        );

        // PERK_SELECTION: Client -> Server perk selection
        event.registrar(ChannelId.PERK_SELECTION.asString()).playToServer(
            nn(PerkSelectionPayload.TYPE),
            nn(PerkSelectionPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handlePerkSelection, PayloadLimits.SMALL)
        );

        // QUEST_COMPLETION: Server -> Client completion screen
        event.registrar(ChannelId.QUEST_COMPLETION.asString()).playToClient(
            nn(QuestCompletionPayload.TYPE),
            nn(QuestCompletionPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleQuestCompletion, PayloadLimits.MEDIUM)
        );

        // PERSONAL_RECORDS_SYNC: Server -> Client personal records
        event.registrar(ChannelId.PERSONAL_RECORDS_SYNC.asString()).playToClient(
            nn(PersonalRecordsSyncPayload.TYPE),
            nn(PersonalRecordsSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handlePersonalRecordsSync, PayloadLimits.MEDIUM)
        );

        // REQUEST_PERSONAL_RECORDS: Client -> Server request for records
        event.registrar(ChannelId.REQUEST_PERSONAL_RECORDS.asString()).playToServer(
            nn(RequestPersonalRecordsPayload.TYPE),
            nn(RequestPersonalRecordsPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleRequestPersonalRecords, PayloadLimits.SMALL)
        );

        // BOSS_ALERT: Server -> Client boss wave alert
        event.registrar(ChannelId.BOSS_ALERT.asString()).playToClient(
            nn(BossAlertPayload.TYPE),
            nn(BossAlertPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleBossAlert, PayloadLimits.SMALL)
        );

        // TENSION_UPDATE: Server -> Client tension system update
        event.registrar(ChannelId.TENSION_UPDATE.asString()).playToClient(
            nn(TensionUpdatePayload.TYPE),
            nn(TensionUpdatePayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleTensionUpdate, PayloadLimits.SMALL)
        );

        // INSTANCE_LOADING: Server -> Client loading overlay
        event.registrar(ChannelId.INSTANCE_LOADING.asString()).playToClient(
            nn(InstanceLoadingPayload.TYPE),
            nn(InstanceLoadingPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleInstanceLoading, PayloadLimits.SMALL)
        );

        // WAVE_DIRECTIVE_CHOICES: Server -> Client wave directive options
        event.registrar(ChannelId.WAVE_DIRECTIVE_CHOICES.asString()).playToClient(
            nn(WaveDirectiveChoicesPayload.TYPE),
            nn(WaveDirectiveChoicesPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleWaveDirectiveChoices, PayloadLimits.SMALL)
        );

        // WAVE_DIRECTIVE_SELECTION: Client -> Server directive selection
        event.registrar(ChannelId.WAVE_DIRECTIVE_SELECTION.asString()).playToServer(
            nn(WaveDirectiveSelectionPayload.TYPE),
            nn(WaveDirectiveSelectionPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleWaveDirectiveSelection, PayloadLimits.SMALL)
        );
    }

    // =================================================================================
    // BOSS ALERT (client-side)
    // =================================================================================
    public static void handleBossAlert(BossAlertPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleBossAlert(payload))), "boss alert");
        }
    }

    // =================================================================================
    // TENSION UPDATE (client-side)
    // =================================================================================
    public static void handleTensionUpdate(TensionUpdatePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleTensionUpdate(payload))), "tension update");
        }
    }

    // =================================================================================
    // START ENDURANCE QUEST
    // =================================================================================
    public static void handleStartEnduranceQuest(StartQuestPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketValidator security = security();
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
                    settings.kitId = payload.kitId() != null ? payload.kitId() : "STARTER";

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
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return; // Fail closed: invalid context
            }

            // Rate limit check
            var validation = security().validatePacket(player, "quest_action", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("quest_action", player.getName().getString());
                return; // Fail closed: rate limited
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
        });
    }

    // =================================================================================
    // QUEST SYNC (client-side)
    // =================================================================================
    public static void handleQuestSync(QuestSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleQuestSync(payload)));
        }
    }

    // =================================================================================
    // SHOP PURCHASE
    // =================================================================================
    public static void handleShopPurchase(ShopPurchasePayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return; // Fail closed: invalid context
            }

            // Rate limit check
            var validation = security().validatePacket(player, "shop_purchase", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("shop_purchase", player.getName().getString());
                return; // Fail closed: rate limited
            }

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
        });
    }

    // =================================================================================
    // SHOP SYNC (client-side)
    // =================================================================================
    public static void handleShopSync(ShopSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleShopSync(payload)));
        }
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
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer player) {
                sendShopSync(player);
            }
        });
    }

    // =================================================================================
    // QUEST DEATH SCREEN (client-side)
    // =================================================================================
    public static void handleQuestDeath(QuestDeathPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleQuestDeath(payload)));
        }
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
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handlePerkChoices(payload)));
        }
    }

    public static void sendPerkChoices(ServerPlayer player, int waveNumber, List<PerkSystem.Perk> perks) {
        List<PerkChoicesPayload.PerkChoice> choices = new ArrayList<>();
        var sessionOpt = PerkSystem.INSTANCE.getSession(player.getUUID());

        // Get owned perks for synergy analysis
        Set<String> ownedPerks = sessionOpt
            .map(PerkSystem.PerkSession::getAcquiredPerkIds)
            .orElse(Set.of());

        for (PerkSystem.Perk perk : perks) {
            int currentStacks = sessionOpt.map(s -> s.getPerkStacks(perk.id)).orElse(0);
            boolean suggested = sessionOpt.map(s -> s.isSuggested(perk.id)).orElse(false);
            boolean required = sessionOpt.map(s -> s.isRequired(perk.id) && !s.hasPerk(perk.id)).orElse(false);

            // Analyze synergies for this perk
            PerkSynergySystem.SynergyPreview synergyPreview =
                PerkSynergySystem.INSTANCE.analyzePerk(perk.id, ownedPerks);

            choices.add(PerkChoicesPayload.PerkChoice.fromWithSynergy(
                perk, currentStacks, suggested, required, synergyPreview));
        }

        long expiresAt = System.currentTimeMillis() + PERK_SELECTION_TIMEOUT_MS;
        PerkChoicesPayload payload = new PerkChoicesPayload(waveNumber, expiresAt, choices);
        sendPacket(player, payload);
    }

    // =================================================================================
    // WAVE DIRECTIVES (risk/reward choices)
    // =================================================================================
    public static void handleWaveDirectiveChoices(WaveDirectiveChoicesPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleWaveDirectiveChoices(payload)));
        }
    }

    public static void handleWaveDirectiveSelection(WaveDirectiveSelectionPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return; // Fail closed: invalid context
            }

            // Rate limit check
            var validation = security().validatePacket(player, "wave_directive", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("wave_directive", player.getName().getString());
                return; // Fail closed: rate limited
            }

            var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
            if (sessionOpt.isEmpty()) {
                return;
            }
            EnduranceQuestManager.ActiveQuestSession session = sessionOpt.get();
            if (payload.waveNumber() != session.getDirectiveWaveNumber()) {
                return;
            }
            if (!payload.isSkip()) {
                session.selectDirective(payload.directiveId());
            }
        });
    }

    public static void sendWaveDirectiveChoices(ServerPlayer player, int waveNumber, List<WaveDirective> directives) {
        List<WaveDirectiveChoicesPayload.DirectiveChoice> choices = new ArrayList<>();
        for (WaveDirective directive : directives) {
            choices.add(WaveDirectiveChoicesPayload.DirectiveChoice.from(directive));
        }
        long expiresAt = System.currentTimeMillis() + DIRECTIVE_SELECTION_TIMEOUT_MS;
        sendPacket(player, new WaveDirectiveChoicesPayload(waveNumber, expiresAt, choices));
    }

    // =================================================================================
    // PERK SELECTION (server-side)
    // =================================================================================
    public static void handlePerkSelection(PerkSelectionPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return; // Fail closed: invalid context
            }

            // Rate limit check
            var validation = security().validatePacket(player, "perk_selection", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("perk_selection", player.getName().getString());
                return; // Fail closed: rate limited
            }

            String perkId = payload.perkId();

            if (payload.isSkip()) {
                var sessionOpt = PerkSystem.INSTANCE.getSession(player.getUUID());
                if (sessionOpt.isPresent() && sessionOpt.get().hasRequiredPending()) {
                    player.sendSystemMessage(nn(net.minecraft.network.chat.Component.literal(
                        "[DevMod] Required perk must be selected before skipping")
                        .withStyle(net.minecraft.ChatFormatting.RED)));
                    return;
                }
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
        });
    }

    // =================================================================================
    // QUEST COMPLETION (client-side)
    // =================================================================================
    public static void handleQuestCompletion(QuestCompletionPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleQuestCompletion(payload)));
        }
    }

    public static void sendQuestCompletionScreen(ServerPlayer player,
            EnduranceQuestManager.ActiveQuestSession session,
            RewardSystem.QuestRewards rewards,
            ComboSystem.ComboSession comboSession,
            int maxCombo) {
        EnduranceQuest quest = session.getQuest();

        List<String> achievementNames = new ArrayList<>();
        if (rewards.achievementsUnlocked != null) {
            for (RewardSystem.Achievement achievement : rewards.achievementsUnlocked) {
                achievementNames.add(achievement.displayName);
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

        sendPacket(player, payload);
    }

    // =================================================================================
    // INSTANCE LOADING OVERLAY (client-side)
    // =================================================================================
    public static void handleInstanceLoading(InstanceLoadingPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleInstanceLoading(payload)));
        }
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
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handlePersonalRecordsSync(payload)));
        }
    }

    public static void handleRequestPersonalRecords(RequestPersonalRecordsPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
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
    // TENSION SYSTEM UPDATE
    // =================================================================================
    public static void sendTensionUpdate(ServerPlayer player, float tensionPercent, int tensionLevel, boolean bossImminent) {
        TensionUpdatePayload payload = new TensionUpdatePayload(tensionPercent, tensionLevel, bossImminent);
        sendPacket(player, payload);
    }

    private static void enqueueWork(IPayloadContext context, Runnable work) {
        var future = context.enqueueWork(java.util.Objects.requireNonNull(work));
        if (future.isCancelled()) {
            LOGGER.debug("[EnduranceNetwork] Enqueued work cancelled");
        }
    }
}
