package com.devmod.compat.mods.dummmmmmy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class DummmmmmyCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(DummmmmmyCompat.class);
    public static final String MOD_ID = "dummmmmmy";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> dummyEntityClass;
    private static Method getTotalDamageMethod;
    private static Method resetDamageMethod;
    private static Method getDpsMethod;

    // Track spawned dummies for Arena
    private static final Map<String, UUID> spawnedDummies = new HashMap<>();
    private static final String DEVMOD_TAG = "devmod_arena_dummy";
    private static final ResourceLocation DUMMY_ENTITY_ID =
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "dummy");

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Dummmmmmy";
    }

    @Override
    public int priority() {
        // Lower priority - testing tool
        return 45;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:dummmmmmy] Dummmmmmy not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:dummmmmmy] Dummmmmmy detected");
        LOGGER.debug("[Compat:dummmmmmy] Version: {}", Compat.getVersion(MOD_ID));

        // Load API classes
        loadApi();
    }

    /**
     * Load Dummmmmmy API classes via reflection.
     */
    private void loadApi() {
        try {
            // Try common package structures
            String[] packages = {
                "com.milamber_.dummmmmmy.common.entities",
                "com.milamber_.dummmmmmy.entity",
                "com.dummmmmmy.common.entities"
            };

            for (String pkg : packages) {
                try {
                    dummyEntityClass = Class.forName(pkg + ".DummyEntity");
                    LOGGER.debug("[Compat:dummmmmmy] Found DummyEntity at {}", pkg);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            if (dummyEntityClass != null) {
                // Try to find damage tracking methods
                try {
                    getTotalDamageMethod = dummyEntityClass.getMethod("getTotalDamage");
                } catch (NoSuchMethodException e) {
                    // Try alternative names
                    try {
                        getTotalDamageMethod = dummyEntityClass.getMethod("getDamageTaken");
                    } catch (NoSuchMethodException ignored) {}
                }

                try {
                    resetDamageMethod = dummyEntityClass.getMethod("resetDamage");
                } catch (NoSuchMethodException e) {
                    try {
                        resetDamageMethod = dummyEntityClass.getMethod("reset");
                    } catch (NoSuchMethodException ignored) {}
                }

                try {
                    getDpsMethod = dummyEntityClass.getMethod("getDps");
                } catch (NoSuchMethodException ignored) {}

                apiAvailable = true;
                LOGGER.info("[Compat:dummmmmmy] Dummmmmmy API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:dummmmmmy] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:dummmmmmy] Client initialization complete");
    }

    @Override
    public void shutdown() {
        // Clean up spawned dummies
        spawnedDummies.clear();
    }

    @Override
    public String getFeatureDescription() {
        return "Training dummy spawning, DPS tracking, damage statistics";
    }

    /**
     * Check if Dummmmmmy is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the API is accessible.
     */
    public static boolean isApiAvailable() {
        return apiAvailable;
    }

    /**
     * Check if an entity is a Dummmmmmy dummy.
     *
     * @param entity The entity to check
     * @return true if entity is a dummy
     */
    public static boolean isDummy(Entity entity) {
        if (!available || entity == null || dummyEntityClass == null) {
            return false;
        }
        return dummyEntityClass.isInstance(entity);
    }

    /**
     * Spawn a training dummy at a position.
     *
     * @param level The server level
     * @param pos The position
     * @param dummyId Unique identifier for tracking
     * @return The spawned entity's UUID, or null if failed
     */
    @Nullable
    public static UUID spawnDummy(ServerLevel level, BlockPos pos, String dummyId) {
        if (!available || level == null || pos == null || dummyId == null) {
            return null;
        }
        String normalizedId = dummyId.trim();
        if (normalizedId.isEmpty()) {
            return null;
        }

        try {
            // Find dummy entity type
            ResourceKey<Registry<EntityType<?>>> registryKey =
                requireNonNull(Registries.ENTITY_TYPE, "entity type registry key");
            Registry<EntityType<?>> entityRegistry = level.registryAccess().registryOrThrow(registryKey);
            Optional<EntityType<?>> entityTypeOpt = entityRegistry.getOptional(DUMMY_ENTITY_ID);

            if (entityTypeOpt.isEmpty()) {
                LOGGER.debug("[Compat:dummmmmmy] Could not find dummy entity type");
                return null;
            }

            Entity dummy = entityTypeOpt.get().create(level);
            if (dummy == null) {
                return null;
            }

            // Set position
            dummy.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            // Add tag for tracking
            dummy.addTag(DEVMOD_TAG);
            dummy.addTag(DEVMOD_TAG + "_" + normalizedId);

            // Spawn the entity
            if (level.addFreshEntity(dummy)) {
                UUID uuid = requireNonNull(dummy.getUUID(), "dummy uuid");
                spawnedDummies.put(normalizedId, uuid);
                LOGGER.debug("[Compat:dummmmmmy] Spawned dummy at {} with UUID {}", pos, uuid);
                return uuid;
            }

        } catch (Exception e) {
            LOGGER.warn("[Compat:dummmmmmy] Failed to spawn dummy: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get the total damage taken by a dummy.
     *
     * @param entity The dummy entity
     * @return Total damage, or -1 if not available
     */
    public static float getTotalDamage(Entity entity) {
        if (!isDummy(entity) || getTotalDamageMethod == null) {
            return -1;
        }

        try {
            Object result = getTotalDamageMethod.invoke(entity);
            if (result instanceof Number) {
                return ((Number) result).floatValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:dummmmmmy] Failed to get total damage: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Get the DPS (damage per second) for a dummy.
     *
     * @param entity The dummy entity
     * @return DPS value, or -1 if not available
     */
    public static float getDps(Entity entity) {
        if (!isDummy(entity) || getDpsMethod == null) {
            return -1;
        }

        try {
            Object result = getDpsMethod.invoke(entity);
            if (result instanceof Number) {
                return ((Number) result).floatValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:dummmmmmy] Failed to get DPS: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Reset the damage counter on a dummy.
     *
     * @param entity The dummy entity
     * @return true if reset was successful
     */
    public static boolean resetDamage(Entity entity) {
        if (!isDummy(entity) || resetDamageMethod == null) {
            return false;
        }

        try {
            resetDamageMethod.invoke(entity);
            LOGGER.debug("[Compat:dummmmmmy] Reset damage on dummy");
            return true;
        } catch (Exception e) {
            LOGGER.debug("[Compat:dummmmmmy] Failed to reset damage: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Remove a spawned dummy.
     *
     * @param level The server level
     * @param dummyId The dummy identifier
     * @return true if removed
     */
    public static boolean removeDummy(ServerLevel level, String dummyId) {
        if (dummyId == null) {
            return false;
        }
        String normalizedId = dummyId.trim();
        if (normalizedId.isEmpty()) {
            return false;
        }
        UUID uuid = spawnedDummies.remove(normalizedId);
        if (uuid == null || level == null) {
            return false;
        }

        try {
            UUID dummyUuid = Objects.requireNonNull(uuid, "dummy uuid");
            Entity entity = level.getEntity(dummyUuid);
            if (entity != null) {
                entity.discard();
                LOGGER.debug("[Compat:dummmmmmy] Removed dummy: {}", dummyId);
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:dummmmmmy] Error removing dummy: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Remove all DevMod-spawned dummies from a level.
     *
     * @param level The server level (null to just clear tracking)
     */
    public static void removeAllDummies(@Nullable ServerLevel level) {
        if (level != null) {
            for (UUID uuid : spawnedDummies.values()) {
                try {
                    if (uuid == null) {
                        continue;
                    }
                    Entity entity = level.getEntity(uuid);
                    if (entity != null) {
                        entity.discard();
                    }
                } catch (Exception e) {
                    LOGGER.debug("[Compat:dummmmmmy] Error removing dummy: {}", e.getMessage());
                }
            }
        }

        int count = spawnedDummies.size();
        spawnedDummies.clear();
        LOGGER.debug("[Compat:dummmmmmy] Removed {} tracked dummies", count);
    }

    /**
     * Get damage statistics for a dummy.
     *
     * @param entity The dummy entity
     * @return Map of damage stats
     */
    public static Map<String, Object> getDamageStats(Entity entity) {
        Map<String, Object> stats = new LinkedHashMap<>();

        if (!isDummy(entity)) {
            return stats;
        }

        stats.put("isDummy", true);

        float totalDamage = getTotalDamage(entity);
        if (totalDamage >= 0) {
            stats.put("totalDamage", totalDamage);
        }

        float dps = getDps(entity);
        if (dps >= 0) {
            stats.put("dps", dps);
        }

        if (entity instanceof LivingEntity living) {
            stats.put("health", living.getHealth());
            stats.put("maxHealth", living.getMaxHealth());
        }

        return stats;
    }

    /**
     * Get all dummies tagged as DevMod arena dummies in a level.
     *
     * @param level The server level
     * @return List of dummy entities
     */
    public static List<Entity> getArenaDummies(ServerLevel level) {
        if (level == null) {
            return Collections.emptyList();
        }

        List<Entity> result = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.getTags().contains(DEVMOD_TAG) && isDummy(entity)) {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * Get the count of active spawned dummies.
     */
    public static int getSpawnedDummyCount() {
        return spawnedDummies.size();
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Dummmmmmy: not available";
        }
        if (!apiAvailable) {
            return "Dummmmmmy: detected (API not loaded)";
        }
        int count = getSpawnedDummyCount();
        return String.format("Dummmmmmy: %d active dummy/dummies", count);
    }

    @Nonnull
    private static <T> T requireNonNull(@Nullable T value, String label) {
        return Objects.requireNonNull(value, label);
    }
}
