package com.devmod.arena.registry;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public class InstanceSettingsValidator {

    public record Result(boolean valid, List<String> errors, List<String> warnings,
                         int effectiveChunkRadius, int effectiveTickDistance) {}

    /**
     * Optional telemetry hook for clamping/coverage events.
     */
    public interface Telemetry {
        void clamped(String templateId, String field, int requested, int effective, int serverMax);
        void coverageInsufficient(String templateId, int maxDim, int requiredChunks, int effectiveChunkRadius);
    }

    @Nullable
    private final Telemetry telemetry;

    public InstanceSettingsValidator() {
        this(null);
    }

    public InstanceSettingsValidator(@Nullable Telemetry telemetry) {
        this.telemetry = telemetry;
    }

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
            emitClamped(template.id(), "chunkRadius", settings.chunkRadius(), effectiveChunkRadius, limits.maxChunkRadius());
        }
        if (effectiveTickDistance < settings.tickDistance()) {
            warnings.add("tickDistance clamped " + settings.tickDistance() + " -> " + effectiveTickDistance);
            emitClamped(template.id(), "tickDistance", settings.tickDistance(), effectiveTickDistance, limits.maxTickDistance());
        }

        Integer sizeXVal = template.sizeX();
        Integer sizeZVal = template.sizeZ();
        int maxDim = Math.max(
            sizeXVal != null ? sizeXVal : template.size(),
            sizeZVal != null ? sizeZVal : template.size()
        );
        int requiredChunks = (int) Math.ceil(maxDim / 16.0) + 1;
        if (effectiveChunkRadius < requiredChunks) {
            errors.add("Arena size " + maxDim + " requires chunkRadius >= " + requiredChunks
                + " (effective " + effectiveChunkRadius + ")");
            emitCoverageInsufficient(template.id(), maxDim, requiredChunks, effectiveChunkRadius);
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

    private void emitClamped(String templateId, String field, int requested, int effective, int serverMax) {
        if (telemetry != null) {
            telemetry.clamped(templateId, field, requested, effective, serverMax);
        }
    }

    private void emitCoverageInsufficient(String templateId, int maxDim, int requiredChunks, int effectiveChunkRadius) {
        if (telemetry != null) {
            telemetry.coverageInsufficient(templateId, maxDim, requiredChunks, effectiveChunkRadius);
        }
    }
}
