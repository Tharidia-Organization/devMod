package com.devmod.client.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.abilities.StaminaSyncPayload;
import com.devmod.arena.network.BuildProgressPayload;
import com.devmod.client.arena.hud.BuildProgressHud;
import com.devmod.client.config.ClientMechanicsCache;
import com.devmod.client.debug.ZoneDebugRenderer;
import com.devmod.client.endurance.ClientCombatFlowCache;
import com.devmod.client.environment.ClientEnvironmentCache;
import com.devmod.endurance.BossAlertPayload;
import com.devmod.endurance.CombatFlowSyncPayload;
import com.devmod.endurance.InstanceLoadingPayload;
import com.devmod.endurance.KitSyncConfirmPayload;
import com.devmod.endurance.MobPoolConfigSyncPayload;
import com.devmod.endurance.PerkChoicesPayload;
import com.devmod.endurance.PersonalRecordsSyncPayload;
import com.devmod.endurance.QuestCompletionPayload;
import com.devmod.endurance.QuestDeathPayload;
import com.devmod.endurance.QuestSyncPayload;
import com.devmod.endurance.ShopSyncPayload;
import com.devmod.endurance.TensionUpdatePayload;
import com.devmod.endurance.WaveDirectiveChoicesPayload;
import com.devmod.endurance.challenges.ChallengeSyncPayload;
import com.devmod.endurance.contracts.ContractSyncPayload;
import com.devmod.mailbox.network.payload.TicketSyncPayload;
import com.devmod.network.EditorApplyConfirmPayload;
import com.devmod.network.GameMechanicsSyncPayload;
import com.devmod.network.GlobalConfigSyncPayload;
import com.devmod.network.MobConfigConfirmPayload;
import com.devmod.network.NetworkHandler;
import com.devmod.network.RecipeClientSyncPayload;
import com.devmod.network.ShieldImpactPayload;
import com.devmod.network.ShieldShatterPayload;
import com.devmod.network.ShieldStatePayload;
import com.devmod.network.ZoneDebugPayload;
import com.devmod.party.PartySyncPayload;
import com.devmod.party.QuestSequencePayload;
import com.devmod.portal.client.PortalTeleportOverlay;
import com.devmod.portal.network.PortalStatePayload;
import com.devmod.hologram.network.HologramOpenScreenPayload;
import com.devmod.hologram.client.screen.HologramConfigScreen;
import com.devmod.clone.network.TelepadOpenScreenPayload;
import com.devmod.clone.client.screen.TelepadConfigScreen;
import com.devmod.runtime.environment.EnvironmentSyncPayload;
import com.devmod.telemetry.network.LVCSyncPayload;

@OnlyIn(Dist.CLIENT)
public final class ClientNetworkPayloadHooks implements NetworkHandler.ClientPayloadHooks {

    @Override
    public void handleGlobalConfigSync(GlobalConfigSyncPayload payload) {
        ClientConfigHandlers.handleGlobalConfigSync(payload);
    }

    @Override
    public void handleRecipeClientSync(RecipeClientSyncPayload payload) {
        ClientConfigHandlers.handleRecipeClientSync(payload);
    }

    @Override
    public void handleGameMechanicsSync(GameMechanicsSyncPayload payload) {
        ClientMechanicsCache cache = ClientMechanicsCache.INSTANCE;
        if (payload.questId() == null) {
            cache.applyGlobalSync(payload.mechanicsConfig());
        } else {
            cache.applyQuestSync(payload.questId(),
                payload.questOverrides() != null ? payload.questOverrides() : payload.mechanicsConfig());
            cache.setActiveQuest(payload.questId());
        }
    }

    @Override
    public void handleEditorApplyConfirm(EditorApplyConfirmPayload payload) {
        ClientConfigFeedbackPayload.handleEditorApplyConfirm(payload);
    }

    @Override
    public void handleConfigEditorApplyConfirm(EditorApplyConfirmPayload payload) {
        ClientConfigHandlers.handleEditorApplyConfirm(payload);
    }

    @Override
    public void handleContractSync(ContractSyncPayload payload) {
        ClientOverlayHandlers.handleContractSync(payload);
    }

    @Override
    public void handleMobConfigConfirm(MobConfigConfirmPayload payload) {
        ClientConfigFeedbackPayload.handleMobConfigConfirm(payload);
    }

    @Override
    public void handleConfigMobConfigConfirm(MobConfigConfirmPayload payload) {
        ClientConfigHandlers.handleMobConfigConfirm(payload);
    }

