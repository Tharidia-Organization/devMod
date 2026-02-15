package com.devmod.network.handlers;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.endurance.ArenaSuggestionsPayload;
import com.devmod.endurance.BossAlertPayload;
import com.devmod.endurance.EnduranceConfigSyncPayload;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.InstanceLoadingPayload;
import com.devmod.endurance.KitSyncConfirmPayload;
import com.devmod.endurance.KitSyncPayload;
import com.devmod.endurance.MobPoolConfigSyncPayload;
import com.devmod.endurance.PartyStatsSyncPayload;
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
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.nutrition.NutritionSyncPayload;
import com.devmod.network.ChannelId;
import com.devmod.network.PayloadValidation.PayloadLimits;

import static com.devmod.network.PayloadValidation.validated;

/**
 * P2: Domain-specific network handler for endurance quest system payloads.
 * Facade that delegates to specialized packet handlers:
 * <ul>
 *   <li>{@link EnduranceQuestPacketHandler} - quest lifecycle, arena, combat feedback</li>
 *   <li>{@link EnduranceShopKitPerkPacketHandler} - kit sync, shop, perks, wave directives</li>
 *   <li>{@link EnduranceConfigPacketHandler} - config sync, mob config</li>
 * </ul>
 */
public final class EnduranceNetworkHandler extends NetworkHandlerBase implements PayloadRegistrar {

    public static final EnduranceNetworkHandler INSTANCE = new EnduranceNetworkHandler();

    private EnduranceNetworkHandler() {}

    // =================================================================================
    // PAYLOAD REGISTRATION (P2: Domain-specific registration)
    // =================================================================================

