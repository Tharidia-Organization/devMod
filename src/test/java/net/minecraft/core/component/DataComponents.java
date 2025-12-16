package net.minecraft.core.component;

import net.minecraft.world.item.component.CustomData;

/**
 * Minimal stub for DataComponents registry.
 */
public final class DataComponents {
    private DataComponents() {}

    public static final DataComponentType<CustomData> CUSTOM_DATA = DataComponentType.<CustomData>builder().build();
}
