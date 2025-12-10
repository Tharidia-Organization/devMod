package com.frenkvs.devmod;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;
import com.frenkvs.devmod.hud.ComboDecayOverlay;
import com.frenkvs.devmod.hud.RecordBannerOverlay;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = DevMod.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class DevModClient {
    public DevModClient(IEventBus modEventBus, ModContainer container) {
        DevMod.LOGGER.debug("[DevMod] DevModClient constructor called");

        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // HUD overlays (mod event bus)
        modEventBus.addListener(ComboDecayOverlay::registerOverlay);
        modEventBus.addListener(RecordBannerOverlay::registerOverlay);

        // NOTA: I keybind sono registrati in DevMod.java per evitare problemi di caricamento
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        DevMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        DevMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        // Load persistent settings
        event.enqueueWork(() -> {
            SettingsManager.INSTANCE.load();
            DevMod.LOGGER.info("[DevMod] Settings loaded from disk");
        });
    }
}
