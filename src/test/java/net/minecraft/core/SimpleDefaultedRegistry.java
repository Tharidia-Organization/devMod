package net.minecraft.core;

import net.minecraft.resources.ResourceLocation;

/**
 * Simple test implementation of DefaultedRegistry.
 */
public class SimpleDefaultedRegistry<T> implements DefaultedRegistry<T> {
    @Override
    public ResourceLocation getKey(T value) {
        try {
            // Item stub exposes getRegistryName(); other types can be added as needed.
            java.lang.reflect.Method m = java.util.Objects.requireNonNull(value, "value cannot be null").getClass().getMethod("getRegistryName");
            Object res = m.invoke(value);
            if (res instanceof ResourceLocation rl) {
                return rl;
            }
        } catch (Exception ignored) {
            // fall through and return null
        }
        return null;
    }
}
