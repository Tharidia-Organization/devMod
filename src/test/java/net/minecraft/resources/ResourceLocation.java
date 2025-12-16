package net.minecraft.resources;

import java.util.Objects;

/**
 * Minimal ResourceLocation stub for JVM tests.
 */
public final class ResourceLocation {
    private final String namespace;
    private final String path;

    public ResourceLocation(String id) {
        String safeId = Objects.requireNonNullElse(id, "");
        String[] parts = safeId.split(":", 2);
        if (parts.length == 2) {
            this.namespace = parts[0];
            this.path = parts[1];
        } else {
            this.namespace = "minecraft";
            this.path = safeId;
        }
    }

    public String getPath() {
        return path;
    }

    public String getNamespace() {
        return namespace;
    }

    public static ResourceLocation parse(String id) {
        return new ResourceLocation(id);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    @Override
    public boolean equals(@javax.annotation.Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof ResourceLocation that)) return false;
        return Objects.equals(namespace, that.namespace) && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }
}
