package com.devmod.client.nexus;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

import com.devmod.DevMod;

/**
 * Client-side setup for the Nexus dimension.
 * Registers custom dimension effects on the MOD event bus.
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NexusClientSetup {

    private NexusClientSetup() {}

    @SubscribeEvent
    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(
            ResourceLocation.fromNamespaceAndPath("devmod", "nexus"),
            new NexusDimensionEffects()
        );
        DevMod.LOGGER.info("[DevMod] Registered Nexus dimension effects");
    }
}
