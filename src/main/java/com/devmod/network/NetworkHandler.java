package com.devmod.network;
import com.devmod.DevMod;

import static com.devmod.DevMod.MODID;

import com.devmod.endurance.*;
import com.devmod.abilities.AbilityActionPayload;
import com.devmod.abilities.StaminaSyncPayload;
import com.devmod.telemetry.duckdb.packets.TelemetryBatchPayload;
import com.devmod.party.*;
import com.devmod.overlay.EnduranceQuestOverlay;
import com.devmod.overlay.BadgePopupOverlay;
import com.devmod.overlay.TokenGainOverlay;
import com.devmod.overlay.RecordBannerOverlay;
import com.devmod.overlay.ComboDecayOverlay;
import com.devmod.network.*;
import com.devmod.network.handlers.*;
import com.devmod.abilities.ClientStaminaCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * Network packet registration and routing.
 *
 * <p>This class registers all network channels and delegates handling to
 * domain-specific handlers for better separation of concerns:
 * <ul>
 *   <li>{@link MobItemNetworkHandler} - mob, weapon, armor, equipment packets</li>
 *   <li>{@link EnduranceNetworkHandler} - endurance quest packets</li>
 *   <li>{@link PartyNetworkHandler} - party system packets</li>
 *   <li>{@link AbilityNetworkHandler} - ability (dash, dodge, stamina) packets</li>
 *   <li>{@link ShieldNetworkHandler} - shield visual effect packets</li>
 *   <li>{@link ConfigNetworkHandler} - config sync, recipe, telemetry packets</li>
 * </ul>
 */
