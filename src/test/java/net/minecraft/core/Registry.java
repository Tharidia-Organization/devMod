package net.minecraft.core;

import net.minecraft.resources.ResourceLocation;

/**
 * Minimal Registry interface for tests.
 */
public interface Registry<T> {
    ResourceLocation getKey(T value);
}
