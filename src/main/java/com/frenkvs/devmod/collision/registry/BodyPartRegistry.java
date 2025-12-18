package com.frenkvs.devmod.collision.registry;

import com.frenkvs.devmod.collision.bodypart.BodyPartHierarchy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry mapping EntityType → BodyPartHierarchy.
 *
 * Supports:
 * - Built-in vanilla entity definitions
 * - Modded entity registration via API
 * - Fallback to adaptive AABB for unknown entities
 *
 * Thread-safe singleton with lazy-loading.
 *
 * Usage:
 * <pre>
 * // Get hierarchy for an entity
 * BodyPartHierarchy hierarchy = BodyPartRegistry.INSTANCE.getHierarchy(entity);
 *
 * // Register custom hierarchy for modded entity
 * BodyPartRegistry.INSTANCE.register(MyEntityType.MY_MOB, myHierarchy);
 * </pre>
 */

public final class BodyPartRegistry {

    /**
     * Singleton instance.
     */
    public static final BodyPartRegistry INSTANCE = new BodyPartRegistry();

    // ==================== Internal State ====================

    private final Map<ResourceLocation, BodyPartHierarchy> hierarchies = new ConcurrentHashMap<>();

    // Default hierarchies for fallback
    @Nullable
    private BodyPartHierarchy defaultHumanoid;
    @Nullable
    private BodyPartHierarchy defaultQuadruped;
    @Nullable
    private BodyPartHierarchy defaultHorizontal;
    @Nullable
    private BodyPartHierarchy defaultTall;

    private volatile boolean initialized = false;

    private BodyPartRegistry() {
        // Private constructor for singleton
    }

    // ==================== Initialization ====================

    /**
     * Initializes the registry with vanilla body parts.
     * Should be called during mod setup (FMLCommonSetupEvent).
     */
    public void initialize() {
        if (initialized) return;

        synchronized (this) {
            if (initialized) return;

            // Register vanilla body parts
            VanillaBodyParts.registerAll(this);

            // Set up default fallbacks
            this.defaultHumanoid = VanillaBodyParts.HUMANOID;
            this.defaultQuadruped = VanillaBodyParts.QUADRUPED;
            this.defaultHorizontal = VanillaBodyParts.HORIZONTAL;
            this.defaultTall = VanillaBodyParts.TALL_HUMANOID;

            initialized = true;
        }
    }

    /**
     * Ensures the registry is initialized.
     */
    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    // ==================== Registration API ====================