    @Override
    public String getDomainName() {
        return "endurance";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // --- Quest lifecycle ---
        event.registrar(ChannelId.START_QUEST.asString()).playToServer(
            nn(StartQuestPayload.TYPE),
            nn(StartQuestPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleStartEnduranceQuest, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.QUEST_ACTION.asString()).playToServer(
            nn(QuestActionPayload.TYPE),
            nn(QuestActionPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleQuestAction, PayloadLimits.QUEST_ACTION)
        );
        event.registrar(ChannelId.QUEST_SYNC.asString()).playToClient(
            nn(QuestSyncPayload.TYPE),
            nn(QuestSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleQuestSync, PayloadLimits.LARGE)
        );
        event.registrar(ChannelId.QUEST_DEATH.asString()).playToClient(
            nn(QuestDeathPayload.TYPE),
            nn(QuestDeathPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleQuestDeath, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.QUEST_COMPLETION.asString()).playToClient(
            nn(QuestCompletionPayload.TYPE),
            nn(QuestCompletionPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleQuestCompletion, PayloadLimits.MEDIUM)
        );
        event.registrar(ChannelId.INSTANCE_LOADING.asString()).playToClient(
            nn(InstanceLoadingPayload.TYPE),
            nn(InstanceLoadingPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleInstanceLoading, PayloadLimits.SMALL)
        );

        // --- Arena suggestions ---
        event.registrar(ChannelId.REQUEST_ARENA_SUGGESTIONS.asString()).playToServer(
            nn(RequestArenaSuggestionsPayload.TYPE),
            nn(RequestArenaSuggestionsPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleRequestArenaSuggestions, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.ARENA_SUGGESTIONS.asString()).playToClient(
            nn(ArenaSuggestionsPayload.TYPE),
            nn(ArenaSuggestionsPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleArenaSuggestions, PayloadLimits.SYNC_MEDIUM)
        );

        // --- Personal records ---
        event.registrar(ChannelId.PERSONAL_RECORDS_SYNC.asString()).playToClient(
            nn(PersonalRecordsSyncPayload.TYPE),
            nn(PersonalRecordsSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handlePersonalRecordsSync, PayloadLimits.MEDIUM)
        );
        event.registrar(ChannelId.REQUEST_PERSONAL_RECORDS.asString()).playToServer(
            nn(RequestPersonalRecordsPayload.TYPE),
            nn(RequestPersonalRecordsPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleRequestPersonalRecords, PayloadLimits.SMALL)
        );

        // --- Combat feedback ---
        event.registrar(ChannelId.BOSS_ALERT.asString()).playToClient(
            nn(BossAlertPayload.TYPE),
            nn(BossAlertPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleBossAlert, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.TENSION_UPDATE.asString()).playToClient(
            nn(TensionUpdatePayload.TYPE),
            nn(TensionUpdatePayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleTensionUpdate, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.PARTY_STATS_SYNC.asString()).playToClient(
            nn(PartyStatsSyncPayload.TYPE),
            nn(PartyStatsSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handlePartyStatsSync, PayloadLimits.MEDIUM)
        );
        event.registrar(ChannelId.NUTRITION_SYNC.asString()).playToClient(
            nn(NutritionSyncPayload.TYPE),
            nn(NutritionSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleNutritionSync, PayloadLimits.SMALL)
        );

        // --- Kit sync ---
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

        // --- Shop ---
        event.registrar(ChannelId.SHOP_PURCHASE.asString()).playToServer(
            nn(ShopPurchasePayload.TYPE),
            nn(ShopPurchasePayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleShopPurchase, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.SHOP_SYNC.asString()).playToClient(
            nn(ShopSyncPayload.TYPE),
            nn(ShopSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleShopSync, PayloadLimits.SYNC_MEDIUM)
        );
        event.registrar(ChannelId.REQUEST_SHOP_SYNC.asString()).playToServer(
            nn(RequestShopSyncPayload.TYPE),
            nn(RequestShopSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleRequestShopSync, PayloadLimits.SMALL)
        );

        // --- Perks ---
        event.registrar(ChannelId.PERK_CHOICES.asString()).playToClient(
            nn(PerkChoicesPayload.TYPE),
            nn(PerkChoicesPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handlePerkChoices, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.PERK_SELECTION.asString()).playToServer(
            nn(PerkSelectionPayload.TYPE),
            nn(PerkSelectionPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handlePerkSelection, PayloadLimits.SMALL)
        );

        // --- Wave directives ---
        event.registrar(ChannelId.WAVE_DIRECTIVE_CHOICES.asString()).playToClient(
            nn(WaveDirectiveChoicesPayload.TYPE),
            nn(WaveDirectiveChoicesPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleWaveDirectiveChoices, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.WAVE_DIRECTIVE_SELECTION.asString()).playToServer(
            nn(WaveDirectiveSelectionPayload.TYPE),
            nn(WaveDirectiveSelectionPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleWaveDirectiveSelection, PayloadLimits.SMALL)
        );

        // --- Config ---
        event.registrar(ChannelId.ENDURANCE_CONFIG_SYNC.asString()).playToServer(
            nn(EnduranceConfigSyncPayload.TYPE),
            nn(EnduranceConfigSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleConfigSync, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.ENDURANCE_MOB_CONFIG_SYNC.asString()).playToServer(
            nn(com.devmod.endurance.EnduranceMobConfigSyncPayload.TYPE),
            nn(com.devmod.endurance.EnduranceMobConfigSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleMobConfigSync, PayloadLimits.LARGE)
        );
        event.registrar(ChannelId.REQUEST_MOB_POOL_CONFIG.asString()).playToServer(
            nn(RequestMobPoolConfigPayload.TYPE),
            nn(RequestMobPoolConfigPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleRequestMobPoolConfig, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.MOB_POOL_CONFIG_SYNC.asString()).playToClient(
            nn(MobPoolConfigSyncPayload.TYPE),
            nn(MobPoolConfigSyncPayload.STREAM_CODEC),
            validated(EnduranceNetworkHandler::handleMobPoolConfigSync, PayloadLimits.SYNC_MEDIUM)
        );
    }

    // =================================================================================
    // QUEST LIFECYCLE DELEGATES (→ EnduranceQuestPacketHandler)
    // =================================================================================

    public static void handleStartEnduranceQuest(StartQuestPayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handleStartEnduranceQuest(p, c);
    }

    public static void handleQuestAction(QuestActionPayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handleQuestAction(p, c);
    }

    public static void handleQuestSync(QuestSyncPayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handleQuestSync(p, c);
    }

    public static void handleQuestDeath(QuestDeathPayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handleQuestDeath(p, c);
    }

    public static void handleQuestCompletion(QuestCompletionPayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handleQuestCompletion(p, c);
    }

    public static void handleInstanceLoading(InstanceLoadingPayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handleInstanceLoading(p, c);
    }

    public static void handleRequestArenaSuggestions(RequestArenaSuggestionsPayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handleRequestArenaSuggestions(p, c);
    }

    public static void handleArenaSuggestions(ArenaSuggestionsPayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handleArenaSuggestions(p, c);
    }

    public static void handlePersonalRecordsSync(PersonalRecordsSyncPayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handlePersonalRecordsSync(p, c);
    }

    public static void handleRequestPersonalRecords(RequestPersonalRecordsPayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handleRequestPersonalRecords(p, c);
    }

    public static void handleBossAlert(BossAlertPayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handleBossAlert(p, c);
    }

    public static void handleTensionUpdate(TensionUpdatePayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handleTensionUpdate(p, c);
    }

    public static void handlePartyStatsSync(PartyStatsSyncPayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handlePartyStatsSync(p, c);
    }

    public static void handleNutritionSync(NutritionSyncPayload p, IPayloadContext c) {
        EnduranceQuestPacketHandler.handleNutritionSync(p, c);
    }

    // =================================================================================
    // SHOP / KIT / PERK DELEGATES (→ EnduranceShopKitPerkPacketHandler)
    // =================================================================================

    public static void handleKitSync(KitSyncPayload p, IPayloadContext c) {
        EnduranceShopKitPerkPacketHandler.handleKitSync(p, c);
    }

    public static void handleKitSyncConfirm(KitSyncConfirmPayload p, IPayloadContext c) {
        EnduranceShopKitPerkPacketHandler.handleKitSyncConfirm(p, c);
    }

    public static void handleShopPurchase(ShopPurchasePayload p, IPayloadContext c) {
        EnduranceShopKitPerkPacketHandler.handleShopPurchase(p, c);
    }

    public static void handleShopSync(ShopSyncPayload p, IPayloadContext c) {
        EnduranceShopKitPerkPacketHandler.handleShopSync(p, c);
    }

    public static void handleRequestShopSync(RequestShopSyncPayload p, IPayloadContext c) {
        EnduranceShopKitPerkPacketHandler.handleRequestShopSync(p, c);
    }

    public static void handlePerkChoices(PerkChoicesPayload p, IPayloadContext c) {
        EnduranceShopKitPerkPacketHandler.handlePerkChoices(p, c);
    }

    public static void handlePerkSelection(PerkSelectionPayload p, IPayloadContext c) {
        EnduranceShopKitPerkPacketHandler.handlePerkSelection(p, c);
    }

    public static void handleWaveDirectiveChoices(WaveDirectiveChoicesPayload p, IPayloadContext c) {
        EnduranceShopKitPerkPacketHandler.handleWaveDirectiveChoices(p, c);
    }

    public static void handleWaveDirectiveSelection(WaveDirectiveSelectionPayload p, IPayloadContext c) {
        EnduranceShopKitPerkPacketHandler.handleWaveDirectiveSelection(p, c);
    }

    // =================================================================================
    // CONFIG DELEGATES (→ EnduranceConfigPacketHandler)
    // =================================================================================

    public static void handleConfigSync(EnduranceConfigSyncPayload p, IPayloadContext c) {
        EnduranceConfigPacketHandler.handleConfigSync(p, c);
    }

    public static void handleMobConfigSync(com.devmod.endurance.EnduranceMobConfigSyncPayload p, IPayloadContext c) {
        EnduranceConfigPacketHandler.handleMobConfigSync(p, c);
    }

    public static void handleRequestMobPoolConfig(RequestMobPoolConfigPayload p, IPayloadContext c) {
        EnduranceConfigPacketHandler.handleRequestMobPoolConfig(p, c);
    }

    public static void handleMobPoolConfigSync(MobPoolConfigSyncPayload p, IPayloadContext c) {
        EnduranceConfigPacketHandler.handleMobPoolConfigSync(p, c);
    }

    // =================================================================================
    // PUBLIC SEND API (preserving existing call sites)
    // =================================================================================

    public static void sendShopSync(ServerPlayer player) {
        EnduranceShopKitPerkPacketHandler.sendShopSync(player);
    }

    public static void sendQuestDeathScreen(ServerPlayer player, int currentWave, int totalWaves,
            boolean endlessMode, int pointsEarned, int deathsThisRun, int respawnCost) {
        EnduranceQuestPacketHandler.sendQuestDeathScreen(player, currentWave, totalWaves,
            endlessMode, pointsEarned, deathsThisRun, respawnCost);
    }

    public static void sendPerkChoices(ServerPlayer player, int waveNumber, List<PerkSystem.Perk> perks) {
        EnduranceShopKitPerkPacketHandler.sendPerkChoices(player, waveNumber, perks);
    }

    public static void sendWaveDirectiveChoices(ServerPlayer player, int waveNumber, List<WaveDirective> directives) {
        EnduranceShopKitPerkPacketHandler.sendWaveDirectiveChoices(player, waveNumber, directives);
    }

    public static void sendQuestCompletionScreen(ServerPlayer player,
            EnduranceQuestManager.ActiveQuestSession session,
            RewardSystem.QuestRewards rewards,
            IComboSession comboSession,
            int maxCombo) {
        EnduranceQuestPacketHandler.sendQuestCompletionScreen(player, session, rewards, comboSession, maxCombo);
    }

    public static void sendPersonalRecordsSync(ServerPlayer player) {
        EnduranceQuestPacketHandler.sendPersonalRecordsSync(player);
    }

    public static void sendBossAlert(ServerPlayer player, long durationMs, String bossType) {
        EnduranceQuestPacketHandler.sendBossAlert(player, durationMs, bossType);
    }

    public static void sendTensionUpdate(ServerPlayer player, float tensionPercent, int tensionLevel, boolean bossImminent) {
        EnduranceQuestPacketHandler.sendTensionUpdate(player, tensionPercent, tensionLevel, bossImminent);
    }

    public static void sendInstanceLoadingShow(ServerPlayer player, String status) {
        EnduranceQuestPacketHandler.sendInstanceLoadingShow(player, status);
    }

    public static void sendInstanceLoadingHide(ServerPlayer player) {
        EnduranceQuestPacketHandler.sendInstanceLoadingHide(player);
    }
}
