package com.frenkvs.devmod.integration;

import com.frenkvs.devmod.DevMod;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Map;
import java.util.Objects;

/**
 * Optional Pufferfish Attributes compatibility.
 * Provides mapping to external attributes when the mod is present.
 */
public final class PufferfishCompat {
    private static final String MODID = "puffish_attributes";
    private static final boolean LOADED = isLoadedSafe();
    private static final Map<String, String> PATH_MAP = Map.of(
        "armor_shred", "armor_shred",
        "life_steal", "life_steal"
    );

    private PufferfishCompat() {}

    private static boolean isLoadedSafe() {
        try {
            ModList list = ModList.get();
            return list != null && list.isLoaded(MODID);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isCompatEnabled() {
        return LOADED;
    }

    /**
     * Map a DevMod attribute to a Pufferfish equivalent if available.
     * This implementation compares attribute holders directly for robustness.
     */
    public static Attribute map(DeferredHolder<Attribute, Attribute> attribute) {
        if (!LOADED) {
            return attribute.isBound() ? attribute.get() : null;
        }

        String mappedPath = PATH_MAP.get(attribute.getId().getPath());

        if (mappedPath == null) {
            return attribute.get();
        }

        ResourceLocation incoming = attribute.getId();
        ResourceLocation targetId = ResourceLocation.fromNamespaceAndPath(MODID, mappedPath);
        Attribute mapped = BuiltInRegistries.ATTRIBUTE.getOptional(targetId).orElse(null);

        if (mapped != null) {
            DevMod.LOGGER.debug("[PufferfishCompat] Mapped {} to {}", incoming, targetId);
            return mapped;
        }

        DevMod.LOGGER.debug("[PufferfishCompat] Missing {} in {}", targetId, MODID);
        return attribute.isBound() ? attribute.get() : null;
    }

    /**
     * Map a resource-location based attribute to Pufferfish if it is in devmod namespace.
     * Returns holder from provided registry or null if none exists.
     */
    public static Holder.Reference<Attribute> map(ResourceLocation id, Registry<Attribute> registry) {
        if (id == null) return null;
        Holder.Reference<Attribute> original = registry.getHolder(Objects.requireNonNull(id)).orElse(null);
        if (!LOADED || !DevMod.MODID.equals(id.getNamespace())) {
            return original;
        }
        String mappedPath = PATH_MAP.get(id.getPath());
        if (mappedPath == null) {
            return original;
        }
        ResourceLocation targetId = ResourceLocation.fromNamespaceAndPath(MODID, mappedPath);
        return registry.getHolder(Objects.requireNonNull(targetId)).orElse(original);
    }
}
