package com.devmod.arena.registry;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates instance settings against server limits and arena coverage.
 */
public class InstanceSettingsValidator {

    public record Result(boolean valid, List<String> errors, List<String> warnings,
                         int effectiveChunkRadius, int effectiveTickDistance) {}

    /**
     * Validate and clamp instance settings.
     *
     * @param template template with instanceSettings populated
     * @param limits server limits (chunkRadius/tickDistance)
     */
    public Result validate(ArenaTemplate template, InstanceLimits limits) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        ArenaTemplate.InstanceSettings settings = template.instanceSettings();
        if (settings == null) {
            return new Result(true, errors, warnings, limits.maxChunkRadius(), limits.maxTickDistance());
        }

        int effectiveChunkRadius = Math.min(settings.chunkRadius(), limits.maxChunkRadius());
        int effectiveTickDistance = Math.min(settings.tickDistance(), limits.maxTickDistance());

        if (effectiveChunkRadius < settings.chunkRadius()) {
            warnings.add("chunkRadius clamped " + settings.chunkRadius() + " -> " + effectiveChunkRadius);
        }
        if (effectiveTickDistance < settings.tickDistance()) {
            warnings.add("tickDistance clamped " + settings.tickDistance() + " -> " + effectiveTickDistance);
        }

        int maxDim = Math.max(
            template.sizeX() != null ? template.sizeX() : template.size(),
            template.sizeZ() != null ? template.sizeZ() : template.size()
        );
        int requiredChunks = (int) Math.ceil(maxDim / 16.0) + 1;
        if (effectiveChunkRadius < requiredChunks) {
            errors.add("Arena size " + maxDim + " requires chunkRadius >= " + requiredChunks
                + " (effective " + effectiveChunkRadius + ")");
        }

        boolean valid = errors.isEmpty();
        return new Result(valid, errors, warnings, effectiveChunkRadius, effectiveTickDistance);
    }

    /**
     * Server-side limits for instance settings.
     */
    public record InstanceLimits(int maxChunkRadius, int maxTickDistance) {
        public InstanceLimits {
            if (maxChunkRadius <= 0 || maxTickDistance <= 0) {
                throw new IllegalArgumentException("Instance limits must be positive");
            }
        }

        public static InstanceLimits defaults() {
            return new InstanceLimits(8, 10);
        }
    }
}
