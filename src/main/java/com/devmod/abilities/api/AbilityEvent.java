package com.devmod.abilities.api;

import java.util.UUID;

import javax.annotation.Nullable;

import com.devmod.abilities.DodgeAbilitySystem.DodgeDirection;

/**
 * Sealed hierarchy of ability system events.
 *
 * <p>Provides extension points for addons to react to or modify dash/dodge behavior.
 * Pre-events can be cancelled by listeners to prevent the ability from executing.
 * Post-events are informational and fired after the ability completes.</p>
 *
 * <p>Example usage with pattern matching:</p>
 * <pre>{@code
 * switch (event) {
 *     case DodgePre dp -> handleDodgePre(dp);
 *     case DodgePost dp -> handleDodgePost(dp);
 *     case DashPre dp -> handleDashPre(dp);
 *     case DashPost dp -> handleDashPost(dp);
 *     case PerfectDodge pd -> handlePerfectDodge(pd);
 * }
 * }</pre>
 */
public sealed interface AbilityEvent {

    /** Player this event belongs to. */
    UUID playerId();

    /** Server tick when event occurred. */
    long timestamp();

    // === Dodge Events ===

    /**
     * Fired before a dodge is executed. Listeners returning {@code true} from
     * {@link IAbilityEventListener#onDodgePre} will cancel the dodge.
     */
    record DodgePre(
        UUID playerId,
        long timestamp,
        @Nullable DodgeDirection direction,
        int chargesAvailable
    ) implements AbilityEvent {}

    /**
     * Fired after a successful dodge.
     */
    record DodgePost(
        UUID playerId,
        long timestamp,
        @Nullable DodgeDirection direction,
        int chargesRemaining,
        int totalDodgeCount
    ) implements AbilityEvent {}

    // === Dash Events ===

    /**
     * Fired before a dash is executed. Listeners returning {@code true} from
     * {@link IAbilityEventListener#onDashPre} will cancel the dash.
     */
    record DashPre(
        UUID playerId,
        long timestamp,
        int chargesAvailable
    ) implements AbilityEvent {}

    /**
     * Fired after a successful dash.
     */
    record DashPost(
        UUID playerId,
        long timestamp,
        int chargesRemaining,
        int totalDashCount
    ) implements AbilityEvent {}

    // === Special Events ===

    /**
     * Fired when a perfect dodge is triggered (damage negated within the perfect dodge window).
     */
    record PerfectDodge(
        UUID playerId,
        long timestamp,
        float damageNegated,
        String damageSourceId,
        int totalPerfectDodges
    ) implements AbilityEvent {}
}
