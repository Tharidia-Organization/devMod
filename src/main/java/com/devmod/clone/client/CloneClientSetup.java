package com.devmod.clone.client;

import java.util.Objects;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.devmod.DevMod;
import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.CloneMenus;
import com.devmod.clone.client.screen.CentrifugeScreen;
import com.devmod.clone.client.screen.NeurocellScreen;
import com.devmod.clone.client.screen.NeurocellLScreen;
import com.devmod.clone.client.renderer.BillboardBatcher;
import com.devmod.clone.client.renderer.EntityBillboardAtlas;
import com.devmod.clone.client.renderer.NeurocellLRenderer;
import com.devmod.clone.client.renderer.NeurocellRenderer;

/**
 * Client-side setup for the Clone module.
 * Registers block entity renderers, render layers, and LOD billboard system.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class CloneClientSetup {
    private CloneClientSetup() {}

    private static boolean renderEventsRegistered = false;

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

        // Register render event handlers for billboard system
        if (!renderEventsRegistered) {
            NeoForge.EVENT_BUS.addListener(CloneClientSetup::onRenderLevelStage);
            renderEventsRegistered = true;
            DevMod.LOGGER.info("[Clone] Registered billboard render events");
        }
    }

    /**
     * Register menu screens.
     */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        DevMod.LOGGER.info("[Clone] Registering menu screens...");
        event.register(Objects.requireNonNull(CloneMenus.NEUROCELL.get()), NeurocellScreen::new);
        event.register(Objects.requireNonNull(CloneMenus.NEUROCELL_L.get()), NeurocellLScreen::new);
        event.register(Objects.requireNonNull(CloneMenus.CENTRIFUGE.get()), CentrifugeScreen::new);
        DevMod.LOGGER.info("[Clone] Menu screens registered");
    }

    /**
     * Handle render level stage events for billboard batching.
     * Billboards are collected during block entity rendering and flushed here.
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // Initialize atlas on first render if needed
        if (!EntityBillboardAtlas.getInstance().isReady()) {
            EntityBillboardAtlas.getInstance().initialize();
        }

        // Begin batch at start of block entity rendering
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            BillboardBatcher.getInstance().beginBatch();
        }

        // Flush batch after block entities are rendered
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            PoseStack poseStack = event.getPoseStack();
            BillboardBatcher.getInstance().renderBatch(poseStack, camera);
        }
    }
}
