package com.devmod.network.handlers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.arena.policy.PolicyResolver;
import com.devmod.arena.policy.TemplateSuggestion;
import com.devmod.endurance.ArenaSuggestionsPayload;
import com.devmod.endurance.BossAlertPayload;
import com.devmod.endurance.CustomKit;
import com.devmod.endurance.EnduranceConfigSyncPayload;
import com.devmod.endurance.EnduranceQuest;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.EnduranceQuestState;
import com.devmod.endurance.InstanceLoadingPayload;
import com.devmod.endurance.KitManager;
import com.devmod.endurance.KitSyncConfirmPayload;
import com.devmod.endurance.KitSyncPayload;
import com.devmod.endurance.MobPoolConfigSyncPayload;
import com.devmod.endurance.PartyStatsSyncPayload;
import com.devmod.endurance.PerkChoicesPayload;
import com.devmod.endurance.PerkSelectionPayload;
import com.devmod.endurance.PerkSynergySystem;
import com.devmod.endurance.PerkSystem;
import com.devmod.endurance.PersonalRecordsSyncPayload;
import com.devmod.endurance.QuestActionPayload;
import com.devmod.endurance.QuestCompletionPayload;
import com.devmod.endurance.QuestDeathPayload;
import com.devmod.endurance.QuestSyncPayload;
import com.devmod.endurance.RequestArenaSuggestionsPayload;
import com.devmod.endurance.RequestMobPoolConfigPayload;
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
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.config.ConfigProposalManager;
import com.devmod.endurance.config.ConfigScope;
import com.devmod.endurance.config.EnduranceMobConfig;
import com.devmod.endurance.config.EnduranceMobPoolConfig;
import com.devmod.endurance.config.GlobalMobConfigStorage;
import com.devmod.endurance.nutrition.NutritionSyncPayload;
import com.devmod.mob.MobRequirements;
import com.devmod.mob.MobRequirementsRegistry;
import com.devmod.network.ChannelId;
import com.devmod.network.NetworkHandler;
import com.devmod.network.PacketValidator;
import com.devmod.network.PacketValidator.ValidationResult;
import com.devmod.network.PayloadValidation.PayloadLimits;
import com.devmod.party.PartyData;
import com.devmod.party.PartyManager;
import com.devmod.shared.SharedColorTokens;
import com.devmod.telemetry.TelemetryJson;
import com.devmod.telemetry.TelemetryService;
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
    private static final long ABANDON_CONFIRM_WINDOW_MS = 3_000L;

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

        // KIT_SYNC: Client -> Server kit sync for dedicated servers
        event.registrar(ChannelId.KIT_SYNC.asString()).playToServer(
            nn(KitSyncPayload.TYPE),
            nn(KitSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleKitSync, PayloadLimits.XLARGE)
        );
        event.registrar(ChannelId.KIT_SYNC_CONFIRM.asString()).playToClient(
            nn(KitSyncConfirmPayload.TYPE),
            nn(KitSyncConfirmPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleKitSyncConfirm, PayloadLimits.SMALL)
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

        // REQUEST_ARENA_SUGGESTIONS: Client -> Server request for arena suggestions
        event.registrar(ChannelId.REQUEST_ARENA_SUGGESTIONS.asString()).playToServer(
            nn(RequestArenaSuggestionsPayload.TYPE),
            nn(RequestArenaSuggestionsPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleRequestArenaSuggestions, PayloadLimits.SMALL)
        );

        // ARENA_SUGGESTIONS: Server -> Client arena suggestions response
        event.registrar(ChannelId.ARENA_SUGGESTIONS.asString()).playToClient(
            nn(ArenaSuggestionsPayload.TYPE),
            nn(ArenaSuggestionsPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleArenaSuggestions, PayloadLimits.SYNC_MEDIUM)
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

        // ENDURANCE_CONFIG_SYNC: Client -> Server config changes (OP/session/proposal)
        event.registrar(ChannelId.ENDURANCE_CONFIG_SYNC.asString()).playToServer(
            nn(EnduranceConfigSyncPayload.TYPE),
            nn(EnduranceConfigSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleConfigSync, PayloadLimits.SMALL)
        );

        // ENDURANCE_MOB_CONFIG_SYNC: Client -> Server mob config changes (OP/session/proposal)
        event.registrar(ChannelId.ENDURANCE_MOB_CONFIG_SYNC.asString()).playToServer(
            nn(com.devmod.endurance.EnduranceMobConfigSyncPayload.TYPE),
            nn(com.devmod.endurance.EnduranceMobConfigSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleMobConfigSync, PayloadLimits.LARGE)
        );

        // REQUEST_MOB_POOL_CONFIG: Client -> Server request for mob pool config
        event.registrar(ChannelId.REQUEST_MOB_POOL_CONFIG.asString()).playToServer(
            nn(RequestMobPoolConfigPayload.TYPE),
            nn(RequestMobPoolConfigPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleRequestMobPoolConfig, PayloadLimits.SMALL)
        );

        // MOB_POOL_CONFIG_SYNC: Server -> Client mob pool config response
        event.registrar(ChannelId.MOB_POOL_CONFIG_SYNC.asString()).playToClient(
            nn(MobPoolConfigSyncPayload.TYPE),
            nn(MobPoolConfigSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleMobPoolConfigSync, PayloadLimits.SYNC_MEDIUM)
        );

        // PARTY_STATS_SYNC: Server -> Client party wave stats (for party debrief tab)
        event.registrar(ChannelId.PARTY_STATS_SYNC.asString()).playToClient(
            nn(PartyStatsSyncPayload.TYPE),
            nn(PartyStatsSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handlePartyStatsSync, PayloadLimits.MEDIUM)
        );

        // NUTRITION_SYNC: Server -> Client nutrition state (Easy-Diet integration)
        event.registrar(ChannelId.NUTRITION_SYNC.asString()).playToClient(
            nn(NutritionSyncPayload.TYPE),
            nn(NutritionSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleNutritionSync, PayloadLimits.SMALL)
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
                    settings.forceTemplateId = payload.forceTemplateId(); // User's template override
                    settings.practiceMode = payload.practiceMode(); // Training dummies mode

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
    // KIT SYNC (client-side kits for dedicated servers)
    // =================================================================================
    public static void handleKitSync(KitSyncPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer player) {
                long startNanos = System.nanoTime();
                int parseFailures = 0;
                int itemCount = 0;
                try {
                    PacketValidator security = security();
                    ValidationResult validation = security.validatePacket(player, "kit_sync", true);
                    if (!validation.isSuccess()) {
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                        sendPacket(player, KitSyncConfirmPayload.failure(
                            payload != null && payload.temporary(),
                            payload != null ? payload.kitId() : "",
                            validation.getErrorMessage()));
                        recordKitSyncTelemetry(player, payload, false, 0, 0,
                            validation.getErrorMessage() != null ? validation.getErrorMessage() : "validation failed", startNanos);
                        return;
                    }

                    if (payload.itemTags().size() > KitSyncPayload.MAX_ITEMS) {
                        String message = "Kit sync rejected (too many items)";
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", message));
                        sendPacket(player, KitSyncConfirmPayload.failure(
                            payload.temporary(),
                            payload.kitId(),
                            message));
                        recordKitSyncTelemetry(player, payload, false, 0, 0, message, startNanos);
                        return;
                    }

                    if (!payload.temporary()) {
                        String kitId = payload.kitId();
                        if (kitId == null || kitId.isBlank()) {
                            sendPacket(player, KitSyncConfirmPayload.failure(false, "", "Kit ID missing"));
                            recordKitSyncTelemetry(player, payload, false, 0, 0, "Kit ID missing", startNanos);
                            return;
                        }
                    }

                    var registryAccess = Objects.requireNonNull(player.registryAccess());
                    List<ItemStack> items = new ArrayList<>();
                    String stackError = null;
                    boolean normalizedStacks = false;
                    for (var tag : payload.itemTags()) {
                        if (tag == null) {
                            parseFailures++;
                            continue;
                        }
                        var parsed = ItemStack.parse(registryAccess, tag);
                        if (parsed.isPresent() && !parsed.get().isEmpty()) {
                            ItemStack parsedStack = parsed.get();
                            if (parsedStack.getCount() <= 0) {
                                parseFailures++;
                                continue;
                            }
                            int added = addNormalizedStack(items, parsedStack, KitSyncPayload.MAX_ITEMS);
                            if (added < 0) {
                                stackError = "Kit sync rejected (stack size exceeds max items)";
                                break;
                            }
                            if (parsedStack.getCount() > parsedStack.getMaxStackSize()) {
                                normalizedStacks = true;
                            }
                        } else {
                            parseFailures++;
                        }
                    }

                    itemCount = items.size();

                    if (stackError != null) {
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", stackError));
                        sendPacket(player, KitSyncConfirmPayload.failure(
                            payload.temporary(),
                            payload.kitId(),
                            stackError));
                        recordKitSyncTelemetry(player, payload, false, itemCount, parseFailures, stackError, startNanos);
                        return;
                    }

                    if (parseFailures > 0) {
                        String message = "Kit sync rejected (" + parseFailures + " invalid item"
                            + (parseFailures == 1 ? "" : "s") + "). Check that the server has the same mods/data packs.";
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", message));
                        sendPacket(player, KitSyncConfirmPayload.failure(
                            payload.temporary(),
                            payload.kitId(),
                            message));
                        recordKitSyncTelemetry(player, payload, false, itemCount, parseFailures, message, startNanos);
                        return;
                    }

                    if (items.isEmpty()) {
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", "Kit sync rejected (empty)"));
                        sendPacket(player, KitSyncConfirmPayload.failure(
                            payload.temporary(),
                            payload.kitId(),
                            "Kit sync rejected (empty)"));
                        recordKitSyncTelemetry(player, payload, false, 0, parseFailures, "Kit sync rejected (empty)", startNanos);
                        return;
                    }

                    if (payload.temporary()) {
                        KitManager.INSTANCE.setTemporaryKit(player.getUUID(), items, payload.name(), registryAccess);
                        LOGGER.debug("[EnduranceKit] Synced temporary kit '{}' for {} ({} items)",
                            payload.name(), player.getName().getString(), items.size());
                        sendPacket(player, KitSyncConfirmPayload.success(true, payload.kitId(),
                            normalizedStacks ? "Temporary kit synced (stack sizes normalized)" : "Temporary kit synced"));
                        recordKitSyncTelemetry(player, payload, true, itemCount, parseFailures, "", startNanos);
                        return;
                    }

                    String kitId = payload.kitId();
                    if (kitId == null || kitId.isBlank()) {
                        kitId = java.util.UUID.randomUUID().toString().substring(0, 8);
                    }
                    String kitName = payload.name() != null && !payload.name().isBlank()
                        ? payload.name()
                        : "Custom Kit";

                    List<CustomKit.KitItem> kitItems = new ArrayList<>();
                    for (ItemStack stack : items) {
                        if (stack != null && !stack.isEmpty()) {
                            kitItems.add(CustomKit.KitItem.fromItemStack(stack, registryAccess));
                        }
                    }
                    CustomKit kit = new CustomKit(kitId, kitName, payload.description(),
                        payload.color(), kitItems, System.currentTimeMillis(), System.currentTimeMillis());

                    KitManager.INSTANCE.registerSyncedCustomKit(player.getUUID(), kit);
                    LOGGER.debug("[EnduranceKit] Synced custom kit '{}' ({}) for {}",
                        kit.getName(), kit.getId(), player.getName().getString());
                    sendPacket(player, KitSyncConfirmPayload.success(false, kit.getId(),
                        normalizedStacks ? "Custom kit synced (stack sizes normalized)" : "Custom kit synced"));
                    recordKitSyncTelemetry(player, payload, true, itemCount, parseFailures, "", startNanos);
                } catch (Exception e) {
                    LOGGER.warn("[EnduranceKit] Failed to sync kit for {}", player.getName().getString(), e);
                    sendPacket(player, KitSyncConfirmPayload.failure(
                        payload != null && payload.temporary(),
                        payload != null ? payload.kitId() : "",
                        "Kit sync failed"));
                    recordKitSyncTelemetry(player, payload, false, itemCount, parseFailures, "Kit sync failed", startNanos);
                }
            }
        });
    }

    private static void recordKitSyncTelemetry(ServerPlayer player, KitSyncPayload payload, boolean success,
                                               int itemCount, int parseFailures, String error, long startNanos) {
        long durationMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
        String kitId = payload != null ? payload.kitId() : "";
        boolean temporary = payload != null && payload.temporary();
        int tagCount = payload != null && payload.itemTags() != null ? payload.itemTags().size() : 0;
        int estimatedSize = payload != null ? payload.estimatedSize() : -1;

        String playerName = player != null ? player.getName().getString() : "unknown";
        String line = "{\"ts\":\"" + Instant.now() + "\","
            + "\"type\":\"kit_sync\","
            + "\"player\":\"" + TelemetryJson.escape(playerName) + "\","
            + "\"playerId\":\"" + (player != null ? player.getUUID() : "") + "\","
            + "\"kitId\":\"" + TelemetryJson.escape(kitId) + "\","
            + "\"temporary\":" + temporary + ","
            + "\"itemCount\":" + itemCount + ","
            + "\"tagCount\":" + tagCount + ","
            + "\"parseFailures\":" + parseFailures + ","
            + "\"estimatedBytes\":" + estimatedSize + ","
            + "\"success\":" + success + ","
            + "\"serverMs\":" + durationMs + ","
            + "\"error\":\"" + TelemetryJson.escape(error) + "\"}";
        TelemetryService.INSTANCE.appendNetworkLine(line);
    }

    private static int addNormalizedStack(List<ItemStack> items, ItemStack stack, int maxItems) {
        int count = stack.getCount();
        if (count <= 0) {
            return 0;
        }
        int max = Math.max(1, stack.getMaxStackSize());
        int added = 0;
        int remaining = count;
        while (remaining > 0) {
            if (items.size() >= maxItems) {
                return -1;
            }
            int size = Math.min(max, remaining);
            ItemStack copy = stack.copy();
            copy.setCount(size);
            items.add(copy);
            remaining -= size;
            added++;
        }
        return added;
    }

    public static void handleKitSyncConfirm(KitSyncConfirmPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleKitSyncConfirm(payload)));
        }
    }

    // =================================================================================
    // ARENA SUGGESTIONS
    // =================================================================================

    /**
     * Handles client request for arena template suggestions.
     * Delegates to PolicyResolver for scoring logic.
     */
    public static void handleRequestArenaSuggestions(RequestArenaSuggestionsPayload payload, IPayloadContext context) {
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

                // Use PolicyResolver to calculate suggestions
                PolicyResolver resolver = EnduranceQuestManager.INSTANCE.getPolicyResolver();
                if (resolver == null) {
                    LOGGER.warn("[ArenaSuggestions] PolicyResolver not available");
                    return;
                }

                List<TemplateSuggestion> suggestions = resolver.getTemplateSuggestions(mobReqs);
                String selectedTemplateId = resolver.getAutoSelectedTemplateId(mobReqs);

                // Send response
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

    /**
     * Client-side handler for receiving arena suggestions.
     */
    public static void handleArenaSuggestions(ArenaSuggestionsPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        // Delegate to client cache
        com.devmod.client.endurance.ClientArenaSuggestionsCache.INSTANCE.receiveSuggestions(payload);
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
                            if (!confirmAbandon(player, session)) {
                                return;
                            }
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
    public static void handleQuestSync(QuestSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleQuestSync(payload)));
        }
    }

    // =================================================================================
    // PARTY STATS SYNC (client-side)
    // =================================================================================
    public static void handlePartyStatsSync(PartyStatsSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                com.devmod.client.endurance.ClientPartyStatsCache.update(payload));
        }
    }

    // =================================================================================
    // NUTRITION SYNC (client-side, Easy-Diet integration)
    // =================================================================================
    public static void handleNutritionSync(NutritionSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                com.devmod.client.endurance.ClientNutritionCache.update(payload));
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
            int currentStacks = sessionOpt.map(s -> s.getPerkStacks(perk.getId())).orElse(0);
            boolean suggested = sessionOpt.map(s -> s.isSuggested(perk.getId())).orElse(false);
            boolean required = sessionOpt.map(s -> s.isRequired(perk.getId()) && !s.hasPerk(perk.getId())).orElse(false);

            // Analyze synergies for this perk
            PerkSynergySystem.SynergyPreview synergyPreview =
                PerkSynergySystem.INSTANCE.analyzePerk(perk.getId(), ownedPerks);

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
                        .withStyle(SharedColorTokens.Chat.RED)));
                    return;
                }
                player.sendSystemMessage(nn(I18n.translate("devmod.network.perk_skipped")
                    .withStyle(SharedColorTokens.Chat.GRAY)));
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
                    if (pendingChoices.get(i).getId().equals(perkId)) {
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

    // =================================================================================
    // CONFIG SYNC (server-side)
    // =================================================================================

    /**
     * Handles config sync requests from clients.
     * Permission levels:
     * - GLOBAL: OP 2+ only, permanently modifies server config
     * - PROPOSAL: Testers can propose changes for admin approval
     * - SESSION: Quest host can override settings for current session only
     */
    public static void handleConfigSync(EnduranceConfigSyncPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "config_sync", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("config_sync", player.getName().getString());
                return;
            }

            ConfigScope scope = payload.scope();
            List<EnduranceConfigSyncPayload.ConfigEntry> entries = payload.entries();

            if (entries.isEmpty()) {
                return;
            }

            switch (scope) {
                case GLOBAL -> handleGlobalConfigChange(player, entries);
                case PROPOSAL -> handleConfigProposal(player, entries);
                case SESSION -> handleSessionConfigOverride(player, entries);
                default -> LOGGER.warn("[Config] Unknown config scope: {}", scope);
            }
        });
    }

    private static void handleGlobalConfigChange(ServerPlayer player, List<EnduranceConfigSyncPayload.ConfigEntry> entries) {
        // Only OP level 2+ can modify global config
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(I18n.error("devmod.config.no_permission"));
            LOGGER.warn("[Config] Player {} attempted global config change without permission",
                player.getName().getString());
            return;
        }

        int applied = 0;
        for (var entry : entries) {
            if (applyConfigValue(entry.key(), entry.valueType(), entry.value())) {
                applied++;
            }
        }

        // Force config save
        saveGameMechanicsConfig();

        player.sendSystemMessage(I18n.translate("devmod.config.applied_global", applied));
        LOGGER.info("[Config] {} applied {} global settings", player.getName().getString(), applied);
    }

    private static void handleConfigProposal(ServerPlayer player, List<EnduranceConfigSyncPayload.ConfigEntry> entries) {
        // Testers can propose changes - save to pending queue for admin approval
        var proposalId = com.devmod.endurance.config.ConfigProposalManager.INSTANCE.submitProposal(player, entries, null);

        if (proposalId != null) {
            player.sendSystemMessage(I18n.translate("devmod.config.proposal_submitted", entries.size()));
            LOGGER.info("[Config] {} submitted proposal {} with {} entries",
                player.getName().getString(), proposalId, entries.size());
        } else {
            player.sendSystemMessage(I18n.error("devmod.config.proposal_limit_reached"));
            LOGGER.warn("[Config] {} proposal rejected - limit reached", player.getName().getString());
        }
    }

    private static void handleSessionConfigOverride(ServerPlayer player, List<EnduranceConfigSyncPayload.ConfigEntry> entries) {
        // Check if player is host of an active quest session
        var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
        if (sessionOpt.isEmpty()) {
            player.sendSystemMessage(I18n.error("devmod.config.no_active_session"));
            return;
        }

        var session = sessionOpt.get();
        if (!session.isHost(player.getUUID())) {
            player.sendSystemMessage(I18n.error("devmod.config.not_host"));
            return;
        }

        int applied = 0;
        for (var entry : entries) {
            session.setConfigOverride(entry.key(), entry.value());
            applied++;
        }

        player.sendSystemMessage(I18n.translate("devmod.config.applied_session", applied));
        LOGGER.info("[Config] {} applied {} session overrides", player.getName().getString(), applied);
    }

    // =================================================================================
    // MOB CONFIG SYNC (per-mob configuration for Endurance)
    // =================================================================================

    /**
     * Handle mob config sync from client.
     * Supports 3 scopes: GLOBAL (OP), PROPOSAL (tester), SESSION (host).
     */
    public static void handleMobConfigSync(com.devmod.endurance.EnduranceMobConfigSyncPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "mob_config_sync", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("mob_config_sync", player.getName().getString());
                return;
            }

            com.devmod.endurance.config.ConfigScope scope = payload.scope();

            switch (scope) {
                case GLOBAL -> handleGlobalMobConfigChange(player, payload);
                case PROPOSAL -> handleMobConfigProposal(player, payload);
                case SESSION -> handleSessionMobConfigOverride(player, payload);
                default -> LOGGER.warn("[MobConfig] Unknown config scope: {}", scope);
            }
        });
    }

    private static void handleGlobalMobConfigChange(ServerPlayer player, com.devmod.endurance.EnduranceMobConfigSyncPayload payload) {
        // Only OP level 2+ can modify global mob config
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(I18n.error("devmod.config.no_permission"));
            LOGGER.warn("[MobConfig] Player {} attempted global mob config change without permission",
                player.getName().getString());
            return;
        }

        // Convert payload to pool config and persist to disk
        var poolConfig = payload.toPoolConfig();
        GlobalMobConfigStorage.save(poolConfig);

        // Note: Config is loaded at server startup and on new session creation
        int mobCount = payload.mobEntries().size();
        player.sendSystemMessage(I18n.translate("devmod.config.mob_global_applied", mobCount));
        LOGGER.info("[MobConfig] {} applied global mob config ({} mobs)",
            player.getName().getString(), mobCount);
    }

    private static void handleMobConfigProposal(ServerPlayer player, com.devmod.endurance.EnduranceMobConfigSyncPayload payload) {
        // Submit proposal to ConfigProposalManager for admin review
        var proposalId = ConfigProposalManager.INSTANCE.submitMobConfigProposal(player, payload, null);

        if (proposalId != null) {
            int mobCount = payload.mobEntries().size();
            player.sendSystemMessage(I18n.translate("devmod.config.mob_proposal_submitted", mobCount));
            LOGGER.info("[MobConfig] {} submitted mob config proposal {} ({} mobs)",
                player.getName().getString(), proposalId, mobCount);
        } else {
            player.sendSystemMessage(I18n.error("devmod.config.proposal_limit_reached"));
            LOGGER.warn("[MobConfig] {} mob proposal rejected - limit reached", player.getName().getString());
        }
    }

    private static void handleSessionMobConfigOverride(ServerPlayer player, com.devmod.endurance.EnduranceMobConfigSyncPayload payload) {
        com.devmod.endurance.config.EnduranceMobPoolConfig poolConfig = payload.toPoolConfig();
        int mobCount = payload.mobEntries().size();

        // Check if player is host of an active quest session
        var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
        if (sessionOpt.isEmpty()) {
            PartyData party = PartyManager.INSTANCE.getPlayerParty(player.getUUID());
            if (party != null && party.isLeader(player.getUUID())) {
                if (party.getState() == PartyData.PartyState.IN_QUEST) {
                    EnduranceQuestManager.INSTANCE.getPartySession(party.getPartyId())
                        .ifPresentOrElse(partySession -> {
                            for (UUID memberId : partySession.getMembers()) {
                                EnduranceQuestManager.INSTANCE.getActiveSession(memberId)
                                    .ifPresent(memberSession -> memberSession.setMobPoolConfig(poolConfig.copy()));
                            }
                            player.sendSystemMessage(I18n.translate("devmod.config.mob_session_applied", mobCount));
                            LOGGER.info("[MobConfig] {} applied session mob config via party {} ({} mobs)",
                                player.getName().getString(), party.getPartyId(), mobCount);
                        }, () -> player.sendSystemMessage(I18n.error("devmod.config.no_active_session")));
                    return;
                }
                party.setMobPoolConfig(poolConfig);
                player.sendSystemMessage(I18n.translate("devmod.config.mob_session_staged", mobCount));
                LOGGER.info("[MobConfig] {} staged session mob config for party {} ({} mobs)",
                    player.getName().getString(), party.getPartyId(), mobCount);
                return;
            }
            player.sendSystemMessage(I18n.error("devmod.config.no_active_session"));
            return;
        }

        var session = sessionOpt.get();
        if (!session.isHost(player.getUUID())) {
            player.sendSystemMessage(I18n.error("devmod.config.not_host"));
            return;
        }

        if (session.getPartyId() != null) {
            EnduranceQuestManager.INSTANCE.getPartySession(session.getPartyId())
                .ifPresentOrElse(partySession -> {
                    for (UUID memberId : partySession.getMembers()) {
                        EnduranceQuestManager.INSTANCE.getActiveSession(memberId)
                            .ifPresent(memberSession -> memberSession.setMobPoolConfig(poolConfig.copy()));
                    }
                }, () -> session.setMobPoolConfig(poolConfig));
        } else {
            session.setMobPoolConfig(poolConfig);
        }

        var overrides = payload.globalOverrides();
        player.sendSystemMessage(I18n.translate("devmod.config.mob_session_applied", mobCount));
        LOGGER.info("[MobConfig] {} applied session mob config: {} mobs, HP={}x, DMG={}x",
            player.getName().getString(), mobCount,
            overrides.healthMult(), overrides.damageMult());
    }

    // =================================================================================
    // MOB POOL CONFIG REQUEST (server-side) + SYNC (client-side)
    // =================================================================================

    public static void handleRequestMobPoolConfig(RequestMobPoolConfigPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ConfigScope scope = payload.scope();
            EnduranceMobPoolConfig poolConfig = null;
            boolean hasConfig = false;

            switch (scope) {
                case SESSION -> {
                    var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
                    if (sessionOpt.isPresent()) {
                        var session = sessionOpt.get();
                        if (session.hasMobPoolConfig()) {
                            poolConfig = session.getMobPoolConfig();
                            hasConfig = true;
                        }
                    }
                    if (poolConfig == null) {
                        PartyData party = PartyManager.INSTANCE.getPlayerParty(player.getUUID());
                        if (party != null && party.hasMobPoolConfig()) {
                            poolConfig = party.getMobPoolConfig();
                            hasConfig = true;
                        }
                    }
                    if (poolConfig == null) {
                        poolConfig = GlobalMobConfigStorage.load().orElse(null);
                    }
                }
                case GLOBAL, PROPOSAL -> {
                    poolConfig = GlobalMobConfigStorage.load().orElse(null);
                    hasConfig = poolConfig != null && poolConfig.hasModifications();
                }
            }

            EnduranceMobPoolConfig normalized = normalizePoolConfig(poolConfig);
            var data = com.devmod.endurance.EnduranceMobConfigSyncPayload.fromPoolConfig(normalized, scope);
            MobPoolConfigSyncPayload response = new MobPoolConfigSyncPayload(hasConfig, data);
            sendPacket(player, response);
        });
    }

    public static void handleMobPoolConfigSync(MobPoolConfigSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleMobPoolConfigSync(payload)));
        }
    }

    private static EnduranceMobPoolConfig normalizePoolConfig(@javax.annotation.Nullable EnduranceMobPoolConfig poolConfig) {
        EnduranceMobPoolConfig normalized = poolConfig != null
            ? poolConfig.copy()
            : new EnduranceMobPoolConfig();
        Set<ResourceLocation> disabledMobs = new HashSet<>(normalized.getDisabledMobs());
        for (ResourceLocation mobId : disabledMobs) {
            if (normalized.getMobConfigOrNull(mobId) == null) {
                EnduranceMobConfig baseConfig = EnduranceMobConfig.fromRegistryOrDefault(mobId).withEnabled(false);
                normalized.setMobConfig(baseConfig);
            }
        }
        return normalized;
    }

    /**
     * Applies a config value to the global GameMechanicsConfig.
     * Key names must match the NeoForge config key names (e.g., "baseMobCount", "mobScaling").
     */
    @SuppressWarnings("UnusedVariable") // valueType reserved for future type validation
    private static boolean applyConfigValue(String key, String valueType, String value) {
        var cfg = com.devmod.config.GameMechanicsConfig.class;
        try {
            return switch (key) {
                // Wave Configuration
                case "baseMobCount" -> setInt(cfg, "WAVE_BASE_MOB_COUNT", value);
                case "mobScaling" -> setDouble(cfg, "WAVE_MOB_SCALING", value);
                case "intermissionTicks" -> setInt(cfg, "WAVE_INTERMISSION_TICKS", value);
                case "eliteChanceBase" -> setDouble(cfg, "WAVE_ELITE_CHANCE_BASE", value);
                case "eliteChanceScaling" -> setDouble(cfg, "WAVE_ELITE_CHANCE_SCALING", value);
                case "bossInterval" -> setInt(cfg, "WAVE_BOSS_INTERVAL", value);

                // Execution System
                case "hpThreshold" -> setDouble(cfg, "EXECUTION_HP_THRESHOLD", value);
                case "durationTicks" -> setInt(cfg, "EXECUTION_DURATION_TICKS", value);
                case "cooldownTicks" -> setInt(cfg, "EXECUTION_COOLDOWN_TICKS", value);
                case "styleReward" -> setInt(cfg, "EXECUTION_STYLE_REWARD", value);
                case "hpRegenPercent" -> setDouble(cfg, "EXECUTION_HP_REGEN_PERCENT", value);
                case "dropBoost" -> setDouble(cfg, "EXECUTION_DROP_BOOST", value);
                case "range" -> setDouble(cfg, "EXECUTION_RANGE", value);

                // Combo System
                case "timeoutTicks" -> setInt(cfg, "COMBO_TIMEOUT_TICKS", value);
                case "basePoints" -> setInt(cfg, "COMBO_BASE_POINTS", value);
                case "multiplierIncrement" -> setDouble(cfg, "COMBO_MULTIPLIER_INCREMENT", value);
                case "maxMultiplier" -> setDouble(cfg, "COMBO_MAX_MULTIPLIER", value);
                case "finisherThreshold" -> setInt(cfg, "COMBO_FINISHER_THRESHOLD", value);
                case "juggleBonus" -> setInt(cfg, "COMBO_JUGGLE_BONUS", value);
                case "headshotBonus" -> setInt(cfg, "COMBO_HEADSHOT_BONUS", value);
                case "executionBonus" -> setInt(cfg, "COMBO_EXECUTION_BONUS", value);

                // Style Rank System
                case "cThreshold" -> setInt(cfg, "STYLE_RANK_C_THRESHOLD", value);
                case "bThreshold" -> setInt(cfg, "STYLE_RANK_B_THRESHOLD", value);
                case "aThreshold" -> setInt(cfg, "STYLE_RANK_A_THRESHOLD", value);
                case "sThreshold" -> setInt(cfg, "STYLE_RANK_S_THRESHOLD", value);
                case "ssThreshold" -> setInt(cfg, "STYLE_RANK_SS_THRESHOLD", value);
                case "sssThreshold" -> setInt(cfg, "STYLE_RANK_SSS_THRESHOLD", value);
                case "decayRate" -> setDouble(cfg, "STYLE_DECAY_RATE", value);
                case "decayDelayTicks" -> setInt(cfg, "STYLE_DECAY_DELAY_TICKS", value);

                // Momentum System
                case "momentumDecayRate" -> setDouble(cfg, "MOMENTUM_DECAY_RATE", value);
                case "killBoost" -> setDouble(cfg, "MOMENTUM_KILL_BOOST", value);
                case "hitBoost" -> setDouble(cfg, "MOMENTUM_HIT_BOOST", value);
                case "overdriveThreshold" -> setDouble(cfg, "MOMENTUM_OVERDRIVE_THRESHOLD", value);
                case "overdriveDurationTicks" -> setInt(cfg, "MOMENTUM_OVERDRIVE_DURATION_TICKS", value);
                case "staleThresholdTicks" -> setInt(cfg, "FLOW_STALE_THRESHOLD_TICKS", value);
                case "freshBonusMultiplier" -> setDouble(cfg, "FLOW_FRESH_BONUS_MULTIPLIER", value);

                // Bargain System
                case "enabled" -> setBool(cfg, "BARGAIN_ENABLED", value);
                case "altarSpawnWave" -> setInt(cfg, "BARGAIN_ALTAR_SPAWN_WAVE", value);
                case "altarIntervalWaves" -> setInt(cfg, "BARGAIN_ALTAR_INTERVAL_WAVES", value);
                case "maxCursesPerRun" -> setInt(cfg, "BARGAIN_MAX_CURSES_PER_RUN", value);
                case "choiceTimeoutTicks" -> setInt(cfg, "BARGAIN_CHOICE_TIMEOUT_TICKS", value);
                case "cursePowerMultiplier" -> setDouble(cfg, "BARGAIN_CURSE_POWER_MULTIPLIER", value);
                case "boonPowerMultiplier" -> setDouble(cfg, "BARGAIN_BOON_POWER_MULTIPLIER", value);

                // Hazards System
                case "hazardEnabled" -> setBool(cfg, "HAZARD_ENABLED", value);
                case "floorCrumbleWave" -> setInt(cfg, "HAZARD_FLOOR_CRUMBLE_WAVE", value);
                case "bloodMoonWave" -> setInt(cfg, "HAZARD_BLOOD_MOON_WAVE", value);
                case "arenaShrinkWave" -> setInt(cfg, "HAZARD_ARENA_SHRINK_WAVE", value);
                case "lightningStormWave" -> setInt(cfg, "HAZARD_LIGHTNING_STORM_WAVE", value);
                case "voidRiftsWave" -> setInt(cfg, "HAZARD_VOID_RIFTS_WAVE", value);
                case "lightningDamage" -> setDouble(cfg, "HAZARD_LIGHTNING_DAMAGE", value);
                case "bloodMoonMobBuff" -> setDouble(cfg, "HAZARD_BLOOD_MOON_MOB_BUFF", value);

                // Rewards
                case "xpMultiplier" -> setDouble(cfg, "REWARD_XP_MULTIPLIER", value);
                case "styleMultiplier" -> setDouble(cfg, "REWARD_STYLE_MULTIPLIER", value);
                case "dropRateBonus" -> setDouble(cfg, "REWARD_DROP_RATE_BONUS", value);
                case "bonusChestWaveInterval" -> setInt(cfg, "REWARD_BONUS_CHEST_WAVE_INTERVAL", value);

                default -> {
                    LOGGER.debug("[Config] Unhandled config key (may be session-only): {}", key);
                    yield false;
                }
            };
        } catch (Exception e) {
            LOGGER.warn("[Config] Failed to apply value for {}: {} - {}", key, value, e.getMessage());
            return false;
        }
    }

    private static boolean setInt(Class<?> cfg, String fieldName, String value) throws Exception {
        var field = cfg.getField(fieldName);
        var configValue = (net.neoforged.neoforge.common.ModConfigSpec.IntValue) field.get(null);
        configValue.set(Integer.parseInt(value));
        return true;
    }

    private static boolean setDouble(Class<?> cfg, String fieldName, String value) throws Exception {
        var field = cfg.getField(fieldName);
        var configValue = (net.neoforged.neoforge.common.ModConfigSpec.DoubleValue) field.get(null);
        configValue.set(Double.parseDouble(value));
        return true;
    }

    private static boolean setBool(Class<?> cfg, String fieldName, String value) throws Exception {
        var field = cfg.getField(fieldName);
        var configValue = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) field.get(null);
        configValue.set(Boolean.parseBoolean(value));
        return true;
    }

    private static void saveGameMechanicsConfig() {
        try {
            // NeoForge configs auto-save on set(), but we can force it
            com.devmod.config.GameMechanicsConfig.SPEC.save();
        } catch (Exception e) {
            LOGGER.error("[Config] Failed to save config", e);
        }
    }

    private static void enqueueWork(IPayloadContext context, Runnable work) {
        var future = context.enqueueWork(java.util.Objects.requireNonNull(work));
        if (future.isCancelled()) {
            LOGGER.debug("[EnduranceNetwork] Enqueued work cancelled");
        }
    }
}
