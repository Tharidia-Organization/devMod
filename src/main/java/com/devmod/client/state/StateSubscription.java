package com.devmod.client.state;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Subscription interface for state changes.
 *
 * <p>Implements a simple observer pattern for state stores.
 * Subscribers are notified when state changes occur.
 *
 * <p>Usage:
 * <pre>{@code
 * // Subscribe to quest state changes
 * ClientStateManager.INSTANCE.getQuestStore().subscribe(
 *     (oldState, newState) -> {
 *         if (newState.getCurrentWave() > oldState.getCurrentWave()) {
 *             // Wave advanced
 *         }
 *     }
 * );
 * }</pre>
 *
 * @param <T> The state type
 */
@OnlyIn(Dist.CLIENT)
@FunctionalInterface
public interface StateSubscription<T> {

    /**
     * Called when state changes.
     *
     * @param oldState The previous state (null if first update)
     * @param newState The new state
     */
    void onStateChanged(@Nullable T oldState, @Nullable T newState);

    /**
     * Priority for subscription ordering.
     * Lower values are notified first.
     * Default is 0 (normal priority).
     */
    default int priority() {
        return 0;
    }

    /**
     * Whether this subscription should be kept after receiving an error.
     * Default is true (keep subscription).
     */
    default boolean keepOnError() {
        return true;
    }

    /**
     * A unique identifier for this subscription (for debugging/logging).
     * Default is null (anonymous).
     */
    @Nullable
    default String getId() {
        return null;
    }
}