    @Override
    public void handleMobPoolConfigSync(MobPoolConfigSyncPayload payload) {
        ClientEnduranceHandlers.handleMobPoolConfigSync(payload);
    }

    @Override
    public void handleQuestSync(QuestSyncPayload payload) {
        ClientEnduranceHandlers.handleQuestSync(payload);
    }

    @Override
    public void handleShopSync(ShopSyncPayload payload) {
        ClientEnduranceHandlers.handleShopSync(payload);
    }

    @Override
    public void handleQuestDeath(QuestDeathPayload payload) {
        ClientEnduranceHandlers.handleQuestDeath();
    }

    @Override
    public void handlePerkChoices(PerkChoicesPayload payload) {
        ClientEnduranceHandlers.handlePerkChoices(payload);
    }

    @Override
    public void handleWaveDirectiveChoices(WaveDirectiveChoicesPayload payload) {
        ClientEnduranceHandlers.handleWaveDirectiveChoices(payload);
    }

    @Override
    public void handleQuestCompletion(QuestCompletionPayload payload) {
        ClientEnduranceHandlers.handleQuestCompletion(payload);
    }

    @Override
    public void handleInstanceLoading(InstanceLoadingPayload payload) {
        ClientEnduranceHandlers.handleInstanceLoading(payload.show(), payload.status());
    }

    @Override
    public void handlePersonalRecordsSync(PersonalRecordsSyncPayload payload) {
        ClientEnduranceHandlers.handlePersonalRecordsSync(payload);
    }

    @Override
    public void handlePartySync(PartySyncPayload payload) {
        ClientPartyHandlers.handlePartySync(payload);
    }

    @Override
    public void handleQuestSequence(QuestSequencePayload payload) {
        ClientPartyHandlers.handleQuestSequence(payload);
    }

    @Override
    public void handleKitSyncConfirm(KitSyncConfirmPayload payload) {
        ClientEnduranceHandlers.handleKitSyncConfirm(payload);
    }

    @Override
    public void handleShieldState(ShieldStatePayload payload) {
        ClientShieldHandlers.handleShieldState(payload.isShattered(), payload.isActive());
    }

    @Override
    public void handleShieldImpact(ShieldImpactPayload payload) {
        ClientShieldHandlers.handleShieldImpact(
            payload.impactX(), payload.impactY(), payload.impactZ(), payload.damage());
    }

    @Override
    public void handleShieldShatter(ShieldShatterPayload payload) {
        ClientShieldHandlers.handleShieldShatter(payload.centerX(), payload.centerY(), payload.centerZ());
    }

    @Override
    public void handleImpactSync(com.devmod.network.ImpactSyncPayload payload) {
        ClientImpactHandlers.handleImpactSync(payload);
    }

    @Override
    public void handleBossAlert(BossAlertPayload payload) {
        ClientOverlayHandlers.handleBossAlert(payload.alertDurationMs(), payload.bossType());
    }

    @Override
    public void handleTensionUpdate(TensionUpdatePayload payload) {
        ClientOverlayHandlers.handleTensionUpdate(
            payload.tensionPercent(), payload.tensionLevel(), payload.bossImminent());
    }

    @Override
    public void handleCombatFlowSync(CombatFlowSyncPayload payload) {
        ClientCombatFlowCache.INSTANCE.update(payload);
    }

    @Override
    public void handleStaminaSync(StaminaSyncPayload payload) {
        ClientOverlayHandlers.handleStaminaSync(payload.currentStamina(), payload.maxStamina());
    }

    @Override
    public void handleBuildProgress(BuildProgressPayload payload) {
        BuildProgressHud.getInstance().handlePayload(payload);
    }

    @Override
    public void handleChallengeSync(ChallengeSyncPayload payload) {
        ClientEnduranceHandlers.handleChallengeSync(payload);
    }

    @Override
    public void handleLvcSync(LVCSyncPayload payload) {
        com.devmod.client.telemetry.ClientLVCCache.INSTANCE.update(payload);
    }

    // ==================== Mailbox System ====================

    @Override
    public void handleMailboxSync(com.devmod.mailbox.network.payload.MailboxSyncPayload payload) {
        com.devmod.mailbox.client.ClientMailboxCache.update(payload);
    }

    @Override
    public void handleMailboxNotify(com.devmod.mailbox.network.payload.MailboxNotifyPayload payload) {
        com.devmod.mailbox.client.ClientMailboxCache.handleNotification(payload);
        com.devmod.client.notification.ClientNotificationManager.INSTANCE.handleMailboxNotify(payload);
    }

