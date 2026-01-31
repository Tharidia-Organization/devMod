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
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.DevMod;
import com.devmod.abilities.StaminaSyncPayload;
import com.devmod.arena.network.ArenaNetworkHandler;
import com.devmod.arena.network.BuildProgressPayload;
import com.devmod.clone.network.CloneNetworkHandler;
import com.devmod.clone.network.TelepadOpenScreenPayload;
import com.devmod.endurance.BossAlertPayload;
import com.devmod.endurance.CombatFlowSyncPayload;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.InstanceLoadingPayload;
import com.devmod.endurance.KitSyncConfirmPayload;
import com.devmod.endurance.MobPoolConfigSyncPayload;
import com.devmod.endurance.PerkChoicesPayload;
import com.devmod.endurance.PerkSystem;
import com.devmod.endurance.PersonalRecordsSyncPayload;
import com.devmod.endurance.QuestCompletionPayload;
import com.devmod.endurance.QuestDeathPayload;
import com.devmod.endurance.QuestSyncPayload;
import com.devmod.endurance.RewardSystem;
import com.devmod.endurance.ShopSyncPayload;
import com.devmod.endurance.TensionUpdatePayload;
import com.devmod.endurance.WaveDirective;
import com.devmod.endurance.WaveDirectiveChoicesPayload;
import com.devmod.endurance.challenges.ChallengeSyncPayload;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.contracts.ContractSyncPayload;
import com.devmod.hologram.network.HologramNetworkHandler;
import com.devmod.hologram.network.HologramOpenScreenPayload;
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
import com.devmod.party.PartySyncPayload;
import com.devmod.party.QuestSequencePayload;
import com.devmod.portal.network.PortalNetworkHandler;
import com.devmod.portal.network.PortalPreviewPayload;
import com.devmod.portal.network.PortalStatePayload;
import com.devmod.runtime.environment.EnvironmentSyncPayload;
import com.devmod.telemetry.network.LVCSyncPayload;

import static com.devmod.DevMod.MODID;
import static com.devmod.network.ChannelId.CHALLENGE_SYNC;
import static com.devmod.network.ChannelId.COMBAT_FLOW_SYNC;
import static com.devmod.network.ChannelId.CONTRACT_SYNC;
import static com.devmod.network.ChannelId.GAME_MECHANICS_SYNC;
import static com.devmod.network.ChannelId.IMPACT_SYNC;
import static com.devmod.network.ChannelId.LVC_SYNC;
import static com.devmod.network.ChannelId.MAILBOX_ACCESS;
import static com.devmod.network.ChannelId.MAILBOX_NOTIFY;
import static com.devmod.network.ChannelId.MAILBOX_READ;
import static com.devmod.network.ChannelId.MAILBOX_SEND;
import static com.devmod.network.ChannelId.MAILBOX_STATUS;
import static com.devmod.network.ChannelId.MAILBOX_SYNC;
import static com.devmod.network.ChannelId.NEWS_READ;
import static com.devmod.network.ChannelId.NEWS_SYNC;
import static com.devmod.network.ChannelId.NOTIFICATION_PREFS_SYNC;
import static com.devmod.network.ChannelId.NOTIFICATION_PREFS_UPDATE;
import static com.devmod.network.ChannelId.REQUEST_SEASON_PASS;
import static com.devmod.network.ChannelId.SEASON_PASS_SYNC;
import static com.devmod.network.ChannelId.TASK_ACTION;
import static com.devmod.network.ChannelId.TASK_SYNC;
import static com.devmod.network.ChannelId.TICKET_ACTION;
import static com.devmod.network.ChannelId.TICKET_CREATE;
import static com.devmod.network.ChannelId.TICKET_SYNC;
import static com.devmod.network.ChannelId.TICKET_SYNC_REQUEST;
import static com.devmod.network.ChannelId.UNIFIED_NOTIFICATION;
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
        // P2: Domain-specific handlers (delegated registration)
        // ===================================================================
        MobItemNetworkHandler.INSTANCE.registerPayloads(event);
        ConfigNetworkHandler.INSTANCE.registerPayloads(event);
        EnduranceNetworkHandler.INSTANCE.registerPayloads(event);
        PartyNetworkHandler.INSTANCE.registerPayloads(event);
        ShieldNetworkHandler.INSTANCE.registerPayloads(event);
        ArenaNetworkHandler.INSTANCE.registerPayloads(event);
        PortalNetworkHandler.INSTANCE.registerPayloads(event);
        HologramNetworkHandler.INSTANCE.registerPayloads(event);
        CloneNetworkHandler.INSTANCE.registerPayloads(event);

        // ===================================================================
        // REMAINING INLINE CHANNELS (not in domain handlers)
        // ===================================================================

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

        // ===================================================================
        // NEXUS HUB CHANNELS (240-249) - Delegated to domain registrar
        // ===================================================================
        com.devmod.nexus.network.NexusNetworkHandler.INSTANCE.registerPayloads(event);
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
                                                 IComboSession comboSession,
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

    /*
     * Send party sync to a specific player.
     */
    public static void sendPartySyncToPlayer(ServerPlayer player) {
        PartyNetworkHandler.sendPartySyncToPlayer(player);
    }

    /*
     * Sync party state to all members.
     */
    public static void syncPartyToAllMembers(MinecraftServer server, UUID partyId) {
        PartyNetworkHandler.syncPartyToAllMembers(server, partyId);
    }

    /*
     * Send stamina sync to a player.
     */
    public static void sendStaminaSync(ServerPlayer player, float currentStamina, float maxStamina) {
        AbilityNetworkHandler.sendStaminaSync(player, currentStamina, maxStamina);
    }

    /*
     * Send LVC (Last Value Cache) telemetry sync to a player.
     * Contains real-time combat stats for HUD display.
     */
    public static void sendLvcSync(ServerPlayer player, LVCSyncPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /*
     * Send environment sync to a player.
     * Used to sync frozen time and biome overrides for arena dimensions.
     */
    public static void sendEnvironmentSync(ServerPlayer player, EnvironmentSyncPayload payload) {
        ArenaNetworkHandler.sendEnvironmentSync(player, payload);
    }

    /*
     * Send zone debug data to a player.
     * Used to enable/disable zone boundary visualization on client.
     */
    public static void sendZoneDebug(ServerPlayer player, ZoneDebugPayload payload) {
        ArenaNetworkHandler.sendZoneDebug(player, payload);
    }

    /*
     * Send portal state sync to a player.
     * Used to sync portal teleportation overlay state.
     */
    public static void sendPortalState(ServerPlayer player, PortalStatePayload payload) {
        PortalNetworkHandler.sendPortalState(player, payload);
    }

    /*
     * Send portal preview to a player.
     * Used to show destination info when looking at a portal.
     */
    public static void sendPortalPreview(ServerPlayer player, PortalPreviewPayload payload) {
        PortalNetworkHandler.sendPortalPreview(player, payload);
    }

    /*
     * Send impact sync data to a player for HUD display.
     * Called from DamageHandler when a player deals damage.
     */
    public static void sendImpactSync(ServerPlayer player, ImpactSyncPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /*
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

    /*
     * Send hologram open screen payload to a player.
     */
    public static void sendHologramOpenScreen(ServerPlayer player, HologramOpenScreenPayload payload) {
        HologramNetworkHandler.sendHologramOpenScreen(player, payload);
    }

    /*
     * Send telepad open screen payload to a player.
     */
    public static void sendTelepadOpenScreen(ServerPlayer player, TelepadOpenScreenPayload payload) {
        CloneNetworkHandler.sendTelepadOpenScreen(player, payload);
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
