package com.devmod.compat.mods.geckolib;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;

import net.minecraft.world.entity.LivingEntity;

import com.devmod.collision.compat.GeckoLibCompat;
import com.devmod.compat.BaseCompatModule;

/**
 * Compatibility module for GeckoLib.
 *
 * <p>Migrated to extend BaseCompatModule for standardized initialization,
 * reflection caching, and error handling.
 */
public class GeckoLibModuleCompat extends BaseCompatModule {

    public static final String MOD_ID = "geckolib";

    // Class names for reflection
    private static final String ANIMATABLE_CACHE_CLASS = "software.bernie.geckolib.animatable.instance.AnimatableInstanceCache";
    private static final String ANIMATION_CONTROLLER_CLASS = "software.bernie.geckolib.animation.AnimationController";

    // Singleton instance
    private static GeckoLibModuleCompat instance;

    public GeckoLibModuleCompat() {
        super(MOD_ID, "GeckoLib", 18); // High priority - animation library affects rendering
        instance = this;
    }

    @Override
    protected void doInitCommon() throws Exception {
        // Check using collision compat first
        if (!GeckoLibCompat.isGeckoLibPresent()) {
            throw new Exception("GeckoLib not present according to collision compat");
        }

        // Load additional API classes
        loadAdditionalApi();

        info("GeckoLib detected and initialized");
    }

    /**
     * Load additional API features beyond collision compat.
     */
    private void loadAdditionalApi() {
        // Try to load animation state tracking
        Class<?> animatableManagerClass = loadOptionalClass(ANIMATABLE_CACHE_CLASS);
        Class<?> animationControllerClass = loadOptionalClass(ANIMATION_CONTROLLER_CLASS);

        if (animationControllerClass != null) {
            debug("AnimationController class: {}", animationControllerClass.getName());
        }

        if (animatableManagerClass != null) {
            debug("Animation API loaded");
        }
    }

    @Override
    protected void doInitClient() {
        debug("Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "GeckoLib entity detection, bone transform extraction, animation tracking";
    }

    // ============================================================================
    // STATIC API
    // ============================================================================

    /**
     * Check if GeckoLib is loaded and available.
     */
    public static boolean isGeckoLibLoaded() {
        return instance != null && instance.available;
    }

    /**
     * Check if an entity uses GeckoLib animations.
     * Delegates to collision compat.
     *
     * @param entity The entity to check
     * @return true if entity is a GeckoLib animated entity
     */
    public static boolean isGeckoLibEntity(@Nonnull LivingEntity entity) {
        return GeckoLibCompat.isGeckoLibEntity(entity);
    }

    /**
     * Extract bone transforms from a GeckoLib entity.
     * Delegates to collision compat.
     *
     * @param entity The entity
     * @return Map of bone name to transform matrix
     */
    public static Map<String, Matrix4f> extractBoneTransforms(@Nonnull LivingEntity entity) {
        return GeckoLibCompat.extractGeckoLibTransforms(entity);
    }

    /**
     * Map a GeckoLib bone name to standard name.
     * Delegates to collision compat.
     *
     * @param geckoLibName The bone name
     * @return Standardized name
     */
    @Nonnull
    public static String mapBoneName(@Nonnull String geckoLibName) {
        return GeckoLibCompat.mapBoneName(geckoLibName);
    }

    /**
     * Get all standard bone mappings.
     */
    public static Map<String, String> getStandardBoneMappings() {
        return GeckoLibCompat.STANDARD_BONE_MAPPINGS;
    }

    /**
     * Get the current animation state for an entity.
     *
     * @param entity The GeckoLib entity
     * @return Animation state info, or empty map
     */
    public static Map<String, Object> getAnimationState(LivingEntity entity) {
        Map<String, Object> state = new LinkedHashMap<>();

        if (!isGeckoLibLoaded() || entity == null || !isGeckoLibEntity(entity)) {
            return state;
        }

        state.put("isGeckoLib", true);

        try {
            // Try to get animation controller info
            Method getAnimatableCacheMethod = entity.getClass().getMethod("getAnimatableInstanceCache");
            Object cache = getAnimatableCacheMethod.invoke(entity);

            Class<?> animatableManagerClass = instance.classCache.get(ANIMATABLE_CACHE_CLASS);
            if (cache != null && animatableManagerClass != null) {
                Method getManagerMethod = cache.getClass().getMethod("getManagerForId", long.class);
                // Get manager for instance ID 0 (common case)
                Object manager = getManagerMethod.invoke(cache, 0L);

                if (manager != null) {
                    Method getControllersMethod = manager.getClass().getMethod("getAnimationControllers");
                    Object controllers = getControllersMethod.invoke(manager);

                    if (controllers instanceof Map<?, ?> controllerMap) {
                        state.put("controllerCount", controllerMap.size());

                        List<String> activeAnimations = new ArrayList<>();
                        for (Object controller : controllerMap.values()) {
                            try {
                                Method getCurrentAnimMethod = controller.getClass()
                                    .getMethod("getCurrentAnimation");
                                Object anim = getCurrentAnimMethod.invoke(controller);
                                if (anim != null) {
                                    Method getNameMethod = anim.getClass().getMethod("name");
                                    Object name = getNameMethod.invoke(anim);
                                    if (name != null) {
                                        activeAnimations.add(name.toString());
                                    }
                                }
                            } catch (Exception e) {
                                instance.logger.trace("[Compat:geckolib] Failed to read animation name", e);
                            }
                        }

                        if (!activeAnimations.isEmpty()) {
                            state.put("activeAnimations", activeAnimations);
                        }
                    }
                }
            }

        } catch (Exception e) {
            if (instance != null) {
                instance.debug("Could not get animation state: {}", e.getMessage());
            }
        }

        return state;
    }

    /**
     * Check if an entity is currently animating.
     *
     * @param entity The entity
     * @return true if entity has active animations
     */
    public static boolean isAnimating(LivingEntity entity) {
        Map<String, Object> state = getAnimationState(entity);
        return state.containsKey("activeAnimations");
    }

    /**
     * Get the entity type name for a GeckoLib entity.
     * Useful for logging and telemetry.
     *
     * @param entity The entity
     * @return Type name
     */
    public static String getGeckoLibEntityType(@Nonnull LivingEntity entity) {
        if (!isGeckoLibEntity(entity)) {
            return "unknown";
        }

        // Try to get the model type
        try {
            Method getModelMethod = entity.getClass().getMethod("getGeoModel");
            Object model = getModelMethod.invoke(entity);
            if (model != null) {
                return model.getClass().getSimpleName();
            }
        } catch (Exception e) {
            if (instance != null) {
                instance.logger.trace("[Compat:geckolib] Failed to get GeoModel", e);
            }
        }

        return entity.getClass().getSimpleName();
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!isGeckoLibLoaded()) {
            return "GeckoLib: not available";
        }

        boolean hasAnimApi = instance != null && instance.classCache.containsKey(ANIMATABLE_CACHE_CLASS);
        return "GeckoLib: available" + (hasAnimApi ? " [animation API]" : "");
    }
}
