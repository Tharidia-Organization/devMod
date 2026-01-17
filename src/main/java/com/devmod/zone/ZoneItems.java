package com.devmod.zone;

import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.zone.item.ZoneMarkerItem;

/**
 * Item registrations for the Zone Marker module.
 */
public final class ZoneItems {
    private ZoneItems() {}

    /**
     * Zone Marker item - places zone marker blocks.
     * Creative tab item for zone creation.
     */
    public static final DeferredHolder<Item, ZoneMarkerItem> ZONE_MARKER =
        DevMod.ITEMS.register("zone_marker",
            () -> new ZoneMarkerItem(new Item.Properties().stacksTo(64)));

    /**
     * Initialize item registrations.
     * Called during module initialization to trigger static field loading.
     */
    public static void init() {
        DevMod.LOGGER.debug("[Zone] Zone items initialized");
    }
}
