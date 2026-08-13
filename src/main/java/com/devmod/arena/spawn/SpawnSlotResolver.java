package com.devmod.arena.spawn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

public class SpawnSlotResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpawnSlotResolver.class);

    /** Upper bound on backtracking nodes so an unsatisfiable constraint set cannot stall the caller. */
    private static final int MAX_BACKTRACK_NODES = 50_000;

    private final List<SpawnSlot> availableSlots;
    private final List<ForbiddenZone> forbiddenZones;
    private final SpawnSlotConstraints constraints;
    private final BlockGetter level;

    // Pluggable LOS checker - can be replaced with actual game engine implementation
    private BiPredicate<SpawnSlot, SpawnSlot> lineOfSightChecker;

    // Pluggable ground checker
    private java.util.function.Predicate<SpawnSlot> groundChecker;

    /**
     * Create a resolver with default constraints.
     */
    public SpawnSlotResolver(List<SpawnSlot> availableSlots) {
        this(availableSlots, SpawnSlotConstraints.MELEE_DEFAULTS, Collections.emptyList(), null);
    }

    /**
     * Create a resolver with specific constraints.
     */
    public SpawnSlotResolver(List<SpawnSlot> availableSlots, SpawnSlotConstraints constraints) {
        this(availableSlots, constraints, Collections.emptyList(), null);
    }

    /**
     * Create a resolver with constraints and forbidden zones.
     */
    public SpawnSlotResolver(
            List<SpawnSlot> availableSlots,
            SpawnSlotConstraints constraints,
            List<ForbiddenZone> forbiddenZones) {
        this(availableSlots, constraints, forbiddenZones, null);
    }

    /**
     * Create a resolver with constraints, forbidden zones, and a level for ground checks.
     */
    public SpawnSlotResolver(
            List<SpawnSlot> availableSlots,
            SpawnSlotConstraints constraints,
            List<ForbiddenZone> forbiddenZones,
            @Nullable BlockGetter level) {

        this.availableSlots = new ArrayList<>(availableSlots);
        this.constraints = constraints;
        this.forbiddenZones = new ArrayList<>(forbiddenZones);
        this.level = level;

        // Default implementations
        this.lineOfSightChecker = this::defaultLineOfSightCheck;
        this.groundChecker = this::defaultGroundCheck;
    }

    /**
     * Set a custom line of sight checker.
     * DD47: LOS check implementation using ClipContext.
     *
     * @param checker A BiPredicate that returns true if there's line of sight between two slots
     */
    public void setLineOfSightChecker(BiPredicate<SpawnSlot, SpawnSlot> checker) {
        this.lineOfSightChecker = checker != null ? checker : this::defaultLineOfSightCheck;
    }

    /**
     * Set a custom ground checker.
     *
     * @param checker A Predicate that returns true if the slot is on valid ground
     */
    public void setGroundChecker(java.util.function.Predicate<SpawnSlot> checker) {
        this.groundChecker = checker != null ? checker : this::defaultGroundCheck;
    }

    /**
     * Check if there's line of sight between two spawn slots.
     * DD47: Uses ClipContext for raycast (default implementation is simplified).
     *
     * @param from Starting slot
     * @param to Target slot
     * @return true if line of sight exists
     */
    public boolean hasLineOfSight(SpawnSlot from, SpawnSlot to) {
        return lineOfSightChecker.test(from, to);
    }

    /**
     * Default line of sight check (simplified - straight line distance).
     * In actual implementation, this would use ClipContext for proper raycast.
     */
    private boolean defaultLineOfSightCheck(SpawnSlot from, SpawnSlot to) {
        // Simplified check - real implementation would raycast through blocks
        // Check if the vertical difference isn't too extreme
        double heightDiff = Math.abs(from.y() - to.y());
        double horizontalDist = from.horizontalDistanceTo(to);

        // If height difference is more than half the horizontal distance, likely blocked
        return heightDiff <= horizontalDist * 0.5;
    }

    /**
     * Default ground check using the provided level.
     * Requires solid ground below and air at/above the slot position.
     */
    private boolean defaultGroundCheck(SpawnSlot slot) {
        if (level == null) {
            LOGGER.warn("Ground check skipped (no level provided) for slot {}", slot.id());
            return false;
        }
        BlockPos pos = Objects.requireNonNull(BlockPos.containing(slot.x(), slot.y(), slot.z()), "slotPos");
        BlockPos below = Objects.requireNonNull(pos.below(), "slotBelow");
        BlockPos above = Objects.requireNonNull(pos.above(), "slotAbove");

        boolean solidBelow = level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
        boolean airAtPos = level.getBlockState(pos).isAir();
        boolean airAbove = level.getBlockState(above).isAir();

        return solidBelow && airAtPos && airAbove;
    }

    /**
     * Check if a slot is on valid ground.
     */
    public boolean isOnValidGround(SpawnSlot slot) {
        if (!constraints.requireGroundCheck()) {
            return true;
        }
        return groundChecker.test(slot);
    }

    /**
     * Check if a slot is in a forbidden zone.
     * DD47: Forbidden zone check.
     */
    public boolean isInForbiddenZone(SpawnSlot slot) {
        if (!constraints.checkForbiddenZones() || forbiddenZones.isEmpty()) {
            return false;
        }

        for (ForbiddenZone zone : forbiddenZones) {
            if (zone.contains(slot)) {
                LOGGER.debug("Slot {} is in forbidden zone: {}", slot.id(), zone.description());
                return true;
            }
        }
        return false;
    }

    /**
     * Resolve spawn slots for a number of players.
     *
     * @param count Number of spawn slots needed
     * @return List of resolved spawn slots, may be less than requested if not enough valid slots
     */
    public List<SpawnSlot> resolveSlots(int count) {
        return resolveSlots(count, Collections.emptyList());
    }

    /**
     * Resolve spawn slots for a number of players, avoiding already assigned slots.
     *
     * @param count Number of spawn slots needed
     * @param occupiedSlots Slots that are already assigned
     * @return List of resolved spawn slots
     */
    public List<SpawnSlot> resolveSlots(int count, List<SpawnSlot> occupiedSlots) {
        if (count <= 0) {
            return new ArrayList<>();
        }

        // Filter available slots first
        List<SpawnSlot> validSlots = availableSlots.stream()
            .filter(slot -> !isInForbiddenZone(slot))
            .filter(this::isOnValidGround)
            .filter(slot -> !occupiedSlots.contains(slot))
            .sorted(Comparator
                .comparingDouble(SpawnSlot::x)
                .thenComparingDouble(SpawnSlot::z)
                .thenComparingDouble(SpawnSlot::y))
            .collect(Collectors.toCollection(ArrayList::new));

        if (validSlots.isEmpty()) {
            LOGGER.warn("No valid spawn slots available");
            return new ArrayList<>();
        }

        List<SpawnSlot> best = new ArrayList<>();
        int[] budget = {MAX_BACKTRACK_NODES};
        backtrackResolve(count, validSlots, 0, new ArrayList<>(), best, budget);

        if (budget[0] <= 0) {
            LOGGER.warn("Spawn slot search exhausted its {} node budget for {} slots; returning best effort ({})",
                MAX_BACKTRACK_NODES, count, best.size());
        }

        if (best.size() < count) {
            LOGGER.warn("Could not find enough valid spawn slots. Requested: {}, Found: {}",
                count, best.size());
        }

        return best;
    }

    /**
     * Backtracking search to find a deterministic set of slots that satisfy constraints.
     * This avoids random selection that could end up in dead ends (e.g., selecting an
     * isolated far-away slot first).
     *
     * <p>{@code budget} is a single-element countdown shared by the whole search: an
     * unsatisfiable constraint set would otherwise explore exponentially many subsets
     * on the calling thread.
     */
    private boolean backtrackResolve(int targetCount,
                                     List<SpawnSlot> candidates,
                                     int startIndex,
                                     List<SpawnSlot> current,
                                     List<SpawnSlot> best,
                                     int[] budget) {
        if (current.size() == targetCount) {
            best.clear();
            best.addAll(current);
            return true;
        }

        if (budget[0] <= 0) {
            return false;
        }
        budget[0]--;

        // If remaining slots cannot beat current best, prune
        if (candidates.size() - startIndex + current.size() <= best.size()) {
            return false;
        }

        for (int i = startIndex; i < candidates.size(); i++) {
            SpawnSlot candidate = candidates.get(i);
            // Forbidden-zone and ground checks were already applied when building
            // the candidate list; only the pairwise constraints remain.
            if (!isPairwiseValid(candidate, current)) {
                continue;
            }

            current.add(candidate);
            boolean done = backtrackResolve(targetCount, candidates, i + 1, current, best, budget);
            if (done) {
                return true;
            }
            current.remove(current.size() - 1);

            if (budget[0] <= 0) {
                break;
            }
        }

        if (current.size() > best.size()) {
            best.clear();
            best.addAll(current);
        }
        return false;
    }

    /**
     * Checks a candidate against already-selected slots (distance and line of sight).
     */
    private boolean isPairwiseValid(SpawnSlot slot, List<SpawnSlot> existingAssignments) {
        for (SpawnSlot existing : existingAssignments) {
            double distance = slot.distanceTo(existing);

            if (!constraints.isDistanceValid(distance)) {
                return false;
            }

            if (constraints.requireLineOfSight() && !hasLineOfSight(slot, existing)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate that a specific slot assignment is valid.
     */
    public boolean validateSlotAssignment(SpawnSlot slot, List<SpawnSlot> existingAssignments) {
        // Check forbidden zones
        if (isInForbiddenZone(slot)) {
            return false;
        }

        // Check ground
        if (!isOnValidGround(slot)) {
            return false;
        }

        // Check against existing assignments
        return isPairwiseValid(slot, existingAssignments);
    }

    /**
     * Get all valid slots from the available pool.
     */
    public List<SpawnSlot> getValidSlots() {
        return availableSlots.stream()
            .filter(slot -> !isInForbiddenZone(slot))
            .filter(this::isOnValidGround)
            .collect(Collectors.toList());
    }

    /**
     * Add a forbidden zone.
     */
    public void addForbiddenZone(ForbiddenZone zone) {
        forbiddenZones.add(zone);
    }

    /**
     * Remove a forbidden zone.
     */
    public void removeForbiddenZone(ForbiddenZone zone) {
        forbiddenZones.remove(zone);
    }

    /**
     * Get the constraints.
     */
    public SpawnSlotConstraints getConstraints() {
        return constraints;
    }

    /**
     * Get all available slots.
     */
    public List<SpawnSlot> getAvailableSlots() {
        return Collections.unmodifiableList(availableSlots);
    }

    /**
     * Get all forbidden zones.
     */
    public List<ForbiddenZone> getForbiddenZones() {
        return Collections.unmodifiableList(forbiddenZones);
    }
}
