package com.devmod.debug;

import net.neoforged.bus.api.IEventBus;

import com.devmod.DevMod;
import com.devmod.debug.block.DebugBlocks;
import com.devmod.debug.block.DebugBlockEntities;

/**
 * Debug module initialization.
 * Provides development and testing tools for entity inspection.
 *
 * <p>Features:
 * <ul>
 *   <li>EntityScanner block for scanning nearby entities</li>
 *   <li>Displays attributes, equipment, and AI goals</li>
 *   <li>Configurable scan radius</li>
 * </ul>
 */
public final class DebugModule {
    private DebugModule() {}

    /**
     * Initialize the debug module.
     * Call from DevMod constructor after BLOCKS registry is set up.
     */
    public static void init(IEventBus modEventBus) {
        // Initialize block registries (static init)
        DebugBlocks.init();

        // Register block entity types
        DebugBlockEntities.register(modEventBus);

        DevMod.LOGGER.info("[Debug] Debug module initialized");
    }
}
