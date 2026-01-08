package com.devmod.config.handler;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import net.minecraft.world.item.ItemStack;

import com.devmod.config.handler.impl.ArmorConfigHandler;
import com.devmod.config.handler.impl.FoodConfigHandler;
import com.devmod.config.handler.impl.FuelConfigHandler;
import com.devmod.config.handler.impl.UsableConfigHandler;
import com.devmod.config.handler.impl.WeaponConfigHandler;
import com.devmod.config.stats.IItemStats;

/**
 * Registry for config handlers.
 * Provides centralized access to all stat handlers.
 */
public final class ConfigHandlerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigHandlerRegistry.class);

    private static final Map<Class<? extends IItemStats>, IConfigHandler<?>> HANDLERS = new ConcurrentHashMap<>();

    private ConfigHandlerRegistry() {} // Utility class

    /**
     * Register a handler for a stats type.
     *
     * @param handler the handler to register
     * @param <S> the stats type
     */
    public static <S extends IItemStats> void register(IConfigHandler<S> handler) {
        Class<S> statsClass = handler.getStatsClass();
        if (HANDLERS.containsKey(statsClass)) {
            LOGGER.warn("Handler already registered for {}, skipping", statsClass.getSimpleName());
            return;
        }
        HANDLERS.put(statsClass, handler);
        LOGGER.debug("Registered config handler for {}", statsClass.getSimpleName());
    }

    /**
     * Get a handler by stats class.
     *
     * @param statsClass the stats class
     * @param <S> the stats type
     * @return the handler, or empty if not registered
     */
    @SuppressWarnings("unchecked")
    public static <S extends IItemStats> Optional<IConfigHandler<S>> get(Class<S> statsClass) {
        IConfigHandler<?> handler = HANDLERS.get(statsClass);
        return Optional.ofNullable((IConfigHandler<S>) handler);
    }

    /**
     * Get a handler that applies to the given item.
     *
     * @param stack the item stack
     * @return the first applicable handler, or null if none
     */
    @Nullable
    public static IConfigHandler<?> getForItem(ItemStack stack) {
        for (IConfigHandler<?> handler : HANDLERS.values()) {
            if (handler.appliesTo(stack)) {
                return handler;
            }
        }
        return null;
    }

    /**
     * Get all registered handlers.
     *
     * @return collection of all handlers
     */
    public static Collection<IConfigHandler<?>> getAll() {
        return HANDLERS.values();
    }

    /**
     * Check if a handler is registered for a stats class.
     *
     * @param statsClass the stats class
     * @return true if registered
     */
    public static boolean isRegistered(Class<? extends IItemStats> statsClass) {
        return HANDLERS.containsKey(statsClass);
    }

    /**
     * Load all registered handlers.
     */
    public static void loadAll() {
        LOGGER.info("Loading {} config handlers", HANDLERS.size());
        for (IConfigHandler<?> handler : HANDLERS.values()) {
            try {
                handler.load();
            } catch (Exception e) {
                LOGGER.error("Failed to load handler for {}", handler.getStatsClass().getSimpleName(), e);
            }
        }
    }

    /**
     * Save all registered handlers.
     */
    public static void saveAll() {
        LOGGER.info("Saving {} config handlers", HANDLERS.size());
        for (IConfigHandler<?> handler : HANDLERS.values()) {
            try {
                handler.save();
            } catch (Exception e) {
                LOGGER.error("Failed to save handler for {}", handler.getStatsClass().getSimpleName(), e);
            }
        }
    }

    /**
     * Clear all handlers (for testing).
     */
    public static void reset() {
        HANDLERS.clear();
        LOGGER.debug("Config handler registry reset");
    }

    /**
     * Get the number of registered handlers.
     */
    public static int size() {
        return HANDLERS.size();
    }

    /**
     * Register all built-in handlers.
     * Call this once during mod initialization.
     */
    public static void registerAll() {
        if (!HANDLERS.isEmpty()) {
            LOGGER.debug("Handlers already registered, skipping registerAll()");
            return;
        }
        register(WeaponConfigHandler.INSTANCE);
        register(ArmorConfigHandler.INSTANCE);
        register(FoodConfigHandler.INSTANCE);
        register(FuelConfigHandler.INSTANCE);
        register(UsableConfigHandler.INSTANCE);
        LOGGER.info("Registered {} built-in config handlers", HANDLERS.size());
    }

    /**
     * Initialize all handlers with the given config directory.
     * Call this during server startup after registerAll().
     *
     * @param configDir the base config directory
     */
    public static void initializeAll(Path configDir) {
        LOGGER.info("Initializing {} config handlers with directory: {}", HANDLERS.size(), configDir);
        for (IConfigHandler<?> handler : HANDLERS.values()) {
            try {
                if (handler instanceof AbstractConfigHandler<?> abstractHandler) {
                    abstractHandler.initialize(configDir);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to initialize handler for {}", handler.getStatsClass().getSimpleName(), e);
            }
        }
    }
}
