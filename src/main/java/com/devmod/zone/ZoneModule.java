package com.devmod.zone;

import net.neoforged.bus.api.IEventBus;

import com.devmod.DevMod;

/**
 * Zone Marker module initialization.
 * Replaces hardcoded zones with a data-driven system.
 */
public final class ZoneModule {
    private ZoneModule() {}

    /**
     * Initializes the Zone Marker module.
     * Called from DevMod during mod initialization.
     *
     * @param modEventBus The mod event bus
     */
    public static void init(IEventBus modEventBus) {
        ZoneBlocks.init();
        ZoneItems.init();
        ZoneBlockEntities.register(modEventBus);

        DevMod.LOGGER.info("[Zone] Zone module initialized");
    }
}
