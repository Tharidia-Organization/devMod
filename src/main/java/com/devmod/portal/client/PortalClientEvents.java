package com.devmod.portal.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.DevMod;
import com.devmod.portal.block.CustomPortalBlock;
import com.devmod.portal.network.PortalPreviewRequestPayload;

/**
 * Client-side event handlers for the custom portal system.
 * Handles tick updates for the teleportation overlay animation and portal preview raycast.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class PortalClientEvents {

    private PortalClientEvents() {}

    // Raycast state for portal preview
    @Nullable
    private static BlockPos lastLookedPortal = null;
    private static long lastRequestTime = 0;
    /** Minimum time between preview requests in milliseconds */
    private static final long REQUEST_COOLDOWN_MS = 1000;

    /**
     * Ticks the portal teleportation overlay when active.
     * Also checks if the player is looking at a portal for preview display.
     *
     * <p>Note: The actual tick count is synchronized from the server via
     * {@link com.devmod.portal.network.PortalStatePayload}, but this provides
     * smoother interpolation between server updates.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Tick teleportation overlay
        if (PortalTeleportOverlay.isActive()) {
            PortalTeleportOverlay.tick();
        }

        // Check if looking at a portal for preview
        checkPortalRaycast();
    }

    /**
     * Checks if the player is looking at a portal block and requests preview if so.
     * Rate-limited to avoid spamming the server.
     */
    private static void checkPortalRaycast() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            lastLookedPortal = null;
            return;
        }

        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);

            if (state.getBlock() instanceof CustomPortalBlock) {
                // Looking at a portal
                long now = System.currentTimeMillis();

                // Request preview if:
                // 1. Looking at a different portal than before, OR
                // 2. Enough time has passed since last request
                if (!pos.equals(lastLookedPortal) || (now - lastRequestTime) > REQUEST_COOLDOWN_MS) {
                    lastLookedPortal = pos;
                    lastRequestTime = now;

                    // Send request to server
                    PacketDistributor.sendToServer(new PortalPreviewRequestPayload(pos));
                }
            } else {
                // Not looking at a portal
                lastLookedPortal = null;
            }
        } else {
            // Not looking at a block
            lastLookedPortal = null;
        }
    }
}
