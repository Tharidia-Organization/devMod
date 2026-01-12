package com.devmod.clone;

import net.neoforged.bus.api.IEventBus;

import com.devmod.DevMod;
import com.devmod.clone.component.CloneComponents;
import com.devmod.clone.recipe.CloneRecipeTypes;

/**
 * Clone module initialization.
 * Provides cloning and teleportation features inspired by Hologenica.
 *
 * <p>Features:
 * <ul>
 *   <li>TELEPAD - Teleportation pads with charging mechanic</li>
 *   <li>BIOSCANNER - Item for storing entity data</li>
 *   <li>IMPRINTER - Block for scanning nearby entities</li>
 *   <li>NEUROCELL - Cloning chamber</li>
 *   <li>NEUROLINK - Connection cables</li>
 *   <li>REFORMER - Entity spawner</li>
 *   <li>PLAYER_CLONE - Companion entity</li>
 * </ul>
 */
public final class CloneModule {
    private CloneModule() {}

    /**
     * Initialize the clone module.
     * Call from DevMod constructor after BLOCKS/ITEMS registries are set up.
     */
    public static void init(IEventBus modEventBus) {
        // Initialize component registry
        CloneComponents.init();
        CloneComponents.COMPONENTS.register(modEventBus);

        // Initialize block/item registries (static init)
        CloneBlocks.init();
        CloneItems.init();

        // Register block entity types
        CloneBlockEntities.register(modEventBus);

        // Register menu types
        CloneMenus.register(modEventBus);

        // Register recipe types (pulverizing, etc.)
        CloneRecipeTypes.register(modEventBus);

        // Register creative tab
        CloneCreativeTab.register(modEventBus);

        DevMod.LOGGER.info("[Clone] Clone module initialized");
    }
}
