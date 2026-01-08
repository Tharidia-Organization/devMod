package com.devmod.portal.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.devmod.DevMod;

/**
 * Client-side event handlers for the custom portal system.
 * Handles tick updates for the teleportation overlay animation.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class PortalClientEvents {

    private PortalClientEvents() {}

    /**
     * Ticks the portal teleportation overlay when active.
     * This keeps the client-side tick counter in sync for smooth animation.
     *
     * <p>Note: The actual tick count is synchronized from the server via
     * {@link com.devmod.portal.network.PortalStatePayload}, but this provides
     * smoother interpolation between server updates.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (PortalTeleportOverlay.isActive()) {
            PortalTeleportOverlay.tick();
        }
    }
}
