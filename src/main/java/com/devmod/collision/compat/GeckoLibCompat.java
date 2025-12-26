package com.devmod.collision.compat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;

public final class GeckoLibCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeckoLibCompat.class);

    private GeckoLibCompat() {} // Utility class

    // ==================== Detection ====================

    private static Boolean geckoLibPresent = null;
    private static Class<?> geoAnimatableClass = null;
    private static Class<?> geoBoneClass = null;
    private static Method getBoneRotationMethod = null;
    private static Method getBonePositionMethod = null;

    /**
     * Checks if GeckoLib is present in the classpath.
     * Result is cached after first check.
     */
    public static boolean isGeckoLibPresent() {
        if (geckoLibPresent == null) {
            geckoLibPresent = detectGeckoLib();
        }
        return geckoLibPresent;
    }

    /**
     * Attempts to detect GeckoLib classes via reflection.
     */
    private static boolean detectGeckoLib() {
        try {
            // GeckoLib 4.x for NeoForge 1.21+
            geoAnimatableClass = Class.forName("software.bernie.geckolib.animatable.GeoAnimatable");
            geoBoneClass = Class.forName("software.bernie.geckolib.cache.object.GeoBone");

            // Get methods for bone data extraction
            getBoneRotationMethod = geoBoneClass.getMethod("getRotation");
            getBonePositionMethod = geoBoneClass.getMethod("getPosition");

            LOGGER.info("[DevMod] GeckoLib detected - OBB compatibility enabled for GeoAnimatable entities");
            return true;

        } catch (ClassNotFoundException e) {
            // Try GeckoLib 3.x class names (older versions)
            try {
                geoAnimatableClass = Class.forName("software.bernie.geckolib3.core.IAnimatable");
                geoBoneClass = Class.forName("software.bernie.geckolib3.geo.render.built.GeoBone");

                getBoneRotationMethod = geoBoneClass.getMethod("getRotationX");
                getBonePositionMethod = geoBoneClass.getMethod("getPivotX");

                LOGGER.info("[DevMod] GeckoLib 3.x detected - OBB compatibility enabled");
                return true;

            } catch (Exception e2) {
                LOGGER.debug("[DevMod] GeckoLib not found - using standard ModelPart system only");
                return false;
            }

        } catch (Exception e) {
            LOGGER.debug("[DevMod] GeckoLib detection failed: {}", e.getMessage());
            return false;
        }
    }

    // ==================== Entity Detection ====================

    /**
     * Checks if an entity uses GeckoLib for rendering.
     *
     * @param entity The entity to check
     * @return true if entity implements GeoAnimatable
     */
    public static boolean isGeckoLibEntity(@Nonnull LivingEntity entity) {
        if (!isGeckoLibPresent() || geoAnimatableClass == null) {
            return false;
        }
        return geoAnimatableClass.isInstance(entity);
    }

    // ==================== Transform Extraction ====================

    /**
     * Extracts bone transforms from a GeckoLib entity.
     * Called during entity rendering when GeckoLib is detected.
     *
     * @param entity The GeckoLib entity
     * @return Map of bone name to transform matrix
     */
    @Nonnull
    public static Map<String, Matrix4f> extractGeckoLibTransforms(@Nonnull LivingEntity entity) {
        Map<String, Matrix4f> transforms = new ConcurrentHashMap<>();

        if (!isGeckoLibEntity(entity)) {
            return transforms;
        }

        try {
            // Get the GeoModel for this entity
            Object model = getGeoModel(entity);
            if (model == null) return transforms;

            // Get all bones from the model
            Object[] bones = getBones(model);
            if (bones == null) return transforms;

            // Extract transform for each bone
            for (Object bone : bones) {
                String boneName = getBoneName(bone);
                if (boneName == null) continue;

                Matrix4f transform = extractBoneTransform(bone);
                if (transform != null) {
                    transforms.put(boneName, transform);
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[DevMod] Failed to extract GeckoLib transforms for {}: {}",
                entity.getClass().getSimpleName(), e.getMessage());
        }

        return transforms;
    }

    /**
     * Gets the GeoModel for a GeckoLib entity via reflection.
     */
    private static Object getGeoModel(LivingEntity entity) {
        try {
            // GeckoLib 4.x: entity.getGeoModel() or through renderer
            // This is complex because GeoModel is retrieved from GeoRenderer
            // For now, return null - full implementation requires renderer hook

            // In practice, we capture transforms via GeoRenderer mixin instead
            return null;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets all bones from a GeoModel.
     */
    private static Object[] getBones(Object model) {
        try {
            // GeoModel.getBones() returns Collection<GeoBone>
            Method getBonesMethod = model.getClass().getMethod("getBones");
            Object result = getBonesMethod.invoke(model);
            if (result instanceof Iterable<?> iterable) {
                return ((java.util.Collection<?>) iterable).toArray();
            }
            return null;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the name of a GeoBone.
     */
    private static String getBoneName(Object bone) {
        try {
            Method getNameMethod = bone.getClass().getMethod("getName");
            return (String) getNameMethod.invoke(bone);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts transform matrix from a GeoBone.
     */
    private static Matrix4f extractBoneTransform(Object bone) {
        try {
            // Get rotation (returns Vector3f or similar)
            Object rotation = getBoneRotationMethod.invoke(bone);
            // Get position
            Object position = getBonePositionMethod.invoke(bone);

            // Extract float values via reflection
            float rotX = 0, rotY = 0, rotZ = 0;
            float posX = 0, posY = 0, posZ = 0;

            if (rotation != null) {
                Class<?> rotClass = rotation.getClass();
                try {
                    // Try JOML Vector3f
                    rotX = (float) rotClass.getMethod("x").invoke(rotation);
                    rotY = (float) rotClass.getMethod("y").invoke(rotation);
                    rotZ = (float) rotClass.getMethod("z").invoke(rotation);
                } catch (NoSuchMethodException e) {
                    // Try field access
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

            // Build transform matrix
            Matrix4f transform = new Matrix4f();
            transform.translate(posX / 16f, posY / 16f, posZ / 16f);
            transform.rotateZYX(rotZ, rotY, rotX);

            return transform;

        } catch (Exception e) {
            LOGGER.debug("[DevMod] Failed to extract bone transform: {}", e.getMessage());
            return null;
        }
    }

    // ==================== Bone Name Mapping ====================

    /**
     * Standard bone name mappings for common GeckoLib models.
     * Maps GeckoLib bone names to our standard body part names.
     */
    public static final Map<String, String> STANDARD_BONE_MAPPINGS = Map.ofEntries(
        // Common GeckoLib naming conventions - Head
        Map.entry("head", "head"),
        Map.entry("Head", "head"),
        // Body/Torso
        Map.entry("body", "body"),
        Map.entry("Body", "body"),
        Map.entry("torso", "body"),
        Map.entry("Torso", "body"),
        // Left Arm
        Map.entry("left_arm", "leftArm"),
        Map.entry("LeftArm", "leftArm"),
        Map.entry("leftArm", "leftArm"),
        // Right Arm
        Map.entry("right_arm", "rightArm"),
        Map.entry("RightArm", "rightArm"),
        Map.entry("rightArm", "rightArm"),
        // Left Leg
        Map.entry("left_leg", "leftLeg"),
        Map.entry("LeftLeg", "leftLeg"),
        Map.entry("leftLeg", "leftLeg"),
        // Right Leg
        Map.entry("right_leg", "rightLeg"),
        Map.entry("RightLeg", "rightLeg"),
        Map.entry("rightLeg", "rightLeg")
    );

    /**
     * Maps a GeckoLib bone name to our standard name.
     *
     * @param geckoLibName The bone name from GeckoLib
     * @return Standardized name, or the original if no mapping exists
     */
    @Nonnull
    public static String mapBoneName(@Nonnull String geckoLibName) {
        String mapped = STANDARD_BONE_MAPPINGS.get(geckoLibName);
        return mapped != null ? mapped : geckoLibName;
    }
}
