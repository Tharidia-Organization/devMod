package net.minecraft.tags;

import net.minecraft.resources.ResourceLocation;

/**
 * Minimal TagKey stub for tests.
 */
public class TagKey<T> {
    private final ResourceLocation id;

    private TagKey(ResourceLocation id) {
        this.id = id;
    }

    public static <T> TagKey<T> create(Object registry, ResourceLocation id) {
        return new TagKey<>(id);
    }

    public ResourceLocation id() {
        return id;
    }
}
