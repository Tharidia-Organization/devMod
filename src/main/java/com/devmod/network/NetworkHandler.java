package com.devmod.network;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.DevMod;
import com.devmod.abilities.StaminaSyncPayload;
import com.devmod.arena.network.BuildProgressPayload;
import com.devmod.clone.network.TelepadConfigPayload;
import com.devmod.clone.network.TelepadOpenScreenPayload;
import com.devmod.endurance.ArenaSuggestionsPayload;
import com.devmod.endurance.BossAlertPayload;
import com.devmod.endurance.CombatFlowSyncPayload;
import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceMobConfigSyncPayload;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.InstanceLoadingPayload;
import com.devmod.endurance.KitSyncConfirmPayload;
import com.devmod.endurance.KitSyncPayload;
import com.devmod.endurance.MobPoolConfigSyncPayload;
import com.devmod.endurance.PerkChoicesPayload;
import com.devmod.endurance.PerkSelectionPayload;
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
import com.devmod.endurance.challenges.ChallengeSyncPayload;
import com.devmod.endurance.contracts.ContractSyncPayload;
import com.devmod.hologram.network.DeleteHologramPayload;
import com.devmod.hologram.network.HologramConfigPayload;
import com.devmod.hologram.network.HologramOpenScreenPayload;
import com.devmod.hologram.network.HologramSyncPayload;
import com.devmod.hologram.network.OpenHologramEditorPayload;
import com.devmod.hologram.network.SaveHologramPayload;
import com.devmod.mailbox.network.payload.TicketActionPayload;
import com.devmod.mailbox.network.payload.TicketCreatePayload;
import com.devmod.mailbox.network.payload.TicketSyncPayload;
import com.devmod.mailbox.network.payload.TicketSyncRequestPayload;
import com.devmod.network.handlers.AbilityNetworkHandler;
import com.devmod.network.handlers.ConfigNetworkHandler;
import com.devmod.network.handlers.EnduranceNetworkHandler;
import com.devmod.network.handlers.MobItemNetworkHandler;
import com.devmod.network.handlers.PartyNetworkHandler;
import com.devmod.network.handlers.ShieldNetworkHandler;
import com.devmod.notification.network.NotificationNetworkHandler;
import com.devmod.notification.network.NotificationPreferencesSyncPayload;
import com.devmod.notification.network.NotificationPreferencesUpdatePayload;
import com.devmod.party.ArrivalConfirmPayload;
import com.devmod.party.CancelSequencePayload;
import com.devmod.party.InviteResponsePayload;
import com.devmod.party.NamedInvitePayload;
import com.devmod.party.PartyActionPayload;
import com.devmod.party.PartySyncPayload;
import com.devmod.party.QuestSequencePayload;
import com.devmod.portal.network.PortalPreviewPayload;
import com.devmod.portal.network.PortalPreviewRequestPayload;
import com.devmod.portal.network.PortalStatePayload;
import com.devmod.runtime.environment.EnvironmentSyncPayload;
import com.devmod.telemetry.duckdb.packets.TelemetryBatchPayload;
import com.devmod.telemetry.network.LVCSyncPayload;

import static com.devmod.DevMod.MODID;
import static com.devmod.network.ChannelId.ARENA_SUGGESTIONS;
import static com.devmod.network.ChannelId.ARMOR_STATS;
import static com.devmod.network.ChannelId.ARRIVAL_CONFIRM;
import static com.devmod.network.ChannelId.BOSS_ALERT;
import static com.devmod.network.ChannelId.BUILD_PROGRESS;
import static com.devmod.network.ChannelId.CANCEL_SEQUENCE;
import static com.devmod.network.ChannelId.CHALLENGE_SYNC;
import static com.devmod.network.ChannelId.COMBAT_FLOW_SYNC;
import static com.devmod.network.ChannelId.CONTRACT_SYNC;
import static com.devmod.network.ChannelId.EDITOR_APPLY_CONFIRM;
import static com.devmod.network.ChannelId.ENDURANCE_MOB_CONFIG_SYNC;
import static com.devmod.network.ChannelId.ENVIRONMENT_SYNC;
import static com.devmod.network.ChannelId.EQUIP_MOB;
import static com.devmod.network.ChannelId.FOOD_STATS;
import static com.devmod.network.ChannelId.FUEL_STATS;
import static com.devmod.network.ChannelId.GAME_MECHANICS_SYNC;
import static com.devmod.network.ChannelId.GLOBAL_CONFIG_SYNC;
import static com.devmod.network.ChannelId.HOLOGRAM_CONFIG;
import static com.devmod.network.ChannelId.HOLOGRAM_DELETE;
import static com.devmod.network.ChannelId.HOLOGRAM_EDITOR_OPEN;
import static com.devmod.network.ChannelId.HOLOGRAM_OPEN_SCREEN;
import static com.devmod.network.ChannelId.HOLOGRAM_SAVE;
import static com.devmod.network.ChannelId.HOLOGRAM_SYNC;
import static com.devmod.network.ChannelId.IMPACT_SYNC;
import static com.devmod.network.ChannelId.INSTANCE_LOADING;
import static com.devmod.network.ChannelId.INVITE_RESPONSE;
import static com.devmod.network.ChannelId.KIT_SYNC;
import static com.devmod.network.ChannelId.KIT_SYNC_CONFIRM;
import static com.devmod.network.ChannelId.LVC_SYNC;
import static com.devmod.network.ChannelId.MAILBOX_ACCESS;
import static com.devmod.network.ChannelId.MAILBOX_NOTIFY;
import static com.devmod.network.ChannelId.MAILBOX_READ;
import static com.devmod.network.ChannelId.MAILBOX_SEND;
import static com.devmod.network.ChannelId.MAILBOX_STATUS;
import static com.devmod.network.ChannelId.MAILBOX_SYNC;
import static com.devmod.network.ChannelId.MOB_CONFIG_CONFIRM;
import static com.devmod.network.ChannelId.MOB_POOL_CONFIG_SYNC;
import static com.devmod.network.ChannelId.MOB_STATS;
import static com.devmod.network.ChannelId.MODIFY_ITEM;
import static com.devmod.network.ChannelId.NAMED_INVITE;
import static com.devmod.network.ChannelId.NEWS_READ;
import static com.devmod.network.ChannelId.NEWS_SYNC;
import static com.devmod.network.ChannelId.NOTIFICATION_PREFS_SYNC;
import static com.devmod.network.ChannelId.NOTIFICATION_PREFS_UPDATE;
import static com.devmod.network.ChannelId.PARTY_ACTION;
import static com.devmod.network.ChannelId.PARTY_SYNC;
import static com.devmod.network.ChannelId.PERK_CHOICES;
import static com.devmod.network.ChannelId.PERK_SELECTION;
import static com.devmod.network.ChannelId.PERSONAL_RECORDS_SYNC;
import static com.devmod.network.ChannelId.PORTAL_PREVIEW;
import static com.devmod.network.ChannelId.PORTAL_PREVIEW_REQUEST;
import static com.devmod.network.ChannelId.PORTAL_STATE;
import static com.devmod.network.ChannelId.QUEST_ACTION;
import static com.devmod.network.ChannelId.QUEST_COMPLETION;
import static com.devmod.network.ChannelId.QUEST_DEATH;
import static com.devmod.network.ChannelId.QUEST_SEQUENCE;
import static com.devmod.network.ChannelId.QUEST_SYNC;
import static com.devmod.network.ChannelId.RANGED_WEAPON_STATS;
import static com.devmod.network.ChannelId.RECIPE_CLIENT_SYNC;
import static com.devmod.network.ChannelId.RECIPE_SYNC;
import static com.devmod.network.ChannelId.REQUEST_ARENA_SUGGESTIONS;
import static com.devmod.network.ChannelId.REQUEST_MOB_POOL_CONFIG;
import static com.devmod.network.ChannelId.REQUEST_PERSONAL_RECORDS;
import static com.devmod.network.ChannelId.REQUEST_SEASON_PASS;
import static com.devmod.network.ChannelId.REQUEST_SHOP_SYNC;
import static com.devmod.network.ChannelId.SEASON_PASS_SYNC;
import static com.devmod.network.ChannelId.SHIELD_IMPACT;
import static com.devmod.network.ChannelId.SHIELD_SHATTER;
import static com.devmod.network.ChannelId.SHIELD_STATE;
import static com.devmod.network.ChannelId.SHOP_PURCHASE;
import static com.devmod.network.ChannelId.SHOP_SYNC;
import static com.devmod.network.ChannelId.START_QUEST;
import static com.devmod.network.ChannelId.TASK_ACTION;
import static com.devmod.network.ChannelId.TASK_SYNC;
import static com.devmod.network.ChannelId.TELEMETRY_BATCH;
import static com.devmod.network.ChannelId.TELEPAD_CONFIG;
import static com.devmod.network.ChannelId.TELEPAD_OPEN_SCREEN;
import static com.devmod.network.ChannelId.TENSION_UPDATE;
import static com.devmod.network.ChannelId.TICKET_ACTION;
import static com.devmod.network.ChannelId.TICKET_CREATE;
import static com.devmod.network.ChannelId.TICKET_SYNC;
import static com.devmod.network.ChannelId.TICKET_SYNC_REQUEST;
import static com.devmod.network.ChannelId.UNIFIED_NOTIFICATION;
import static com.devmod.network.ChannelId.UPDATE_ARMOR;
import static com.devmod.network.ChannelId.USABLE_STATS;
import static com.devmod.network.ChannelId.WAVE_DIRECTIVE_CHOICES;
import static com.devmod.network.ChannelId.WAVE_DIRECTIVE_SELECTION;
import static com.devmod.network.ChannelId.WEAPON_LEGACY;
import static com.devmod.network.ChannelId.WEAPON_STATS_V2;
import static com.devmod.network.ChannelId.ZONE_DEBUG;
import static com.devmod.network.PayloadValidation.PayloadLimits;
import static com.devmod.network.PayloadValidation.validated;

