package com.devmod.clone.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import com.devmod.DevMod;
import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.CloneBlocks;
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
            CloneBlockEntities.NEUROCELL.get(),
            NeurocellRenderer::new
        );
        event.registerBlockEntityRenderer(
            CloneBlockEntities.NEUROCELL_L.get(),
            NeurocellLRenderer::new
        );
        DevMod.LOGGER.info("[Clone] Registered neurocell renderers");
    }

    /**
     * Client setup - register render types for translucent blocks.
     */
    @SubscribeEvent
    @SuppressWarnings("deprecation")
    public static void onClientSetup(FMLClientSetupEvent event) {
        var unused = event.enqueueWork(() -> {
            DevMod.LOGGER.info("[Clone] Setting up render layers...");
            ItemBlockRenderTypes.setRenderLayer(CloneBlocks.NEUROCELL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(CloneBlocks.NEUROCELL_L.get(), RenderType.translucent());
            DevMod.LOGGER.info("[Clone] Render layers configured");
        });
    }
}
