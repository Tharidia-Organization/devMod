package com.devmod.arena.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class HazardValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(HazardValidator.class);

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
    private static final double MAX_COVERAGE = 0.30; // 30%
    private static final double WARN_COVERAGE = 0.25; // 25%

    @Nullable
    private final HazardTelemetry telemetry;
    @Nullable
    private final Set<String> registeredCustomBuilders;

    public HazardValidator() {
        this(null, null);
    }

    public HazardValidator(@Nullable HazardTelemetry telemetry) {
        this(telemetry, null);
    }

    public HazardValidator(@Nullable HazardTelemetry telemetry, @Nullable Set<String> registeredCustomBuilders) {
        this.telemetry = telemetry;
        this.registeredCustomBuilders = registeredCustomBuilders;
    }

    public ValidationResult validate(ArenaTemplate template, Bounds bounds, List<ArenaTemplate.SpawnSlot> spawnSlots) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<ArenaTemplate.Hazard> hazards = template.hazards() == null ? List.of() : template.hazards();
        if (hazards.size() > MAX_HAZARDS) {
            errors.add("Hazard count %d exceeds max %d".formatted(hazards.size(), MAX_HAZARDS));
            emitValidationFailed(template.id(), "too_many_hazards");
        }

        Map<String, Integer> typeCounts = new HashMap<>();
        double estimatedCoverage = 0.0;

        for (int i = 0; i < hazards.size(); i++) {
            var hazard = hazards.get(i);

            // Whitelist
            if (!SUPPORTED_TYPES.contains(hazard.type())) {
                errors.add("Hazard[%d] unknown type '%s'".formatted(i, hazard.type()));
                emitValidationFailed(template.id(), "unknown_type");
                continue;
            }

            // Type limit
            int count = typeCounts.merge(hazard.type(), 1, Integer::sum);
            int limit = TYPE_LIMITS.getOrDefault(hazard.type(), Integer.MAX_VALUE);
            if (count > limit) {
                errors.add("Hazard[%d] exceeds type limit %d for '%s'".formatted(i, limit, hazard.type()));
                emitValidationFailed(template.id(), "type_limit_" + hazard.type());
            }

            // Parameter checks
            validateParams(template.id(), hazards, hazard, i, bounds, errors, warnings);

            // Overlap with spawn slots (best effort: use center/radius if available)
            if (spawnSlots != null && !spawnSlots.isEmpty()) {
                maybeValidateOverlapWithSpawn(template.id(), hazard, i, spawnSlots, errors);
            }

            // Rough coverage estimate: use radius/area when available
            estimatedCoverage += estimateCoverage(hazard, bounds);
        }

        // Coverage checks (DD8)
        if (estimatedCoverage > MAX_COVERAGE) {
            errors.add("Hazard coverage %.1f%% exceeds max 30%%".formatted(estimatedCoverage * 100));
            emitValidationFailed(template.id(), "coverage_exceeded");
        } else if (estimatedCoverage > WARN_COVERAGE) {
            warnings.add("Hazard coverage %.1f%% > 25%%".formatted(estimatedCoverage * 100));
            emitHighCoverage(template.id(), estimatedCoverage);
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private void validateParams(
        String templateId,
        List<ArenaTemplate.Hazard> hazards,
        ArenaTemplate.Hazard hazard,
        int idx,
        Bounds bounds,
        List<String> errors,
        List<String> warnings
    ) {
        int spanX = bounds.maxX() - bounds.minX() + 1;
        int spanZ = bounds.maxZ() - bounds.minZ() + 1;
        int maxDim = Math.max(spanX, spanZ);
        int half = Math.max(1, maxDim / 2);
        int quarter = Math.max(1, maxDim / 4);

        switch (hazard.type()) {
            case "lava_ring" -> {
                int inner = intParam(hazard, "innerRadius", errors, idx);
                int outer = intParam(hazard, "outerRadius", errors, idx);
                if (inner >= outer) {
                    errors.add(err(idx, "innerRadius must be < outerRadius"));
                }
                int innerMax = Math.max(1, half - 2);
                if (inner < 1 || inner > innerMax) {
                    errors.add(err(idx, "innerRadius out of range 1-%d".formatted(innerMax)));
                }
                if (outer < 1 || outer > half) {
                    errors.add(err(idx, "outerRadius out of range 1-%d".formatted(half)));
                }
            }
            case "void_pit", "lava_pool" -> {
                int radius = intParam(hazard, "radius", errors, idx);
                int radiusCap = Math.max(1, quarter); // size/4 cap
                if (radius > radiusCap) {
                    warnings.add(warn(idx, "radius %d clamped to %d".formatted(radius, radiusCap)));
                    emitValidationFailed(templateId, "radius_clamp");
                    safePut(hazards, idx, hazard, "radius", radiusCap, warnings);
                }
                int[] center = posParam(hazard, "center", errors, idx);
                if (center != null && !bounds.contains(center[0], center[1], center[2])) {
                    errors.add(err(idx, "center out of bounds"));
                }
                // Depth clamp if present
                Object depthObj = hazard.params() != null ? hazard.params().get("depth") : null;
                if (depthObj instanceof Number depthNum) {
                    int depth = depthNum.intValue();
                    if (depth < 1 || depth > 64) {
                        warnings.add(warn(idx, "depth %d clamped to [1,64]".formatted(depth)));
                        int clamped = Math.max(1, Math.min(64, depth));
                        emitValidationFailed(templateId, "depth_clamp");
                        safePut(hazards, idx, hazard, "depth", clamped, warnings);
                    }
                }
            }
            case "spike_trap" -> {
                Object positions = hazard.params() != null ? hazard.params().get("positions") : null;
                if (!(positions instanceof List<?> list)) {
                    errors.add(err(idx, "positions[] required"));
                } else if (list.size() > 20) {
                    errors.add(err(idx, "positions[] exceeds max 20"));
                } else {
                    for (int pi = 0; pi < list.size(); pi++) {
                        Object entry = list.get(pi);
                        if (entry instanceof List<?> pList && pList.size() == 3) {
                            int[] p = new int[]{
                                ((Number) pList.get(0)).intValue(),
                                ((Number) pList.get(1)).intValue(),
                                ((Number) pList.get(2)).intValue()
                            };
                            if (!bounds.contains(p)) {
                                errors.add(err(idx, "positions[" + pi + "] out of bounds"));
                            }
                        }
                    }
                }
                // Optional damage clamp
                Object dmgObj = hazard.params() != null ? hazard.params().get("damage") : null;
                if (dmgObj instanceof Number dmgNum) {
                    double dmg = dmgNum.doubleValue();
                    if (dmg < 0 || dmg > 20.0) {
                        warnings.add(warn(idx, "damage %.2f clamped to [0,20]".formatted(dmg)));
                        emitValidationFailed(templateId, "damage_clamp");
                        safePut(hazards, idx, hazard, "damage", Math.max(0.0, Math.min(20.0, dmg)), warnings);
                    }
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
                    if (hazard.params() != null) {
                        emitValidationFailed(templateId, "coverage_clamp");
                        safePut(hazards, idx, hazard, "coverage", 0.5, warnings);
                    }
                }
            }
            case "falling_blocks" -> {
                int[] area = posParam(hazard, "area", errors, idx);
                if (area != null && !bounds.contains(area[0], area[1], area[2])) {
                    errors.add(err(idx, "area center out of bounds"));
                }
                // Validate interval (tick interval)
                Object intervalObj = hazard.params() != null ? hazard.params().get("interval") : null;
                if (intervalObj instanceof Number intervalNum) {
                    int interval = intervalNum.intValue();
                    if (interval < 20 || interval > 600) {
                        int clamped = Math.max(20, Math.min(600, interval));
                        warnings.add(warn(idx, "interval %d clamped to [20,600]".formatted(interval)));
                        emitValidationFailed(templateId, "interval_clamp");
                        safePut(hazards, idx, hazard, "interval", clamped, warnings);
                    }
                }
                // Validate count
                Object countObj = hazard.params() != null ? hazard.params().get("count") : null;
                if (countObj instanceof Number countNum) {
                    int count = countNum.intValue();
                    if (count < 1) {
                        errors.add(err(idx, "count must be >= 1"));
                    } else if (count > 50) {
                        warnings.add(warn(idx, "count %d is high, may impact performance".formatted(count)));
                    }
                }
            }
            case "custom" -> {
                if (hazard.params() == null || !hazard.params().containsKey("builderId")) {
                    errors.add(err(idx, "custom hazard requires builderId"));
                } else {
                    Object builderId = hazard.params().get("builderId");
                    if (!(builderId instanceof String s) || s.isBlank()) {
                        errors.add(err(idx, "custom hazard builderId must be non-empty string"));
                    } else if (registeredCustomBuilders != null && !registeredCustomBuilders.contains(s)) {
                        errors.add(err(idx, "custom hazard builderId '%s' is not registered".formatted(s)));
                        emitValidationFailed("custom", "builder_not_registered");
                    }
                }
            }
            default -> {}
        }
    }

    private void maybeValidateOverlapWithSpawn(
        String templateId,
        ArenaTemplate.Hazard hazard,
        int idx,
        List<ArenaTemplate.SpawnSlot> spawnSlots,
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
                emitValidationFailed(templateId, "overlap_spawn");
            }
        }
    }

    private double estimateCoverage(ArenaTemplate.Hazard hazard, Bounds bounds) {
        int spanX = bounds.maxX() - bounds.minX() + 1;
        int spanZ = bounds.maxZ() - bounds.minZ() + 1;
        int maxArea = spanX * spanZ;
        if (maxArea <= 0) return 0.0;
        Map<String, Object> params = hazard.params() != null ? hazard.params() : Map.of();

        switch (hazard.type()) {
            case "lava_ring" -> {
                int inner = (int) params.getOrDefault("innerRadius", 0);
                int outer = (int) params.getOrDefault("outerRadius", 0);
                double area = Math.PI * (outer * outer - inner * inner);
                return area / maxArea;
            }
            case "lava_pool", "void_pit" -> {
                Number rNum = (Number) params.getOrDefault("radius", 0);
                double area = Math.PI * rNum.doubleValue() * rNum.doubleValue();
                return area / maxArea;
            }
            case "fire_zone" -> {
                int[] min = posParam(hazard, "min", null, 0);
                int[] max = posParam(hazard, "max", null, 0);
                if (min != null && max != null) {
                    int dx = Math.abs(max[0] - min[0]) + 1;
                    int dz = Math.abs(max[2] - min[2]) + 1;
                    return (dx * dz) / (double) maxArea;
                }
            }
            case "magma_floor" -> {
                Number cov = (Number) params.getOrDefault("coverage", 0.0);
                return Math.min(0.5, cov.doubleValue());
            }
            default -> {}
        }
        return 0.0;
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

    @Nullable
    private int[] posParam(ArenaTemplate.Hazard hazard, String key, @Nullable List<String> errors, int idx) {
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

    private void emitValidationFailed(String templateId, String reason) {
        if (telemetry != null) {
            telemetry.validationFailed(templateId, reason);
        }
    }

    private void emitHighCoverage(String templateId, double coverage) {
        if (telemetry != null) {
            telemetry.highCoverage(templateId, coverage);
        }
    }

    private void safePut(List<ArenaTemplate.Hazard> hazards, int idx, ArenaTemplate.Hazard hazard, String key, Object value, List<String> warnings) {
        Map<String, Object> params = hazard.params();
        if (params == null) {
            return;
        }
        try {
            params.put(key, value);
            return;
        } catch (UnsupportedOperationException ex) {
            Map<String, Object> mutable = new HashMap<>(params);
            mutable.put(key, value);
            try {
                hazards.set(idx, new ArenaTemplate.Hazard(hazard.type(), mutable, hazard.y(), hazard.yMode()));
                warnings.add(warn(idx, "params map was immutable; replaced with mutable copy for clamp"));
            } catch (Exception e) {
                LOGGER.debug("Failed to replace hazard params after clamp: {}", e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to apply hazard param clamp: {}", e.getMessage());
        }
    }

    /**
     * Minimal telemetry hook to avoid hard dependency cycles.
     */
    public interface HazardTelemetry {
        void validationFailed(String templateId, String reason);
        void highCoverage(String templateId, double coverage);
    }
}
