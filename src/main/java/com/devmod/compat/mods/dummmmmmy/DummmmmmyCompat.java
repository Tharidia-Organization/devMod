package com.devmod.compat.mods.dummmmmmy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
import com.devmod.endurance.EnduranceTags;

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

    // Track spawned dummies for Arena and debug commands
    private static final Map<UUID, DummyRecord> trackedDummies = new HashMap<>();
    private static final Map<String, Set<UUID>> dummiesById = new HashMap<>();
    private static final String DEVMOD_TAG = "devmod_arena_dummy";
    private static final String DEVMOD_TAG_PREFIX = DEVMOD_TAG + "_";
    private static final ResourceLocation DUMMY_ENTITY_ID =
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "dummy");
    private record DummyRecord(String id, ResourceKey<Level> dimension) {}

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
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:dummmmmmy] DummyEntity not found in {}", pkg);
                }
            }

            if (dummyEntityClass != null) {
                // Try to find damage tracking methods
                try {
                    getTotalDamageMethod = dummyEntityClass.getMethod("getTotalDamage");
                } catch (NoSuchMethodException e) {
                    // Try alternative names
                    try {
                        getTotalDamageMethod = dummyEntityClass.getMethod("getDamageTaken");
                    } catch (NoSuchMethodException e2) {
                        LOGGER.trace("[Compat:dummmmmmy] getDamageTaken not available", e2);
                    }
                }

                try {
                    resetDamageMethod = dummyEntityClass.getMethod("resetDamage");
                } catch (NoSuchMethodException e) {
                    try {
                        resetDamageMethod = dummyEntityClass.getMethod("reset");
                    } catch (NoSuchMethodException e2) {
                        LOGGER.trace("[Compat:dummmmmmy] reset not available", e2);
                    }
                }

                try {
                    getDpsMethod = dummyEntityClass.getMethod("getDps");
                } catch (NoSuchMethodException e) {
                    LOGGER.trace("[Compat:dummmmmmy] getDps not available", e);
                }

                if (getTotalDamageMethod != null && !isNumericReturn(getTotalDamageMethod)) {
                    LOGGER.debug("[Compat:dummmmmmy] getTotalDamage has unexpected return type {}",
                        getTotalDamageMethod.getReturnType());
                    getTotalDamageMethod = null;
                }
                if (getDpsMethod != null && !isNumericReturn(getDpsMethod)) {
                    LOGGER.debug("[Compat:dummmmmmy] getDps has unexpected return type {}",
                        getDpsMethod.getReturnType());
                    getDpsMethod = null;
                }
                if (resetDamageMethod != null && !isResetReturn(resetDamageMethod)) {
                    LOGGER.debug("[Compat:dummmmmmy] resetDamage has unexpected return type {}",
                        resetDamageMethod.getReturnType());
                    resetDamageMethod = null;
                }

                apiAvailable = getTotalDamageMethod != null || getDpsMethod != null || resetDamageMethod != null;
                if (apiAvailable) {
                    LOGGER.info("[Compat:dummmmmmy] Dummmmmmy API loaded");
                } else {
                    LOGGER.warn("[Compat:dummmmmmy] Dummmmmmy API not accessible (methods missing)");
                }
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
        trackedDummies.clear();
        dummiesById.clear();
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
        if (entity == null) {
            return false;
        }
        if (entity.getTags().contains(DEVMOD_TAG)) {
            return true;
        }
        if (dummyEntityClass != null && dummyEntityClass.isInstance(entity)) {
            return true;
        }
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(java.util.Objects.requireNonNull(entity.getType()));
        if (DUMMY_ENTITY_ID.equals(typeId)) {
            return true;
        }
        return typeId != null
            && MOD_ID.equals(typeId.getNamespace())
            && typeId.getPath().contains("dummy");
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
        return spawnDummy(level, pos, dummyId, null);
    }

    /**
     * Spawn a training dummy at a position with optional initializer.
     *
     * @param level The server level
     * @param pos The position
     * @param dummyId Unique identifier for tracking
     * @param initializer Optional callback to tag/configure entity before spawn
     * @return The spawned entity's UUID, or null if failed
     */
    @Nullable
    public static UUID spawnDummy(ServerLevel level, BlockPos pos, String dummyId, @Nullable Consumer<Entity> initializer) {
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
            Optional<EntityType<?>> entityTypeOpt = findDummyEntityType(entityRegistry);

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
            if (initializer != null) {
                initializer.accept(dummy);
            }

            // Spawn the entity
            if (level.addFreshEntity(dummy)) {
                UUID uuid = requireNonNull(dummy.getUUID(), "dummy uuid");
                trackDummy(normalizedId, uuid, level.dimension());
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
        return removeDummyCount(level, dummyId) > 0;
    }

    /**
     * Remove dummies by ID in the current dimension.
     *
     * @return number of dummies removed
     */
    public static int removeDummyCount(ServerLevel level, String dummyId) {
        return removeDummyCount(level, dummyId, false);
    }

    /**
     * Remove dummies by ID, optionally across all dimensions.
     */
    public static int removeDummyCount(ServerLevel level, String dummyId, boolean includeAllLevels) {
        if (level == null || dummyId == null) {
            return 0;
        }
        String normalizedId = dummyId.trim();
        if (normalizedId.isEmpty()) {
            return 0;
        }
        int removed = 0;
        if (includeAllLevels) {
            var server = level.getServer();
            if (server != null) {
                for (ServerLevel target : server.getAllLevels()) {
                    removed += removeDummyCountInLevel(target, normalizedId);
                }
            }
        } else {
            removed += removeDummyCountInLevel(level, normalizedId);
        }
        if (removed > 0) {
            LOGGER.debug("[Compat:dummmmmmy] Removed {} dummy/dummies: {}", removed, dummyId);
        }
        return removed;
    }

    /**
     * Remove all DevMod-spawned dummies from a level.
     *
     * @param level The server level (null to just clear tracking)
     */
    public static void removeAllDummies(@Nullable ServerLevel level) {
        if (level == null) {
            trackedDummies.clear();
            dummiesById.clear();
            return;
        }

        int removed = removeTaggedDummies(level, null, null);
        LOGGER.debug("[Compat:dummmmmmy] Removed {} dummies from {}", removed, level.dimension().location());
    }

    /**
     * Remove DevMod-spawned dummies scoped to an arena and optional bounds.
     *
     * @return number of dummies removed
     */
    public static int removeArenaDummies(@Nullable ServerLevel level,
                                         @Nullable UUID arenaId,
                                         @Nullable AABB bounds) {
        if (level == null) {
            return 0;
        }
        int removed = removeTaggedDummies(level, bounds, arenaId);
        if (removed > 0) {
            String scope = arenaId != null ? arenaId.toString() : "unknown";
            LOGGER.debug("[Compat:dummmmmmy] Removed {} dummies for arena {}", removed, scope);
        }
        return removed;
    }

    private static int removeDummyCountInLevel(ServerLevel level, String normalizedId) {
        if (level == null || normalizedId == null || normalizedId.isEmpty()) {
            return 0;
        }
        String idTag = DEVMOD_TAG_PREFIX + normalizedId;
        int removed = 0;
        for (Entity entity : level.getAllEntities()) {
            if (!entity.getTags().contains(DEVMOD_TAG) || !entity.getTags().contains(idTag)) {
                continue;
            }
            if (!isDummy(entity)) {
                continue;
            }
            UUID uuid = entity.getUUID();
            entity.discard();
            removed++;
            untrackDummy(uuid);
        }
        pruneTrackingForDimension(level);
        return removed;
    }

    private static int removeTaggedDummies(ServerLevel level, @Nullable AABB bounds, @Nullable UUID arenaId) {
        Iterable<Entity> entities = bounds != null
            ? level.getEntities((Entity) null, bounds)
            : level.getAllEntities();
        int removed = 0;
        for (Entity entity : entities) {
            if (!entity.getTags().contains(DEVMOD_TAG) || !isDummy(entity)) {
                continue;
            }
            if (arenaId != null) {
                CompoundTag data = entity.getPersistentData();
                if (!data.hasUUID(EnduranceTags.ARENA_ID)
                    || !arenaId.equals(data.getUUID(EnduranceTags.ARENA_ID))) {
                    continue;
                }
            }
            UUID uuid = entity.getUUID();
            entity.discard();
            removed++;
            untrackDummy(uuid);
        }
        pruneTrackingForDimension(level);
        return removed;
    }

    private static Optional<EntityType<?>> findDummyEntityType(Registry<EntityType<?>> entityRegistry) {
        Optional<EntityType<?>> direct = entityRegistry.getOptional(DUMMY_ENTITY_ID);
        if (direct.isPresent()) {
            return direct;
        }
        for (ResourceLocation id : entityRegistry.keySet()) {
            if (!MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            if (!id.getPath().contains("dummy")) {
                continue;
            }
            Optional<EntityType<?>> candidate = entityRegistry.getOptional(id);
            if (candidate.isPresent()) {
                LOGGER.debug("[Compat:dummmmmmy] Using fallback dummy entity type {}", id);
                return candidate;
            }
        }
        return Optional.empty();
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
            return new ArrayList<>();
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
     * Rebuild tracking from tagged dummies in a level.
     *
     * @return number of newly tracked dummies
     */
    public static int refreshTracking(@Nullable ServerLevel level) {
        return refreshTracking(level, false);
    }

    /**
     * Rebuild tracking from tagged dummies in a level (or all levels).
     */
    public static int refreshTracking(@Nullable ServerLevel level, boolean includeAllLevels) {
        if (level == null) {
            return 0;
        }
        int added = 0;
        if (includeAllLevels) {
            var server = level.getServer();
            if (server != null) {
                for (ServerLevel target : server.getAllLevels()) {
                    added += refreshTrackingInLevel(target);
                }
                return added;
            }
        }
        return refreshTrackingInLevel(level);
    }

    /**
     * Get the count of active spawned dummies.
     */
    public static int getSpawnedDummyCount() {
        return trackedDummies.size();
    }

    /**
     * Get the count of tracked dummies in a level.
     */
    public static int getTrackedDummyCount(@Nullable ServerLevel level) {
        if (level == null) {
            return 0;
        }
        ResourceKey<Level> dimension = level.dimension();
        int count = 0;
        for (DummyRecord record : trackedDummies.values()) {
            if (record != null && record.dimension().equals(dimension)) {
                count++;
            }
        }
        return count;
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

    /**
     * Get the count of dummies tagged in a level.
     */
    public static int getArenaDummyCount(@Nullable ServerLevel level) {
        if (level == null) {
            return 0;
        }
        return getArenaDummies(level).size();
    }

    private static int refreshTrackingInLevel(ServerLevel level) {
        if (level == null) {
            return 0;
        }
        pruneTrackingForDimension(level);
        int added = 0;
        for (Entity entity : level.getAllEntities()) {
            if (!entity.getTags().contains(DEVMOD_TAG) || !isDummy(entity)) {
                continue;
            }
            String id = extractDummyId(entity.getTags());
            if (id == null || id.isBlank()) {
                continue;
            }
            UUID uuid = entity.getUUID();
            if (!trackedDummies.containsKey(uuid)) {
                added++;
            }
            trackDummy(id, uuid, level.dimension());
        }
        return added;
    }

    private static @Nullable String extractDummyId(Set<String> tags) {
        if (tags == null) {
            return null;
        }
        for (String tag : tags) {
            if (tag != null && tag.startsWith(DEVMOD_TAG_PREFIX)) {
                return tag.substring(DEVMOD_TAG_PREFIX.length());
            }
        }
        return null;
    }

    private static void pruneTrackingForDimension(ServerLevel level) {
        if (level == null) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        for (Iterator<Map.Entry<UUID, DummyRecord>> iterator = trackedDummies.entrySet().iterator();
             iterator.hasNext();) {
            Map.Entry<UUID, DummyRecord> entry = iterator.next();
            DummyRecord record = entry.getValue();
            if (record == null || !record.dimension().equals(dimension)) {
                continue;
            }
            if (level.getEntity(java.util.Objects.requireNonNull(entry.getKey())) != null) {
                continue;
            }
            Set<UUID> ids = dummiesById.get(record.id());
            if (ids != null) {
                ids.remove(entry.getKey());
                if (ids.isEmpty()) {
                    dummiesById.remove(record.id());
                }
            }
            iterator.remove();
        }
    }

    private static boolean isNumericReturn(Method method) {
        if (method == null) {
            return false;
        }
        Class<?> type = method.getReturnType();
        if (type == null) {
            return false;
        }
        if (type.isPrimitive()) {
            return type == int.class
                || type == long.class
                || type == float.class
                || type == double.class
                || type == short.class
                || type == byte.class;
        }
        return Number.class.isAssignableFrom(type);
    }

    private static boolean isResetReturn(Method method) {
        if (method == null) {
            return false;
        }
        Class<?> type = method.getReturnType();
        return type == void.class || type == boolean.class || type == Boolean.class;
    }

    @Nonnull
    private static <T> T requireNonNull(@Nullable T value, String label) {
        return Objects.requireNonNull(value, label);
    }

    private static void trackDummy(String id, UUID uuid, ResourceKey<Level> dimension) {
        trackedDummies.put(uuid, new DummyRecord(id, dimension));
        dummiesById.computeIfAbsent(id, key -> new LinkedHashSet<>()).add(uuid);
    }

    private static void untrackDummy(UUID uuid) {
        DummyRecord record = trackedDummies.remove(uuid);
        if (record == null) {
            return;
        }
        Set<UUID> ids = dummiesById.get(record.id());
        if (ids != null) {
            ids.remove(uuid);
            if (ids.isEmpty()) {
                dummiesById.remove(record.id());
            }
        }
    }

}
