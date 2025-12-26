package com.devmod.compat.mods.geckolib;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;

import com.devmod.collision.compat.GeckoLibCompat;
import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class GeckoLibModuleCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeckoLibModuleCompat.class);
    public static final String MOD_ID = "geckolib";

    private static boolean available = false;
    private static boolean initialized = false;

    // Cached reflection for additional features
    private static Class<?> animatableManagerClass;
    private static Class<?> animationControllerClass;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "GeckoLib";
    }

    @Override
    public int priority() {
        // High priority - animation library affects rendering
        return 18;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:geckolib] GeckoLib not found");
            return;
        }

        // Use existing detection from collision compat
        available = GeckoLibCompat.isGeckoLibPresent();

        if (available) {
            LOGGER.info("[Compat:geckolib] GeckoLib detected");
            LOGGER.debug("[Compat:geckolib] Version: {}", Compat.getVersion(MOD_ID));
            loadAdditionalApi();
        }
    }

    /**
     * Load additional API features beyond collision compat.
     */
    private void loadAdditionalApi() {
        try {
            // Try to load animation state tracking
            animatableManagerClass = Class.forName(
                "software.bernie.geckolib.animatable.instance.AnimatableInstanceCache");

            animationControllerClass = Class.forName(
                "software.bernie.geckolib.animation.AnimationController");

            LOGGER.debug("[Compat:geckolib] Animation API loaded");

        } catch (ClassNotFoundException e) {
            LOGGER.debug("[Compat:geckolib] Animation API not available: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:geckolib] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "GeckoLib entity detection, bone transform extraction, animation tracking";
    }

    /**
     * Check if GeckoLib is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if an entity uses GeckoLib animations.
     * Delegates to collision compat.
     *
     * @param entity The entity to check
     * @return true if entity is a GeckoLib animated entity
     */
    public static boolean isGeckoLibEntity(LivingEntity entity) {
        return GeckoLibCompat.isGeckoLibEntity(entity);
    }

    /**
     * Extract bone transforms from a GeckoLib entity.
     * Delegates to collision compat.
     *
     * @param entity The entity
     * @return Map of bone name to transform matrix
     */
    public static Map<String, Matrix4f> extractBoneTransforms(LivingEntity entity) {
        return GeckoLibCompat.extractGeckoLibTransforms(entity);
    }

    /**
     * Map a GeckoLib bone name to standard name.
     * Delegates to collision compat.
     *
     * @param geckoLibName The bone name
     * @return Standardized name
     */
    public static String mapBoneName(String geckoLibName) {
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
     * @return Animation state info, or null
     */
    public static Map<String, Object> getAnimationState(LivingEntity entity) {
        Map<String, Object> state = new LinkedHashMap<>();

        if (!available || entity == null || !isGeckoLibEntity(entity)) {
            return state;
        }

        state.put("isGeckoLib", true);

        try {
            // Try to get animation controller info
            Method getAnimatableCacheMethod = entity.getClass().getMethod("getAnimatableInstanceCache");
            Object cache = getAnimatableCacheMethod.invoke(entity);

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
                                LOGGER.trace("[Compat:geckolib] Failed to read animation name", e);
                            }
                        }

                        if (!activeAnimations.isEmpty()) {
                            state.put("activeAnimations", activeAnimations);
                        }
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:geckolib] Could not get animation state: {}", e.getMessage());
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
    public static String getGeckoLibEntityType(LivingEntity entity) {
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
            LOGGER.trace("[Compat:geckolib] Failed to get GeoModel", e);
        }

        return entity.getClass().getSimpleName();
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "GeckoLib: not available";
        }
        boolean hasAnimApi = animatableManagerClass != null;
        return "GeckoLib: available" + (hasAnimApi ? " [animation API]" : "");
    }
}