@EventBusSubscriber(modid = MODID)
public class NetworkHandler {
    /**
     * Client-side payload handlers registered from client initialization.
     */
    public interface ClientPayloadHooks {
        void handleGlobalConfigSync(GlobalConfigSyncPayload payload);

        void handleRecipeClientSync(RecipeClientSyncPayload payload);

        void handleGameMechanicsSync(GameMechanicsSyncPayload payload);

        void handleEditorApplyConfirm(EditorApplyConfirmPayload payload);

        void handleConfigEditorApplyConfirm(EditorApplyConfirmPayload payload);

        void handleContractSync(ContractSyncPayload payload);

        void handleMobConfigConfirm(MobConfigConfirmPayload payload);

        void handleConfigMobConfigConfirm(MobConfigConfirmPayload payload);

        void handleMobPoolConfigSync(MobPoolConfigSyncPayload payload);

        void handleQuestSync(QuestSyncPayload payload);

        void handleShopSync(ShopSyncPayload payload);

        void handleQuestDeath(QuestDeathPayload payload);

        void handlePerkChoices(PerkChoicesPayload payload);

        void handleWaveDirectiveChoices(WaveDirectiveChoicesPayload payload);

        void handleQuestCompletion(QuestCompletionPayload payload);

        void handleInstanceLoading(InstanceLoadingPayload payload);

        void handlePersonalRecordsSync(PersonalRecordsSyncPayload payload);

        void handlePartySync(PartySyncPayload payload);

        void handleQuestSequence(QuestSequencePayload payload);

        void handleKitSyncConfirm(KitSyncConfirmPayload payload);

        void handleShieldState(ShieldStatePayload payload);

        void handleShieldImpact(ShieldImpactPayload payload);

        void handleShieldShatter(ShieldShatterPayload payload);

        void handleBossAlert(BossAlertPayload payload);

        void handleTensionUpdate(TensionUpdatePayload payload);

        void handleCombatFlowSync(CombatFlowSyncPayload payload);

        void handleStaminaSync(StaminaSyncPayload payload);

        void handleBuildProgress(BuildProgressPayload payload);

        void handleChallengeSync(ChallengeSyncPayload payload);

        void handleLvcSync(LVCSyncPayload payload);

        // Mailbox system handlers
        void handleMailboxSync(com.devmod.mailbox.network.payload.MailboxSyncPayload payload);

        void handleMailboxNotify(com.devmod.mailbox.network.payload.MailboxNotifyPayload payload);

        void handleMailboxStatus(com.devmod.mailbox.network.payload.MailboxStatusPayload payload);

        void handleMailboxAccess(com.devmod.mailbox.network.payload.MailboxAccessPayload payload);

        void handleNewsSync(com.devmod.mailbox.network.payload.NewsSyncPayload payload);

        void handleTaskSync(com.devmod.mailbox.network.payload.TaskSyncPayload payload);

        void handleTicketSync(TicketSyncPayload payload);

        // Unified Notification Center handlers
        void handleUnifiedNotification(com.devmod.notification.network.UnifiedNotificationPayload payload);

        void handleNotificationPreferencesSync(NotificationPreferencesSyncPayload payload);

        void handleImpactSync(ImpactSyncPayload payload);

        void handleEnvironmentSync(EnvironmentSyncPayload payload);

        void handleZoneDebug(ZoneDebugPayload payload);

        void handleSeasonPassSync(com.devmod.endurance.season.SeasonPassPayload payload);

        void handlePortalState(PortalStatePayload payload);

        void handlePortalPreview(PortalPreviewPayload payload);

        void handleHologramOpenScreen(HologramOpenScreenPayload payload);

        void handleTelepadOpenScreen(TelepadOpenScreenPayload payload);
    }

    @Nullable
    private static volatile ClientPayloadHooks clientPayloadHooks;

    public static void setClientPayloadHooks(@Nonnull ClientPayloadHooks hooks) {
        clientPayloadHooks = Objects.requireNonNull(hooks, "hooks");
    }

