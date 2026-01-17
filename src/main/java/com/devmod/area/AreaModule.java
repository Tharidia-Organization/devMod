package com.devmod.area;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

import com.devmod.DevMod;
import com.devmod.area.builder.AreaBuildTaskManager;
import com.devmod.area.snapshot.AreaSnapshotManager;

/**
 * Area Builder module initialization.
 * Provides the Area Builder system for creating custom areas in the Nexus.
 */
public final class AreaModule {
    private AreaModule() {}

    /**
     * Initialize the Area module.
     * Called from DevMod constructor during mod loading.
     *
     * @param modEventBus The mod event bus for registrations
     */
    public static void init(IEventBus modEventBus) {
        // Initialize block and item registrations (triggers static field loading)
        AreaBlocks.init();
        AreaItems.init();

        // Register block entity types with the event bus
        AreaBlockEntities.register(modEventBus);

        // Register creative tab
        AreaCreativeTab.init(modEventBus);

        // Register build task manager for server tick events
        NeoForge.EVENT_BUS.register(AreaBuildTaskManager.class);

        // Register snapshot manager for server tick events
        NeoForge.EVENT_BUS.register(AreaSnapshotManager.class);

        DevMod.LOGGER.info("[Area] Area module initialized");
    }

    /**
     * Cleanup on server shutdown.
     * Called from DevMod's server stopping event.
     */
    public static void cleanup() {
        AreaBuildTaskManager.INSTANCE.cleanup();
        AreaSnapshotManager.INSTANCE.cleanup();
    }
}
