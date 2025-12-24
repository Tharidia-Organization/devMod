package com.devmod.collision.bodypart;

import com.devmod.combat.HitHelper;
import com.devmod.collision.obb.OrientedBoundingBox;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Runtime instance of a body part with current world-space transform.
 * Created each frame from BodyPartDefinition + current model pose.
 *
 * Uses object pooling to reduce GC pressure during combat.
 *
 * Lifecycle:
 * 1. Acquire from pool: BodyPartInstance.acquire(definition)
 * 2. Update with transform: instance.update(worldTransform, tick)
 * 3. Use for raycast: instance.getWorldOBB()
 * 4. Release back to pool: instance.release()
 */
public final class BodyPartInstance {

    // ==================== Object Pool ====================

    private static final int POOL_MAX_SIZE = 256;
    private static final Deque<BodyPartInstance> POOL = new ArrayDeque<>(POOL_MAX_SIZE);

    /**
     * Acquires an instance from the pool or creates a new one.
     *
     * @param definition The body part definition
     * @return A BodyPartInstance ready for use
     */
    @Nonnull
    public static BodyPartInstance acquire(@Nonnull BodyPartDefinition definition) {
        BodyPartInstance instance;
        synchronized (POOL) {
            instance = POOL.pollFirst();
        }

        if (instance == null) {
            instance = new BodyPartInstance();
        }

        instance.initialize(definition);
        return instance;
    }

    /**
     * Acquires multiple instances for a set of definitions.
     *
     * @param definitions Array of body part definitions
     * @return Array of BodyPartInstances
     */
    @Nonnull
    public static BodyPartInstance[] acquireMultiple(@Nonnull BodyPartDefinition[] definitions) {
        BodyPartInstance[] instances = new BodyPartInstance[definitions.length];
        for (int i = 0; i < definitions.length; i++) {
            BodyPartDefinition def = Objects.requireNonNull(definitions[i], "definition");
            instances[i] = acquire(def);
        }
        return instances;
    }

    /**
     * Releases multiple instances back to the pool.
     *
     * @param instances Array of instances to release
     */
    public static void releaseMultiple(@Nullable BodyPartInstance[] instances) {
        if (instances == null) return;
        for (BodyPartInstance instance : instances) {
            if (instance != null) {
                instance.release();
            }
        }
    }

    /**
     * Gets the current pool size (for debugging/monitoring).
     */
    public static int getPoolSize() {
        synchronized (POOL) {
            return POOL.size();
        }
    }

    /**
     * Clears the pool (call during world unload).
     */
    public static void clearPool() {
        synchronized (POOL) {
            POOL.clear();
        }
    }

    // ==================== Instance Fields ====================

    @Nullable
    private BodyPartDefinition definition;

    @Nullable
    private OrientedBoundingBox worldOBB;

    @Nullable
    private Matrix4f worldTransform;

    private long lastUpdateTick;
    private boolean inUse;

    /**
     * Private constructor - use acquire() instead.
     */
    private BodyPartInstance() {
        this.inUse = false;
        this.lastUpdateTick = -1;
    }

    /**
     * Initializes this instance with a definition.
     */
    private void initialize(@Nonnull BodyPartDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.worldOBB = definition.createBaseOBB();
        this.worldTransform = null;
        this.lastUpdateTick = -1;
        this.inUse = true;
    }

    /**
     * Releases this instance back to the pool.
     */
    public void release() {
        if (!inUse) return;

        this.definition = null;
        this.worldOBB = null;
        this.worldTransform = null;
        this.lastUpdateTick = -1;
        this.inUse = false;

        synchronized (POOL) {
            if (POOL.size() < POOL_MAX_SIZE) {
                POOL.addLast(this);
            }
            // If pool is full, let GC collect this instance
        }
    }

    // ==================== Update Methods ====================

    /**
     * Updates this instance with the current model part transform.
     * Should be called when pose changes or on first query per tick.
     *
     * @param modelPartTransform World-space transform of the attached bone
     * @param currentTick        Current game tick
     */
    public void update(@Nonnull Matrix4f modelPartTransform, long currentTick) {
        BodyPartDefinition def = requireDefinition();

        // Skip if already updated this tick
        if (currentTick == lastUpdateTick && worldTransform != null) {
            return;
        }

        this.worldTransform = new Matrix4f(modelPartTransform);
        this.lastUpdateTick = currentTick;

        // Transform the base OBB by the world transform
        OrientedBoundingBox baseOBB = def.createBaseOBB();
        this.worldOBB = baseOBB.transform(modelPartTransform);
    }

