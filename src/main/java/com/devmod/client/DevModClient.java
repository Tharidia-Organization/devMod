package com.devmod.client;

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

import com.devmod.DevMod;
import com.devmod.actions.client.DevModClientActions;
import com.devmod.client.input.KeyInputHandler;
import com.devmod.client.network.ClientNetworkPayloadHooks;
import com.devmod.client.overlay.ImpactHudController;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.integration.ModIntegrationManager;
import com.devmod.network.NetworkHandler;

@Mod(value = DevMod.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class DevModClient {
    public DevModClient(IEventBus modEventBus, ModContainer container) {
        DevMod.LOGGER.debug("[DevMod] DevModClient constructor called");

        // Initialize client UI bridge (allows common code to request UI operations)
        ClientUiBridgeImpl.init();
        NetworkHandler.setClientPayloadHooks(new ClientNetworkPayloadHooks());

        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // Keybinds registration (client-only, in client.input package)
        modEventBus.addListener(KeyInputHandler::registerKeyMappings);

        DevModClientActions.register();
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        DevMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        DevMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        // Initialize client-side mod compatibility modules
        ModIntegrationManager.initClient();

        // Load persistent settings on the client thread to avoid race conditions during startup.
        event.enqueueWork(() -> {
            SettingsManager.INSTANCE.load();
            DevMod.LOGGER.info("[DevMod] Settings loaded from disk");

            // Initialize Impact HUD Controller (context-aware display modes)
            ImpactHudController.INSTANCE.initialize();
            DevMod.LOGGER.info("[DevMod] ImpactHudController initialized");
        }).exceptionally(e -> {
            DevMod.LOGGER.error("[DevMod] Failed to load settings", e);
            return null;
        });
    }
}