    @Override
    public void handleMailboxStatus(com.devmod.mailbox.network.payload.MailboxStatusPayload payload) {
        com.devmod.mailbox.client.ClientMailboxCache.handleStatus(payload);
    }

    @Override
    public void handleMailboxAccess(com.devmod.mailbox.network.payload.MailboxAccessPayload payload) {
        com.devmod.mailbox.client.ClientMailboxAccess.update(payload);
    }

    @Override
    public void handleNewsSync(com.devmod.mailbox.network.payload.NewsSyncPayload payload) {
        com.devmod.mailbox.client.ClientNewsCache.update(payload);
    }

    @Override
    public void handleTaskSync(com.devmod.mailbox.network.payload.TaskSyncPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            java.util.UUID playerUuid = mc.player.getUUID();
            java.util.List<com.devmod.mailbox.task.TestTask> tasks = payload.toTasks(playerUuid);
            com.devmod.mailbox.client.ClientTaskCache.update(tasks);
        }
    }

    @Override
    public void handleTicketSync(TicketSyncPayload payload) {
        java.util.List<com.devmod.mailbox.client.ClientTicketCache.TicketData> tickets = payload.tickets().stream()
            .map(ticket -> new com.devmod.mailbox.client.ClientTicketCache.TicketData(
                ticket.id(),
                ticket.category(),
                ticket.priority(),
                ticket.status(),
                ticket.subject(),
                ticket.description(),
                ticket.createdAt(),
                ticket.updatedAt(),
                ticket.commentCount()
            ))
            .toList();
        com.devmod.mailbox.client.ClientTicketCache.sync(tickets);
    }

    // ==================== Unified Notification Center ====================

    @Override
    public void handleUnifiedNotification(com.devmod.notification.network.UnifiedNotificationPayload payload) {
        com.devmod.client.notification.ClientNotificationManager.INSTANCE.handleNotification(payload);
    }

    @Override
    public void handleNotificationPreferencesSync(com.devmod.notification.network.NotificationPreferencesSyncPayload payload) {
        com.devmod.client.notification.ClientNotificationPreferences.INSTANCE.applySyncPayload(payload);
    }

    // ==================== Environment Sync ====================

    @Override
    public void handleEnvironmentSync(EnvironmentSyncPayload payload) {
        if (payload.isTimeFrozen()) {
            ClientEnvironmentCache.setOverride(payload.dimensionKey(), payload.frozenTime(), payload.biomeId());
        } else {
            ClientEnvironmentCache.clearOverride(payload.dimensionKey());
        }
    }

    // ==================== Zone Debug ====================

    @Override
    public void handleZoneDebug(ZoneDebugPayload payload) {
        if (payload.enabled()) {
            ZoneDebugRenderer.setEnabled(true);
            ZoneDebugRenderer.setZoneLayout(payload.toZoneLayout(), payload.baseY());
        } else {
            ZoneDebugRenderer.setEnabled(false);
            ZoneDebugRenderer.clearZoneLayout();
        }
    }

    // ==================== Season Pass ====================

    @Override
    public void handleSeasonPassSync(com.devmod.endurance.season.SeasonPassPayload payload) {
        if (payload != null) {
            com.devmod.client.season.ClientSeasonPassCache.INSTANCE.update(payload);
        }
    }

    // ==================== Portal State ====================

    @Override
    public void handlePortalState(PortalStatePayload payload) {
        if (payload.inPortal()) {
            PortalTeleportOverlay.enterPortal(payload.getColor(), payload.requiredDelay());
            PortalTeleportOverlay.updateTicks(payload.ticksInPortal());
        } else {
            PortalTeleportOverlay.exitPortal();
        }
    }

    // ==================== Portal Preview ====================

    @Override
    public void handlePortalPreview(com.devmod.portal.network.PortalPreviewPayload payload) {
        com.devmod.portal.client.PortalPreviewOverlay.showPreview(payload);
    }

    // ==================== Hologram Config ====================

    @Override
    public void handleHologramOpenScreen(HologramOpenScreenPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            mc.setScreen(new HologramConfigScreen(
                payload.pos(),
                payload.scanSize(),
                payload.blockSize(),
                payload.rotationEnabled(),
                payload.transparentMode()
            ));
        });
    }

    // ==================== Telepad Config ====================

    @Override
    public void handleTelepadOpenScreen(TelepadOpenScreenPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            mc.setScreen(new TelepadConfigScreen(
                payload.pos(),
                payload.telepadName()
            ));
        });
    }
}
