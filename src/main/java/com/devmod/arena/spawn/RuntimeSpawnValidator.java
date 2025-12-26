package com.devmod.arena.spawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class RuntimeSpawnValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeSpawnValidator.class);

    private final SpawnSlotResolver resolver;
    private final ValidationCache cache;
    private final Set<UUID> occupiedSlots = ConcurrentHashMap.newKeySet();

    private boolean cacheBuilt = false;

    /**
     * Create a validator for a resolver.
     */
    public RuntimeSpawnValidator(SpawnSlotResolver resolver) {
        this.resolver = resolver;
        this.cache = new ValidationCache();
    }

    /**
     * Build the validation cache.
     * DD48: O(n^2) at load time - pre-computes valid slot pairs.
     *
     * <p>This method should be called during arena load, not during gameplay.
     */
    public void buildCache() {
        long startTime = System.nanoTime();

        List<SpawnSlot> slots = resolver.getAvailableSlots();
        int n = slots.size();

        LOGGER.info("Building spawn slot validation cache for {} slots", n);

        // Clear existing cache
        cache.clear();

        // Pre-compute valid slot pairs - O(n^2)
        for (int i = 0; i < n; i++) {
            SpawnSlot slotA = slots.get(i);

            // Check if slot is in forbidden zone or not on ground
            boolean slotAValid = !resolver.isInForbiddenZone(slotA) && resolver.isOnValidGround(slotA);
            cache.setSlotValid(slotA.id(), slotAValid);

            if (!slotAValid) {
                continue;
            }

            // Check pairs with all other slots
            for (int j = i + 1; j < n; j++) {
                SpawnSlot slotB = slots.get(j);

                // Skip if B is not valid
                if (resolver.isInForbiddenZone(slotB) || !resolver.isOnValidGround(slotB)) {
                    continue;
                }

                // Check if pair is valid
                boolean pairValid = isSlotPairValid(slotA, slotB);
                cache.setSlotPairValid(slotA.id(), slotB.id(), pairValid);
            }
        }

        cacheBuilt = true;

        long elapsed = System.nanoTime() - startTime;
        LOGGER.info("Validation cache built in {}ms. Cached {} slots, {} pairs",
            elapsed / 1_000_000.0, cache.getSlotCount(), cache.getPairCount());
    }

    /**
     * Check if a slot pair is valid based on constraints.
     */
    private boolean isSlotPairValid(SpawnSlot a, SpawnSlot b) {
        SpawnSlotConstraints constraints = resolver.getConstraints();

        // Check distance
        double distance = a.distanceTo(b);
        if (!constraints.isDistanceValid(distance)) {
            return false;
        }

        // Check LOS if required
        if (constraints.requireLineOfSight()) {
            if (!resolver.hasLineOfSight(a, b)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if a slot is valid (not in forbidden zone, on ground).
     * DD48: O(1) runtime lookup.
     */
    public boolean isSlotValid(SpawnSlot slot) {
        if (!cacheBuilt) {
            LOGGER.warn("Cache not built, falling back to direct check");
            return !resolver.isInForbiddenZone(slot) && resolver.isOnValidGround(slot);
        }

        return cache.isSlotValid(slot.id());
    }

    /**
     * Check if a slot pair is compatible.
     * DD48: O(1) runtime lookup.
     */
    public boolean isSlotPairCompatible(SpawnSlot a, SpawnSlot b) {
        if (!cacheBuilt) {
            LOGGER.warn("Cache not built, falling back to direct check");
            return isSlotPairValid(a, b);
        }

        return cache.isSlotPairValid(a.id(), b.id());
    }

    /**
     * Check if a position is occupied.
     * DD48: Lightweight runtime check.
     */
    public boolean isPositionOccupied(SpawnSlot slot) {
        return occupiedSlots.contains(slot.id());
    }

    /**
     * Check if a position is occupied by UUID.
     */
    public boolean isPositionOccupied(UUID slotId) {
        return occupiedSlots.contains(slotId);
    }

    /**
     * Mark a slot as occupied.
     */
    public void markOccupied(SpawnSlot slot) {
        occupiedSlots.add(slot.id());
    }

    /**
     * Mark a slot as unoccupied.
     */
    public void markUnoccupied(SpawnSlot slot) {
        occupiedSlots.remove(slot.id());
    }

    /**
     * Clear all occupied markers.
     */
    public void clearOccupied() {
        occupiedSlots.clear();
    }

    /**
     * Get all occupied slot IDs.
     */
    public Set<UUID> getOccupiedSlots() {
        return Collections.unmodifiableSet(new HashSet<>(occupiedSlots));
    }

    /**
     * Find valid slots that are compatible with a set of already assigned slots.
     * Uses cache for O(1) per-slot lookup.
     */
    public List<SpawnSlot> findCompatibleSlots(List<SpawnSlot> assigned) {
        List<SpawnSlot> compatible = new ArrayList<>();

        for (SpawnSlot candidate : resolver.getAvailableSlots()) {
            // Skip if not valid or occupied
            if (!isSlotValid(candidate) || isPositionOccupied(candidate)) {
                continue;
            }

            // Check compatibility with all assigned slots
            boolean allCompatible = true;
            for (SpawnSlot existing : assigned) {
                if (!isSlotPairCompatible(candidate, existing)) {
                    allCompatible = false;
                    break;
                }
            }

            if (allCompatible) {
                compatible.add(candidate);
            }
        }

        return compatible;
    }

    /**
     * Validate a complete spawn assignment.
     */
    public ValidationResult validateAssignment(List<SpawnSlot> slots) {
        List<String> errors = new ArrayList<>();

        // Check individual slots
        for (SpawnSlot slot : slots) {
            if (!isSlotValid(slot)) {
                errors.add("Slot " + slot.id() + " is not valid (forbidden zone or ground check)");
            }
        }

        // Check all pairs
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                if (!isSlotPairCompatible(slots.get(i), slots.get(j))) {
                    errors.add("Slots " + slots.get(i).id() + " and " + slots.get(j).id() + " are incompatible");
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Check if cache is built.
     */
    public boolean isCacheBuilt() {
        return cacheBuilt;
    }

    /**
     * Invalidate the cache (force rebuild on next use).
     */
    public void invalidateCache() {
        cache.clear();
        cacheBuilt = false;
    }

    /**
     * Get cache statistics.
     */
    public CacheStats getCacheStats() {
        return new CacheStats(
            cache.getSlotCount(),
            cache.getPairCount(),
            cacheBuilt
        );
    }

    /**
     * Validation result record.
     */
    public record ValidationResult(boolean valid, List<String> errors) {
        public boolean isValid() {
            return valid;
        }
    }

    /**
     * Cache statistics record.
     */
    public record CacheStats(int slotCount, int pairCount, boolean built) {}

    /**
     * Internal validation cache.
     * DD48: Stores pre-computed validation results for O(1) lookup.
     */
    public static class ValidationCache {

        // Individual slot validity
        private final Map<UUID, Boolean> slotValidity = new HashMap<>();

        // Pair validity: adjacency list per slot for valid pairings (symmetric)
        private final Map<UUID, Set<UUID>> pairValidity = new HashMap<>();

        /**
         * Set whether a slot is valid.
         */
        public void setSlotValid(UUID slotId, boolean valid) {
            slotValidity.put(slotId, valid);
        }

        /**
         * Check if a slot is valid.
         */
        public boolean isSlotValid(UUID slotId) {
            return slotValidity.getOrDefault(slotId, false);
        }

        /**
         * Set whether a slot pair is valid.
         */
        public void setSlotPairValid(UUID slotA, UUID slotB, boolean valid) {
            if (!valid) {
                return; // store only positive pairs
            }
            pairValidity.computeIfAbsent(slotA, k -> new HashSet<>()).add(slotB);
            pairValidity.computeIfAbsent(slotB, k -> new HashSet<>()).add(slotA);
        }

        /**
         * Check if a slot pair is valid.
         * O(1) lookup.
         */
        public boolean isSlotPairValid(UUID slotA, UUID slotB) {
            Set<UUID> compatible = pairValidity.get(slotA);
            return compatible != null && compatible.contains(slotB);
        }

        /**
         * Clear the cache.
         */
        public void clear() {
            slotValidity.clear();
            pairValidity.clear();
        }

        /**
         * Get number of cached slots.
         */
        public int getSlotCount() {
            return slotValidity.size();
        }

        /**
         * Get number of cached pairs.
         */
        public int getPairCount() {
            return pairValidity.values().stream().mapToInt(Set::size).sum();
        }
    }
}
