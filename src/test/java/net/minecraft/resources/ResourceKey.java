package net.minecraft.resources;

import java.util.Objects;

/**
 * Minimal stub for ResourceKey.
 */
public final class ResourceKey<T> {
    private final ResourceLocation location;

    private ResourceKey(ResourceLocation loc) {
        this.location = Objects.requireNonNull(loc);
    }

    public static <T> ResourceKey<T> create(ResourceLocation loc) {
        return new ResourceKey<>(loc);
    }

    public ResourceLocation location() {
        return location;
    }
}
