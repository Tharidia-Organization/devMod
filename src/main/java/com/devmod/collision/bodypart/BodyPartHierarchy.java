package com.devmod.collision.bodypart;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.devmod.collision.transform.AnimationSnapshot;
import com.devmod.combat.HitHelper;

/**
 * Manages the parent-child relationships between body parts.
 * Transforms propagate from root → children.
 *
 * Structure mirrors PlayerAnimationLibrary but for collision:
 * <pre>
 * Root (entity position/rotation)
 *   └─ Body
 *        ├─ Head
 *        ├─ Left Arm
 *        └─ Right Arm
 *   ├─ Left Leg
 *   └─ Right Leg
 * </pre>
 *
 * Usage:
 * <pre>
 * BodyPartHierarchy hierarchy = BodyPartHierarchy.builder()
 *     .addPart(bodyDef)
 *     .addPart(headDef)
 *     .addPart(leftArmDef)
 *     .build();
 *
 * BodyPartInstance[] parts = hierarchy.computeWorldTransforms(snapshot);
 * </pre>
 */

public final class BodyPartHierarchy {

    private final Map<String, BodyPartDefinition> parts;
    private final Map<String, List<String>> childrenMap;
    private final List<String> rootPartIds;
    private final BodyPartDefinition[] partArray; // For fast iteration
    private final String[] topologicalOrder; // Parts sorted by dependency

    private BodyPartHierarchy(Map<String, BodyPartDefinition> parts,
                              Map<String, List<String>> childrenMap,
                              List<String> rootPartIds) {
        Map<String, BodyPartDefinition> safeParts = Objects.requireNonNull(parts, "parts");
        Map<String, List<String>> safeChildren = Objects.requireNonNull(childrenMap, "childrenMap");
        List<String> safeRoots = Objects.requireNonNull(rootPartIds, "rootPartIds");

        this.parts = Collections.unmodifiableMap(new HashMap<>(safeParts));
        this.childrenMap = Collections.unmodifiableMap(deepCopyChildrenMap(safeChildren));
        this.rootPartIds = Collections.unmodifiableList(new ArrayList<>(safeRoots));
        this.partArray = safeParts.values().toArray(new BodyPartDefinition[0]);
        this.topologicalOrder = computeTopologicalOrder(safeParts, safeChildren, safeRoots);
    }

