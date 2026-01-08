package com.devmod.portal;

import com.devmod.DevMod;
import com.devmod.portal.item.PortalIgniterItem;
import com.devmod.portal.item.PortalLinkerItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Portal module item registrations.
 * Includes 16 colored igniters, 1 linker, and 5 rune block items.
 */
public final class PortalItems {
    private PortalItems() {}

    // ========================================================================
    // Portal Igniters (16 colors)
    // ========================================================================

    private static final Map<PortalColor, DeferredHolder<Item, PortalIgniterItem>> IGNITERS = new EnumMap<>(PortalColor.class);

    static {
        for (PortalColor color : PortalColor.values()) {
            String name = "portal_igniter_" + color.getSerializedName();
            IGNITERS.put(color, DevMod.ITEMS.register(name,
                () -> new PortalIgniterItem(color, new Item.Properties()
                    .durability(64)
                    .stacksTo(1))));
        }
    }

    /**
     * Gets the igniter item for the specified color.
     */
    @Nullable
    public static DeferredHolder<Item, PortalIgniterItem> getIgniter(@Nonnull PortalColor color) {
        return IGNITERS.get(color);
    }

    /**
     * Gets all igniter items.
     */
    @Nonnull
    public static Map<PortalColor, DeferredHolder<Item, PortalIgniterItem>> getIgniters() {
        return IGNITERS;
    }

    // Convenience accessors for common colors
    public static final DeferredHolder<Item, PortalIgniterItem> IGNITER_BLUE = IGNITERS.get(PortalColor.BLUE);
    public static final DeferredHolder<Item, PortalIgniterItem> IGNITER_ORANGE = IGNITERS.get(PortalColor.ORANGE);
    public static final DeferredHolder<Item, PortalIgniterItem> IGNITER_PURPLE = IGNITERS.get(PortalColor.PURPLE);
    public static final DeferredHolder<Item, PortalIgniterItem> IGNITER_RED = IGNITERS.get(PortalColor.RED);
    public static final DeferredHolder<Item, PortalIgniterItem> IGNITER_GREEN = IGNITERS.get(PortalColor.GREEN);

    // ========================================================================
    // Portal Linker
    // ========================================================================

    public static final DeferredHolder<Item, PortalLinkerItem> PORTAL_LINKER = DevMod.ITEMS.register(
        "portal_linker",
        () -> new PortalLinkerItem(new Item.Properties()
            .stacksTo(1)));

    // ========================================================================
    // Rune Block Items (5 types)
    // ========================================================================

    private static final Map<RuneType, DeferredHolder<Item, BlockItem>> RUNE_ITEMS = new EnumMap<>(RuneType.class);

    static {
        for (RuneType rune : RuneType.values()) {
            String name = "rune_" + rune.getSerializedName();
            RUNE_ITEMS.put(rune, DevMod.ITEMS.register(name,
                () -> new BlockItem(
                    Objects.requireNonNull(PortalBlocks.getRuneBlock(rune)).get(),
                    new Item.Properties().stacksTo(64))));
        }
    }

    /**
     * Gets the rune block item for the specified type.
     */
    @Nullable
    public static DeferredHolder<Item, BlockItem> getRuneItem(@Nonnull RuneType type) {
        return RUNE_ITEMS.get(type);
    }

    /**
     * Gets all rune block items.
     */
    @Nonnull
    public static Map<RuneType, DeferredHolder<Item, BlockItem>> getRuneItems() {
        return RUNE_ITEMS;
    }

    // Convenience accessors
    public static final DeferredHolder<Item, BlockItem> RUNE_HASTE = RUNE_ITEMS.get(RuneType.HASTE);
    public static final DeferredHolder<Item, BlockItem> RUNE_GATE = RUNE_ITEMS.get(RuneType.GATE);
    public static final DeferredHolder<Item, BlockItem> RUNE_ENHANCER = RUNE_ITEMS.get(RuneType.ENHANCER);
    public static final DeferredHolder<Item, BlockItem> RUNE_STRONG_ENHANCER = RUNE_ITEMS.get(RuneType.STRONG_ENHANCER);
    public static final DeferredHolder<Item, BlockItem> RUNE_INFINITY = RUNE_ITEMS.get(RuneType.INFINITY);

    /**
     * Called during mod initialization to ensure items are registered.
     */
    public static void init() {
        // Static init ensures items are registered
        DevMod.LOGGER.debug("[Portal] Portal items initialized: {} igniters, 1 linker, {} rune blocks",
            IGNITERS.size(), RUNE_ITEMS.size());
    }
}