    /**
     * Updates with entity position only (no bone rotation).
     * Use this as fallback when bone transforms aren't available.
     *
     * @param entityPosition World position of the entity
     * @param entityYRot     Entity Y rotation in degrees
     * @param currentTick    Current game tick
     */
    public void updateSimple(@Nonnull net.minecraft.world.phys.Vec3 entityPosition,
                             float entityYRot, long currentTick) {
        BodyPartDefinition def = requireDefinition();

        if (currentTick == lastUpdateTick && worldTransform != null) {
            return;
        }

        // Build transform from entity position + rotation
        Matrix4f transform = new Matrix4f();
        transform.translate((float) entityPosition.x, (float) entityPosition.y, (float) entityPosition.z);
        transform.rotateY((float) Math.toRadians(-entityYRot)); // Minecraft Y rotation is inverted

        // Apply local offset
        net.minecraft.world.phys.Vec3 offset = def.localOffset();
        transform.translate((float) offset.x, (float) offset.y, (float) offset.z);

        this.worldTransform = transform;
        this.lastUpdateTick = currentTick;

        // Transform the base OBB
        OrientedBoundingBox baseOBB = def.createOriginOBB();
        this.worldOBB = baseOBB.transform(transform);
    }

    /**
     * Forces recalculation on next update.
     */
    public void invalidate() {
        this.lastUpdateTick = -1;
    }

    @Nonnull
    private BodyPartDefinition requireDefinition() {
        BodyPartDefinition def = definition;
        if (def == null) {
            throw new IllegalStateException("Instance not initialized - call acquire() first");
        }
        return def;
    }

    // ==================== Accessors ====================

    /**
     * Gets the world-space OBB for this body part.
     *
     * @throws IllegalStateException if not updated
     */
    @Nonnull
    public OrientedBoundingBox getWorldOBB() {
        OrientedBoundingBox obb = worldOBB;
        if (obb == null) {
            throw new IllegalStateException("Instance not updated - call update() first");
        }
        return obb;
    }

    /**
     * Gets the world-space OBB or null if not updated.
     */
    @Nullable
    public OrientedBoundingBox getWorldOBBOrNull() {
        return worldOBB;
    }

    /**
     * Gets the definition for this body part.
     */
    @Nonnull
    public BodyPartDefinition getDefinition() {
        return requireDefinition();
    }

    /**
     * Gets the body part type enum.
     */
    @Nonnull
    public HitHelper.BodyPart getBodyPartType() {
        return getDefinition().bodyPartType();
    }

    /**
     * Gets the body part ID.
     */
    @Nonnull
    public String getId() {
        return getDefinition().id();
    }

    /**
     * Gets the damage multiplier for this body part.
     */
    public float getDamageMultiplier() {
        return getDefinition().damageMultiplier();
    }

    /**
     * Gets the render color for this body part.
     */
    public int getRenderColor() {
        return getDefinition().renderColor();
    }

    /**
     * Gets the current world transform matrix.
     */
    @Nullable
    public Matrix4f getWorldTransform() {
        return worldTransform;
    }

    /**
     * Gets the tick when this instance was last updated.
     */
    public long getLastUpdateTick() {
        return lastUpdateTick;
    }

    /**
     * Checks if this instance is currently in use.
     */
    public boolean isInUse() {
        return inUse;
    }

    /**
     * Checks if this instance has been updated.
     */
    public boolean isUpdated() {
        return worldOBB != null && lastUpdateTick >= 0;
    }

    /**
     * Checks if update is stale (older than given tick threshold).
     *
     * @param currentTick Current game tick
     * @param maxAge      Maximum age in ticks
     * @return true if update is stale
     */
    public boolean isStale(long currentTick, int maxAge) {
        return lastUpdateTick < 0 || (currentTick - lastUpdateTick) > maxAge;
    }

    @Override
    public String toString() {
        String defId = definition != null ? definition.id() : "null";
        return String.format("BodyPartInstance[%s, updated=%d, inUse=%b]",
            defId, lastUpdateTick, inUse);
    }
}
