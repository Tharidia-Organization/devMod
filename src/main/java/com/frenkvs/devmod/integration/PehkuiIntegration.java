package com.frenkvs.devmod.integration;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Soft dependency wrapper per Pehkui API.
 * Usa reflection per evitare hard dependency a compile time.
 *
 * Pehkui API Reference:
 * - ScaleTypes.BASE.getScaleData(entity).getScale() -> scala visiva
 * - ScaleTypes.HITBOX_WIDTH.getScaleData(entity).getScale() -> scala hitbox
 *
 * Per aggiungere Pehkui come dipendenza opzionale in build.gradle:
 *
 * repositories {
 *     maven { url "https://jitpack.io" }
 * }
 *
 * dependencies {
 *     compileOnly "com.github.Virtuoel:Pehkui:3.8.3+1.21-neoforge"
 * }
 */
public class PehkuiIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(PehkuiIntegration.class);

    // Cache of classes and methods for performance
    private static Class<?> scaleTypesClass = null;
    private static Object baseScaleType = null;
    private static Object hitboxWidthScaleType = null;
    private static Method getScaleDataMethod = null;
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
            baseScaleType = scaleTypesClass.getField("BASE").get(null);

            // Get HITBOX_WIDTH field (hitbox scale) - may not exist in old versions
            try {
                hitboxWidthScaleType = scaleTypesClass.getField("HITBOX_WIDTH").get(null);
            } catch (NoSuchFieldException e) {
                // Fallback a WIDTH se HITBOX_WIDTH non esiste
                try {
                    hitboxWidthScaleType = scaleTypesClass.getField("WIDTH").get(null);
                } catch (NoSuchFieldException e2) {
                    hitboxWidthScaleType = baseScaleType; // Use BASE as fallback
                }
            }

            // Get getScaleData(Entity) method
            Class<?> scaleTypeClass = baseScaleType.getClass();
            getScaleDataMethod = scaleTypeClass.getMethod("getScaleData", Entity.class);

            // Get getScale() method from ScaleData
            Object sampleScaleData = getScaleDataMethod.invoke(baseScaleType, (Entity) null);
            if (sampleScaleData != null) {
                getScaleMethod = sampleScaleData.getClass().getMethod("getScale");
            } else {
                // Try to get the method from ScaleData class directly
                Class<?> scaleDataClass = Class.forName("virtuoel.pehkui.api.ScaleData");
                getScaleMethod = scaleDataClass.getMethod("getScale");
            }

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
    public static Float getScale(LivingEntity entity) {
        if (entity == null) return null;

        if (!initAttempted) {
            initReflection();
        }

        if (!initSuccess || baseScaleType == null) {
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
    public static Float getHitboxScale(LivingEntity entity) {
        if (entity == null) return null;

        if (!initAttempted) {
            initReflection();
        }

        if (!initSuccess || hitboxWidthScaleType == null) {
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
