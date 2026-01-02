package com.devmod.integration;

import java.lang.reflect.Method;
import java.util.Objects;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import net.neoforged.fml.ModList;

/**
 * Integration with LittleTiles mod.
 *
 * LittleTiles uses per-level LittleAnimationHandler instances that are
 * created lazily. For dynamic dimensions, we need to force handler
 * initialization to prevent NPE in Entity.checkInsideBlocks mixin.
 */
public class LittleTilesIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(LittleTilesIntegration.class);

    private static boolean littleTilesLoaded = false;
    private static boolean initialized = false;
    private static boolean handlersAvailable = false;

    // Cached reflection references
    private static @Nullable Object handlersInstance = null;
    private static @Nullable Method getHandlerMethod = null;

    /**
     * Initialize the LittleTiles integration.
     * Call during mod setup (FMLCommonSetupEvent).
     */
    public static void init() {
        if (initialized) return;

        littleTilesLoaded = ModList.get().isLoaded("littletiles");

        if (littleTilesLoaded) {
            try {
                // Get the LittleAnimationHandlers singleton instance
                Class<?> handlersClass = Class.forName(
                    "team.creative.littletiles.common.level.handler.LittleAnimationHandlers");

                // LittleAnimationHandlers has a static INSTANCE field or is accessed via LittleTiles main class
                // Try to find the get method that takes a Level parameter
                getHandlerMethod = handlersClass.getMethod("get", Level.class);

                // The handlers instance is typically a singleton accessed statically
                // In LittleTiles, it's usually LittleTiles.ANIMATION_HANDLERS or similar
                Class<?> mainClass = Class.forName("team.creative.littletiles.LittleTiles");
                java.lang.reflect.Field handlersField = null;

                // Try common field names
                for (String fieldName : new String[]{"ANIMATION_HANDLERS", "HANDLERS", "animationHandlers"}) {
                    try {
                        handlersField = mainClass.getDeclaredField(fieldName);
                        handlersField.setAccessible(true);
                        handlersInstance = handlersField.get(null);
                        if (handlersInstance != null) {
                            LOGGER.debug("[DevMod-LT] Found handlers field: {}", fieldName);
                            break;
                        }
                    } catch (NoSuchFieldException ignored) {
                        // Try next field name
                    }
                }

                if (handlersInstance != null && getHandlerMethod != null) {
                    handlersAvailable = true;
                    LOGGER.info("[DevMod] LittleTiles detected - dynamic dimension compatibility enabled");
                } else {
                    LOGGER.info("[DevMod] LittleTiles detected but handlers not accessible - basic compatibility mode");
                }

            } catch (ClassNotFoundException e) {
                LOGGER.debug("[DevMod] LittleTiles classes not found: {}", e.getMessage());
            } catch (Exception e) {
                LOGGER.warn("[DevMod] Failed to initialize LittleTiles integration: {}", e.getMessage());
            }
        } else {
            LOGGER.debug("[DevMod] LittleTiles not detected - integration disabled");
        }

        initialized = true;
    }

    /**
     * Check if LittleTiles is loaded.
     */
    public static boolean isLittleTilesLoaded() {
        return littleTilesLoaded;
    }

    /**
     * Check if handler registration is available.
     */
    public static boolean isHandlersAvailable() {
        return handlersAvailable;
    }

    /**
     * Register a dynamic dimension with LittleTiles.
     * This forces lazy initialization of the LittleAnimationHandler for this level.
     *
     * MUST be called after the ServerLevel is fully created and added to the server.
     *
     * @param level The ServerLevel of the new dimension
     */
    public static void registerDynamicDimension(ServerLevel level) {
        Objects.requireNonNull(level, "ServerLevel cannot be null");

        if (!littleTilesLoaded) return;

        try {
            if (handlersAvailable && handlersInstance != null && getHandlerMethod != null) {
                // Call get(level) to force lazy initialization of the handler
                Object handler = getHandlerMethod.invoke(handlersInstance, level);

                if (handler != null) {
                    LOGGER.info("[DevMod-LT] Initialized LittleAnimationHandler for dimension {}",
                        level.dimension().location());
                } else {
                    LOGGER.warn("[DevMod-LT] Handler initialization returned null for dimension {}",
                        level.dimension().location());
                }
            } else {
                LOGGER.debug("[DevMod-LT] Handlers not available, skipping registration for {}",
                    level.dimension().location());
            }
        } catch (Exception e) {
            LOGGER.warn("[DevMod-LT] Failed to register dimension {} with LittleTiles: {}",
                level.dimension().location(), e.getMessage());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[DevMod-LT] Stack trace:", e);
            }
        }
    }

    /**
     * Unregister a dynamic dimension from LittleTiles.
     * This cleans up the handler when the dimension is destroyed.
     *
     * @param level The ServerLevel being destroyed
     */
    public static void unregisterDynamicDimension(ServerLevel level) {
        Objects.requireNonNull(level, "ServerLevel cannot be null");

        if (!littleTilesLoaded) return;

        // LittleTiles handlers are typically cleaned up automatically when the level is unloaded
        // via LevelEvent.Unload. We just log for debugging purposes.
        LOGGER.debug("[DevMod-LT] Dimension {} unregistered from LittleTiles tracking",
            level.dimension().location());
    }
}
