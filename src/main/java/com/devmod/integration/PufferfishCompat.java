package com.devmod.integration;

import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/**
 * Compatibility layer for Pufferfish's Skills mod.
 *
 * <p>Provides utilities for mapping between string IDs and registry holders,
 * particularly useful for attribute lookups in modded environments.
 */
public final class PufferfishCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(PufferfishCompat.class);

    private PufferfishCompat() {}

    /**
     * Map a string ID to a registry holder.
     *
     * @param id The resource location string (e.g., "minecraft:generic.max_health")
     * @param registry The registry to look up in
     * @return The holder if found, null otherwise
     */
    @Nullable
    public static <T> Holder<T> map(String id, Registry<T> registry) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        try {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null) {
                LOGGER.warn("[PufferfishCompat] Invalid resource location: {}", id);
                return null;
            }

            Optional<Holder.Reference<T>> holder = registry.getHolder(location);
            return holder.orElse(null);
        } catch (Exception e) {
            LOGGER.debug("[PufferfishCompat] Failed to map '{}': {}", id, e.getMessage());
            return null;
        }
    }

    /**
     * Check if Pufferfish's Skills mod is loaded.
     */
    public static boolean isLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded("puffish_skills");
    }

    /**
     * Get the mod version if loaded.
     */
    @Nullable
    public static String getVersion() {
        return net.neoforged.fml.ModList.get()
            .getModContainerById("puffish_skills")
            .map(c -> c.getModInfo().getVersion().toString())
            .orElse(null);
    }
}