    public static void withClientHooks(Consumer<ClientPayloadHooks> action) {
        ClientPayloadHooks hooks = clientPayloadHooks;
        if (hooks != null) {
            action.accept(hooks);
        }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // Validate channel IDs at registration time (fail-fast)
        ChannelId.validateNoCollisions();

        // Register debug payloads (separate handler for modularity)
        com.devmod.debug.DebugNetworkHandler.registerPayloads(event);

        // ===================================================================
        // MOB/ITEM CHANNELS (1-4) - see ChannelId enum
        // ===================================================================

        event.registrar(MOB_STATS.asString()).playToServer(
                nn(UpdateMobStatsPayload.TYPE),
                nn(UpdateMobStatsPayload.STREAM_CODEC),
                validated(MobItemNetworkHandler::handleMobData, PayloadLimits.EDITOR)
        );
        event.registrar(WEAPON_LEGACY.asString()).playToServer(
                nn(UpdateWeaponPayload.TYPE),
                nn(UpdateWeaponPayload.STREAM_CODEC),
                validated(MobItemNetworkHandler::handleWeaponData, PayloadLimits.EDITOR)
        );
        event.registrar(EQUIP_MOB.asString()).playToServer(
                nn(EquipMobPayload.TYPE),
                nn(EquipMobPayload.STREAM_CODEC),
                validated(MobItemNetworkHandler::handleEquipData, PayloadLimits.EDITOR)
        );
        event.registrar(MODIFY_ITEM.asString()).playToServer(
                nn(ModifyItemPayload.TYPE),
                nn(ModifyItemPayload.STREAM_CODEC),
                validated(MobItemNetworkHandler::handleItemModification, PayloadLimits.EDITOR)
        );
        // WEAPON_STATS_NBT removed - uses same payload type as WEAPON_STATS_V2
        // Use WEAPON_STATS_V2 channel for all weapon stats communication

        // ===================================================================
        // CONFIG/TELEMETRY CHANNELS (36-45) - see ChannelId enum
        // ===================================================================

        event.registrar(UPDATE_ARMOR.asString()).playToServer(
                nn(UpdateArmorPayload.TYPE),
                nn(UpdateArmorPayload.STREAM_CODEC),
                validated(MobItemNetworkHandler::handleArmorData, PayloadLimits.EDITOR)
        );
        event.registrar(RANGED_WEAPON_STATS.asString()).playToServer(
                nn(RangedWeaponStatsPayload.TYPE),
                nn(RangedWeaponStatsPayload.STREAM_CODEC),
                validated(MobItemNetworkHandler::handleRangedWeaponData, PayloadLimits.EDITOR)
        );
        event.registrar(ARMOR_STATS.asString()).playToServer(
                nn(ArmorStatsPayload.TYPE),
                nn(ArmorStatsPayload.STREAM_CODEC),
                validated(MobItemNetworkHandler::handleArmorStatsDataV2, PayloadLimits.EDITOR)
        );
        event.registrar(GLOBAL_CONFIG_SYNC.asString()).playToClient(
                nn(GlobalConfigSyncPayload.TYPE),
                nn(GlobalConfigSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, payload::applyToClientConfigs);
                    }
                }, PayloadLimits.SYNC_LARGE)
        );
        event.registrar(RECIPE_SYNC.asString()).playToServer(
                nn(RecipeSyncPayload.TYPE),
                nn(RecipeSyncPayload.STREAM_CODEC),
                validated(ConfigNetworkHandler::handleRecipeSync, PayloadLimits.XLARGE)
        );
        event.registrar(RECIPE_CLIENT_SYNC.asString()).playToClient(
                nn(RecipeClientSyncPayload.TYPE),
                nn(RecipeClientSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () -> {
                            var operation = payload.operation();
                            var recipes = payload.recipes();
                            boolean firstSyncAll = true;
                            for (var recipe : recipes) {
                                switch (operation) {
                                    case ADD -> com.devmod.recipe.RecipeConfigManager.addRecipeClientOnly(recipe);
                                    case DELETE -> com.devmod.recipe.RecipeConfigManager.removeRecipeClientOnly(recipe.id());
                                    case SYNC_ALL -> {
                                        if (firstSyncAll) {
                                            com.devmod.recipe.RecipeConfigManager.clearClientRecipes();
                                            firstSyncAll = false;
                                        }
                                        com.devmod.recipe.RecipeConfigManager.addRecipeClientOnly(recipe);
                                    }
                                }
                            }
                        });
                    }
                }, PayloadLimits.SYNC_LARGE)
        );
        event.registrar(TELEMETRY_BATCH.asString()).playToServer(
                nn(TelemetryBatchPayload.TYPE),
                nn(TelemetryBatchPayload.STREAM_CODEC),
                validated(ConfigNetworkHandler::handleTelemetryBatch, PayloadLimits.TELEMETRY)
        );
        event.registrar(EDITOR_APPLY_CONFIRM.asString()).playToClient(
                nn(EditorApplyConfirmPayload.TYPE),
                nn(EditorApplyConfirmPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleEditorApplyConfirm(payload)));
                    }
                }, PayloadLimits.SMALL)
        );
        event.registrar(CONTRACT_SYNC.asString()).playToClient(
                nn(com.devmod.endurance.contracts.ContractSyncPayload.TYPE),
                nn(com.devmod.endurance.contracts.ContractSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleContractSync(payload)));
                    }
                }, PayloadLimits.SMALL)
        );
        event.registrar(GAME_MECHANICS_SYNC.asString()).playToClient(
                nn(GameMechanicsSyncPayload.TYPE),
                nn(GameMechanicsSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, payload::applyToClient);
                    }
                }, PayloadLimits.LARGE)
        );

        // ===================================================================
        // ITEM STATS CHANNELS (46-55) - see ChannelId enum
        // ===================================================================

        event.registrar(USABLE_STATS.asString()).playToServer(
                nn(UsableStatsPayload.TYPE),
                nn(UsableStatsPayload.STREAM_CODEC),
                validated(MobItemNetworkHandler::handleUsableStatsData, PayloadLimits.EDITOR)
        );
        event.registrar(FOOD_STATS.asString()).playToServer(
                nn(FoodStatsPayload.TYPE),
                nn(FoodStatsPayload.STREAM_CODEC),
                validated(MobItemNetworkHandler::handleFoodStatsData, PayloadLimits.EDITOR)
        );
        event.registrar(FUEL_STATS.asString()).playToServer(
                nn(FuelStatsPayload.TYPE),
                nn(FuelStatsPayload.STREAM_CODEC),
                validated(MobItemNetworkHandler::handleFuelStatsData, PayloadLimits.EDITOR)
        );
        event.registrar(WEAPON_STATS_V2.asString()).playToServer(
                nn(WeaponStatsPayload.TYPE),
                nn(WeaponStatsPayload.STREAM_CODEC),
                validated(MobItemNetworkHandler::handleWeaponStatsDataV2, PayloadLimits.EDITOR)
        );

        // ===================================================================
        // ENDURANCE QUEST CHANNELS (5-25) - see ChannelId enum
        // ===================================================================

        event.registrar(START_QUEST.asString()).playToServer(
                nn(StartQuestPayload.TYPE),
                nn(StartQuestPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleStartEnduranceQuest, PayloadLimits.SMALL)
        );
        event.registrar(KIT_SYNC.asString()).playToServer(
                nn(KitSyncPayload.TYPE),
                nn(KitSyncPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleKitSync, PayloadLimits.XLARGE)
        );
        event.registrar(KIT_SYNC_CONFIRM.asString()).playToClient(
                nn(KitSyncConfirmPayload.TYPE),
                nn(KitSyncConfirmPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleKitSyncConfirm(payload)));
                    }
                }, PayloadLimits.SMALL)
        );
        event.registrar(QUEST_ACTION.asString()).playToServer(
                nn(QuestActionPayload.TYPE),
                nn(QuestActionPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleQuestAction, PayloadLimits.QUEST_ACTION)
        );
        event.registrar(QUEST_SYNC.asString()).playToClient(
                nn(QuestSyncPayload.TYPE),
                nn(QuestSyncPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleQuestSync, PayloadLimits.LARGE)
        );
        event.registrar(SHOP_PURCHASE.asString()).playToServer(
                nn(ShopPurchasePayload.TYPE),
                nn(ShopPurchasePayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleShopPurchase, PayloadLimits.SMALL)
        );
        event.registrar(SHOP_SYNC.asString()).playToClient(
                nn(ShopSyncPayload.TYPE),
                nn(ShopSyncPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleShopSync, PayloadLimits.SYNC_MEDIUM)
        );
        event.registrar(REQUEST_SHOP_SYNC.asString()).playToServer(
                nn(RequestShopSyncPayload.TYPE),
                nn(RequestShopSyncPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleRequestShopSync, PayloadLimits.SMALL)
        );
        event.registrar(MOB_CONFIG_CONFIRM.asString()).playToClient(
                nn(MobConfigConfirmPayload.TYPE),
                nn(MobConfigConfirmPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleMobConfigConfirm(payload)));
                    }
                }, PayloadLimits.SMALL)
        );
        event.registrar(QUEST_DEATH.asString()).playToClient(
                nn(QuestDeathPayload.TYPE),
                nn(QuestDeathPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleQuestDeath, PayloadLimits.SMALL)
        );
        event.registrar(PERK_CHOICES.asString()).playToClient(
                nn(PerkChoicesPayload.TYPE),
                nn(PerkChoicesPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handlePerkChoices, PayloadLimits.SMALL)
        );
        event.registrar(PERK_SELECTION.asString()).playToServer(
                nn(PerkSelectionPayload.TYPE),
                nn(PerkSelectionPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handlePerkSelection, PayloadLimits.SMALL)
        );
        event.registrar(QUEST_COMPLETION.asString()).playToClient(
                nn(QuestCompletionPayload.TYPE),
                nn(QuestCompletionPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleQuestCompletion, PayloadLimits.MEDIUM)
        );
        event.registrar(PERSONAL_RECORDS_SYNC.asString()).playToClient(
                nn(PersonalRecordsSyncPayload.TYPE),
                nn(PersonalRecordsSyncPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handlePersonalRecordsSync, PayloadLimits.MEDIUM)
        );
        event.registrar(REQUEST_PERSONAL_RECORDS.asString()).playToServer(
                nn(RequestPersonalRecordsPayload.TYPE),
                nn(RequestPersonalRecordsPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleRequestPersonalRecords, PayloadLimits.SMALL)
        );
        event.registrar(BOSS_ALERT.asString()).playToClient(
                nn(BossAlertPayload.TYPE),
                nn(BossAlertPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleBossAlert(payload)));
                    }
                }, PayloadLimits.SMALL)
        );
        // REQUEST_ARENA_SUGGESTIONS (19): Client -> Server request for arena suggestions
        event.registrar(REQUEST_ARENA_SUGGESTIONS.asString()).playToServer(
                nn(RequestArenaSuggestionsPayload.TYPE),
                nn(RequestArenaSuggestionsPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleRequestArenaSuggestions, PayloadLimits.SMALL)
        );
        // ARENA_SUGGESTIONS (20): Server -> Client arena suggestions response
        event.registrar(ARENA_SUGGESTIONS.asString()).playToClient(
                nn(ArenaSuggestionsPayload.TYPE),
                nn(ArenaSuggestionsPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleArenaSuggestions, PayloadLimits.SYNC_MEDIUM)
        );
        event.registrar(TENSION_UPDATE.asString()).playToClient(
                nn(TensionUpdatePayload.TYPE),
                nn(TensionUpdatePayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleTensionUpdate(payload)));
                    }
                }, PayloadLimits.SMALL)
        );
        event.registrar(COMBAT_FLOW_SYNC.asString()).playToClient(
                nn(CombatFlowSyncPayload.TYPE),
                nn(CombatFlowSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleCombatFlowSync(payload)));
                    }
                }, PayloadLimits.SMALL)
        );
        event.registrar(INSTANCE_LOADING.asString()).playToClient(
                nn(InstanceLoadingPayload.TYPE),
                nn(InstanceLoadingPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleInstanceLoading, PayloadLimits.SMALL)
        );
        event.registrar(WAVE_DIRECTIVE_CHOICES.asString()).playToClient(
                nn(WaveDirectiveChoicesPayload.TYPE),
                nn(WaveDirectiveChoicesPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleWaveDirectiveChoices, PayloadLimits.SMALL)
        );
        event.registrar(WAVE_DIRECTIVE_SELECTION.asString()).playToServer(
                nn(WaveDirectiveSelectionPayload.TYPE),
                nn(WaveDirectiveSelectionPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleWaveDirectiveSelection, PayloadLimits.SMALL)
        );
        // ENDURANCE_MOB_CONFIG_SYNC (53): Client -> Server mob pool config changes
        event.registrar(ENDURANCE_MOB_CONFIG_SYNC.asString()).playToServer(
                nn(EnduranceMobConfigSyncPayload.TYPE),
                nn(EnduranceMobConfigSyncPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleMobConfigSync, PayloadLimits.MEDIUM)
        );
        // REQUEST_MOB_POOL_CONFIG (131): Client -> Server request for mob pool config
        event.registrar(REQUEST_MOB_POOL_CONFIG.asString()).playToServer(
                nn(RequestMobPoolConfigPayload.TYPE),
                nn(RequestMobPoolConfigPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleRequestMobPoolConfig, PayloadLimits.SMALL)
        );
        // MOB_POOL_CONFIG_SYNC (132): Server -> Client mob pool config response
        event.registrar(MOB_POOL_CONFIG_SYNC.asString()).playToClient(
                nn(MobPoolConfigSyncPayload.TYPE),
                nn(MobPoolConfigSyncPayload.STREAM_CODEC),
                validated(EnduranceNetworkHandler::handleMobPoolConfigSync, PayloadLimits.SYNC_MEDIUM)
        );

        // ===================================================================
        // PARTY SYSTEM CHANNELS (26-33) - see ChannelId enum
        // ===================================================================

        event.registrar(PARTY_ACTION.asString()).playToServer(
                nn(PartyActionPayload.TYPE),
                nn(PartyActionPayload.STREAM_CODEC),
                validated(PartyNetworkHandler::handlePartyAction, PayloadLimits.SMALL)
        );
        event.registrar(PARTY_SYNC.asString()).playToClient(
                nn(PartySyncPayload.TYPE),
                nn(PartySyncPayload.STREAM_CODEC),
                validated(PartyNetworkHandler::handlePartySync, PayloadLimits.SMALL)
        );
        event.registrar(QUEST_SEQUENCE.asString()).playToClient(
                nn(QuestSequencePayload.TYPE),
                nn(QuestSequencePayload.STREAM_CODEC),
                validated(PartyNetworkHandler::handleQuestSequence, PayloadLimits.SMALL)
        );
        event.registrar(NAMED_INVITE.asString()).playToServer(
                nn(NamedInvitePayload.TYPE),
                nn(NamedInvitePayload.STREAM_CODEC),
                validated(PartyNetworkHandler::handleNamedInvite, PayloadLimits.SMALL)
        );
        event.registrar(ARRIVAL_CONFIRM.asString()).playToServer(
                nn(ArrivalConfirmPayload.TYPE),
                nn(ArrivalConfirmPayload.STREAM_CODEC),
                validated(PartyNetworkHandler::handleArrivalConfirm, PayloadLimits.SMALL)
        );
        event.registrar(CANCEL_SEQUENCE.asString()).playToServer(
                nn(CancelSequencePayload.TYPE),
                nn(CancelSequencePayload.STREAM_CODEC),
                validated(PartyNetworkHandler::handleCancelSequence, PayloadLimits.SMALL)
        );
        event.registrar(INVITE_RESPONSE.asString()).playToServer(
                nn(InviteResponsePayload.TYPE),
                nn(InviteResponsePayload.STREAM_CODEC),
                validated(PartyNetworkHandler::handleInviteResponse, PayloadLimits.SMALL)
        );

        // ===================================================================
        // SHIELD VISUAL EFFECTS CHANNELS (56-58) - see ChannelId enum
        // ===================================================================

        event.registrar(SHIELD_STATE.asString()).playToClient(
                nn(ShieldStatePayload.TYPE),
                nn(ShieldStatePayload.STREAM_CODEC),
                validated(ShieldNetworkHandler::handleShieldState, PayloadLimits.SMALL)
        );
        event.registrar(SHIELD_IMPACT.asString()).playToClient(
                nn(ShieldImpactPayload.TYPE),
                nn(ShieldImpactPayload.STREAM_CODEC),
                validated(ShieldNetworkHandler::handleShieldImpact, PayloadLimits.SMALL)
        );
        event.registrar(SHIELD_SHATTER.asString()).playToClient(
                nn(ShieldShatterPayload.TYPE),
                nn(ShieldShatterPayload.STREAM_CODEC),
                validated(ShieldNetworkHandler::handleShieldShatter, PayloadLimits.SMALL)
        );
        event.registrar(IMPACT_SYNC.asString()).playToClient(
                nn(ImpactSyncPayload.TYPE),
                nn(ImpactSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleImpactSync(payload)));
                    }
                }, PayloadLimits.SMALL)
        );

        // ===================================================================
        // ABILITY SYSTEM CHANNELS (66-67) - P2: Delegated to domain registrar
        // ===================================================================
        AbilityNetworkHandler.INSTANCE.registerPayloads(event);

        event.registrar(LVC_SYNC.asString()).playToClient(
                nn(LVCSyncPayload.TYPE),
                nn(LVCSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleLvcSync(payload)));
                    }
                }, PayloadLimits.SMALL)
        );

        // ===================================================================
        // ARENA CHANNELS (76-85) - see ChannelId enum
        // ===================================================================

        event.registrar(BUILD_PROGRESS.asString()).playToClient(
                nn(BuildProgressPayload.TYPE),
                nn(BuildProgressPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleBuildProgress(payload)));
                    }
                }, PayloadLimits.SMALL)
        );
        event.registrar(ENVIRONMENT_SYNC.asString()).playToClient(
                nn(EnvironmentSyncPayload.TYPE),
                nn(EnvironmentSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleEnvironmentSync(payload)));
                    }
                }, PayloadLimits.SMALL)
        );
        event.registrar(ZONE_DEBUG.asString()).playToClient(
                nn(ZoneDebugPayload.TYPE),
                nn(ZoneDebugPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleZoneDebug(payload)));
                    }
                }, PayloadLimits.LARGE)
        );

        // ===================================================================
        // CHALLENGES CHANNELS (86-89) - see ChannelId enum
        // ===================================================================

        event.registrar(CHALLENGE_SYNC.asString()).playToClient(
                nn(com.devmod.endurance.challenges.ChallengeSyncPayload.TYPE),
                nn(com.devmod.endurance.challenges.ChallengeSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleChallengeSync(payload)));
                    }
                }, PayloadLimits.MEDIUM)
        );

        // ===================================================================
        // MAILBOX SYSTEM CHANNELS (100-115) - see ChannelId enum
        // ===================================================================

        event.registrar(MAILBOX_SYNC.asString()).playToClient(
                nn(com.devmod.mailbox.network.payload.MailboxSyncPayload.TYPE),
                nn(com.devmod.mailbox.network.payload.MailboxSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleMailboxSync(payload)));
                    }
                }, PayloadLimits.SYNC_LARGE)
        );
        // P0-002: Validated mailbox send with size and rate limits
        event.registrar(MAILBOX_SEND.asString()).playToServer(
                nn(com.devmod.mailbox.network.payload.MailboxSendPayload.TYPE),
                nn(com.devmod.mailbox.network.payload.MailboxSendPayload.STREAM_CODEC),
                validated(com.devmod.mailbox.network.MailboxNetworkHandler::handleSend, PayloadLimits.MAILBOX)
        );
        event.registrar(MAILBOX_READ.asString()).playToServer(
                nn(com.devmod.mailbox.network.payload.MailboxActionPayload.TYPE),
                nn(com.devmod.mailbox.network.payload.MailboxActionPayload.STREAM_CODEC),
                validated(com.devmod.mailbox.network.MailboxNetworkHandler::handleAction, PayloadLimits.SMALL)
        );
        event.registrar(MAILBOX_NOTIFY.asString()).playToClient(
                nn(com.devmod.mailbox.network.payload.MailboxNotifyPayload.TYPE),
                nn(com.devmod.mailbox.network.payload.MailboxNotifyPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleMailboxNotify(payload)));
                    }
                }, PayloadLimits.SMALL)
        );
        event.registrar(MAILBOX_STATUS.asString()).playToClient(
                nn(com.devmod.mailbox.network.payload.MailboxStatusPayload.TYPE),
                nn(com.devmod.mailbox.network.payload.MailboxStatusPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleMailboxStatus(payload)));
                    }
                }, PayloadLimits.SMALL)
        );
        event.registrar(MAILBOX_ACCESS.asString()).playToClient(
                nn(com.devmod.mailbox.network.payload.MailboxAccessPayload.TYPE),
                nn(com.devmod.mailbox.network.payload.MailboxAccessPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleMailboxAccess(payload)));
                    }
                }, PayloadLimits.SMALL)
        );
        event.registrar(NEWS_SYNC.asString()).playToClient(
                nn(com.devmod.mailbox.network.payload.NewsSyncPayload.TYPE),
                nn(com.devmod.mailbox.network.payload.NewsSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleNewsSync(payload)));
                    }
                }, PayloadLimits.SYNC_LARGE)
        );
        event.registrar(NEWS_READ.asString()).playToServer(
                nn(com.devmod.mailbox.network.payload.NewsReadPayload.TYPE),
                nn(com.devmod.mailbox.network.payload.NewsReadPayload.STREAM_CODEC),
                validated(com.devmod.mailbox.network.MailboxNetworkHandler::handleNewsRead, PayloadLimits.SMALL)
        );
        event.registrar(TASK_SYNC.asString()).playToClient(
                nn(com.devmod.mailbox.network.payload.TaskSyncPayload.TYPE),
                nn(com.devmod.mailbox.network.payload.TaskSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleTaskSync(payload)));
                    }
                }, PayloadLimits.SYNC_MEDIUM)
        );
        event.registrar(TASK_ACTION.asString()).playToServer(
                nn(com.devmod.mailbox.network.payload.TaskActionPayload.TYPE),
                nn(com.devmod.mailbox.network.payload.TaskActionPayload.STREAM_CODEC),
                validated(com.devmod.mailbox.network.MailboxNetworkHandler::handleTaskAction, PayloadLimits.SMALL)
        );
        event.registrar(TICKET_SYNC.asString()).playToClient(
                nn(TicketSyncPayload.TYPE),
                nn(TicketSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleTicketSync(payload)));
                    }
                }, PayloadLimits.SYNC_MEDIUM)
        );
        // P0-002: Validated ticket create with size and rate limits
        event.registrar(TICKET_CREATE.asString()).playToServer(
                nn(TicketCreatePayload.TYPE),
                nn(TicketCreatePayload.STREAM_CODEC),
                validated(com.devmod.mailbox.network.TicketNetworkHandler::handleTicketCreate, PayloadLimits.TICKET)
        );
        // P0-002: Validated ticket action with size and rate limits
        event.registrar(TICKET_ACTION.asString()).playToServer(
                nn(TicketActionPayload.TYPE),
                nn(TicketActionPayload.STREAM_CODEC),
                validated(com.devmod.mailbox.network.TicketNetworkHandler::handleTicketAction, PayloadLimits.TICKET)
        );
        event.registrar(TICKET_SYNC_REQUEST.asString()).playToServer(
                nn(TicketSyncRequestPayload.TYPE),
                nn(TicketSyncRequestPayload.STREAM_CODEC),
                validated(com.devmod.mailbox.network.TicketNetworkHandler::handleTicketSyncRequest, PayloadLimits.SMALL)
        );

        // ===================================================================
        // UNIFIED NOTIFICATION CENTER CHANNELS (120-129)
        // ===================================================================
        event.registrar(UNIFIED_NOTIFICATION.asString()).playToClient(
                nn(com.devmod.notification.network.UnifiedNotificationPayload.TYPE),
                nn(com.devmod.notification.network.UnifiedNotificationPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleUnifiedNotification(payload)));
                    }
                }, PayloadLimits.MEDIUM)
        );
        event.registrar(NOTIFICATION_PREFS_SYNC.asString()).playToClient(
                nn(NotificationPreferencesSyncPayload.TYPE),
                nn(NotificationPreferencesSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleNotificationPreferencesSync(payload)));
                    }
                }, PayloadLimits.LARGE)
        );
        event.registrar(NOTIFICATION_PREFS_UPDATE.asString()).playToServer(
                nn(NotificationPreferencesUpdatePayload.TYPE),
                nn(NotificationPreferencesUpdatePayload.STREAM_CODEC),
                validated(NotificationNetworkHandler::handlePreferencesUpdate, PayloadLimits.SMALL)
        );

        // ===================================================================
        // SEASON PASS CHANNELS (123-124) - see ChannelId enum
        // ===================================================================
        event.registrar(SEASON_PASS_SYNC.asString()).playToClient(
                nn(com.devmod.endurance.season.SeasonPassPayload.TYPE),
                nn(com.devmod.endurance.season.SeasonPassPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleSeasonPassSync(payload)));
                    }
                }, PayloadLimits.MEDIUM)
        );
        event.registrar(REQUEST_SEASON_PASS.asString()).playToServer(
                nn(com.devmod.endurance.season.RequestSeasonPassPayload.TYPE),
                nn(com.devmod.endurance.season.RequestSeasonPassPayload.STREAM_CODEC),
                validated(NetworkHandler::handleRequestSeasonPass, PayloadLimits.SMALL)
        );

        // ===================================================================
        // NEXUS SYSTEM CHANNELS (140-149) - P2: Delegated to domain registrar
        // ===================================================================
        com.devmod.runtime.network.NexusNetworkHandler.INSTANCE.registerPayloads(event);

        // ===================================================================
        // PORTAL CHANNELS (150-159)
        // ===================================================================
        event.registrar(PORTAL_STATE.asString()).playToClient(
                nn(PortalStatePayload.TYPE),
                nn(PortalStatePayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handlePortalState(payload)));
                    }
                }, PayloadLimits.SMALL)
        );

        // Portal preview request (client -> server)
        event.registrar(PORTAL_PREVIEW_REQUEST.asString()).playToServer(
                nn(PortalPreviewRequestPayload.TYPE),
                nn(PortalPreviewRequestPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        handlePortalPreviewRequest(player, payload);
                    }
                }, PayloadLimits.SMALL)
        );

        // Portal preview response (server -> client)
        event.registrar(PORTAL_PREVIEW.asString()).playToClient(
                nn(PortalPreviewPayload.TYPE),
                nn(PortalPreviewPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handlePortalPreview(payload)));
                    }
                }, PayloadLimits.SMALL)
        );

        // ===================================================================
        // HOLOGRAM CHANNELS (160-169)
        // ===================================================================

        // Hologram config (client -> server)
        event.registrar(HOLOGRAM_CONFIG.asString()).playToServer(
                nn(HologramConfigPayload.TYPE),
                nn(HologramConfigPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        handleHologramConfig(player, payload);
                    }
                }, PayloadLimits.SMALL)
        );

        // Hologram open screen (server -> client) - 3D Projector
        event.registrar(HOLOGRAM_OPEN_SCREEN.asString()).playToClient(
                nn(HologramOpenScreenPayload.TYPE),
                nn(HologramOpenScreenPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleHologramOpenScreen(payload)));
                    }
                }, PayloadLimits.SMALL)
        );

        // Hologram Editor (TextDisplay Builder System) - Channels 162-165

        // Open hologram editor (server -> client)
        event.registrar(HOLOGRAM_EDITOR_OPEN.asString()).playToClient(
                nn(OpenHologramEditorPayload.TYPE),
                nn(OpenHologramEditorPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            com.devmod.client.hologram.HologramClientHandler.handleOpenEditor(payload));
                    }
                }, PayloadLimits.MEDIUM)
        );

        // Save hologram (client -> server)
        event.registrar(HOLOGRAM_SAVE.asString()).playToServer(
                nn(SaveHologramPayload.TYPE),
                nn(SaveHologramPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        handleHologramSave(player, payload);
                    }
                }, PayloadLimits.MEDIUM)
        );

        // Delete hologram (client -> server)
        event.registrar(HOLOGRAM_DELETE.asString()).playToServer(
                nn(DeleteHologramPayload.TYPE),
                nn(DeleteHologramPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        handleHologramDelete(player, payload);
                    }
                }, PayloadLimits.SMALL)
        );

        // Hologram sync (server -> client) - broadcast updates
        event.registrar(HOLOGRAM_SYNC.asString()).playToClient(
                nn(HologramSyncPayload.TYPE),
                nn(HologramSyncPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            com.devmod.client.hologram.HologramClientHandler.handleSync(payload));
                    }
                }, PayloadLimits.MEDIUM)
        );

        // ===================================================================
        // CLONE MODULE CHANNELS (170-179)
        // ===================================================================

        // Telepad config (client -> server)
        event.registrar(TELEPAD_CONFIG.asString()).playToServer(
                nn(TelepadConfigPayload.TYPE),
                nn(TelepadConfigPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        handleTelepadConfig(player, payload);
                    }
                }, PayloadLimits.SMALL)
        );

        // Telepad open screen (server -> client)
        event.registrar(TELEPAD_OPEN_SCREEN.asString()).playToClient(
                nn(TelepadOpenScreenPayload.TYPE),
                nn(TelepadOpenScreenPayload.STREAM_CODEC),
                validated((payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        enqueueWork(context, () ->
                            withClientHooks(hooks -> hooks.handleTelepadOpenScreen(payload)));
                    }
                }, PayloadLimits.SMALL)
        );

        // ===================================================================
        // NPC SYSTEM CHANNELS (180-189) - Delegated to domain registrar
        // ===================================================================
        com.devmod.npc.network.NpcNetworkHandler.INSTANCE.registerPayloads(event);

        // ===================================================================
        // AREA BUILDER CHANNELS (190-199) - Delegated to domain registrar
        // ===================================================================
        com.devmod.area.network.AreaNetworkHandler.INSTANCE.registerPayloads(event);

        // ===================================================================
        // ZONE MARKER CHANNELS (200-209) - Delegated to domain registrar
        // ===================================================================
        com.devmod.zone.network.ZoneNetworkHandler.INSTANCE.registerPayloads(event);

        // ===================================================================
        // UNIFIED TRANSPORT CHANNELS (210-220) - Delegated to domain registrar
        // ===================================================================
        com.devmod.transport.network.TransportNetworkHandler.INSTANCE.registerPayloads(event);

        // ===================================================================
        // ADMIN INSTANCE CHANNELS (230-239) - Delegated to domain registrar
        // ===================================================================
        com.devmod.runtime.network.AdminInstanceNetworkHandler.INSTANCE.registerPayloads(event);
    }

    // ===================================================================
    // PUBLIC API - Delegated to domain handlers
    // ===================================================================

    /**
     * Send shop/wallet sync data to a player.
     */
    public static void sendShopSync(ServerPlayer player) {
        EnduranceNetworkHandler.sendShopSync(player);
    }

    /**
     * Send quest death notification to player.
     */
    public static void sendQuestDeathScreen(ServerPlayer player, int currentWave, int totalWaves,
            boolean endlessMode, int pointsEarned, int deathsThisRun, int respawnCost) {
        EnduranceNetworkHandler.sendQuestDeathScreen(player, currentWave, totalWaves,
            endlessMode, pointsEarned, deathsThisRun, respawnCost);
    }

    /**
     * Send perk choices to player for selection.
     */
    public static void sendPerkChoices(ServerPlayer player, int waveNumber, java.util.List<PerkSystem.Perk> perks) {
        EnduranceNetworkHandler.sendPerkChoices(player, waveNumber, perks);
    }

    public static void sendWaveDirectiveChoices(ServerPlayer player, int waveNumber,
                                                java.util.List<WaveDirective> directives) {
        EnduranceNetworkHandler.sendWaveDirectiveChoices(player, waveNumber, directives);
    }

    /**
     * Send quest completion notification to player.
     */
    public static void sendQuestCompletionScreen(ServerPlayer player,
                                                 EnduranceQuestManager.ActiveQuestSession session,
                                                 RewardSystem.QuestRewards rewards,
                                                 ComboSystem.ComboSession comboSession,
                                                 int maxCombo) {
        EnduranceNetworkHandler.sendQuestCompletionScreen(player, session, rewards, comboSession, maxCombo);
    }

    /**
     * Send personal records to player.
     */
    public static void sendPersonalRecordsSync(ServerPlayer player) {
        EnduranceNetworkHandler.sendPersonalRecordsSync(player);
    }

    /**
     * Send boss alert to a player.
     */
    public static void sendBossAlert(ServerPlayer player, long durationMs, String bossType) {
        EnduranceNetworkHandler.sendBossAlert(player, durationMs, bossType);
    }

    /**
     * Send tension system update to a player for HUD display.
     */
    public static void sendTensionUpdate(ServerPlayer player, float tensionPercent, int tensionLevel, boolean bossImminent) {
        EnduranceNetworkHandler.sendTensionUpdate(player, tensionPercent, tensionLevel, bossImminent);
    }

    /**
     * Show loading overlay on client during instance creation.
     */
    public static void sendInstanceLoadingShow(ServerPlayer player, String status) {
        EnduranceNetworkHandler.sendInstanceLoadingShow(player, status);
    }

    /**
     * Hide loading overlay on client when instance is ready.
     */
    public static void sendInstanceLoadingHide(ServerPlayer player) {
        EnduranceNetworkHandler.sendInstanceLoadingHide(player);
    }

    /**
     * Send party sync to a specific player.
     */
    public static void sendPartySyncToPlayer(ServerPlayer player) {
        PartyNetworkHandler.sendPartySyncToPlayer(player);
    }

    /**
     * Sync party state to all members.
     */
    public static void syncPartyToAllMembers(MinecraftServer server, UUID partyId) {
        PartyNetworkHandler.syncPartyToAllMembers(server, partyId);
    }

    /**
     * Send stamina sync to a player.
     */
    public static void sendStaminaSync(ServerPlayer player, float currentStamina, float maxStamina) {
        AbilityNetworkHandler.sendStaminaSync(player, currentStamina, maxStamina);
    }

    /**
     * Send LVC (Last Value Cache) telemetry sync to a player.
     * Contains real-time combat stats for HUD display.
     */
    public static void sendLvcSync(ServerPlayer player, LVCSyncPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /**
     * Send environment sync to a player.
     * Used to sync frozen time and biome overrides for arena dimensions.
     */
    public static void sendEnvironmentSync(ServerPlayer player, EnvironmentSyncPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /**
     * Send zone debug data to a player.
     * Used to enable/disable zone boundary visualization on client.
     */
    public static void sendZoneDebug(ServerPlayer player, ZoneDebugPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /**
     * Send portal state sync to a player.
     * Used to sync portal teleportation overlay state.
     */
    public static void sendPortalState(ServerPlayer player, PortalStatePayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /**
     * Send portal preview to a player.
     * Used to show destination info when looking at a portal.
     */
    public static void sendPortalPreview(ServerPlayer player, PortalPreviewPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /**
     * Handle portal preview request from a client.
     * Looks up the portal data and sends back destination information.
     */
    private static void handlePortalPreviewRequest(ServerPlayer player, PortalPreviewRequestPayload request) {
        var level = Objects.requireNonNull(player.serverLevel(), "serverLevel");
        var registry = com.devmod.portal.PortalRegistry.get(level);
        var blockState = level.getBlockState(request.portalPos());

        // Verify the block is actually a portal
        if (!(blockState.getBlock() instanceof com.devmod.portal.block.CustomPortalBlock)) {
            return;
        }

        var color = Objects.requireNonNull(
            blockState.getValue(Objects.requireNonNull(com.devmod.portal.block.CustomPortalBlock.COLOR)),
            "portal color"
        );

        // Find the portal data
        var portalOpt = registry.findPortalContaining(level, request.portalPos(), color);
        if (portalOpt.isEmpty()) {
            return;
        }

        var portal = portalOpt.get();

        // Build the preview response
        PortalPreviewPayload response;

        if (portal.hasFixedDestination()) {
            // Fixed destination (Nexus zone portal)
            String zoneName = Objects.requireNonNull(
                getFixedDestinationName(player.getServer(), portal), "zoneName");
            String dimName = Objects.requireNonNull(
                getDimensionDisplayName(portal.fixedDestinationDimension().orElse(null)), "dimName");
            response = PortalPreviewPayload.forFixedDestination(
                request.portalPos(), zoneName, dimName, color);
        } else if (portal.isLinked()) {
            // Linked portal
            var linkedOpt = portal.linkedPortalId().flatMap(registry::get);
            if (linkedOpt.isEmpty()) {
                return;
            }
            var linked = linkedOpt.get();
            String ownerName = Objects.requireNonNull(getLinkedPortalName(linked), "ownerName");
            String dimName = Objects.requireNonNull(
                getDimensionDisplayName(linked.dimension().orElse(null)), "dimName");
            int distance = calculateDistance(portal, linked);
            response = PortalPreviewPayload.forLinkedPortal(
                request.portalPos(), ownerName, dimName, distance, color);
        } else {
            // Unlinked portal - no preview
            return;
        }

        sendPortalPreview(player, response);
    }

    /**
     * Get display name for a fixed destination portal (Nexus zone).
     */
    private static String getFixedDestinationName(@Nullable MinecraftServer server,
                                                  com.devmod.portal.PortalData portal) {
        // Try to determine zone from destination position
        var destPos = portal.fixedDestination().orElse(null);
        if (server != null && destPos != null) {
            var hubOrigin = Objects.requireNonNull(
                com.devmod.runtime.NexusDimensionManager.getHubOrigin(), "hubOrigin");
            var zone = com.devmod.runtime.NexusPortalManager.INSTANCE.getZoneAtPosition(
                server, hubOrigin, destPos);
            if (zone != null) {
                return zone.displayName();
            }
        }
        return "Portal Destination";
    }

    /*
     * Get display name for a linked portal.
     */
    private static String getLinkedPortalName(com.devmod.portal.PortalData portal) {
        var creatorOpt = portal.creator();
        if (creatorOpt.isPresent()) {
            // Try to get player name - for now just show "Linked Portal"
            return "Linked Portal";
        }
        return "Linked Portal";
    }

    /*
     * Get display name for a dimension.
     */
    private static String getDimensionDisplayName(@Nullable net.minecraft.resources.ResourceLocation dim) {
        if (dim == null) {
            return "Unknown";
        }
        // Format dimension ID nicely
        String path = dim.getPath();
        if (path.equals("overworld")) return "Overworld";
        if (path.equals("the_nether")) return "Nether";
        if (path.equals("the_end")) return "The End";
        if (path.equals("nexus")) return "Nexus";
        // Capitalize first letter
        return path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1).replace("_", " ");
    }

    /*
     * Calculate distance between two portals.
     * Returns -1 if cross-dimension.
     */
    private static int calculateDistance(com.devmod.portal.PortalData from, com.devmod.portal.PortalData to) {
        Objects.requireNonNull(to, "to");
        if (from.isInterDimensional(to)) {
            return -1;
        }
        var fromPos = from.position().orElse(null);
        var toPos = to.position().orElse(null);
        if (fromPos == null || toPos == null) {
            return -1;
        }
        return (int) Math.sqrt(fromPos.distSqr(toPos));
    }

    /**
     * Send impact sync data to a player for HUD display.
     * Called from DamageHandler when a player deals damage.
     */
    public static void sendImpactSync(ServerPlayer player, ImpactSyncPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /**
     * Send season pass data to a player.
     */
    public static void sendSeasonPassSync(ServerPlayer player) {
        com.devmod.endurance.season.SeasonPassPayload payload =
            com.devmod.endurance.season.SeasonPassPayload.create(player.getUUID());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /*
     * Handle client request for season pass data.
     */
    private static void handleRequestSeasonPass(
            com.devmod.endurance.season.RequestSeasonPassPayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                sendSeasonPassSync(serverPlayer);
            }
        });
    }

    /**
     * Send hologram open screen payload to a player.
     */
    public static void sendHologramOpenScreen(ServerPlayer player, HologramOpenScreenPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /*
     * Handle hologram configuration from client.
     */
    private static void handleHologramConfig(ServerPlayer player, HologramConfigPayload payload) {
        var level = player.serverLevel();
        var pos = Objects.requireNonNull(payload.pos(), "pos");

        // Validate distance (max 8 blocks)
        if (player.blockPosition().distManhattan(pos) > 8) {
            return;
        }

        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof com.devmod.hologram.block.entity.HologramProjectorBlockEntity projector) {
            // Apply settings
            if (projector.getScanSize() != payload.scanSize()) {
                projector.setScanSize(payload.scanSize());
            }
            if (projector.getBlockSize() != payload.blockSize()) {
                projector.setBlockSize(payload.blockSize());
            }
            if (projector.isRotationEnabled() != payload.rotationEnabled()) {
                projector.toggleRotation();
            }
            if (projector.isTransparentMode() != payload.transparentMode()) {
                projector.toggleTransparency();
            }

            // Force rescan if requested
            if (payload.rescan()) {
                projector.setupScanRegion();
            }
        }
    }

    /**
     * Send telepad open screen payload to a player.
     */
    public static void sendTelepadOpenScreen(ServerPlayer player, TelepadOpenScreenPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /*
     * Handle hologram save from client.
     */
    private static void handleHologramSave(ServerPlayer player, SaveHologramPayload payload) {
        var server = player.getServer();
        if (server == null) return;

        // Permission check
        if (!player.hasPermissions(2)) {
            return;
        }

        var registry = com.devmod.hologram.data.HologramRegistry.get(server);
        var existingOpt = registry.getHologram(payload.hologramId());
        if (existingOpt.isEmpty()) {
            return;
        }

        var existing = existingOpt.get();

        // Optimistic locking check
        if (existing.revision() != payload.expectedRevision()) {
            player.displayClientMessage(
                Objects.requireNonNull(
                    net.minecraft.network.chat.Component.translatable("message.devmod.hologram.error.revision_conflict")),
                true
            );
            return;
        }

        // Update hologram using the withUpdate method
        var updated = Objects.requireNonNull(
            existing.withUpdate(payload.lines(), payload.style(), payload.options()), "updated");

        var hologramId = Objects.requireNonNull(existing.id(), "hologramId");
        registry.updateHologram(hologramId, updated, payload.expectedRevision());

        // Update entity: despawn and respawn
        var serverLevel = Objects.requireNonNull(player.serverLevel(), "serverLevel");
        com.devmod.hologram.runtime.HologramManager.INSTANCE.despawnHologram(serverLevel, hologramId);
        com.devmod.hologram.runtime.HologramManager.INSTANCE.spawnHologram(serverLevel, updated);

        // Feedback
        player.displayClientMessage(
            Objects.requireNonNull(
                net.minecraft.network.chat.Component.translatable("message.devmod.hologram.saved")),
            true
        );
    }

    /*
     * Handle hologram delete from client.
     */
    private static void handleHologramDelete(ServerPlayer player, DeleteHologramPayload payload) {
        var server = player.getServer();
        if (server == null) return;

        // Permission check
        if (!player.hasPermissions(2)) {
            return;
        }

        var registry = com.devmod.hologram.data.HologramRegistry.get(server);
        var existingOpt = registry.getHologram(payload.hologramId());
        if (existingOpt.isEmpty()) {
            return;
        }

        var existing = existingOpt.get();

        // Check if locked
        if (existing.options().locked()) {
            player.displayClientMessage(
                Objects.requireNonNull(
                    net.minecraft.network.chat.Component.translatable("message.devmod.hologram.error.locked")),
                true
            );
            return;
        }

        // Despawn entity
        var serverLevel = Objects.requireNonNull(player.serverLevel(), "serverLevel");
        com.devmod.hologram.runtime.HologramManager.INSTANCE.despawnHologram(serverLevel, payload.hologramId());

        // Remove from registry
        registry.deleteHologram(payload.hologramId());

        // Feedback
        player.displayClientMessage(
            Objects.requireNonNull(
                net.minecraft.network.chat.Component.translatable("message.devmod.hologram.removed")),
            true
        );
    }

    /*
     * Handle telepad configuration from client.
     */
    private static void handleTelepadConfig(ServerPlayer player, TelepadConfigPayload payload) {
        var level = player.serverLevel();
        var pos = Objects.requireNonNull(payload.pos(), "pos");

        // Validate distance (max 8 blocks)
        if (player.blockPosition().distManhattan(pos) > 8) {
            return;
        }

        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof com.devmod.clone.block.entity.TelepadBlockEntity telepad) {
            telepad.setTelepadName(payload.telepadName());
        }
    }

    private static void enqueueWork(IPayloadContext context, Runnable work) {
        context.enqueueWork(Objects.requireNonNull(work))
            .exceptionally(ex -> {
                DevMod.LOGGER.error("[NetworkHandler] Enqueued work failed", ex);
                return null;
            });
    }

    // ===================================================================
    // NULL-SAFETY HELPER
    // ===================================================================

    @Nonnull
    private static <T> T nn(T value) {
        return Objects.requireNonNull(value);
    }
}