    /**
     * Registers body parts for an entity type.
     *
     * @param entityType The entity type
     * @param hierarchy  The body part hierarchy for this type
     */
    public void register(@Nonnull EntityType<?> entityType, @Nonnull BodyPartHierarchy hierarchy) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (key != null) {
            hierarchies.put(key, hierarchy);
        }
    }

    /**
     * Registers body parts using a resource location key.
     *
     * @param key       The entity type resource location
     * @param hierarchy The body part hierarchy
     */
    public void register(@Nonnull ResourceLocation key, @Nonnull BodyPartHierarchy hierarchy) {
        hierarchies.put(key, hierarchy);
    }

    /**
     * Registers body parts using a string key (mod:entity format).
     *
     * @param key       The entity type key (e.g., "minecraft:zombie")
     * @param hierarchy The body part hierarchy
     */
    public void register(@Nonnull String key, @Nonnull BodyPartHierarchy hierarchy) {
        hierarchies.put(ResourceLocation.parse(key), hierarchy);
    }

    /**
     * Unregisters body parts for an entity type.
     *
     * @param entityType The entity type to unregister
     * @return The removed hierarchy, or null if none was registered
     */
    @Nullable
    public BodyPartHierarchy unregister(@Nonnull EntityType<?> entityType) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return key != null ? hierarchies.remove(key) : null;
    }

    // ==================== Query API ====================

    /**
     * Gets the hierarchy for an entity.
     * Falls back to adaptive detection for unregistered entities.
     *
     * @param entity The entity to get parts for
     * @return Hierarchy (never null - uses fallback)
     */
    @Nonnull
    public BodyPartHierarchy getHierarchy(@Nonnull Entity entity) {
        ensureInitialized();

        // Try to find registered hierarchy
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key != null) {
            BodyPartHierarchy registered = hierarchies.get(key);
            if (registered != null) {
                return registered;
            }
        }

        // Fallback to adaptive detection
        return getAdaptiveHierarchy(entity);
    }

    /**
     * Gets the hierarchy for an entity type (not instance).
     * May return null if not registered and can't determine from type alone.
     *
     * @param entityType The entity type
     * @return Hierarchy or null if not registered
     */
    @Nullable
    public BodyPartHierarchy getHierarchyForType(@Nonnull EntityType<?> entityType) {
        ensureInitialized();

        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return key != null ? hierarchies.get(key) : null;
    }

    /**
     * Checks if an entity type has a registered hierarchy.
     *
     * @param entityType The entity type
     * @return true if custom parts are registered
     */
    public boolean hasCustomParts(@Nonnull EntityType<?> entityType) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return key != null && hierarchies.containsKey(key);
    }

    /**
     * Gets all registered entity type keys.
     */
    @Nonnull
    public Set<ResourceLocation> getRegisteredTypes() {
        return hierarchies.keySet();
    }

    /**
     * Gets the number of registered hierarchies.
     */
    public int getRegisteredCount() {
        return hierarchies.size();
    }

    // ==================== Adaptive Detection ====================

    /**
     * Determines the appropriate hierarchy based on entity dimensions.
     * Uses aspect ratio and size heuristics.
     */
    @Nonnull
    private BodyPartHierarchy getAdaptiveHierarchy(@Nonnull Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return getDefaultHumanoid();
        }

        float width = entity.getBbWidth();
        float height = entity.getBbHeight();

        // Prevent division by zero
        if (height < 0.1f) {
            return getDefaultHumanoid();
        }

        float aspectRatio = width / height;

        // Horizontal body (dragons, fish, spiders)
        // Width significantly greater than height
        if (aspectRatio > 2.0f) {
            return getDefaultHorizontal();
        }

        // Tall body (enderman, iron golem)
        // Height > 2.5 and relatively thin
        if (height > 2.5f && aspectRatio < 0.5f) {
            return getDefaultTall();
        }

        // Quadruped (pigs, cows, wolves)
        // Width similar to height but not humanoid proportions
        // Check if entity is likely a quadruped based on width/height
        if (aspectRatio > 0.8f && aspectRatio < 1.5f && height < 1.5f) {
            return getDefaultQuadruped();
        }

        // Default to humanoid
        return getDefaultHumanoid();
    }

    // ==================== Default Hierarchies ====================

    @Nonnull
    private BodyPartHierarchy getDefaultHumanoid() {
        if (defaultHumanoid == null) {
            defaultHumanoid = VanillaBodyParts.HUMANOID;
        }
        return defaultHumanoid;
    }

    @Nonnull
    private BodyPartHierarchy getDefaultQuadruped() {
        if (defaultQuadruped == null) {
            defaultQuadruped = VanillaBodyParts.QUADRUPED;
        }
        return defaultQuadruped;
    }

    @Nonnull
    private BodyPartHierarchy getDefaultHorizontal() {
        if (defaultHorizontal == null) {
            defaultHorizontal = VanillaBodyParts.HORIZONTAL;
        }
        return defaultHorizontal;
    }

    @Nonnull
    private BodyPartHierarchy getDefaultTall() {
        if (defaultTall == null) {
            defaultTall = VanillaBodyParts.TALL_HUMANOID;
        }
        return defaultTall;
    }

    /**
     * Sets the default humanoid hierarchy.
     */
    public void setDefaultHumanoid(@Nonnull BodyPartHierarchy hierarchy) {
        this.defaultHumanoid = hierarchy;
    }

    /**
     * Sets the default quadruped hierarchy.
     */
    public void setDefaultQuadruped(@Nonnull BodyPartHierarchy hierarchy) {
        this.defaultQuadruped = hierarchy;
    }

    /**
     * Sets the default horizontal body hierarchy.
     */
    public void setDefaultHorizontal(@Nonnull BodyPartHierarchy hierarchy) {
        this.defaultHorizontal = hierarchy;
    }

    /**
     * Sets the default tall entity hierarchy.
     */
    public void setDefaultTall(@Nonnull BodyPartHierarchy hierarchy) {
        this.defaultTall = hierarchy;
    }

    // ==================== Utility Methods ====================

    /**
     * Clears all registered hierarchies.
     * Useful for testing or reload scenarios.
     */
    public void clear() {
        hierarchies.clear();
        initialized = false;
    }

    /**
     * Reloads the registry (re-initializes vanilla parts).
     */
    public void reload() {
        clear();
        initialize();
    }

    /**
     * Checks if the registry has been initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public String toString() {
        return String.format("BodyPartRegistry[%d types registered, initialized=%b]",
            hierarchies.size(), initialized);
    }
}
