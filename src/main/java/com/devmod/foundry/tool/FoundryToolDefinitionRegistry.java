package com.devmod.foundry.tool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/**
 * Registry for foundry tool definitions.
 */
public final class FoundryToolDefinitionRegistry {
    private FoundryToolDefinitionRegistry() {}

    private static final Map<ResourceLocation, FoundryToolDefinition> DEFINITIONS = new LinkedHashMap<>();

    public static void clear() {
        DEFINITIONS.clear();
    }

    public static void register(FoundryToolDefinition definition) {
        DEFINITIONS.put(definition.id(), definition);
    }

    public static Collection<FoundryToolDefinition> all() {
        return java.util.List.copyOf(DEFINITIONS.values());
    }

    public static FoundryToolDefinition get(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }

    public static Optional<FoundryToolDefinition> findMatch(java.util.List<FoundryPartType> provided) {
        FoundryToolDefinition best = null;
        for (FoundryToolDefinition def : DEFINITIONS.values()) {
            if (!def.matches(provided)) {
                continue;
            }
            if (best == null || def.priority() > best.priority()) {
                best = def;
            }
        }
        return Optional.ofNullable(best);
    }
}
