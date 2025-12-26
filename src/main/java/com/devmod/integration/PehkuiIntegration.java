package com.devmod.integration;

import java.lang.reflect.Method;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class PehkuiIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(PehkuiIntegration.class);

    // Cache of classes and methods for performance
    @Nullable
    private static Class<?> scaleTypesClass = null;
    @Nullable
    private static Object baseScaleType = null;
    @Nullable
    private static Object hitboxWidthScaleType = null;
    @Nullable
    private static Method getScaleDataMethod = null;
    @Nullable
    private static Method getScaleMethod = null;
    private static boolean initAttempted = false;
    private static boolean initSuccess = false;

    /**
     * Initialize Pehkui reflection API.
     * Called automatically on first use.
     */
    private static synchronized void initReflection() {
        if (initAttempted) return;
        initAttempted = true;

        try {
            // Load ScaleTypes class
            scaleTypesClass = Class.forName("virtuoel.pehkui.api.ScaleTypes");

            // Get BASE field (visual scale)
            Object baseScaleTypeLocal = scaleTypesClass.getField("BASE").get(null);
            if (baseScaleTypeLocal == null) {
                throw new IllegalStateException("Pehkui BASE scale type missing");
            }

            // Get HITBOX_WIDTH field (hitbox scale) - may not exist in old versions
            Object hitboxScaleTypeLocal = null;
            try {
                hitboxScaleTypeLocal = scaleTypesClass.getField("HITBOX_WIDTH").get(null);
            } catch (NoSuchFieldException e) {
                // Fallback a WIDTH se HITBOX_WIDTH non esiste
                try {
                    hitboxScaleTypeLocal = scaleTypesClass.getField("WIDTH").get(null);
                } catch (NoSuchFieldException e2) {
                    hitboxScaleTypeLocal = baseScaleTypeLocal; // Use BASE as fallback
                }
            }
            if (hitboxScaleTypeLocal == null) {
                hitboxScaleTypeLocal = baseScaleTypeLocal;
            }

            // Get getScaleData(Entity) method
            Class<?> scaleTypeClass = baseScaleTypeLocal.getClass();
            Method scaleDataMethod = scaleTypeClass.getMethod("getScaleData", Entity.class);

            // Get getScale() method from ScaleData
            Object sampleScaleData = scaleDataMethod.invoke(baseScaleTypeLocal, (Entity) null);
            Method scaleMethod;
            if (sampleScaleData != null) {
                scaleMethod = sampleScaleData.getClass().getMethod("getScale");
            } else {
                // Try to get the method from ScaleData class directly
                Class<?> scaleDataClass = Class.forName("virtuoel.pehkui.api.ScaleData");
                scaleMethod = scaleDataClass.getMethod("getScale");
            }

            baseScaleType = baseScaleTypeLocal;
            hitboxWidthScaleType = hitboxScaleTypeLocal;
            getScaleDataMethod = scaleDataMethod;
            getScaleMethod = scaleMethod;
            initSuccess = true;
            LOGGER.debug("[DevMod] Pehkui reflection API initialized successfully");

        } catch (ClassNotFoundException e) {
            LOGGER.debug("[DevMod] Pehkui not found (ClassNotFoundException)");
        } catch (Exception e) {
            LOGGER.warn("[DevMod] Failed to initialize Pehkui reflection: {}", e.getMessage());
        }
    }

    /**
     * Get the base visual scale of the entity.
     *
     * @param entity The entity to check
     * @return The scale (1.0 = normal), or null if error/Pehkui not present
     */
    @Nullable
    public static Float getScale(@Nullable LivingEntity entity) {
        if (entity == null) return null;

        if (!initAttempted) {
            initReflection();
        }

        if (!initSuccess || baseScaleType == null || getScaleDataMethod == null || getScaleMethod == null) {
            return null;
        }

        try {
            Object scaleData = getScaleDataMethod.invoke(baseScaleType, entity);
            if (scaleData == null) return 1.0f;

            Object result = getScaleMethod.invoke(scaleData);
            if (result instanceof Float f) {
                return f;
            } else if (result instanceof Number n) {
                return n.floatValue();
            }
            return 1.0f;

        } catch (Exception e) {
            LOGGER.debug("[DevMod] Error getting Pehkui scale: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the hitbox scale of the entity.
     * May differ from visual scale in some configurations.
     *
     * @param entity The entity to check
     * @return The hitbox scale (1.0 = normal), or null if error
     */
    @Nullable
    public static Float getHitboxScale(@Nullable LivingEntity entity) {
        if (entity == null) return null;

        if (!initAttempted) {
            initReflection();
        }

        if (!initSuccess || hitboxWidthScaleType == null || getScaleDataMethod == null || getScaleMethod == null) {
            return null;
        }

        try {
            Object scaleData = getScaleDataMethod.invoke(hitboxWidthScaleType, entity);
            if (scaleData == null) return 1.0f;

            Object result = getScaleMethod.invoke(scaleData);
            if (result instanceof Float f) {
                return f;
            } else if (result instanceof Number n) {
                return n.floatValue();
            }
            return 1.0f;

        } catch (Exception e) {
            LOGGER.debug("[DevMod] Error getting Pehkui hitbox scale: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if Pehkui is available and working.
     */
    public static boolean isAvailable() {
        if (!initAttempted) {
            initReflection();
        }
        return initSuccess;
    }
}
