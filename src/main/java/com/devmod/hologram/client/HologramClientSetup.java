package com.devmod.hologram.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import com.devmod.DevMod;
import com.devmod.hologram.HologramBlockEntities;
import com.devmod.hologram.client.renderer.HologramRenderer;

/**
 * Client-side setup for the Hologram module.
 * Registers block entity renderers.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class HologramClientSetup {
    private HologramClientSetup() {}

    /**
     * Register block entity renderers.
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            HologramBlockEntities.HOLOGRAM_PROJECTOR.get(),
            HologramRenderer::new
        );
        DevMod.LOGGER.debug("[Hologram] Registered hologram projector renderer");
    }
}
