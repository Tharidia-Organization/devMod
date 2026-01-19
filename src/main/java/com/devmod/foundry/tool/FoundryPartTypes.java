package com.devmod.foundry.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Built-in part types for foundry tools.
 */
public final class FoundryPartTypes {
    private FoundryPartTypes() {}

    private static final Map<ResourceLocation, FoundryPartType> REGISTRY = new LinkedHashMap<>();

    public static final FoundryPartType TOOL_HEAD = register("tool_head", "head", 1);
    public static final FoundryPartType TOOL_HANDLE = register("tool_handle", "handle", 1);
    public static final FoundryPartType TOOL_BINDING = register("tool_binding", "binding", 1);

    private static FoundryPartType register(String id, String statKey, int cost) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, id);
        FoundryPartType type = new FoundryPartType(key, statKey, cost);
        REGISTRY.put(key, type);
        return type;
    }

    public static FoundryPartType get(ResourceLocation id) {
        return Objects.requireNonNull(REGISTRY.get(id));
    }

    public static Map<ResourceLocation, FoundryPartType> all() {
        return Map.copyOf(REGISTRY);
    }
}
