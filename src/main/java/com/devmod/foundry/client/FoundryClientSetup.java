package com.devmod.foundry.client;

import java.util.Objects;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import com.devmod.DevMod;
import com.devmod.foundry.FoundryMenus;
import com.devmod.foundry.client.screen.FoundryControllerScreen;

/**
 * Client-side setup for the Foundry module.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class FoundryClientSetup {
    private FoundryClientSetup() {}

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(Objects.requireNonNull(FoundryMenus.FOUNDRY_CONTROLLER.get()), FoundryControllerScreen::new);
    }
}
