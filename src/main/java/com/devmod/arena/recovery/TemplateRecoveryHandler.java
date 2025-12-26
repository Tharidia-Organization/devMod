package com.devmod.arena.recovery;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.registry.ArenaTemplate;
public class TemplateRecoveryHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateRecoveryHandler.class);

    /**
     * DD27: Default fallback template ID.
     */
    public static final String DEFAULT_FALLBACK_TEMPLATE = "default_flat_64";

    /**
     * DD27: Maximum fallback chain depth.
     */
    public static final int MAX_FALLBACK_DEPTH = 3;

    /**
     * Result of a recovery attempt.
     */
    public record RecoveryResult(
        boolean recovered,
        String originalTemplateId,
        String resolvedTemplateId,
        RecoveryReason reason,
        int fallbackDepth,
        Instant timestamp
    ) {
        public boolean usedFallback() {
            return !originalTemplateId.equals(resolvedTemplateId);
        }
    }

    /**
     * Reason for recovery.
     */
    public enum RecoveryReason {
        /** Template not found in registry */
        TEMPLATE_NOT_FOUND,
        /** Template found but disabled */
        TEMPLATE_DISABLED,
        /** Template found but incompatible version */
        VERSION_MISMATCH,
        /** Template loading failed */
        LOAD_FAILURE,
        /** No recovery needed */
        NONE
    }

    /**
     * Recovery event for telemetry.
     */
    public record RecoveryEvent(
        UUID eventId,
        String originalTemplateId,
        String resolvedTemplateId,
        RecoveryReason reason,
        UUID playerId,
        Instant timestamp,
        Map<String, Object> metadata
    ) {}

    /**
     * Interface for template lookup.
     */
    public interface TemplateResolver {
        Optional<ArenaTemplate> resolve(String templateId);
        boolean isEnabled(String templateId);
        ArenaTemplate getDefault();
    }

    /**
     * Interface for recovery telemetry.
     */
    public interface RecoveryTelemetry {
        void emit(RecoveryEvent event);
    }

    private final TemplateResolver resolver;
    private final RecoveryTelemetry telemetry;
    private final Map<String, String> fallbackChain;
    private final Map<String, AtomicInteger> missingTemplateCounts = new ConcurrentHashMap<>();

    public TemplateRecoveryHandler(TemplateResolver resolver, RecoveryTelemetry telemetry) {
        this.resolver = resolver;
        this.telemetry = telemetry;
        this.fallbackChain = new ConcurrentHashMap<>();

        // Default fallback chain
        fallbackChain.put("boss_arena", "standard_arena");
        fallbackChain.put("standard_arena", "simple_arena");
        fallbackChain.put("simple_arena", DEFAULT_FALLBACK_TEMPLATE);
    }

    /**
     * Recovers a template, falling back if necessary.
     * DD27: Fallback to default when template missing.
     *
     * @param templateId the requested template ID
     * @param playerId the requesting player (for telemetry)
     * @return recovery result with resolved template
     */
    public RecoveryResult recover(String templateId, UUID playerId) {
        return recover(templateId, playerId, 0);
    }

    private RecoveryResult recover(String templateId, UUID playerId, int depth) {
        // Check depth limit
        if (depth >= MAX_FALLBACK_DEPTH) {
            LOGGER.warn("Max fallback depth reached for template: {}, using default", templateId);
            return createResult(templateId, DEFAULT_FALLBACK_TEMPLATE,
                RecoveryReason.TEMPLATE_NOT_FOUND, depth, playerId);
        }

        // Try to resolve the template
        Optional<ArenaTemplate> template = resolver.resolve(templateId);

        if (template.isEmpty()) {
            // Template not found - try fallback chain
            trackMissingTemplate(templateId);

            String fallback = fallbackChain.getOrDefault(templateId, DEFAULT_FALLBACK_TEMPLATE);
            LOGGER.info("Template '{}' not found, trying fallback: {}", templateId, fallback);

            RecoveryResult fallbackResult = recover(fallback, playerId, depth + 1);

            // Emit telemetry for the original missing template
            emitRecoveryEvent(templateId, fallbackResult.resolvedTemplateId(),
                RecoveryReason.TEMPLATE_NOT_FOUND, playerId);

            return new RecoveryResult(
                true,
                templateId,
                fallbackResult.resolvedTemplateId(),
                RecoveryReason.TEMPLATE_NOT_FOUND,
                depth + 1,
                Instant.now()
            );
        }

        // Check if template is enabled
        if (!resolver.isEnabled(templateId)) {
            String fallback = fallbackChain.getOrDefault(templateId, DEFAULT_FALLBACK_TEMPLATE);
            LOGGER.info("Template '{}' is disabled, trying fallback: {}", templateId, fallback);

            emitRecoveryEvent(templateId, fallback, RecoveryReason.TEMPLATE_DISABLED, playerId);

            return recover(fallback, playerId, depth + 1);
        }

        // Template found and enabled
        return new RecoveryResult(
            true,
            templateId,
            templateId,
            RecoveryReason.NONE,
            0,
            Instant.now()
        );
    }

    /**
     * Resolves a template with automatic recovery.
     * DD27: Returns resolved template, never null.
     *
     * @param templateId the requested template ID
     * @param playerId the requesting player
     * @return the resolved template (may be fallback)
     */
    public ArenaTemplate resolveWithRecovery(String templateId, UUID playerId) {
        RecoveryResult result = recover(templateId, playerId);

        Optional<ArenaTemplate> template = resolver.resolve(result.resolvedTemplateId());

        if (template.isEmpty()) {
            // Even fallback failed - use hardcoded default
            LOGGER.error("All fallbacks failed for template: {}, using system default", templateId);
            return resolver.getDefault();
        }

        return template.get();
    }

    /**
     * Configures a fallback for a template.
     *
     * @param templateId the template ID
     * @param fallbackId the fallback template ID
     */
    public void setFallback(String templateId, String fallbackId) {
        fallbackChain.put(templateId, fallbackId);
        LOGGER.debug("Configured fallback: {} -> {}", templateId, fallbackId);
    }

    /**
     * Removes a fallback configuration.
     *
     * @param templateId the template ID
     */
    public void removeFallback(String templateId) {
        fallbackChain.remove(templateId);
    }

    /**
     * Gets the fallback chain for a template.
     *
     * @param templateId the starting template
     * @return the fallback chain as a string
     */
    public String getFallbackChain(String templateId) {
        StringBuilder chain = new StringBuilder(templateId);
        String current = templateId;
        int depth = 0;

        while (fallbackChain.containsKey(current) && depth < MAX_FALLBACK_DEPTH) {
            current = fallbackChain.get(current);
            chain.append(" -> ").append(current);
            depth++;
        }

        return chain.toString();
    }

    /**
     * Gets the count of missing template occurrences.
     *
     * @param templateId the template ID
     * @return count of missing template events
     */
    public int getMissingCount(String templateId) {
        AtomicInteger count = missingTemplateCounts.get(templateId);
        return count != null ? count.get() : 0;
    }

    /**
     * Gets all missing template statistics.
     *
     * @return map of template ID to missing count
     */
    public Map<String, Integer> getMissingStats() {
        Map<String, Integer> stats = new ConcurrentHashMap<>();
        missingTemplateCounts.forEach((k, v) -> stats.put(k, v.get()));
        return stats;
    }

    /**
     * Clears missing template statistics.
     */
    public void clearStats() {
        missingTemplateCounts.clear();
    }

    private void trackMissingTemplate(String templateId) {
        missingTemplateCounts.computeIfAbsent(templateId, k -> new AtomicInteger(0))
            .incrementAndGet();
    }

    private RecoveryResult createResult(String original, String resolved,
            RecoveryReason reason, int depth, UUID playerId) {
        emitRecoveryEvent(original, resolved, reason, playerId);
        return new RecoveryResult(true, original, resolved, reason, depth, Instant.now());
    }

    private void emitRecoveryEvent(String original, String resolved,
            RecoveryReason reason, UUID playerId) {
        if (telemetry != null && reason != RecoveryReason.NONE) {
            RecoveryEvent event = new RecoveryEvent(
                UUID.randomUUID(),
                original,
                resolved,
                reason,
                playerId,
                Instant.now(),
                Map.of(
                    "fallbackChain", getFallbackChain(original),
                    "missingCount", getMissingCount(original)
                )
            );
            telemetry.emit(event);
        }
    }
}
