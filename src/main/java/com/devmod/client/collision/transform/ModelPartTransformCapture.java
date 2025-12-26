package com.devmod.client.collision.transform;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

public final class ModelPartTransformCapture {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ModelPartTransformCapture() {} // Utility class

    /**
     * Captured transform data for a single ModelPart.
     */
    public record CapturedTransform(
        Matrix4f worldTransform,  // Full transform matrix in world space
        float localXRot,          // Local X rotation (pitch)
        float localYRot,          // Local Y rotation (yaw)
        float localZRot,          // Local Z rotation (roll)
        long captureTime          // System time when captured
    ) {
        /**
         * Checks if this capture is still fresh (within TTL).
         */
        public boolean isFresh(long ttlMs) {
            return System.currentTimeMillis() - captureTime < ttlMs;
        }
    }

    // Currently capturing entity (thread-local for safety)
    private static final ThreadLocal<LivingEntity> currentEntity = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> capturing = ThreadLocal.withInitial(() -> false);

    // Per-entity transform storage: entityId -> (partName -> transform)
    private static final Map<Integer, Map<String, CapturedTransform>> entityTransforms = new ConcurrentHashMap<>();

    // Cache TTL in milliseconds (transforms older than this are stale)
    private static final long TRANSFORM_TTL_MS = 100; // 5 ticks at 20 TPS

    // Maximum number of entities to cache (prevents unbounded growth)
    private static final int MAX_CACHED_ENTITIES = 256;

    // Cleanup tracking
    private static volatile long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL_MS = 5000; // 5 seconds

    // ==================== Capture Control ====================

    /**
     * Begins capturing transforms for an entity.
     * Call this before the entity's model renders.
     *
     * @param entity The entity being rendered
     */
    public static void beginCapture(LivingEntity entity) {
        Objects.requireNonNull(entity);
        currentEntity.set(entity);
        capturing.set(true);

        // Periodic cleanup to prevent memory leaks
        maybeCleanup();

        // Enforce max cache size - evict oldest entries if needed
        if (entityTransforms.size() >= MAX_CACHED_ENTITIES) {
            evictOldestEntries();
        }

        // Clear old transforms for this entity
        entityTransforms.computeIfAbsent(entity.getId(), k -> new ConcurrentHashMap<>()).clear();
    }

    /**
     * Evicts the oldest (stalest) entries when cache is full.
     */
    private static void evictOldestEntries() {
        int toRemove = entityTransforms.size() - MAX_CACHED_ENTITIES + 16; // Remove 16 extra to avoid thrashing

        entityTransforms.entrySet().stream()
            .sorted((a, b) -> {
                // Find oldest capture time for each entity
                long aMax = a.getValue().values().stream()
                    .mapToLong(t -> t.captureTime).max().orElse(0);
                long bMax = b.getValue().values().stream()
                    .mapToLong(t -> t.captureTime).max().orElse(0);
                return Long.compare(aMax, bMax);
            })
            .limit(Math.max(1, toRemove))
            .map(Map.Entry::getKey)
            .toList()
            .forEach(entityTransforms::remove);
    }

    /**
     * Ends transform capture.
     * Call this after the entity's model finishes rendering.
     */
    public static void endCapture() {
        capturing.set(false);
        currentEntity.remove();
    }

    /**
     * Checks if the system is currently capturing transforms.
     * Called by the Mixin to avoid unnecessary work.
     */
    public static boolean isCapturing() {
        return Boolean.TRUE.equals(capturing.get());
    }

    /**
     * Periodic cleanup hook (call from client tick).
     */
    public static void clientTick() {
        maybeCleanup();
    }

    /**
     * Gets the entity currently being captured.
     */
    @Nullable
    public static LivingEntity getCurrentEntity() {
        return currentEntity.get();
    }

    // ==================== Transform Storage ====================

    /**
     * Captures a transform for a ModelPart.
     * Called by ModelPartTransformMixin during rendering.
     *
     * @param part           The ModelPart being transformed
     * @param worldTransform The current world transform matrix
     * @param xRot           Local X rotation
     * @param yRot           Local Y rotation
     * @param zRot           Local Z rotation
     */
    public static void captureTransform(ModelPart part, Matrix4f worldTransform,
                                        float xRot, float yRot, float zRot) {
        LivingEntity entity = currentEntity.get();
        if (entity == null) return;

        // Get or create the transform map for this entity
        Map<String, CapturedTransform> transforms = entityTransforms.computeIfAbsent(
            entity.getId(), k -> new ConcurrentHashMap<>());

        // Try to identify the part by its characteristics
        // ModelPart doesn't have a name field accessible, so we use a hash-based identifier
        String partId = identifyPart(Objects.requireNonNull(part));

        CapturedTransform captured = new CapturedTransform(
            new Matrix4f(worldTransform), // Copy to avoid mutation
            xRot, yRot, zRot,
            System.currentTimeMillis()
        );

        transforms.put(partId, captured);
    }

