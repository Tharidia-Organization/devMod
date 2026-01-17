package com.devmod.npc;

import java.util.Objects;

import net.neoforged.bus.api.IEventBus;

import com.devmod.DevMod;
import com.devmod.npc.component.NpcComponents;
import com.devmod.npc.item.NpcItems;

/**
 * NPC module initialization.
 * Provides NPC creation and dialog system features.
 *
 * <p>Features:
 * <ul>
 *   <li>NEUROCELL_NPC - Item for spawning and configuring NPCs</li>
 *   <li>Dialog System - In-game dialog configuration</li>
 *   <li>NPC Behavior - Floating, particles, glow effects</li>
 *   <li>Persistence - NPC data saved with world</li>
 * </ul>
 */
public final class NpcModule {
    private NpcModule() {}

    /**
     * Initialize the NPC module.
     * Call from DevMod constructor after BLOCKS/ITEMS registries are set up.
     */
    public static void init(IEventBus modEventBus) {
        // Initialize component registry
        NpcComponents.init();
        NpcComponents.COMPONENTS.register(Objects.requireNonNull(modEventBus));

        // Initialize item registry (static init)
        NpcItems.init();

        DevMod.LOGGER.info("[NPC] NPC module initialized");
    }
}
