package com.devmod.client.transport;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.devmod.DevMod;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class TransportClientEvents {
    private TransportClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        TransportClientPayloadHooks.getOverlay().tick();
    }
}
