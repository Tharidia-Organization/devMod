package com.devmod.client.zone;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.devmod.DevMod;
import com.devmod.client.zone.widget.ZonePreviewRenderer;
import com.devmod.runtime.NexusDimensionManager;
import com.devmod.zone.network.ZoneSyncPayload;

/**
 * Client-side event handlers for Zone Marker system.
 * Handles cache cleanup on disconnect and dimension change.
 * Also renders zone boundary previews in the world.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class ZoneClientEvents {

    private ZoneClientEvents() {}

    /** Whether to show zone boundary previews (toggle via debug) */
    private static boolean showZonePreviews = false;

    /**
     * Toggles zone preview rendering (for debug/editor mode).
     */
    public static void setShowZonePreviews(boolean show) {
        showZonePreviews = show;
    }

    public static boolean isShowingZonePreviews() {
        return showZonePreviews;
    }

    /**
     * Clear zone cache when player disconnects.
     */
    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DevMod.LOGGER.debug("[Zone] Player logout - clearing client cache");
        ZoneClientCache.INSTANCE.clear();
        showZonePreviews = false;
    }

    /**
     * Clear zone cache when player changes dimension.
     * Zone data is dimension-specific (Nexus zones only exist in Nexus).
     */
    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Only handle on client side
        if (event.getEntity().level().isClientSide()) {
            DevMod.LOGGER.debug("[Zone] Dimension change {} -> {} - clearing client cache",
                event.getFrom().location(), event.getTo().location());
            ZoneClientCache.INSTANCE.clear();
        }
    }

    /**
     * Render zone boundary previews in the world.
     * Only renders when showZonePreviews is enabled and player is in Nexus dimension.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!showZonePreviews) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || mc.player == null) return;

        // Only render in Nexus dimension
        if (!level.dimension().equals(NexusDimensionManager.NEXUS_DIMENSION)) return;

        Camera camera = event.getCamera();
        Vec3 cameraPos = java.util.Objects.requireNonNull(camera.getPosition(), "cameraPos");

        var poseStack = java.util.Objects.requireNonNull(event.getPoseStack(), "poseStack");
        var bufferSource = java.util.Objects.requireNonNull(mc.renderBuffers().bufferSource(), "bufferSource");

        // Render each zone in cache
        for (ZoneSyncPayload.ZoneSummary zone : ZoneClientCache.INSTANCE.getAllZones()) {
            // Skip zones too far away
            if (!ZonePreviewRenderer.isInRenderRange(zone.bounds(), cameraPos,
                    ZonePreviewRenderer.DEFAULT_RENDER_DISTANCE)) {
                continue;
            }

            // Render bounds outline with zone color (50% alpha)
            int color = com.devmod.client.ui.editor.core.DesignTokens.withAlpha(
                zone.color(), com.devmod.client.ui.editor.core.DesignTokens.Alpha.A50);
            ZonePreviewRenderer.renderBoundsOutline(
                poseStack,
                bufferSource,
                zone.bounds(),
                color,
                cameraPos
            );
        }
    }
}
