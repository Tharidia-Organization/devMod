package com.devmod.transport;

import net.neoforged.bus.api.IEventBus;

import com.devmod.DevMod;

/**
 * Unified Transport module initialization.
 * Provides the Warp Core transport system replacing 11 legacy transport systems.
 *
 * <p>Features:
 * <ul>
 *   <li>WARP_CORE - Central transport node block</li>
 *   <li>FRAME_SEGMENT - Visual portal frame blocks</li>
 *   <li>Module blocks - Enhancement modules (Chromatic Lens, Range Amplifier, etc.)</li>
 *   <li>Network mode - Connect to any node in the same network</li>
 *   <li>Linked mode - Direct 1:1 teleportation</li>
 *   <li>Charging system - Stand on pad to charge and teleport</li>
 * </ul>
 *
 * <p>Replaces:
 * <ul>
 *   <li>Portal system (CustomPortalBlock)</li>
 *   <li>Telepad system (TelepadBlockEntity)</li>
 *   <li>Rift system (RiftStampManager)</li>
 *   <li>Zone portals (NexusZoneLayout)</li>
 *   <li>Instance system (InstanceManager)</li>
 *   <li>Recovery points</li>
 *   <li>Zone entry/exit</li>
 *   <li>Custom dimension transitions</li>
 * </ul>
 */
public final class TransportModule {
    private TransportModule() {}

    /**
     * Initialize the transport module.
     * Call from DevMod constructor after BLOCKS/ITEMS registries are set up.
     */
    public static void init(IEventBus modEventBus) {
        // Initialize block registries (static init triggers registration)
        // TransportBlocks fields are registered when the class is loaded
        TransportBlocks.init();

        // Register block entity types
        TransportBlockEntities.register(modEventBus);

        DevMod.LOGGER.info("[Transport] Unified Transport module initialized");
    }
}
