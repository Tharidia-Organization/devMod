package com.devmod.arena.instance;

import com.devmod.arena.identity.ArenaIdentity;
import com.devmod.arena.policy.ArenaPolicy;
import com.devmod.arena.pool.PoolState;
import com.devmod.arena.registry.ArenaTemplate;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DD2: Unified Arena Instance (L2 Runtime Layer).
 *
 * <p>ArenaInstance combines:
 * <ul>
 *   <li>L1 Template: Physical layout (ArenaTemplate)</li>
 *   <li>L2 Policy: Gameplay rules (ArenaPolicy)</li>
 *   <li>Identity: Unique identification (ArenaIdentity)</li>
 *   <li>State: Lifecycle state machine (PoolState)</li>
 * </ul>
 *
 * <p>This is the primary runtime representation of an arena, used for:
 * <ul>
 *   <li>Context passing between systems</li>
 *   <li>State tracking and transitions</li>
 *   <li>Unified access to template + policy data</li>
 * </ul>
 *
 * <p>State machine transitions (DD64):
 * <pre>
 *   BUILDING → READY → RESERVED → IN_USE → CLEANUP → READY (recycled)
 *                                                  └→ DISPOSED (evicted)
 * </pre>
 */
public class ArenaInstance {

    private final ArenaIdentity identity;
    private final ArenaTemplate template;
    private final ArenaPolicy policy;
    private final Instant createdAt;

    private final AtomicReference<PoolState> state;
    private volatile Instant lastStateChange;
    private volatile UUID reservedByPartyId;
    private volatile Instant matchStartTime;
    private volatile Instant matchEndTime;

