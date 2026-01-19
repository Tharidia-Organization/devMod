package com.devmod.foundry.client;

import java.util.Objects;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import com.devmod.DevMod;
import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.FoundryMenus;
import com.devmod.foundry.client.renderer.FoundryChannelRenderer;
import com.devmod.foundry.client.renderer.FoundryTankRenderer;
import com.devmod.foundry.client.screen.FoundryControllerScreen;
import com.devmod.foundry.client.screen.FoundryPartBuilderScreen;
import com.devmod.foundry.client.screen.FoundryToolAnvilScreen;
import com.devmod.foundry.client.screen.FoundryToolStationScreen;

/**
 * Client-side setup for the Foundry module.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class FoundryClientSetup {
    private FoundryClientSetup() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_CHANNEL.get()),
            FoundryChannelRenderer::new
        );
        event.registerBlockEntityRenderer(
            Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_TANK.get()),
            FoundryTankRenderer::new
        );
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(Objects.requireNonNull(FoundryMenus.FOUNDRY_CONTROLLER.get()), FoundryControllerScreen::new);
        event.register(Objects.requireNonNull(FoundryMenus.FOUNDRY_PART_BUILDER.get()), FoundryPartBuilderScreen::new);
        event.register(Objects.requireNonNull(FoundryMenus.FOUNDRY_TOOL_STATION.get()), FoundryToolStationScreen::new);
        event.register(Objects.requireNonNull(FoundryMenus.FOUNDRY_TOOL_ANVIL.get()), FoundryToolAnvilScreen::new);
    }
}
