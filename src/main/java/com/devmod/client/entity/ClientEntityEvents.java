package com.devmod.client.entity;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import com.devmod.DevMod;
import com.devmod.clone.client.renderer.PlayerCloneEntityRenderer;
import com.devmod.entity.ModEntities;

/**
 * Client-side event handlers for entity rendering.
 * Registers entity renderers on the mod event bus.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientEntityEvents {
    private ClientEntityEvents() {}

    /**
     * Register entity renderers.
     * Called during client initialization.
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.PLAYER_CLONE.get(), PlayerCloneEntityRenderer::new);
        DevMod.LOGGER.debug("[ModEntities] Registered entity renderers");
    }
}
