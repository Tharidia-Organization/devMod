package com.devmod.clone.client;

import java.util.Objects;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import com.devmod.DevMod;
import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.CloneMenus;
import com.devmod.clone.client.screen.NeurocellScreen;
import com.devmod.clone.client.screen.NeurocellLScreen;
import com.devmod.clone.client.renderer.NeurocellLRenderer;
import com.devmod.clone.client.renderer.NeurocellRenderer;

/**
 * Client-side setup for the Clone module.
 * Registers block entity renderers and render layers.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class CloneClientSetup {
    private CloneClientSetup() {}

    /**
     * Register block entity renderers.
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        DevMod.LOGGER.info("[Clone] Registering neurocell renderers...");
        event.registerBlockEntityRenderer(
            Objects.requireNonNull(CloneBlockEntities.NEUROCELL.get()),
            NeurocellRenderer::new
        );
        event.registerBlockEntityRenderer(
            Objects.requireNonNull(CloneBlockEntities.NEUROCELL_L.get()),
            NeurocellLRenderer::new
        );
        DevMod.LOGGER.info("[Clone] Registered neurocell renderers");
    }

    /**
     * Register menu screens.
     */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        DevMod.LOGGER.info("[Clone] Registering menu screens...");
        event.register(Objects.requireNonNull(CloneMenus.NEUROCELL.get()), NeurocellScreen::new);
        event.register(Objects.requireNonNull(CloneMenus.NEUROCELL_L.get()), NeurocellLScreen::new);
        DevMod.LOGGER.info("[Clone] Menu screens registered");
    }
}