    private static Map<String, List<String>> deepCopyChildrenMap(Map<String, List<String>> original) {
        Map<String, List<String>> copy = new HashMap<>();
        for (var entry : original.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * Computes topological order for transform propagation (parents before children).
     */
    private static String[] computeTopologicalOrder(Map<String, BodyPartDefinition> parts,
                                                    Map<String, List<String>> childrenMap,
                                                    List<String> rootPartIds) {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        // BFS from roots
        Deque<String> queue = new ArrayDeque<>(rootPartIds);
        while (!queue.isEmpty()) {
            String partId = queue.pollFirst();
            if (visited.contains(partId)) continue;
            visited.add(partId);
            order.add(partId);

            List<String> children = childrenMap.get(partId);
            if (children != null) {
                queue.addAll(children);
            }
        }

        // Add any orphaned parts (shouldn't happen normally)
        for (String partId : parts.keySet()) {
            if (!visited.contains(partId)) {
                order.add(partId);
            }
        }

        return order.toArray(new String[0]);
    }

    // ==================== Query Methods ====================

    /**
     * Gets a part definition by ID.
     */
    @Nullable
    public BodyPartDefinition getPart(@Nonnull String partId) {
        return parts.get(partId);
    }

    /**
     * Gets all part definitions.
     */
    @Nonnull
    public Collection<BodyPartDefinition> getAllParts() {
        return Objects.requireNonNull(Collections.unmodifiableCollection(new ArrayList<>(parts.values())));
    }

    /**
     * Gets the number of parts in this hierarchy.
     */
    public int getPartCount() {
        return parts.size();
    }

    /**
     * Gets children of a part.
     */
    @Nonnull
    public List<String> getChildren(@Nonnull String partId) {
        List<String> children = childrenMap.getOrDefault(partId, Collections.emptyList());
        if (children.isEmpty()) {
            return Objects.requireNonNull(List.of());
        }
        return Objects.requireNonNull(List.copyOf(children));
    }

    /**
     * Gets the root part IDs (parts with no parent).
     */
    @Nonnull
    public List<String> getRootPartIds() {
        return Objects.requireNonNull(List.copyOf(rootPartIds));
    }

    /**
     * Checks if a part exists in this hierarchy.
     */
    public boolean hasPart(@Nonnull String partId) {
        return parts.containsKey(partId);
    }

    /**
     * Gets the part array for fast iteration.
     */
    @Nonnull
    public BodyPartDefinition[] getPartArray() {
        return partArray.clone();
    }

    // ==================== Transform Computation ====================

    /**
     * Computes world transforms for all parts given current pose.
     *
     * @param snapshot Current animation state with bone transforms
     * @return Array of updated BodyPartInstances (caller must release when done)
     */
    @Nonnull
    public BodyPartInstance[] computeWorldTransforms(@Nonnull AnimationSnapshot snapshot) {
        BodyPartInstance[] instances = new BodyPartInstance[partArray.length];
        Map<String, Matrix4f> computedTransforms = new HashMap<>();

        // Entity base transform
        Matrix4f entityTransform = new Matrix4f();
        entityTransform.translate(
            (float) snapshot.entityPosition().x,
            (float) snapshot.entityPosition().y,
            (float) snapshot.entityPosition().z
        );
        entityTransform.rotateY((float) Math.toRadians(-snapshot.yBodyRot()));

        // Process parts in topological order (parents first)
        for (int i = 0; i < topologicalOrder.length; i++) {
            String partId = topologicalOrder[i];
            BodyPartDefinition def = parts.get(partId);
            if (def == null) continue;
            BodyPartDefinition resolvedDef = Objects.requireNonNull(def, "part definition");

            // Find index in partArray
            int arrayIndex = findPartIndex(resolvedDef);
            if (arrayIndex < 0) continue;

            // Compute world transform for this part
            Matrix4f worldTransform = computePartTransform(resolvedDef, entityTransform, snapshot, computedTransforms);
            computedTransforms.put(partId, worldTransform);

            // Create and update instance
            BodyPartInstance instance = BodyPartInstance.acquire(resolvedDef);
            instance.update(worldTransform, snapshot.tickCaptured());
            instances[arrayIndex] = instance;
        }

        return instances;
    }

    private int findPartIndex(BodyPartDefinition def) {
        for (int i = 0; i < partArray.length; i++) {
            if (partArray[i] == def) return i;
        }
        return -1;
    }

    /**
     * Computes the world transform for a single part.
     */
    @Nonnull
    private Matrix4f computePartTransform(@Nonnull BodyPartDefinition def,
                                          @Nonnull Matrix4f entityTransform,
                                          @Nonnull AnimationSnapshot snapshot,
                                          @Nonnull Map<String, Matrix4f> computedTransforms) {
        Matrix4f result = new Matrix4f();

        // Start with parent transform or entity transform
        String parentId = def.parentPartId();
        if (parentId != null && computedTransforms.containsKey(parentId)) {
            result.set(computedTransforms.get(parentId));
        } else {
            result.set(entityTransform);
        }

        // Apply bone transform if available
        String boneId = def.parentBoneId();
        if (boneId != null) {
            Matrix4f boneTransform = snapshot.getPartTransform(boneId);
            if (boneTransform != null) {
                result.mul(boneTransform);
            }
        }

        // Apply local offset
        net.minecraft.world.phys.Vec3 offset = def.localOffset();
        result.translate((float) offset.x, (float) offset.y, (float) offset.z);

        return result;
    }

    /**
     * Computes transforms using simple fallback (entity position + rotation only).
     * Use this when bone transforms aren't available.
     *
     * @param entityPosition Entity world position
     * @param entityYRot     Entity Y rotation in degrees
     * @param currentTick    Current game tick
     * @return Array of updated BodyPartInstances
     */
    @Nonnull
    public BodyPartInstance[] computeSimpleTransforms(@Nonnull net.minecraft.world.phys.Vec3 entityPosition,
                                                      float entityYRot, long currentTick) {
        BodyPartInstance[] instances = new BodyPartInstance[partArray.length];

        for (int i = 0; i < partArray.length; i++) {
            BodyPartDefinition def = Objects.requireNonNull(partArray[i], "partArray element");
            BodyPartInstance instance = BodyPartInstance.acquire(def);
            instance.updateSimple(entityPosition, entityYRot, currentTick);
            instances[i] = instance;
        }

        return instances;
    }

    /**
     * Gets the transform chain from entity root to a specific part.
     * Useful for debugging transform issues.
     *
     * @param partId The part to trace to
     * @return List of part IDs from root to target (inclusive)
     */
    @Nonnull
    public List<String> getTransformChain(@Nonnull String partId) {
        List<String> chain = new ArrayList<>();
        String current = partId;

        while (current != null) {
            chain.add(0, current); // Prepend
            BodyPartDefinition def = parts.get(current);
            current = def != null ? def.parentPartId() : null;
        }

        return chain;
    }

    // ==================== Builder ====================

    /**
     * Creates a new builder for constructing BodyPartHierarchy.
     */
    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating BodyPartHierarchy instances.
     */
    public static final class Builder {
        private final Map<String, BodyPartDefinition> parts = new LinkedHashMap<>();
        private final Map<String, List<String>> childrenMap = new HashMap<>();
        private final List<String> rootPartIds = new ArrayList<>();

        private Builder() {}

        /**
         * Adds a body part to the hierarchy.
         * Parent-child relationships are inferred from the definition's parentPartId.
         */
        @Nonnull
        public Builder addPart(@Nonnull BodyPartDefinition part) {
            String partId = part.id();
            parts.put(partId, part);

            String parentId = part.parentPartId();
            if (parentId == null) {
                // Root part
                if (!rootPartIds.contains(partId)) {
                    rootPartIds.add(partId);
                }
            } else {
                // Add to parent's children list
                childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(partId);
            }

            return this;
        }

        /**
         * Adds multiple parts at once.
         */
        @Nonnull
        public Builder addParts(@Nonnull BodyPartDefinition... parts) {
            for (BodyPartDefinition part : parts) {
                addPart(Objects.requireNonNull(part, "part"));
            }
            return this;
        }

        /**
         * Explicitly sets parent for a part (overrides definition's parentPartId).
         */
        @Nonnull
        public Builder setParent(@Nonnull String childId, @Nullable String parentId) {
            // Remove from old parent
            for (List<String> children : childrenMap.values()) {
                children.remove(childId);
            }
            rootPartIds.remove(childId);

            if (parentId == null) {
                rootPartIds.add(childId);
            } else {
                childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(childId);
            }

            return this;
        }

        /**
         * Builds the BodyPartHierarchy.
         *
         * @throws IllegalStateException if no parts have been added
         */
        @Nonnull
        public BodyPartHierarchy build() {
            if (parts.isEmpty()) {
                throw new IllegalStateException("Cannot build hierarchy with no parts");
            }

            // Validate: ensure all parent references exist
            for (BodyPartDefinition part : parts.values()) {
                String parentId = part.parentPartId();
                if (parentId != null && !parts.containsKey(parentId)) {
                    throw new IllegalStateException(
                        "Part '" + part.id() + "' references non-existent parent '" + parentId + "'");
                }
            }

            // If no explicit roots, find parts with null parents
            if (rootPartIds.isEmpty()) {
                for (BodyPartDefinition part : parts.values()) {
                    if (part.parentPartId() == null) {
                        rootPartIds.add(part.id());
                    }
                }
            }

            return new BodyPartHierarchy(parts, childrenMap, rootPartIds);
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Creates a simple flat hierarchy with no parent-child relationships.
     * All parts are treated as roots.
     */
    @Nonnull
    public static BodyPartHierarchy flat(@Nonnull BodyPartDefinition... definitions) {
        Builder builder = builder();
        for (BodyPartDefinition def : definitions) {
            // Override parent to null for flat hierarchy
            BodyPartDefinition flatDef = new BodyPartDefinition(
                def.id(), def.bodyPartType(), def.localOffset(), def.halfExtents(),
                def.parentBoneId(), null, def.renderColor(), def.damageMultiplier()
            );
            builder.addPart(flatDef);
        }
        return builder.build();
    }

    /**
     * Finds a part by body part type (returns first match).
     */
    @Nullable
    public BodyPartDefinition findByType(@Nonnull HitHelper.BodyPart type) {
        for (BodyPartDefinition def : partArray) {
            if (def.bodyPartType() == type) {
                return def;
            }
        }
        return null;
    }

    /**
     * Finds all parts of a given type.
     */
    @Nonnull
    public List<BodyPartDefinition> findAllByType(@Nonnull HitHelper.BodyPart type) {
        List<BodyPartDefinition> result = new ArrayList<>();
        for (BodyPartDefinition def : partArray) {
            if (def.bodyPartType() == type) {
                result.add(def);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return String.format("BodyPartHierarchy[%d parts, roots=%s]",
            parts.size(), rootPartIds);
    }
}
