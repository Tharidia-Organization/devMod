package net.minecraft.world.item;

import net.minecraft.resources.ResourceLocation;

/**
 * Minimal Item stub for tests.
 */
public class Item {
    private final ResourceLocation registryName;

    public Item(String id) {
        this.registryName = new ResourceLocation(id);
    }

    public ResourceLocation getRegistryName() {
        return registryName;
    }

    @Override
    public String toString() {
        return registryName == null ? "item" : registryName.toString();
    }
}
