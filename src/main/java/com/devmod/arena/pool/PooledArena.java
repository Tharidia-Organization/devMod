package com.devmod.arena.pool;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
public class PooledArena {

    private static final Duration UNUSED_THRESHOLD = Duration.ofMinutes(10);

    private final UUID arenaId;
    private final String templateId;
    private final Instant createdAt;
    private final AtomicReference<PoolState> state;

    private volatile Instant lastStateChange;
    private volatile Instant lastUsed;
    private volatile String reservedBy;

    public PooledArena(UUID arenaId, String templateId) {
        this.arenaId = arenaId;
        this.templateId = templateId;
        this.createdAt = Instant.now();
        this.state = new AtomicReference<>(PoolState.BUILDING);
        this.lastStateChange = Instant.now();
        this.lastUsed = null;
        this.reservedBy = null;
    }

    /**
     * Returns the arena ID.
     */
    public UUID getArenaId() {
        return arenaId;
    }

    /**
     * Returns the template ID.
     */
    public String getTemplateId() {
        return templateId;
    }

    /**
     * Returns the creation timestamp.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the current state.
     */
    public PoolState getState() {
        return state.get();
    }

    /**
     * Returns the last state change timestamp.
     */
    public Instant getLastStateChange() {
        return lastStateChange;
    }

    /**
     * Returns the last used timestamp, or null if never used.
     */
    public Instant getLastUsed() {
        return lastUsed;
    }

    /**
     * Returns who reserved this arena, or null if not reserved.
     */
    public String getReservedBy() {
        return reservedBy;
    }

    /**
     * Checks if this arena is unused (READY for more than threshold).
     */
    public boolean isUnused() {
        if (state.get() != PoolState.READY) {
            return false;
        }

        Duration timeSinceStateChange = Duration.between(lastStateChange, Instant.now());
        return timeSinceStateChange.compareTo(UNUSED_THRESHOLD) > 0;
    }

    /**
     * Checks if this arena can be evicted from the pool.
     */
    public boolean canBeEvicted() {
        return state.get().canBeEvicted();
    }

    /**
     * Atomically reserves this arena for use.
     *
     * @param reservedBy Who is reserving the arena
     * @return true if successfully reserved, false if not available
     */
    public boolean reserve(String reservedBy) {
        PoolState current = state.get();
        if (current != PoolState.READY) {
            return false;
        }

        if (state.compareAndSet(PoolState.READY, PoolState.RESERVED)) {
            this.reservedBy = reservedBy;
            this.lastStateChange = Instant.now();
            return true;
        }

        return false;
    }

    /**
     * Attempts to transition to a new state.
     *
     * @param newState The target state
     * @return true if transition succeeded, false if invalid
     */
    public boolean transitionTo(PoolState newState) {
        PoolState current = state.get();

        if (!current.canTransitionTo(newState)) {
            return false;
        }

        if (state.compareAndSet(current, newState)) {
            this.lastStateChange = Instant.now();

            // Track usage
            if (newState == PoolState.IN_USE) {
                this.lastUsed = Instant.now();
            }

            // Clear reservation on cleanup
            if (newState == PoolState.CLEANUP || newState == PoolState.READY) {
                this.reservedBy = null;
            }

            return true;
        }

        return false;
    }

    /**
     * Returns time since last state change.
     */
    public Duration getTimeSinceStateChange() {
        return Duration.between(lastStateChange, Instant.now());
    }

    /**
     * Returns time since creation.
     */
    public Duration getAge() {
        return Duration.between(createdAt, Instant.now());
    }

    @Override
    public String toString() {
        return "PooledArena{" +
            "arenaId=" + arenaId +
            ", templateId='" + templateId + '\'' +
            ", state=" + state.get() +
            ", age=" + getAge().toMinutes() + "min" +
            '}';
    }
}
