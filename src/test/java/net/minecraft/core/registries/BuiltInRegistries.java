package net.minecraft.core.registries;

import net.minecraft.core.SimpleDefaultedRegistry;
import net.minecraft.world.item.Item;

/**
 * Minimal BuiltInRegistries stub for tests. Provides a tiny Registry with getKey behavior.
 */
public class BuiltInRegistries {
    public static final net.minecraft.core.DefaultedRegistry<Item> ITEM = new SimpleDefaultedRegistry<>();
}
