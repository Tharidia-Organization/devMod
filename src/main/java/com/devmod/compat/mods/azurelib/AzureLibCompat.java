package com.devmod.compat.mods.azurelib;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class AzureLibCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(AzureLibCompat.class);
    public static final String MOD_ID = "azurelib";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> geoAnimatableClass;
    private static Class<?> geoBoneClass;
    private static Class<?> animatableManagerClass;
    private static Method getBoneRotationMethod;
    private static Method getBonePositionMethod;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "AzureLib";
    }

    @Override
    public int priority() {
        // Same priority as GeckoLib
        return 19;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:azurelib] AzureLib not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:azurelib] AzureLib detected");
        LOGGER.debug("[Compat:azurelib] Version: {}", Compat.getVersion(MOD_ID));

        // Load API classes
        loadApi();
    }

    /**
     * Load AzureLib API classes via reflection.
     */
    private void loadApi() {
        try {
            // AzureLib package structure
            geoAnimatableClass = Class.forName("mod.azure.azurelib.animatable.GeoAnimatable");
            geoBoneClass = Class.forName("mod.azure.azurelib.cache.object.GeoBone");

            // Get bone methods
            getBoneRotationMethod = geoBoneClass.getMethod("getRotation");
            getBonePositionMethod = geoBoneClass.getMethod("getPosition");

            // Try animation manager
            try {
                animatableManagerClass = Class.forName(
                    "mod.azure.azurelib.animatable.instance.AnimatableInstanceCache");
            } catch (ClassNotFoundException ignored) {}

            apiAvailable = true;
            LOGGER.info("[Compat:azurelib] AzureLib API loaded");

        } catch (ClassNotFoundException e) {
            LOGGER.debug("[Compat:azurelib] AzureLib API classes not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            LOGGER.debug("[Compat:azurelib] AzureLib API method not found: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.debug("[Compat:azurelib] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:azurelib] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "AzureLib entity detection, bone transforms, animation tracking";
    }

    /**
     * Check if AzureLib is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the AzureLib API is accessible.
     */
    public static boolean isApiAvailable() {
        return apiAvailable;
    }

    /**
     * Check if an entity uses AzureLib animations.
     *
     * @param entity The entity to check
     * @return true if entity is an AzureLib animated entity
     */
    public static boolean isAzureLibEntity(LivingEntity entity) {
        if (!apiAvailable || entity == null || geoAnimatableClass == null) {
            return false;
        }
        return geoAnimatableClass.isInstance(entity);
    }

    /**
     * Extract bone transforms from an AzureLib entity.
     *
     * @param entity The entity
     * @return Map of bone name to transform matrix
     */
    public static Map<String, Matrix4f> extractBoneTransforms(LivingEntity entity) {
        Map<String, Matrix4f> transforms = new HashMap<>();

        if (!isAzureLibEntity(entity)) {
            return transforms;
        }

        try {
            // Get the model and bones
            Object model = getGeoModel(entity);
            if (model == null) return transforms;

            Object[] bones = getBones(model);
            if (bones == null) return transforms;

            for (Object bone : bones) {
                String boneName = getBoneName(bone);
                if (boneName == null) continue;

                Matrix4f transform = extractBoneTransform(bone);
                if (transform != null) {
                    transforms.put(boneName, transform);
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:azurelib] Failed to extract transforms: {}", e.getMessage());
        }

        return transforms;
    }

    private static Object getGeoModel(LivingEntity entity) {
        // Similar to GeckoLib - requires renderer hook
        return null;
    }

    private static Object[] getBones(Object model) {
        try {
            Method getBonesMethod = model.getClass().getMethod("getBones");
            Object result = getBonesMethod.invoke(model);
            if (result instanceof Collection<?> collection) {
                return collection.toArray();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String getBoneName(Object bone) {
        try {
            Method getNameMethod = bone.getClass().getMethod("getName");
            return (String) getNameMethod.invoke(bone);
        } catch (Exception ignored) {}
        return null;
    }

    private static Matrix4f extractBoneTransform(Object bone) {
        try {
            Object rotation = getBoneRotationMethod.invoke(bone);
            Object position = getBonePositionMethod.invoke(bone);

            float rotX = 0, rotY = 0, rotZ = 0;
            float posX = 0, posY = 0, posZ = 0;

            if (rotation != null) {
                Class<?> rotClass = rotation.getClass();
                try {
                    rotX = (float) rotClass.getMethod("x").invoke(rotation);
                    rotY = (float) rotClass.getMethod("y").invoke(rotation);
                    rotZ = (float) rotClass.getMethod("z").invoke(rotation);
                } catch (NoSuchMethodException e) {
                    rotX = rotClass.getField("x").getFloat(rotation);
                    rotY = rotClass.getField("y").getFloat(rotation);
                    rotZ = rotClass.getField("z").getFloat(rotation);
                }
            }

            if (position != null) {
                Class<?> posClass = position.getClass();
                try {
                    posX = (float) posClass.getMethod("x").invoke(position);
                    posY = (float) posClass.getMethod("y").invoke(position);
                    posZ = (float) posClass.getMethod("z").invoke(position);
                } catch (NoSuchMethodException e) {
                    posX = posClass.getField("x").getFloat(position);
                    posY = posClass.getField("y").getFloat(position);
                    posZ = posClass.getField("z").getFloat(position);
                }
            }

            Matrix4f transform = new Matrix4f();
            transform.translate(posX / 16f, posY / 16f, posZ / 16f);
            transform.rotateZYX(rotZ, rotY, rotX);

            return transform;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get animation state for an AzureLib entity.
     *
     * @param entity The entity
     * @return Animation state info
     */
    public static Map<String, Object> getAnimationState(LivingEntity entity) {
        Map<String, Object> state = new LinkedHashMap<>();

        if (!isAzureLibEntity(entity)) {
            return state;
        }

        state.put("isAzureLib", true);

        try {
            Method getCacheMethod = entity.getClass().getMethod("getAnimatableInstanceCache");
            Object cache = getCacheMethod.invoke(entity);

            if (cache != null) {
                Method getManagerMethod = cache.getClass().getMethod("getManagerForId", long.class);
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
                            } catch (Exception ignored) {}
                        }

                        if (!activeAnimations.isEmpty()) {
                            state.put("activeAnimations", activeAnimations);
                        }
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:azurelib] Could not get animation state: {}", e.getMessage());
        }

        return state;
    }

    /**
     * Standard bone name mappings (same as GeckoLib).
     */
    public static final Map<String, String> STANDARD_BONE_MAPPINGS = Map.ofEntries(
        Map.entry("head", "head"),
        Map.entry("Head", "head"),
        Map.entry("body", "body"),
        Map.entry("Body", "body"),
        Map.entry("torso", "body"),
        Map.entry("left_arm", "leftArm"),
        Map.entry("LeftArm", "leftArm"),
        Map.entry("right_arm", "rightArm"),
        Map.entry("RightArm", "rightArm"),
        Map.entry("left_leg", "leftLeg"),
        Map.entry("LeftLeg", "leftLeg"),
        Map.entry("right_leg", "rightLeg"),
        Map.entry("RightLeg", "rightLeg")
    );

    /**
     * Map a bone name to standard name.
     */
    public static String mapBoneName(String azureLibName) {
        String mapped = STANDARD_BONE_MAPPINGS.get(azureLibName);
        return mapped != null ? mapped : azureLibName;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "AzureLib: not available";
        }
        if (!apiAvailable) {
            return "AzureLib: detected (API not loaded)";
        }
        return "AzureLib: API available";
    }
}
