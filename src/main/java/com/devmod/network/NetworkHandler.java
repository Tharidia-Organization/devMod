package com.devmod.network;

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

import com.devmod.abilities.AbilityActionPayload;
import com.devmod.abilities.StaminaSyncPayload;
import com.devmod.arena.network.BuildProgressPayload;
import com.devmod.endurance.BadgeUnlockPayload;
import com.devmod.endurance.BossAlertPayload;
import com.devmod.endurance.ComboDecayPayload;
import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.InstanceLoadingPayload;
import com.devmod.endurance.PerkChoicesPayload;
import com.devmod.endurance.PerkSelectionPayload;
import com.devmod.endurance.PerkSystem;
import com.devmod.endurance.PersonalRecordsSyncPayload;
import com.devmod.endurance.QuestActionPayload;
import com.devmod.endurance.QuestCompletionPayload;
import com.devmod.endurance.QuestDeathPayload;
import com.devmod.endurance.QuestSyncPayload;
import com.devmod.endurance.RecordBannerPayload;
import com.devmod.endurance.RequestPersonalRecordsPayload;
import com.devmod.endurance.RequestShopSyncPayload;
import com.devmod.endurance.RewardSystem;
import com.devmod.endurance.ShopPurchasePayload;
import com.devmod.endurance.ShopSyncPayload;
import com.devmod.endurance.StartQuestPayload;
import com.devmod.endurance.TensionUpdatePayload;
import com.devmod.endurance.TokenGainPayload;
import com.devmod.endurance.WaveDirective;
import com.devmod.endurance.WaveDirectiveChoicesPayload;
import com.devmod.endurance.WaveDirectiveSelectionPayload;
import com.devmod.endurance.challenges.ChallengeSyncPayload;
import com.devmod.endurance.contracts.ContractSyncPayload;
import com.devmod.endurance.resonance.ResonanceNotificationPayload;
import com.devmod.endurance.season.SeasonTierUpPayload;
import com.devmod.network.handlers.AbilityNetworkHandler;
import com.devmod.network.handlers.ConfigNetworkHandler;
import com.devmod.network.handlers.EnduranceNetworkHandler;
import com.devmod.network.handlers.MobItemNetworkHandler;
import com.devmod.network.handlers.PartyNetworkHandler;
import com.devmod.network.handlers.ShieldNetworkHandler;
import com.devmod.party.ArrivalConfirmPayload;
import com.devmod.party.CancelSequencePayload;
import com.devmod.party.InviteResponsePayload;
import com.devmod.party.NamedInvitePayload;
import com.devmod.party.PartyActionPayload;
import com.devmod.party.PartyNotificationPayload;
import com.devmod.party.PartySyncPayload;
import com.devmod.party.QuestSequencePayload;
import com.devmod.telemetry.duckdb.packets.TelemetryBatchPayload;
import com.devmod.telemetry.network.LVCSyncPayload;

