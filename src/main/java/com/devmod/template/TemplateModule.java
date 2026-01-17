package com.devmod.template;

import net.neoforged.bus.api.IEventBus;

import com.devmod.DevMod;
import com.devmod.template.network.TemplateNetworkHandler;

/**
 * Room Templates module initialization.
 * Provides decoration templates for Nexus zones.
 *
 * <p>Features:
 * <ul>
 *   <li>Template presets for all 11 Nexus zone types</li>
 *   <li>Component library for room decorations</li>
 *   <li>Custom template creation and saving</li>
 *   <li>Integration with Area Builder and Zone Marker</li>
 * </ul>
 */
public final class TemplateModule {
    private TemplateModule() {}

    /**
     * Initialize the template module.
     * Call from DevMod constructor.
     */
    public static void init(IEventBus modEventBus) {
        DevMod.LOGGER.info("[Template] Room Templates module initialized");
    }

    /**
     * Register network handlers.
     * Called from NetworkHandler registration.
     */
    public static void registerNetwork(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        TemplateNetworkHandler.INSTANCE.registerPayloads(event);
    }
}
