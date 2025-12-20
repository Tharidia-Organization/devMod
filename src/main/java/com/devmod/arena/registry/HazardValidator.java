package com.devmod.arena.registry;

import java.util.*;

/**
 * Hazard validation according to TODO_ARENA_TEMPLATE v2.23 (DD8).
 */
public class HazardValidator {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "lava_ring", "lava_pool", "void_pit", "spike_trap",
        "fire_zone", "magma_floor", "falling_blocks", "custom"
    );

    private static final Map<String, Integer> TYPE_LIMITS = Map.of(
        "lava_ring", 3,
        "lava_pool", 5,
        "void_pit", 3,
        "spike_trap", 20,
        "fire_zone", 5,
        "magma_floor", 1,
        "falling_blocks", 2,
        "custom", 2
    );

    private static final int MAX_HAZARDS = 50;

    public ValidationResult validate(ArenaTemplate template, Bounds bounds, List<ArenaTemplate.SpawnSlot> spawnSlots) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<ArenaTemplate.Hazard> hazards = template.hazards() == null ? List.of() : template.hazards();
        if (hazards.size() > MAX_HAZARDS) {
            errors.add("Hazard count %d exceeds max %d".formatted(hazards.size(), MAX_HAZARDS));
        }

        Map<String, Integer> typeCounts = new HashMap<>();

        for (int i = 0; i < hazards.size(); i++) {
            var hazard = hazards.get(i);

            // Whitelist
            if (!SUPPORTED_TYPES.contains(hazard.type())) {
                errors.add("Hazard[%d] unknown type '%s'".formatted(i, hazard.type()));
                continue;
            }

            // Type limit
            typeCounts.merge(hazard.type(), 1, Integer::sum);
            int limit = TYPE_LIMITS.getOrDefault(hazard.type(), Integer.MAX_VALUE);
            if (typeCounts.get(hazard.type()) > limit) {
                errors.add("Hazard[%d] exceeds type limit %d for '%s'".formatted(i, limit, hazard.type()));
            }

            // Parameter checks
            validateParams(hazard, i, bounds, errors, warnings);

            // Overlap with spawn slots (best effort: use center/radius if available)
            if (spawnSlots != null && !spawnSlots.isEmpty()) {
                maybeValidateOverlapWithSpawn(hazard, i, spawnSlots, bounds, errors);
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private void validateParams(
        ArenaTemplate.Hazard hazard,
        int idx,
        Bounds bounds,
        List<String> errors,
        List<String> warnings
    ) {
        int maxRadius = bounds.maxHorizontalRadius();

        switch (hazard.type()) {
            case "lava_ring" -> {
                int inner = intParam(hazard, "innerRadius", errors, idx);
                int outer = intParam(hazard, "outerRadius", errors, idx);
                if (inner >= outer) {
                    errors.add(err(idx, "innerRadius must be < outerRadius"));
                }
                if (inner < 1 || inner > maxRadius - 2) {
                    errors.add(err(idx, "innerRadius out of range 1-%d".formatted(maxRadius - 2)));
                }
                if (outer < 1 || outer > maxRadius) {
                    errors.add(err(idx, "outerRadius out of range 1-%d".formatted(maxRadius)));
                }
            }
            case "void_pit", "lava_pool" -> {
                int radius = intParam(hazard, "radius", errors, idx);
                if (radius > maxRadius / 2) {
                    warnings.add(warn(idx, "radius %d clamped to %d".formatted(radius, maxRadius / 2)));
                }
                int[] center = posParam(hazard, "center", errors, idx);
                if (center != null && !bounds.contains(center[0], center[1], center[2])) {
                    errors.add(err(idx, "center out of bounds"));
                }
            }
            case "spike_trap" -> {
                Object positions = hazard.params() != null ? hazard.params().get("positions") : null;
                if (!(positions instanceof List<?> list)) {
                    errors.add(err(idx, "positions[] required"));
                } else if (list.size() > 20) {
                    errors.add(err(idx, "positions[] exceeds max 20"));
                }
            }
            case "fire_zone" -> {
                int[] min = posParam(hazard, "min", errors, idx);
                int[] max = posParam(hazard, "max", errors, idx);
                if (min != null && max != null && !bounds.containsAabb(min, max)) {
                    errors.add(err(idx, "fire_zone AABB out of bounds"));
                }
            }
            case "magma_floor" -> {
                double coverage = doubleParam(hazard, "coverage", errors, idx);
                if (coverage > 0.5) {
                    warnings.add(warn(idx, "coverage %.2f clamped to 0.5".formatted(coverage)));
                }
            }
            case "falling_blocks" -> {
                int[] area = posParam(hazard, "area", errors, idx);
                if (area != null && !bounds.contains(area[0], area[1], area[2])) {
                    errors.add(err(idx, "area center out of bounds"));
                }
            }
            case "custom" -> {
                if (hazard.params() == null || !hazard.params().containsKey("builderId")) {
                    errors.add(err(idx, "custom hazard requires builderId"));
                }
            }
            default -> {}
        }
    }

    private void maybeValidateOverlapWithSpawn(
        ArenaTemplate.Hazard hazard,
        int idx,
        List<ArenaTemplate.SpawnSlot> spawnSlots,
        Bounds bounds,
        List<String> errors
    ) {
        // Only best-effort if center+radius present
        int[] center = posParam(hazard, "center", null, idx);
        Number rNum = hazard.params() != null ? (Number) hazard.params().get("radius") : null;
        if (center == null || rNum == null) return;

        int radius = rNum.intValue();
        for (int i = 0; i < spawnSlots.size(); i++) {
            var slot = spawnSlots.get(i);
            int[] pos = slot.pos();
            if (pos == null || pos.length != 3) continue;
            int sx = pos[0];
            int sz = pos[2];
            // horizontal check only
            int dx = sx - center[0];
            int dz = sz - center[2];
            if ((dx * dx + dz * dz) <= radius * radius) {
                errors.add("Hazard[%d] overlaps SpawnSlot[%d]".formatted(idx, i));
            }
        }
    }

    private int intParam(ArenaTemplate.Hazard hazard, String key, List<String> errors, int idx) {
        Object v = hazard.params() != null ? hazard.params().get(key) : null;
        if (v instanceof Number n) return n.intValue();
        errors.add(err(idx, "%s required".formatted(key)));
        return 0;
    }

    private double doubleParam(ArenaTemplate.Hazard hazard, String key, List<String> errors, int idx) {
        Object v = hazard.params() != null ? hazard.params().get(key) : null;
        if (v instanceof Number n) return n.doubleValue();
        errors.add(err(idx, "%s required".formatted(key)));
        return 0.0;
    }

    private int[] posParam(ArenaTemplate.Hazard hazard, String key, List<String> errors, int idx) {
        Object v = hazard.params() != null ? hazard.params().get(key) : null;
        if (v instanceof List<?> list && list.size() == 3) {
            int[] arr = new int[3];
            for (int i = 0; i < 3; i++) {
                Object elem = list.get(i);
                if (elem instanceof Number n) arr[i] = n.intValue();
                else {
                    if (errors != null) errors.add(err(idx, "%s[%d] must be number".formatted(key, i)));
                    return null;
                }
            }
            return arr;
        }
        if (errors != null) errors.add(err(idx, "%s must be int[3]".formatted(key)));
        return null;
    }

    private String err(int idx, String msg) {
        return "Hazard[%d]: %s".formatted(idx, msg);
    }

    private String warn(int idx, String msg) {
        return "Hazard[%d]: %s".formatted(idx, msg);
    }
}
