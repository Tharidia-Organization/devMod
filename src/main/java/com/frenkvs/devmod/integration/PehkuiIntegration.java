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

    // Cache delle classi e metodi per performance
    private static Class<?> scaleTypesClass = null;
    private static Object baseScaleType = null;
    private static Object hitboxWidthScaleType = null;
    private static Method getScaleDataMethod = null;
    private static Method getScaleMethod = null;
    private static boolean initAttempted = false;
    private static boolean initSuccess = false;

    /**
     * Inizializza la reflection API di Pehkui.
     * Chiamato automaticamente al primo uso.
     */
    private static synchronized void initReflection() {
        if (initAttempted) return;
        initAttempted = true;

        try {
            // Carica la classe ScaleTypes
            scaleTypesClass = Class.forName("virtuoel.pehkui.api.ScaleTypes");

            // Ottieni il campo BASE (scala visiva)
            baseScaleType = scaleTypesClass.getField("BASE").get(null);

            // Ottieni il campo HITBOX_WIDTH (scala hitbox) - potrebbe non esistere in versioni vecchie
            try {
                hitboxWidthScaleType = scaleTypesClass.getField("HITBOX_WIDTH").get(null);
            } catch (NoSuchFieldException e) {
                // Fallback a WIDTH se HITBOX_WIDTH non esiste
                try {
                    hitboxWidthScaleType = scaleTypesClass.getField("WIDTH").get(null);
                } catch (NoSuchFieldException e2) {
                    hitboxWidthScaleType = baseScaleType; // Usa BASE come fallback
                }
            }

            // Ottieni il metodo getScaleData(Entity)
            Class<?> scaleTypeClass = baseScaleType.getClass();
            getScaleDataMethod = scaleTypeClass.getMethod("getScaleData", Entity.class);

            // Ottieni il metodo getScale() da ScaleData
            Object sampleScaleData = getScaleDataMethod.invoke(baseScaleType, (Entity) null);
            if (sampleScaleData != null) {
                getScaleMethod = sampleScaleData.getClass().getMethod("getScale");
            } else {
                // Prova a ottenere il metodo dalla classe ScaleData direttamente
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
     * Ottiene la scala visiva base dell'entità.
     *
     * @param entity L'entità da controllare
     * @return La scala (1.0 = normale), o null se errore/Pehkui non presente
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
     * Ottiene la scala hitbox dell'entità.
     * Può essere diversa dalla scala visiva in alcune configurazioni.
     *
     * @param entity L'entità da controllare
     * @return La scala hitbox (1.0 = normale), o null se errore
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
     * Verifica se Pehkui è disponibile e funzionante.
     */
    public static boolean isAvailable() {
        if (!initAttempted) {
            initReflection();
        }
        return initSuccess;
    }
}
