package com.devmod.transport;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.devmod.DevMod;
import com.devmod.transport.executor.TransportExecutor;

@EventBusSubscriber(modid = DevMod.MODID)
public final class TransportEventHandler {
    private TransportEventHandler() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TransportExecutor.INSTANCE.tick(event.getServer());
    }
}
