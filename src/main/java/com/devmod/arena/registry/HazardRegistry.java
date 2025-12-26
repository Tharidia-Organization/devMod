package com.devmod.arena.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
public final class HazardRegistry {

    /** Global instance limit across all hazard types. */
    public static final int MAX_TOTAL_HAZARDS = 50;

    /** Maximum floor coverage by hazards (30%). */
    public static final double MAX_COVERAGE = 0.30;

    /** Warning threshold for coverage (25%). */
    public static final double WARN_COVERAGE = 0.25;

    private static final List<HazardTypeInfo> BUILTIN_TYPES = List.of(
        new HazardTypeInfo("lava_ring", 3,
            List.of("innerRadius", "outerRadius"),
            List.of("material"),
            "Ring of lava surrounding the arena"),
        new HazardTypeInfo("lava_pool", 5,
            List.of("radius", "center"),
            List.of("depth", "material"),
            "Circular lava pool at specified position"),
        new HazardTypeInfo("void_pit", 3,
            List.of("radius", "center"),
            List.of("depth"),
            "Void pit that kills on contact"),
        new HazardTypeInfo("spike_trap", 20,
            List.of("positions"),
            List.of("damage", "cooldown"),
            "Spike traps at specified positions"),
        new HazardTypeInfo("fire_zone", 5,
            List.of("min", "max"),
            List.of("damage", "interval"),
            "Fire damage zone within AABB"),
        new HazardTypeInfo("magma_floor", 1,
            List.of("coverage"),
            List.of("pattern"),
            "Magma block floor with coverage percentage"),
        new HazardTypeInfo("falling_blocks", 2,
            List.of("area"),
            List.of("interval", "count", "blockType"),
            "Falling blocks in specified area"),
        new HazardTypeInfo("custom", 2,
            List.of("builderId"),
            List.of(),
            "Custom hazard via registered builder")
    );

    private final Map<String, HazardTypeInfo> registry = new LinkedHashMap<>();
    private final Set<String> customBuilders = new HashSet<>();

    public HazardRegistry() {
        for (HazardTypeInfo info : BUILTIN_TYPES) {
            registry.put(info.type(), info);
        }
    }

    /**
     * Registers a hazard type.
     */
    public void register(HazardTypeInfo info) {
        registry.put(info.type(), info);
    }

    /**
     * Registers a custom hazard builder ID.
     */
    public void registerCustomBuilder(String builderId) {
        if (builderId != null && !builderId.isBlank()) {
            customBuilders.add(builderId);
        }
    }

    /**
     * Registers multiple custom builder IDs.
     */
    public void registerCustomBuilders(Collection<String> builderIds) {
        if (builderIds != null) {
            builderIds.forEach(this::registerCustomBuilder);
        }
    }

    /**
     * Checks if a hazard type is registered.
     */
    public boolean isRegistered(String type) {
        return registry.containsKey(type);
    }

    /**
     * Checks if a custom builder ID is registered.
     */
    public boolean isCustomBuilderRegistered(String builderId) {
        return customBuilders.contains(builderId);
    }

    /**
     * Gets info for a hazard type.
     */
    public Optional<HazardTypeInfo> get(String type) {
        return Optional.ofNullable(registry.get(type));
    }

    /**
     * Gets the instance limit for a hazard type.
     */
    public int getLimit(String type) {
        HazardTypeInfo info = registry.get(type);
        return info != null ? info.instanceLimit() : 0;
    }

    /**
     * Returns all registered hazard type names.
     */
    public Set<String> getRegisteredTypes() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    /**
     * Returns all registered custom builder IDs.
     */
    public Set<String> getRegisteredCustomBuilders() {
        return Collections.unmodifiableSet(customBuilders);
    }

    /**
     * Validates a hazard against registry rules.
     */
    public ValidationResult validate(ArenaTemplate.Hazard hazard, int index) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String type = hazard.type();

        // Check type is registered
        if (!isRegistered(type)) {
            errors.add("Hazard[%d]: unknown type '%s'. Valid types: %s".formatted(
                index, type, String.join(", ", getRegisteredTypes())));
            return new ValidationResult(false, errors, warnings);
        }

        HazardTypeInfo info = registry.get(type);
        Map<String, Object> params = hazard.params() != null ? hazard.params() : Map.of();

        // Check required params
        for (String required : info.requiredParams()) {
            if (!params.containsKey(required)) {
                errors.add("Hazard[%d]: missing required param '%s' for type '%s'".formatted(
                    index, required, type));
            }
        }

        // Check custom builder registration
        if ("custom".equals(type)) {
            Object builderId = params.get("builderId");
            if (builderId instanceof String s && !s.isBlank()) {
                if (!isCustomBuilderRegistered(s)) {
                    errors.add("Hazard[%d]: custom builder '%s' is not registered".formatted(index, s));
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    // === Records ===

    /**
     * Information about a hazard type.
     */
    public record HazardTypeInfo(
        String type,
        int instanceLimit,
        List<String> requiredParams,
        List<String> optionalParams,
        String description
    ) {
        public HazardTypeInfo {
            requiredParams = List.copyOf(requiredParams);
            optionalParams = List.copyOf(optionalParams);
        }

        /**
         * Returns all valid parameter names for this type.
         */
        public Set<String> allParams() {
            Set<String> all = new HashSet<>(requiredParams);
            all.addAll(optionalParams);
            return all;
        }
    }

    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of(), List.of());
        }
    }
}