    /**
     * Gets all captured transforms for an entity.
     *
     * @param entity The entity to get transforms for
     * @return Map of part identifiers to transforms, or empty map if none
     */
    @Nonnull
    public static Map<String, CapturedTransform> getTransforms(LivingEntity entity) {
        Map<String, CapturedTransform> transforms = entityTransforms.get(Objects.requireNonNull(entity).getId());
        if (transforms == null) {
            return Objects.requireNonNull(Map.of());
        }
        return transforms;
    }

    /**
     * Gets a specific transform by part ID.
     *
     * @param entity The entity
     * @param partId The part identifier
     * @return The captured transform, or null if not found
     */
    @Nullable
    public static CapturedTransform getTransform(LivingEntity entity, String partId) {
        Map<String, CapturedTransform> transforms = entityTransforms.get(Objects.requireNonNull(entity).getId());
        if (transforms == null) return null;

        CapturedTransform transform = transforms.get(Objects.requireNonNull(partId));
        if (transform == null || !transform.isFresh(TRANSFORM_TTL_MS)) {
            return null;
        }
        return transform;
    }

    /**
     * Checks if we have fresh transforms for an entity.
     */
    public static boolean hasTransforms(LivingEntity entity) {
        Map<String, CapturedTransform> transforms = entityTransforms.get(Objects.requireNonNull(entity).getId());
        if (transforms == null || transforms.isEmpty()) return false;

        // Check if any transform is fresh
        long now = System.currentTimeMillis();
        for (CapturedTransform t : transforms.values()) {
            if (now - t.captureTime < TRANSFORM_TTL_MS) {
                return true;
            }
        }
        return false;
    }

    // ==================== Part Identification ====================

    /**
     * Identifies a ModelPart using a hash-based approach.
     * Since ModelPart doesn't expose its name, we use structural properties.
     *
     * Common patterns:
     * - "head": typically at top, small cube
     * - "body": center mass, larger cube
     * - "left_arm", "right_arm": offset from body center horizontally
     * - "left_leg", "right_leg": offset from body center vertically below
     */
    @Nonnull
    private static String identifyPart(ModelPart part) {
        // Use object identity hash as a stable identifier
        // This works because the same ModelPart instance is reused per entity type
        return "part_" + System.identityHashCode(Objects.requireNonNull(part));
    }

    // ==================== Cleanup ====================

    /**
     * Remove cached transforms for a specific entity (called on unload/death).
     */
    public static void removeEntity(int entityId) {
        entityTransforms.remove(entityId);
    }

    /**
     * Remove cached transforms for a specific entity (called on unload/death).
     */
    public static void removeEntity(@Nullable LivingEntity entity) {
        if (entity != null) {
            removeEntity(entity.getId());
        }
    }

    private static void maybeCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            cleanup();
        }
    }

    /**
     * Removes stale transforms for entities that no longer exist or haven't been updated.
     * Call periodically (e.g., every second) to prevent memory leaks.
     */
    public static void cleanup() {
        int sizeBefore = entityTransforms.size();
        long now = System.currentTimeMillis();
        entityTransforms.entrySet().removeIf(entry -> {
            Map<String, CapturedTransform> transforms = entry.getValue();
            if (transforms.isEmpty()) return true;

            // Remove if all transforms are stale
            boolean allStale = transforms.values().stream()
                .allMatch(t -> now - t.captureTime > TRANSFORM_TTL_MS * 10);
            return allStale;
        });
        int sizeAfter = entityTransforms.size();

        // Log cache cleanup metrics periodically (every ~5 seconds based on cleanup interval)
        if (sizeBefore != sizeAfter || sizeAfter > 100) {
            LOGGER.debug("[ModelPartTransformCapture] Cache: {} entities, {} transforms (evicted {} entities)",
                sizeAfter, getTotalTransformCount(), sizeBefore - sizeAfter);
        }
    }

    /**
     * Clears all captured transforms.
     * Call when leaving a world or during cleanup.
     */
    public static void clearAll() {
        entityTransforms.clear();
    }

    /**
     * Gets the number of entities with captured transforms.
     * Useful for debugging/profiling.
     */
    public static int getCapturedEntityCount() {
        return entityTransforms.size();
    }

    /**
     * Gets the total number of captured transforms across all entities.
     */
    public static int getTotalTransformCount() {
        return entityTransforms.values().stream()
            .mapToInt(Map::size)
            .sum();
    }
}