@EventBusSubscriber(modid = MODID)
public class NetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // ===================================================================
        // MOB/ITEM/ARMOR/USABLE CHANNELS (1-4, 7, 17, 34, 36-37, 44)
        // ===================================================================

        // Channel 1: Monster Statistics
        event.registrar("1").playToServer(
                nn(UpdateMobStatsPayload.TYPE),
                nn(UpdateMobStatsPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleMobData
        );
        // Channel 2: Weapon Statistics (legacy)
        event.registrar("2").playToServer(
                nn(UpdateWeaponPayload.TYPE),
                nn(UpdateWeaponPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleWeaponData
        );
        // Channel 7: Weapon Stats Payload (NBT-based)
        event.registrar("7").playToServer(
                nn(WeaponStatsPayload.TYPE),
                nn(WeaponStatsPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleWeaponStatsData
        );
        // Channel 17: Weapon Stats Payload v2 (typed)
        event.registrar("17").playToServer(
                nn(WeaponStatsPayloadV2.TYPE),
                nn(WeaponStatsPayloadV2.STREAM_CODEC),
                MobItemNetworkHandler::handleWeaponStatsDataV2
        );
        // Channel 3: Monster Equipment
        event.registrar("3").playToServer(
                nn(EquipMobPayload.TYPE),
                nn(EquipMobPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleEquipData
        );
        // Channel 4: Complete Item Modification
        event.registrar("4").playToServer(
                nn(ModifyItemPayload.TYPE),
                nn(ModifyItemPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleItemModification
        );
        // Channel 34: Armor Statistics
        event.registrar("34").playToServer(
                nn(UpdateArmorPayload.TYPE),
                nn(UpdateArmorPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleArmorData
        );
        // Channel 36: Ranged weapon stats
        event.registrar("36").playToServer(
                nn(RangedWeaponStatsPayload.TYPE),
                nn(RangedWeaponStatsPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleRangedWeaponData
        );
        // Channel 37: Armor Stats Payload v2
        event.registrar("37").playToServer(
                nn(ArmorStatsPayloadV2.TYPE),
                nn(ArmorStatsPayloadV2.STREAM_CODEC),
                MobItemNetworkHandler::handleArmorStatsDataV2
        );
        // Channel 44: Usable Stats Payload
        event.registrar("44").playToServer(
                nn(UsableStatsPayload.TYPE),
                nn(UsableStatsPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleUsableStatsData
        );
        // Channel 45: Food Stats Payload
        event.registrar("45").playToServer(
                nn(FoodStatsPayload.TYPE),
                nn(FoodStatsPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleFoodStatsData
        );
        // Channel 46: Fuel Stats Payload
        event.registrar("46").playToServer(
                nn(FuelStatsPayload.TYPE),
                nn(FuelStatsPayload.STREAM_CODEC),
                MobItemNetworkHandler::handleFuelStatsData
        );

        // ===================================================================
        // ENDURANCE QUEST CHANNELS (5-23)
        // ===================================================================

        // Channel 5: Start Quest
        event.registrar("5").playToServer(
                nn(StartQuestPayload.TYPE),
                nn(StartQuestPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleStartEnduranceQuest
        );
        // Channel 6: Quest Actions
        event.registrar("6").playToServer(
                nn(QuestActionPayload.TYPE),
                nn(QuestActionPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleQuestAction
        );
        // Channel 7: Quest Sync (server to client)
        event.registrar("7").playToClient(
                nn(QuestSyncPayload.TYPE),
                nn(QuestSyncPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleQuestSync
        );
        // Channel 8: Shop Purchase
        event.registrar("8").playToServer(
                nn(ShopPurchasePayload.TYPE),
                nn(ShopPurchasePayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleShopPurchase
        );
        // Channel 9: Shop Sync
        event.registrar("9").playToClient(
                nn(ShopSyncPayload.TYPE),
                nn(ShopSyncPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleShopSync
        );
        // Channel 10: Request Shop Sync
        event.registrar("10").playToServer(
                nn(RequestShopSyncPayload.TYPE),
                nn(RequestShopSyncPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleRequestShopSync
        );
        // Channel 12: Quest Death Screen
        event.registrar("12").playToClient(
                nn(QuestDeathPayload.TYPE),
                nn(QuestDeathPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleQuestDeath
        );
        // Channel 13: Perk Choices
        event.registrar("13").playToClient(
                nn(PerkChoicesPayload.TYPE),
                nn(PerkChoicesPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handlePerkChoices
        );
        // Channel 14: Perk Selection
        event.registrar("14").playToServer(
                nn(PerkSelectionPayload.TYPE),
                nn(PerkSelectionPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handlePerkSelection
        );
        // Channel 15: Quest Completion
        event.registrar("15").playToClient(
                nn(QuestCompletionPayload.TYPE),
                nn(QuestCompletionPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleQuestCompletion
        );
        // Channel 16: Personal Records Sync
        event.registrar("16").playToClient(
                nn(PersonalRecordsSyncPayload.TYPE),
                nn(PersonalRecordsSyncPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handlePersonalRecordsSync
        );
        // Channel 17: Request Personal Records
        event.registrar("17").playToServer(
                nn(RequestPersonalRecordsPayload.TYPE),
                nn(RequestPersonalRecordsPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleRequestPersonalRecords
        );
        // Channel 18: Boss Alert
        event.registrar("18").playToClient(
                nn(BossAlertPayload.TYPE),
                nn(BossAlertPayload.STREAM_CODEC),
                (payload, context) -> context.enqueueWork(() ->
                    EnduranceQuestOverlay.onBossAlert(payload.alertDurationMs(), payload.bossType()))
        );
        // Channel 19: Badge Unlock
        event.registrar("19").playToClient(
                nn(BadgeUnlockPayload.TYPE),
                nn(BadgeUnlockPayload.STREAM_CODEC),
                (payload, context) -> context.enqueueWork(() ->
                    BadgePopupOverlay.showBadge(payload.badgeName(), payload.rarity()))
        );
        // Channel 20: Token Gain Animation
        event.registrar("20").playToClient(
                nn(TokenGainPayload.TYPE),
                nn(TokenGainPayload.STREAM_CODEC),
                (payload, context) -> context.enqueueWork(() ->
                    TokenGainOverlay.show(payload.amount()))
        );
        // Channel 21: Record Banner
        event.registrar("21").playToClient(
                nn(RecordBannerPayload.TYPE),
                nn(RecordBannerPayload.STREAM_CODEC),
                (payload, context) -> context.enqueueWork(() ->
                    RecordBannerOverlay.showRecord(payload.recordType(), payload.recordValue()))
        );
        // Channel 22: Combo Decay Feedback
        event.registrar("22").playToClient(
                nn(ComboDecayPayload.TYPE),
                nn(ComboDecayPayload.STREAM_CODEC),
                (payload, context) -> context.enqueueWork(() ->
                    ComboDecayOverlay.show(payload.lostCombo(), payload.previousRankOrdinal(), payload.newRankOrdinal()))
        );
        // Channel 23: Instance Loading Overlay
        event.registrar("23").playToClient(
                nn(InstanceLoadingPayload.TYPE),
                nn(InstanceLoadingPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleInstanceLoading
        );
        // Channel 24: Wave Directive Choices
        event.registrar("24").playToClient(
                nn(WaveDirectiveChoicesPayload.TYPE),
                nn(WaveDirectiveChoicesPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleWaveDirectiveChoices
        );
        // Channel 25: Wave Directive Selection
        event.registrar("25").playToServer(
                nn(WaveDirectiveSelectionPayload.TYPE),
                nn(WaveDirectiveSelectionPayload.STREAM_CODEC),
                EnduranceNetworkHandler::handleWaveDirectiveSelection
        );

        // ===================================================================
        // PARTY SYSTEM CHANNELS (24-30)
        // ===================================================================

        // Channel 24: Party Action
        event.registrar("24").playToServer(
                nn(PartyActionPayload.TYPE),
                nn(PartyActionPayload.STREAM_CODEC),
                PartyNetworkHandler::handlePartyAction
        );
        // Channel 25: Invite Response
        event.registrar("25").playToServer(
                nn(InviteResponsePayload.TYPE),
                nn(InviteResponsePayload.STREAM_CODEC),
                PartyNetworkHandler::handleInviteResponse
        );
        // Channel 26: Party Notification
        event.registrar("26").playToClient(
                nn(PartyNotificationPayload.TYPE),
                nn(PartyNotificationPayload.STREAM_CODEC),
                PartyNetworkHandler::handlePartyNotification
        );
        // Channel 27: Party Sync
        event.registrar("27").playToClient(
                nn(PartySyncPayload.TYPE),
                nn(PartySyncPayload.STREAM_CODEC),
                PartyNetworkHandler::handlePartySync
        );
        // Channel 28: Quest Sequence Status (server -> client)
        event.registrar("28").playToClient(
                nn(QuestSequencePayload.TYPE),
                nn(QuestSequencePayload.STREAM_CODEC),
                PartyNetworkHandler::handleQuestSequence
        );
        // Channel 28: Named Invite (client -> server)
        event.registrar("28").playToServer(
                nn(NamedInvitePayload.TYPE),
                nn(NamedInvitePayload.STREAM_CODEC),
                PartyNetworkHandler::handleNamedInvite
        );
        // Channel 29: Arrival Confirm
        event.registrar("29").playToServer(
                nn(ArrivalConfirmPayload.TYPE),
                nn(ArrivalConfirmPayload.STREAM_CODEC),
                PartyNetworkHandler::handleArrivalConfirm
        );
        // Channel 30: Cancel Sequence
        event.registrar("30").playToServer(
                nn(CancelSequencePayload.TYPE),
                nn(CancelSequencePayload.STREAM_CODEC),
                PartyNetworkHandler::handleCancelSequence
        );

        // ===================================================================
        // ABILITY SYSTEM CHANNELS (31-32)
        // ===================================================================

        // Channel 31: Stamina Sync
        event.registrar("31").playToClient(
                nn(StaminaSyncPayload.TYPE),
                nn(StaminaSyncPayload.STREAM_CODEC),
                (payload, context) -> context.enqueueWork(() ->
                    ClientStaminaCache.update(payload.currentStamina(), payload.maxStamina()))
        );
        // Channel 32: Ability Action
        event.registrar("32").playToServer(
                nn(AbilityActionPayload.TYPE),
                nn(AbilityActionPayload.STREAM_CODEC),
                AbilityNetworkHandler::handleAbilityAction
        );

        // ===================================================================
        // CONFIG/TELEMETRY CHANNELS (11, 33, 35, 38-39, 43)
        // ===================================================================

        // Channel 11: Mob Config Confirmation
        event.registrar("11").playToClient(
                nn(MobConfigConfirmPayload.TYPE),
                nn(MobConfigConfirmPayload.STREAM_CODEC),
                ConfigNetworkHandler::handleMobConfigConfirm
        );
        // Channel 33: Telemetry Batch
        event.registrar("33").playToServer(
                nn(TelemetryBatchPayload.TYPE),
                nn(TelemetryBatchPayload.STREAM_CODEC),
                ConfigNetworkHandler::handleTelemetryBatch
        );
        // Channel 35: Editor apply confirmation
        event.registrar("35").playToClient(
                nn(EditorApplyConfirmPayload.TYPE),
                nn(EditorApplyConfirmPayload.STREAM_CODEC),
                ConfigNetworkHandler::handleEditorApplyConfirm
        );
        // Channel 38: Global Config Sync
        event.registrar("38").playToClient(
                nn(GlobalConfigSyncPayload.TYPE),
                nn(GlobalConfigSyncPayload.STREAM_CODEC),
                ConfigNetworkHandler::handleGlobalConfigSync
        );
        // Channel 39: Recipe Sync
        event.registrar("39").playToServer(
                nn(RecipeSyncPayload.TYPE),
                nn(RecipeSyncPayload.STREAM_CODEC),
                ConfigNetworkHandler::handleRecipeSync
        );
        // Channel 43: Recipe Sync (server to client)
        event.registrar("43").playToClient(
                nn(RecipeClientSyncPayload.TYPE),
                nn(RecipeClientSyncPayload.STREAM_CODEC),
                ConfigNetworkHandler::handleRecipeClientSync
        );

        // ===================================================================
        // SHIELD VISUAL EFFECTS CHANNELS (40-42)
        // ===================================================================

        // Channel 40: Shield State Sync
        event.registrar("40").playToClient(
                nn(ShieldStatePayload.TYPE),
                nn(ShieldStatePayload.STREAM_CODEC),
                ShieldNetworkHandler::handleShieldState
        );
        // Channel 41: Shield Impact Effect
        event.registrar("41").playToClient(
                nn(ShieldImpactPayload.TYPE),
                nn(ShieldImpactPayload.STREAM_CODEC),
                ShieldNetworkHandler::handleShieldImpact
        );
        // Channel 42: Shield Shatter Effect
        event.registrar("42").playToClient(
                nn(ShieldShatterPayload.TYPE),
                nn(ShieldShatterPayload.STREAM_CODEC),
                ShieldNetworkHandler::handleShieldShatter
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

    // ===================================================================
    // NULL-SAFETY HELPER
    // ===================================================================

    @Nonnull
    private static <T> T nn(T value) {
        return Objects.requireNonNull(value);
    }
}
