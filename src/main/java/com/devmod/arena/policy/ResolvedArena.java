package com.devmod.arena.policy;

import com.devmod.arena.registry.ArenaTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Result of policy resolution - the selected template and policy combination.
 */
public record ResolvedArena(
    /** The resolved arena template */
    ArenaTemplate template,

    /** The policy that selected this template */
    ArenaPolicy policy,

    /** Unique arena instance ID */
    UUID arenaId,

    /** Resolution timestamp */
    Instant resolvedAt,

    /** Score breakdown for debugging */
    Map<String, Double> scoreBreakdown
) {
    /**
     * Creates a resolved arena with generated ID.
     */
    public static ResolvedArena create(ArenaTemplate template, ArenaPolicy policy, Map<String, Double> scoreBreakdown) {
        return new ResolvedArena(
            template,
            policy,
            UUID.randomUUID(),
            Instant.now(),
            scoreBreakdown
        );
    }

    /**
     * Gets the total score.
     */
    public double totalScore() {
        return scoreBreakdown.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