    private ArenaInstance(
            ArenaIdentity identity,
            ArenaTemplate template,
            ArenaPolicy policy,
            PoolState initialState) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.template = Objects.requireNonNull(template, "template");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.createdAt = Instant.now();
        this.state = new AtomicReference<>(initialState);
        this.lastStateChange = this.createdAt;
    }

    // === Factory Methods ===

    /**
     * Creates a new ArenaInstance in BUILDING state.
     */
    public static ArenaInstance create(
            ArenaIdentity identity,
            ArenaTemplate template,
            ArenaPolicy policy) {
        return new ArenaInstance(identity, template, policy, PoolState.BUILDING);
    }

    /**
     * Creates a new ArenaInstance that is already READY.
     */
    public static ArenaInstance createReady(
            ArenaIdentity identity,
            ArenaTemplate template,
            ArenaPolicy policy) {
        return new ArenaInstance(identity, template, policy, PoolState.READY);
    }

    // === Identity ===

    public ArenaIdentity identity() {
        return identity;
    }

    public UUID arenaId() {
        return identity.arenaId();
    }

    public String templateId() {
        return template.id();
    }

    public String policyId() {
        return policy.id();
    }

    public String formattedRoomId() {
        return identity.formattedRoomId();
    }

    // === L1 Template Access ===

    public ArenaTemplate template() {
        return template;
    }

    public int size() {
        return template.size();
    }

    public Optional<Integer> sizeX() {
        return Optional.ofNullable(template.sizeX());
    }

    public Optional<Integer> sizeZ() {
        return Optional.ofNullable(template.sizeZ());
    }

    // === L2 Policy Access ===

    public ArenaPolicy policy() {
        return policy;
    }

    public double policyWeight() {
        return policy.weight();
    }

    public int policyPriority() {
        return policy.priority();
    }

    // === State Machine ===

    public PoolState state() {
        return state.get();
    }

    public Instant lastStateChange() {
        return lastStateChange;
    }

    /**
     * DD2: Transitions to a new state if valid.
     *
     * @param targetState The target state
     * @return true if transition succeeded, false if invalid
     */
    public boolean transitionTo(PoolState targetState) {
        PoolState current = state.get();
        if (!current.canTransitionTo(targetState)) {
            return false;
        }

        if (state.compareAndSet(current, targetState)) {
            lastStateChange = Instant.now();

            // Handle state-specific updates
            if (targetState == PoolState.IN_USE) {
                matchStartTime = Instant.now();
            } else if (targetState == PoolState.CLEANUP && current == PoolState.IN_USE) {
                matchEndTime = Instant.now();
            } else if (targetState == PoolState.READY) {
                // Reset match tracking on recycle
                reservedByPartyId = null;
                matchStartTime = null;
                matchEndTime = null;
            }

            return true;
        }
        return false;
    }

    /**
     * Marks the arena as READY after build completion.
     */
    public boolean markReady() {
        return transitionTo(PoolState.READY);
    }

    /**
     * Reserves the arena for a party.
     */
    public boolean reserve(UUID partyId) {
        if (transitionTo(PoolState.RESERVED)) {
            this.reservedByPartyId = partyId;
            return true;
        }
        return false;
    }

    /**
     * Marks the arena as in use.
     */
    public boolean markInUse() {
        return transitionTo(PoolState.IN_USE);
    }

    /**
     * Starts cleanup process.
     */
    public boolean startCleanup() {
        return transitionTo(PoolState.CLEANUP);
    }

    // === Match Tracking ===

    public Optional<UUID> reservedByPartyId() {
        return Optional.ofNullable(reservedByPartyId);
    }

    public Optional<Instant> matchStartTime() {
        return Optional.ofNullable(matchStartTime);
    }

    public Optional<Instant> matchEndTime() {
        return Optional.ofNullable(matchEndTime);
    }

    /**
     * Returns match duration in milliseconds, or empty if match hasn't ended.
     */
    public Optional<Long> matchDurationMs() {
        if (matchStartTime == null) {
            return Optional.empty();
        }
        Instant end = matchEndTime != null ? matchEndTime : Instant.now();
        return Optional.of(end.toEpochMilli() - matchStartTime.toEpochMilli());
    }

    // === Status Queries ===

    public boolean isBuilding() {
        return state.get() == PoolState.BUILDING;
    }

    public boolean isReady() {
        return state.get() == PoolState.READY;
    }

    public boolean isReserved() {
        return state.get() == PoolState.RESERVED;
    }

    public boolean isInUse() {
        return state.get() == PoolState.IN_USE;
    }

    public boolean isCleaningUp() {
        return state.get() == PoolState.CLEANUP;
    }

    public boolean isAvailable() {
        return state.get().isAvailable();
    }

    public boolean isActive() {
        return state.get().isActive();
    }

    public boolean canBeEvicted() {
        return state.get().canBeEvicted();
    }

    // === Snapshot ===

    /**
     * DD2: Creates an immutable snapshot of current instance state.
     */
    public ArenaInstanceSnapshot snapshot() {
        return new ArenaInstanceSnapshot(
            identity,
            template.id(),
            template.version(),
            policy.id(),
            policy.version(),
            state.get(),
            createdAt,
            lastStateChange,
            reservedByPartyId,
            matchStartTime,
            matchEndTime
        );
    }

    /**
     * Immutable snapshot of arena instance state.
     */
    public record ArenaInstanceSnapshot(
        ArenaIdentity identity,
        String templateId,
        int templateVersion,
        String policyId,
        int policyVersion,
        PoolState state,
        Instant createdAt,
        Instant lastStateChange,
        @Nullable UUID reservedByPartyId,
        @Nullable Instant matchStartTime,
        @Nullable Instant matchEndTime
    ) {
        public UUID arenaId() {
            return identity.arenaId();
        }

        public long ageMs() {
            return Instant.now().toEpochMilli() - createdAt.toEpochMilli();
        }

        public long stateAgeMs() {
            return Instant.now().toEpochMilli() - lastStateChange.toEpochMilli();
        }
    }

    @Override
    public String toString() {
        return String.format("ArenaInstance[id=%s, template=%s, policy=%s, state=%s]",
            arenaId(), templateId(), policyId(), state.get());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArenaInstance that)) return false;
        return Objects.equals(identity.arenaId(), that.identity.arenaId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity.arenaId());
    }
}
