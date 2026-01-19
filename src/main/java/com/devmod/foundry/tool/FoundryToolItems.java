package com.devmod.foundry.tool;

import java.util.Objects;

import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.foundry.tool.FoundryToolKind;

/**
 * Tooling item registrations for the foundry module.
 */
public final class FoundryToolItems {
    private FoundryToolItems() {}

    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_TOOL_HEAD = DevMod.ITEMS.register(
        "foundry_pattern_tool_head",
        () -> new FoundryPatternItem(FoundryPartTypes.TOOL_HEAD)
    );

    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_TOOL_HANDLE = DevMod.ITEMS.register(
        "foundry_pattern_tool_handle",
        () -> new FoundryPatternItem(FoundryPartTypes.TOOL_HANDLE)
    );

    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_TOOL_BINDING = DevMod.ITEMS.register(
        "foundry_pattern_tool_binding",
        () -> new FoundryPatternItem(FoundryPartTypes.TOOL_BINDING)
    );

    public static final DeferredHolder<Item, FoundryPartItem> PART_TOOL_HEAD = DevMod.ITEMS.register(
        "foundry_tool_head",
        () -> new FoundryPartItem(FoundryPartTypes.TOOL_HEAD)
    );

    public static final DeferredHolder<Item, FoundryPartItem> PART_TOOL_HANDLE = DevMod.ITEMS.register(
        "foundry_tool_handle",
        () -> new FoundryPartItem(FoundryPartTypes.TOOL_HANDLE)
    );

    public static final DeferredHolder<Item, FoundryPartItem> PART_TOOL_BINDING = DevMod.ITEMS.register(
        "foundry_tool_binding",
        () -> new FoundryPartItem(FoundryPartTypes.TOOL_BINDING)
    );

    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_PICKAXE = DevMod.ITEMS.register(
        "foundry_pickaxe",
        () -> new FoundryToolItem(FoundryToolKind.PICKAXE)
    );

    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_AXE = DevMod.ITEMS.register(
        "foundry_axe",
        () -> new FoundryToolItem(FoundryToolKind.AXE)
    );

    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_SHOVEL = DevMod.ITEMS.register(
        "foundry_shovel",
        () -> new FoundryToolItem(FoundryToolKind.SHOVEL)
    );

    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_SWORD = DevMod.ITEMS.register(
        "foundry_sword",
        () -> new FoundryToolItem(FoundryToolKind.SWORD)
    );

    public static void init() {
        // Static init only.
    }

    public static FoundryPartItem getPartItem(FoundryPartType partType) {
        if (partType == FoundryPartTypes.TOOL_HEAD) {
            return Objects.requireNonNull(PART_TOOL_HEAD.get());
        }
        if (partType == FoundryPartTypes.TOOL_HANDLE) {
            return Objects.requireNonNull(PART_TOOL_HANDLE.get());
        }
        if (partType == FoundryPartTypes.TOOL_BINDING) {
            return Objects.requireNonNull(PART_TOOL_BINDING.get());
        }
        return null;
    }
}
