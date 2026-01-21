package com.devmod.foundry.data.listener;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Utility that caches a list of resources for quick existence checks. */
public class FoundryResourceValidator implements FoundryEarlySafeManagerReloadListener, Predicate<ResourceLocation> {
    private final String folder;
    private final int trim;
    private final String extension;
    protected Set<ResourceLocation> resources;

    public FoundryResourceValidator(String folder, String trim, String extension) {
        this.folder = folder;
        this.trim = trim.length() + 1;
        this.extension = extension;
        this.resources = Set.of();
    }

    @Override
    public void onReloadSafe(ResourceManager manager) {
        int extensionLength = extension.length();
        this.resources = manager.listResources(folder, loc -> loc.getPath().endsWith(extension))
            .keySet()
            .stream()
            .filter(location -> {
                String path = location.getPath();
                int endIndex = path.length() - extensionLength;
                return trim <= endIndex && trim >= 0 && endIndex >= 0;
            })
            .map(location -> {
                String path = location.getPath();
                return ResourceLocation.fromNamespaceAndPath(
                    location.getNamespace(),
                    path.substring(trim, path.length() - extensionLength)
                );
            })
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean test(ResourceLocation location) {
        return resources.contains(location);
    }

    /** Clears the cached resources to save memory. */
    public void clear() {
        resources = Set.of();
    }
}
