package com.frenkvs.devmod.event.client;

import com.frenkvs.devmod.permission.PermissionManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
public class ClientPermissionEvents {
    
    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // Reset permission cache when player logs in
        PermissionManager.resetCache();
    }
    
    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Reset permission cache when player logs out
        PermissionManager.resetCache();
    }
}
