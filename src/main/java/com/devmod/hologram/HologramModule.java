package com.devmod.hologram;

import net.neoforged.bus.api.IEventBus;

import com.devmod.DevMod;

/**
 * Hologram module initialization.
 * Provides a 3D holographic projector block that displays miniature terrain.
 *
 * <p>Features:
 * <ul>
 *   <li>Scans terrain in configurable area around the block</li>
 *   <li>Greedy meshing for efficient rendering</li>
 *   <li>GPU-accelerated VBO rendering</li>
 *   <li>Configurable rotation, transparency, and scale</li>
 * </ul>
 */
public final class HologramModule {
    private HologramModule() {}

    /**
     * Initialize the hologram module.
     * Call from DevMod constructor after BLOCKS/ITEMS registries are set up.
     */
    public static void init(IEventBus modEventBus) {
        // Initialize block/item registries (static init)
        HologramBlocks.init();
        HologramItems.init();

        // Register block entity types
        HologramBlockEntities.register(modEventBus);

        DevMod.LOGGER.info("[Hologram] Hologram module initialized");
    }
}
