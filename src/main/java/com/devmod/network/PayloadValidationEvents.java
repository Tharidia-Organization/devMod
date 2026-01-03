package com.devmod.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.devmod.DevMod;

@EventBusSubscriber(modid = DevMod.MODID)
public final class PayloadValidationEvents {
    private static final int CLEANUP_INTERVAL_TICKS = 20 * 60; // 60s
    private static int tickCounter = 0;

    private PayloadValidationEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter >= CLEANUP_INTERVAL_TICKS) {
            tickCounter = 0;
            PayloadValidation.cleanupStaleEntries();
            IpRateLimiter.INSTANCE.cleanup();
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        NetworkCommand.register(event.getDispatcher());
    }
}