import static com.devmod.DevMod.MODID;
import static com.devmod.network.ChannelId.ABILITY_ACTION;
import static com.devmod.network.ChannelId.ARMOR_STATS;
import static com.devmod.network.ChannelId.ARRIVAL_CONFIRM;
import static com.devmod.network.ChannelId.BADGE_UNLOCK;
import static com.devmod.network.ChannelId.BOSS_ALERT;
import static com.devmod.network.ChannelId.BUILD_PROGRESS;
import static com.devmod.network.ChannelId.CANCEL_SEQUENCE;
import static com.devmod.network.ChannelId.CHALLENGE_SYNC;
import static com.devmod.network.ChannelId.COMBO_DECAY;
import static com.devmod.network.ChannelId.CONTRACT_SYNC;
import static com.devmod.network.ChannelId.EDITOR_APPLY_CONFIRM;
import static com.devmod.network.ChannelId.EQUIP_MOB;
import static com.devmod.network.ChannelId.FOOD_STATS;
import static com.devmod.network.ChannelId.FUEL_STATS;
import static com.devmod.network.ChannelId.GAME_MECHANICS_SYNC;
import static com.devmod.network.ChannelId.GLOBAL_CONFIG_SYNC;
import static com.devmod.network.ChannelId.INSTANCE_LOADING;
import static com.devmod.network.ChannelId.INVITE_RESPONSE;
import static com.devmod.network.ChannelId.LVC_SYNC;
import static com.devmod.network.ChannelId.MOB_CONFIG_CONFIRM;
import static com.devmod.network.ChannelId.MOB_STATS;
import static com.devmod.network.ChannelId.MODIFY_ITEM;
import static com.devmod.network.ChannelId.NAMED_INVITE;
import static com.devmod.network.ChannelId.PARTY_ACTION;
import static com.devmod.network.ChannelId.PARTY_NOTIFICATION;
import static com.devmod.network.ChannelId.PARTY_SYNC;
import static com.devmod.network.ChannelId.PERK_CHOICES;
import static com.devmod.network.ChannelId.PERK_SELECTION;
import static com.devmod.network.ChannelId.PERSONAL_RECORDS_SYNC;
import static com.devmod.network.ChannelId.QUEST_ACTION;
import static com.devmod.network.ChannelId.QUEST_COMPLETION;
import static com.devmod.network.ChannelId.QUEST_DEATH;
import static com.devmod.network.ChannelId.QUEST_SEQUENCE;
import static com.devmod.network.ChannelId.QUEST_SYNC;
import static com.devmod.network.ChannelId.RANGED_WEAPON_STATS;
import static com.devmod.network.ChannelId.RECIPE_CLIENT_SYNC;
import static com.devmod.network.ChannelId.RECIPE_SYNC;
import static com.devmod.network.ChannelId.RECORD_BANNER;
import static com.devmod.network.ChannelId.REQUEST_PERSONAL_RECORDS;
import static com.devmod.network.ChannelId.REQUEST_SHOP_SYNC;
import static com.devmod.network.ChannelId.RESONANCE_NOTIFICATION;
import static com.devmod.network.ChannelId.SHIELD_IMPACT;
import static com.devmod.network.ChannelId.SHIELD_SHATTER;
import static com.devmod.network.ChannelId.SHIELD_STATE;
import static com.devmod.network.ChannelId.SHOP_PURCHASE;
import static com.devmod.network.ChannelId.SHOP_SYNC;
import static com.devmod.network.ChannelId.STAMINA_SYNC;
import static com.devmod.network.ChannelId.START_QUEST;
import static com.devmod.network.ChannelId.TELEMETRY_BATCH;
import static com.devmod.network.ChannelId.TENSION_UPDATE;
import static com.devmod.network.ChannelId.TOKEN_GAIN;
import static com.devmod.network.ChannelId.UPDATE_ARMOR;
import static com.devmod.network.ChannelId.USABLE_STATS;
import static com.devmod.network.ChannelId.WAVE_DIRECTIVE_CHOICES;
import static com.devmod.network.ChannelId.WAVE_DIRECTIVE_SELECTION;
import static com.devmod.network.ChannelId.WEAPON_LEGACY;
import static com.devmod.network.ChannelId.WEAPON_STATS_V2;
import static com.devmod.network.ChannelId.SEASON_TIER_UP;

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

        void handleResonanceTriggered(ResonanceNotificationPayload payload);

        void handleContractSync(ContractSyncPayload payload);

        void handleMobConfigConfirm(MobConfigConfirmPayload payload);

        void handleConfigMobConfigConfirm(MobConfigConfirmPayload payload);

        void handleQuestSync(QuestSyncPayload payload);

        void handleShopSync(ShopSyncPayload payload);

        void handleQuestDeath(QuestDeathPayload payload);

        void handlePerkChoices(PerkChoicesPayload payload);

        void handleWaveDirectiveChoices(WaveDirectiveChoicesPayload payload);

        void handleQuestCompletion(QuestCompletionPayload payload);

        void handleInstanceLoading(InstanceLoadingPayload payload);

        void handlePersonalRecordsSync(PersonalRecordsSyncPayload payload);

        void handlePartyNotification(PartyNotificationPayload payload);

        void handlePartySync(PartySyncPayload payload);

        void handleQuestSequence(QuestSequencePayload payload);

        void handleShieldState(ShieldStatePayload payload);

        void handleShieldImpact(ShieldImpactPayload payload);

        void handleShieldShatter(ShieldShatterPayload payload);

        void handleBossAlert(BossAlertPayload payload);

        void handleBadgeUnlock(BadgeUnlockPayload payload);

        void handleTokenGain(TokenGainPayload payload);

        void handleRecordBanner(RecordBannerPayload payload);

        void handleComboDecay(ComboDecayPayload payload);

        void handleTensionUpdate(TensionUpdatePayload payload);

        void handleStaminaSync(StaminaSyncPayload payload);

        void handleBuildProgress(BuildProgressPayload payload);

        void handleChallengeSync(ChallengeSyncPayload payload);

        void handleLvcSync(LVCSyncPayload payload);

        void handleSeasonTierUp(SeasonTierUpPayload payload);
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

        // ===================================================================
        // MOB/ITEM CHANNELS (1-4) - see ChannelId enum
        // ===================================================================

        event.registrar(MOB_STATS.asString()).playToServer(
                nn(UpdateMobStatsPayload.TYPE),
                nn(UpdateMobStatsPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleMobData
        );
        event.registrar(WEAPON_LEGACY.asString()).playToServer(
                nn(UpdateWeaponPayload.TYPE),
                nn(UpdateWeaponPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleWeaponData
        );
        event.registrar(EQUIP_MOB.asString()).playToServer(
                nn(EquipMobPayload.TYPE),
                nn(EquipMobPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleEquipData
        );
        event.registrar(MODIFY_ITEM.asString()).playToServer(
                nn(ModifyItemPayload.TYPE),
                nn(ModifyItemPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleItemModification
        );
        // WEAPON_STATS_NBT removed - uses same payload type as WEAPON_STATS_V2
        // Use WEAPON_STATS_V2 channel for all weapon stats communication

        // ===================================================================
        // CONFIG/TELEMETRY CHANNELS (36-45) - see ChannelId enum
        // ===================================================================

        event.registrar(UPDATE_ARMOR.asString()).playToServer(
                nn(UpdateArmorPayload.TYPE),
                nn(UpdateArmorPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleArmorData
        );
        event.registrar(RANGED_WEAPON_STATS.asString()).playToServer(
                nn(RangedWeaponStatsPayload.TYPE),
                nn(RangedWeaponStatsPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleRangedWeaponData
        );
        event.registrar(ARMOR_STATS.asString()).playToServer(
                nn(ArmorStatsPayload.TYPE),
                nn(ArmorStatsPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleArmorStatsDataV2
        );
        event.registrar(GLOBAL_CONFIG_SYNC.asString()).playToClient(
                nn(GlobalConfigSyncPayload.TYPE),
                nn(GlobalConfigSyncPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(payload::applyToClientConfigs);
                    }
                }
        );
        event.registrar(RECIPE_SYNC.asString()).playToServer(
                nn(RecipeSyncPayload.TYPE),
                nn(RecipeSyncPayload.STREAM_CODEC),
                ConfigNetworkHandler::handleRecipeSync
        );
        event.registrar(RECIPE_CLIENT_SYNC.asString()).playToClient(
                nn(RecipeClientSyncPayload.TYPE),
                nn(RecipeClientSyncPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() -> {
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
                }
        );
        event.registrar(TELEMETRY_BATCH.asString()).playToServer(
                nn(TelemetryBatchPayload.TYPE),
                nn(TelemetryBatchPayload.STREAM_CODEC),
                ConfigNetworkHandler::handleTelemetryBatch
        );
        event.registrar(EDITOR_APPLY_CONFIRM.asString()).playToClient(
                nn(EditorApplyConfirmPayload.TYPE),
                nn(EditorApplyConfirmPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleEditorApplyConfirm(payload)));
                    }
                }
        );
        event.registrar(RESONANCE_NOTIFICATION.asString()).playToClient(
                nn(com.devmod.endurance.resonance.ResonanceNotificationPayload.TYPE),
                nn(com.devmod.endurance.resonance.ResonanceNotificationPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleResonanceTriggered(payload)));
                    }
                }
        );
        event.registrar(CONTRACT_SYNC.asString()).playToClient(
                nn(com.devmod.endurance.contracts.ContractSyncPayload.TYPE),
                nn(com.devmod.endurance.contracts.ContractSyncPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleContractSync(payload)));
                    }
                }
        );
        event.registrar(GAME_MECHANICS_SYNC.asString()).playToClient(
                nn(GameMechanicsSyncPayload.TYPE),
                nn(GameMechanicsSyncPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(payload::applyToClient);
                    }
                }
        );

        // ===================================================================
        // ITEM STATS CHANNELS (46-55) - see ChannelId enum
        // ===================================================================

        event.registrar(USABLE_STATS.asString()).playToServer(
                nn(UsableStatsPayload.TYPE),
                nn(UsableStatsPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleUsableStatsData
        );
        event.registrar(FOOD_STATS.asString()).playToServer(
                nn(FoodStatsPayload.TYPE),
                nn(FoodStatsPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleFoodStatsData
        );
        event.registrar(FUEL_STATS.asString()).playToServer(
                nn(FuelStatsPayload.TYPE),
                nn(FuelStatsPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleFuelStatsData
        );
        event.registrar(WEAPON_STATS_V2.asString()).playToServer(
                nn(WeaponStatsPayload.TYPE),
                nn(WeaponStatsPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleWeaponStatsDataV2
        );

        // ===================================================================
        // ENDURANCE QUEST CHANNELS (5-25) - see ChannelId enum
        // ===================================================================

        event.registrar(START_QUEST.asString()).playToServer(
                nn(StartQuestPayload.TYPE),
                nn(StartQuestPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleStartEnduranceQuest
        );
        event.registrar(QUEST_ACTION.asString()).playToServer(
                nn(QuestActionPayload.TYPE),
                nn(QuestActionPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleQuestAction
        );
        event.registrar(QUEST_SYNC.asString()).playToClient(
                nn(QuestSyncPayload.TYPE),
                nn(QuestSyncPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleQuestSync
        );
        event.registrar(SHOP_PURCHASE.asString()).playToServer(
                nn(ShopPurchasePayload.TYPE),
                nn(ShopPurchasePayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleShopPurchase
        );
        event.registrar(SHOP_SYNC.asString()).playToClient(
                nn(ShopSyncPayload.TYPE),
                nn(ShopSyncPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleShopSync
        );
        event.registrar(REQUEST_SHOP_SYNC.asString()).playToServer(
                nn(RequestShopSyncPayload.TYPE),
                nn(RequestShopSyncPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleRequestShopSync
        );
        event.registrar(MOB_CONFIG_CONFIRM.asString()).playToClient(
                nn(MobConfigConfirmPayload.TYPE),
                nn(MobConfigConfirmPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleMobConfigConfirm(payload)));
                    }
                }
        );
        event.registrar(QUEST_DEATH.asString()).playToClient(
                nn(QuestDeathPayload.TYPE),
                nn(QuestDeathPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleQuestDeath
        );
        event.registrar(PERK_CHOICES.asString()).playToClient(
                nn(PerkChoicesPayload.TYPE),
                nn(PerkChoicesPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handlePerkChoices
        );
        event.registrar(PERK_SELECTION.asString()).playToServer(
                nn(PerkSelectionPayload.TYPE),
                nn(PerkSelectionPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handlePerkSelection
        );
        event.registrar(QUEST_COMPLETION.asString()).playToClient(
                nn(QuestCompletionPayload.TYPE),
                nn(QuestCompletionPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleQuestCompletion
        );
        event.registrar(PERSONAL_RECORDS_SYNC.asString()).playToClient(
                nn(PersonalRecordsSyncPayload.TYPE),
                nn(PersonalRecordsSyncPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handlePersonalRecordsSync
        );
        event.registrar(REQUEST_PERSONAL_RECORDS.asString()).playToServer(
                nn(RequestPersonalRecordsPayload.TYPE),
                nn(RequestPersonalRecordsPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleRequestPersonalRecords
        );
        event.registrar(BOSS_ALERT.asString()).playToClient(
                nn(BossAlertPayload.TYPE),
                nn(BossAlertPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleBossAlert(payload)));
                    }
                }
        );
        event.registrar(BADGE_UNLOCK.asString()).playToClient(
                nn(BadgeUnlockPayload.TYPE),
                nn(BadgeUnlockPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleBadgeUnlock(payload)));
                    }
                }
        );
        event.registrar(TOKEN_GAIN.asString()).playToClient(
                nn(TokenGainPayload.TYPE),
                nn(TokenGainPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleTokenGain(payload)));
                    }
                }
        );
        event.registrar(RECORD_BANNER.asString()).playToClient(
                nn(RecordBannerPayload.TYPE),
                nn(RecordBannerPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleRecordBanner(payload)));
                    }
                }
        );
        event.registrar(COMBO_DECAY.asString()).playToClient(
                nn(ComboDecayPayload.TYPE),
                nn(ComboDecayPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleComboDecay(payload)));
                    }
                }
        );
        event.registrar(TENSION_UPDATE.asString()).playToClient(
                nn(TensionUpdatePayload.TYPE),
                nn(TensionUpdatePayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleTensionUpdate(payload)));
                    }
                }
        );
        event.registrar(INSTANCE_LOADING.asString()).playToClient(
                nn(InstanceLoadingPayload.TYPE),
                nn(InstanceLoadingPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleInstanceLoading
        );
        event.registrar(WAVE_DIRECTIVE_CHOICES.asString()).playToClient(
                nn(WaveDirectiveChoicesPayload.TYPE),
                nn(WaveDirectiveChoicesPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleWaveDirectiveChoices
        );
        event.registrar(WAVE_DIRECTIVE_SELECTION.asString()).playToServer(
                nn(WaveDirectiveSelectionPayload.TYPE),
                nn(WaveDirectiveSelectionPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleWaveDirectiveSelection
        );

        // ===================================================================
        // PARTY SYSTEM CHANNELS (26-33) - see ChannelId enum
        // ===================================================================

        event.registrar(PARTY_ACTION.asString()).playToServer(
                nn(PartyActionPayload.TYPE),
                nn(PartyActionPayload.STREAM_CODEC),
                PartyNetworkHandler::handlePartyAction
        );
        event.registrar(PARTY_NOTIFICATION.asString()).playToClient(
                nn(PartyNotificationPayload.TYPE),
                nn(PartyNotificationPayload.STREAM_CODEC),
                PartyNetworkHandler::handlePartyNotification
        );
        event.registrar(PARTY_SYNC.asString()).playToClient(
                nn(PartySyncPayload.TYPE),
                nn(PartySyncPayload.STREAM_CODEC),
                PartyNetworkHandler::handlePartySync
        );
        event.registrar(QUEST_SEQUENCE.asString()).playToClient(
                nn(QuestSequencePayload.TYPE),
                nn(QuestSequencePayload.STREAM_CODEC),
                PartyNetworkHandler::handleQuestSequence
        );
        event.registrar(NAMED_INVITE.asString()).playToServer(
                nn(NamedInvitePayload.TYPE),
                nn(NamedInvitePayload.STREAM_CODEC),
                PartyNetworkHandler::handleNamedInvite
        );
        event.registrar(ARRIVAL_CONFIRM.asString()).playToServer(
                nn(ArrivalConfirmPayload.TYPE),
                nn(ArrivalConfirmPayload.STREAM_CODEC),
                PartyNetworkHandler::handleArrivalConfirm
        );
        event.registrar(CANCEL_SEQUENCE.asString()).playToServer(
                nn(CancelSequencePayload.TYPE),
                nn(CancelSequencePayload.STREAM_CODEC),
                PartyNetworkHandler::handleCancelSequence
        );
        event.registrar(INVITE_RESPONSE.asString()).playToServer(
                nn(InviteResponsePayload.TYPE),
                nn(InviteResponsePayload.STREAM_CODEC),
                PartyNetworkHandler::handleInviteResponse
        );

        // ===================================================================
        // SHIELD VISUAL EFFECTS CHANNELS (56-58) - see ChannelId enum
        // ===================================================================

        event.registrar(SHIELD_STATE.asString()).playToClient(
                nn(ShieldStatePayload.TYPE),
                nn(ShieldStatePayload.STREAM_CODEC),
                ShieldNetworkHandler::handleShieldState
        );
        event.registrar(SHIELD_IMPACT.asString()).playToClient(
                nn(ShieldImpactPayload.TYPE),
                nn(ShieldImpactPayload.STREAM_CODEC),
                ShieldNetworkHandler::handleShieldImpact
        );
        event.registrar(SHIELD_SHATTER.asString()).playToClient(
                nn(ShieldShatterPayload.TYPE),
                nn(ShieldShatterPayload.STREAM_CODEC),
                ShieldNetworkHandler::handleShieldShatter
        );

        // ===================================================================
        // ABILITY SYSTEM CHANNELS (66-67) - see ChannelId enum
        // ===================================================================

        event.registrar(STAMINA_SYNC.asString()).playToClient(
                nn(StaminaSyncPayload.TYPE),
                nn(StaminaSyncPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleStaminaSync(payload)));
                    }
                }
        );
        event.registrar(ABILITY_ACTION.asString()).playToServer(
                nn(AbilityActionPayload.TYPE),
                nn(AbilityActionPayload.STREAM_CODEC),
                AbilityNetworkHandler::handleAbilityAction
        );
        event.registrar(LVC_SYNC.asString()).playToClient(
                nn(LVCSyncPayload.TYPE),
                nn(LVCSyncPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleLvcSync(payload)));
                    }
                }
        );

        // ===================================================================
        // ARENA CHANNELS (76-85) - see ChannelId enum
        // ===================================================================

        event.registrar(BUILD_PROGRESS.asString()).playToClient(
                nn(BuildProgressPayload.TYPE),
                nn(BuildProgressPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleBuildProgress(payload)));
                    }
                }
        );

        // ===================================================================
        // CHALLENGES CHANNELS (86-89) - see ChannelId enum
        // ===================================================================

        event.registrar(CHALLENGE_SYNC.asString()).playToClient(
                nn(com.devmod.endurance.challenges.ChallengeSyncPayload.TYPE),
                nn(com.devmod.endurance.challenges.ChallengeSyncPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleChallengeSync(payload)));
                    }
                }
        );

        // ===================================================================
        // SEASON PASS CHANNELS (92-99) - see ChannelId enum
        // ===================================================================

        event.registrar(SEASON_TIER_UP.asString()).playToClient(
                nn(SeasonTierUpPayload.TYPE),
                nn(SeasonTierUpPayload.STREAM_CODEC),
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        context.enqueueWork(() ->
                            withClientHooks(hooks -> hooks.handleSeasonTierUp(payload)));
                    }
                }
        );
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
     * Send badge unlock notification to a player.
     */
    public static void sendBadgeUnlock(ServerPlayer player, String badgeName, String rarity) {
        EnduranceNetworkHandler.sendBadgeUnlock(player, badgeName, rarity);
    }

    /**
     * Send token gain animation to a player.
     */
    public static void sendTokenGain(ServerPlayer player, int amount) {
        EnduranceNetworkHandler.sendTokenGain(player, amount);
    }

    /**
     * Send record banner notification to a player.
     */
    public static void sendRecordBanner(ServerPlayer player, String recordType, String recordValue) {
        EnduranceNetworkHandler.sendRecordBanner(player, recordType, recordValue);
    }

    /**
     * Send combo decay feedback to a player.
     */
    public static void sendComboDecay(ServerPlayer player, int lostCombo, int previousRank, int newRank) {
        EnduranceNetworkHandler.sendComboDecay(player, lostCombo, previousRank, newRank);
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
     * Send notification to all party members.
     */
    public static void notifyPartyMembers(MinecraftServer server, UUID partyId,
            PartyNotificationPayload notification, UUID excludePlayer) {
        PartyNetworkHandler.notifyPartyMembers(server, partyId, notification, excludePlayer);
    }

    /**
     * Send party notification to a specific player.
     */
    public static void sendPartyNotification(ServerPlayer player, PartyNotificationPayload notification) {
        PartyNetworkHandler.sendPartyNotification(player, notification);
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
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * Send season pass tier-up notification to a player.
     */
    public static void sendSeasonTierUp(ServerPlayer player, SeasonTierUpPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, Objects.requireNonNull(payload));
    }

    // ===================================================================
    // NULL-SAFETY HELPER
    // ===================================================================

    @Nonnull
    private static <T> T nn(T value) {
        return Objects.requireNonNull(value);
    }
}
