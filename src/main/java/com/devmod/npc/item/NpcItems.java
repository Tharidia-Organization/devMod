package com.devmod.npc.item;

import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;

/**
 * NPC module item registrations.
 * Registers all NPC-related items.
 */
public final class NpcItems {
    private NpcItems() {}

    /**
     * The Neurocell NPC item for spawning and configuring NPCs.
     * Right-click in air to open config GUI, right-click on block to spawn.
     * Only usable by operators (OP 2+).
     */
    public static final DeferredHolder<Item, NeurocellNpcItem> NEUROCELL_NPC = DevMod.ITEMS.register(
        "neurocell_npc",
        NeurocellNpcItem::new
    );

    /**
     * Called during mod initialization to ensure items are registered.
     */
    public static void init() {
        DevMod.LOGGER.debug("[NPC] NPC items initialized");
    }
}
