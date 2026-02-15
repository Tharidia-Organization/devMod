package com.devmod.network;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import com.devmod.arena.network.ArenaNetworkHandler;
import com.devmod.clone.network.CloneNetworkHandler;
import com.devmod.clone.network.TelepadOpenScreenPayload;
import com.devmod.endurance.BossAlertPayload;
import com.devmod.endurance.CombatFlowSyncPayload;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.InstanceLoadingPayload;
import com.devmod.endurance.PerkSystem;
import com.devmod.endurance.RewardSystem;
import com.devmod.endurance.TensionUpdatePayload;
import com.devmod.endurance.WaveDirective;
import com.devmod.endurance.challenges.ChallengeSyncPayload;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.contracts.ContractSyncPayload;
import com.devmod.hologram.network.HologramNetworkHandler;
import com.devmod.hologram.network.HologramOpenScreenPayload;
import com.devmod.mailbox.network.payload.TicketSyncPayload;
import com.devmod.network.handlers.AbilityNetworkHandler;
import com.devmod.network.handlers.CombatPacketHandler;
import com.devmod.network.handlers.ConfigNetworkHandler;
import com.devmod.network.handlers.EnduranceNetworkHandler;
import com.devmod.network.handlers.GameplayPacketHandler;
import com.devmod.network.handlers.MobItemNetworkHandler;
import com.devmod.network.handlers.PartyNetworkHandler;
import com.devmod.network.handlers.ShieldNetworkHandler;
import com.devmod.network.handlers.SystemPacketHandler;
import com.devmod.notification.network.NotificationPreferencesSyncPayload;
import com.devmod.party.PartySyncPayload;
import com.devmod.party.QuestSequencePayload;
import com.devmod.portal.network.PortalNetworkHandler;
import com.devmod.portal.network.PortalPreviewPayload;
import com.devmod.portal.network.PortalStatePayload;
import com.devmod.runtime.environment.EnvironmentSyncPayload;
import com.devmod.telemetry.network.LVCSyncPayload;

import static com.devmod.DevMod.MODID;
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

        void handleMobPoolConfigSync(com.devmod.endurance.MobPoolConfigSyncPayload payload);

        void handleQuestSync(com.devmod.endurance.QuestSyncPayload payload);

        void handleShopSync(com.devmod.endurance.ShopSyncPayload payload);

        void handleQuestDeath(com.devmod.endurance.QuestDeathPayload payload);

        void handlePerkChoices(com.devmod.endurance.PerkChoicesPayload payload);

        void handleWaveDirectiveChoices(com.devmod.endurance.WaveDirectiveChoicesPayload payload);

        void handleQuestCompletion(com.devmod.endurance.QuestCompletionPayload payload);

        void handleInstanceLoading(InstanceLoadingPayload payload);

        void handlePersonalRecordsSync(com.devmod.endurance.PersonalRecordsSyncPayload payload);

        void handlePartySync(PartySyncPayload payload);

        void handleQuestSequence(QuestSequencePayload payload);

        void handleKitSyncConfirm(com.devmod.endurance.KitSyncConfirmPayload payload);

        void handleShieldState(ShieldStatePayload payload);

        void handleShieldImpact(ShieldImpactPayload payload);

        void handleShieldShatter(ShieldShatterPayload payload);

        void handleBossAlert(BossAlertPayload payload);

        void handleTensionUpdate(TensionUpdatePayload payload);

        void handleCombatFlowSync(CombatFlowSyncPayload payload);

        void handleStaminaSync(com.devmod.abilities.StaminaSyncPayload payload);

        void handleBuildProgress(com.devmod.arena.network.BuildProgressPayload payload);

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
        // Domain-specific handlers (delegated registration)
        // ===================================================================
        MobItemNetworkHandler.INSTANCE.registerPayloads(event);
        ConfigNetworkHandler.INSTANCE.registerPayloads(event);
        EnduranceNetworkHandler.INSTANCE.registerPayloads(event);
        PartyNetworkHandler.INSTANCE.registerPayloads(event);
        ShieldNetworkHandler.INSTANCE.registerPayloads(event);
        AbilityNetworkHandler.INSTANCE.registerPayloads(event);
        CombatPacketHandler.INSTANCE.registerPayloads(event);
        SystemPacketHandler.INSTANCE.registerPayloads(event);
        GameplayPacketHandler.INSTANCE.registerPayloads(event);
        ArenaNetworkHandler.INSTANCE.registerPayloads(event);
        PortalNetworkHandler.INSTANCE.registerPayloads(event);
        HologramNetworkHandler.INSTANCE.registerPayloads(event);
        CloneNetworkHandler.INSTANCE.registerPayloads(event);

        // ===================================================================
        // NEXUS SYSTEM CHANNELS (140-149) - Delegated to domain registrar
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
}
