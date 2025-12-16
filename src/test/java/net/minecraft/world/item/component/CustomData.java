package net.minecraft.world.item.component;

import net.minecraft.nbt.CompoundTag;

/**
 * Minimal stub for CustomData used in JVM tests.
 */
public class CustomData {
    private final CompoundTag tag;

    private CustomData(CompoundTag tag) {
        this.tag = tag == null ? new CompoundTag() : tag;
    }

    public static CustomData of(CompoundTag tag) {
        return new CustomData(tag);
    }

    public boolean contains(String key) {
        return tag != null && tag.contains(key);
    }

    public CompoundTag copyTag() {
        return tag == null ? new CompoundTag() : tag.copy();
    }
}
