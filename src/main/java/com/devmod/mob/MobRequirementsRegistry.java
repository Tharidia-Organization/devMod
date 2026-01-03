package com.devmod.mob;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;

/**
 * Central registry for mob requirements.
 * Combines auto-detected requirements with JSON overrides using a merge strategy.
 *
 * Usage:
 *   MobRequirements reqs = MobRequirementsRegistry.INSTANCE.get(mobId);
 *   // Or with server for enhanced biome detection:
 *   MobRequirements reqs = MobRequirementsRegistry.INSTANCE.getWithServer(mobId, server);
 */
public class MobRequirementsRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(MobRequirementsRegistry.class);

    /** Singleton instance */
    public static final MobRequirementsRegistry INSTANCE = new MobRequirementsRegistry();

    private final MobRequirementsDetector detector;
    @Nullable
    private MobRequirementsLoader loader;

    /** Cache of computed requirements (auto-detected + merged with overrides) */
    private final Map<ResourceLocation, MobRequirements> cache = new ConcurrentHashMap<>();

    /** JSON override files loaded from config */
    private final Map<ResourceLocation, MobRequirements> overrides = new ConcurrentHashMap<>();

    /** Whether the registry has been initialized with a game directory (thread-safe) */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private MobRequirementsRegistry() {
        this.detector = new MobRequirementsDetector();
    }

    /**
     * Initializes the registry with the game directory.
     * Should be called during mod initialization.
     *
     * @param gameDir The game directory (FMLPaths.GAMEDIR)
     */
    public void initialize(Path gameDir) {
        // Thread-safe initialization using compareAndSet
        if (!initialized.compareAndSet(false, true)) {
            // Already initialized - if explicit reload is desired, use reload() instead
            LOGGER.debug("MobRequirementsRegistry already initialized, skipping");
            return;
        }

        this.loader = new MobRequirementsLoader(gameDir);
        loadOverrides();

        LOGGER.info("MobRequirementsRegistry initialized with {} overrides", overrides.size());
    }

    /**
     * Reloads JSON overrides from config directory.
     * Clears cache to force re-computation.
     */
    public void reload() {
        if (!initialized.get()) {
            LOGGER.warn("Cannot reload - registry not initialized");
            return;
        }

        cache.clear();
        overrides.clear();
        loadOverrides();

        LOGGER.info("MobRequirementsRegistry reloaded with {} overrides", overrides.size());
    }

    /**
     * Reloads a single mob's JSON override and clears its cache entry.
     *
     * @param mobId The mob ID to reload
     * @return true if reload succeeded, false if no override file exists
     */
    public boolean reloadSingle(ResourceLocation mobId) {
        MobRequirementsLoader currentLoader = loader;
        if (!initialized.get() || currentLoader == null) {
            LOGGER.warn("Cannot reload single mob - registry not initialized");
            return false;
        }

        // Remove from cache to force re-computation
        cache.remove(mobId);

        // Try to load the override file for this mob
        java.nio.file.Path configDir = currentLoader.getConfigDirectory();
        String filename = mobId.getNamespace() + "_" + mobId.getPath() + ".json";
        java.nio.file.Path filePath = configDir.resolve(filename);

        if (java.nio.file.Files.exists(filePath)) {
            try {
                MobRequirements reqs = currentLoader.loadFromFile(filePath);
                if (reqs != null) {
                    overrides.put(mobId, reqs);
                    LOGGER.info("Reloaded override for: {}", mobId);
                    return true;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to reload override for {}: {}", mobId, e.getMessage());
            }
        } else {
            // Remove any existing override if file was deleted
            overrides.remove(mobId);
            LOGGER.info("No override file for: {} (cache cleared)", mobId);
        }

        return false;
    }

    private void loadOverrides() {
        if (loader != null) {
            Map<ResourceLocation, MobRequirements> loaded = loader.loadAll();
            overrides.putAll(loaded);
        }
    }

    /**
     * Gets requirements for a mob.
     * Uses cached value if available, otherwise auto-detects and merges with any override.
     *
     * @param mobId The mob's resource location
     * @return Requirements for the mob (never null)
     */
    public MobRequirements get(ResourceLocation mobId) {
        return cache.computeIfAbsent(mobId, this::computeRequirements);
    }

    /**
     * Gets requirements for a mob by EntityType.
     * Convenience method for use in spawn systems like WaveManager and BossWaveSystem.
     *
     * @param entityType The entity type to get requirements for
     * @return Requirements for the mob (never null)
     */
    public MobRequirements get(EntityType<?> entityType) {
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(Objects.requireNonNull(entityType));
        return get(mobId);
    }

    /**
     * Gets requirements with enhanced biome detection using server registries.
     * Falls back to basic detection if server is null.
     *
     * @param mobId The mob's resource location
     * @param server The Minecraft server (can be null)
     * @return Requirements for the mob
     */
    public MobRequirements getWithServer(ResourceLocation mobId, MinecraftServer server) {
        // Check if we already have a cached value with high confidence
        MobRequirements cached = cache.get(mobId);
        if (cached != null && cached.confidence() > 0.8f) {
            return cached;
        }

        // Compute with server access for better biome detection
        MobRequirements computed;
        if (server != null) {
            computed = detector.detectWithServer(mobId, server);
        } else {
            computed = detector.detect(mobId);
        }

        // Merge with any JSON override
        MobRequirements override = overrides.get(mobId);
        if (override != null) {
            computed = computed.merge(override);
        }

        cache.put(mobId, computed);
        return computed;
    }

    /**
     * Gets requirements for a mob, returning default if not found.
     */
    public MobRequirements getOrDefault(ResourceLocation mobId) {
        try {
            return get(mobId);
        } catch (Exception e) {
            LOGGER.warn("Failed to get requirements for {}, using defaults", mobId, e);
            return MobRequirements.defaultFor(mobId);
        }
    }

    private MobRequirements computeRequirements(ResourceLocation mobId) {
        // First, auto-detect from vanilla APIs
        MobRequirements detected = detector.detect(mobId);

        // Then, merge with any JSON override
        MobRequirements override = overrides.get(mobId);
        if (override != null) {
            return detected.merge(override);
        }

        return detected;
    }

    /**
     * Registers a programmatic override (useful for mods that want to register requirements at runtime).
     *
     * @param requirements The requirements to register
     */
    public void registerOverride(MobRequirements requirements) {
        overrides.put(requirements.mobId(), requirements);
        // Invalidate cache for this mob
        cache.remove(requirements.mobId());
        LOGGER.info("Registered programmatic override for: {}", requirements.mobId());
    }

    /**
     * Checks if a mob has an explicit JSON override.
     */
    public boolean hasOverride(ResourceLocation mobId) {
        return overrides.containsKey(mobId);
    }

    /**
     * Gets all registered overrides.
     */
    public Map<ResourceLocation, MobRequirements> getOverrides() {
        return Collections.unmodifiableMap(overrides);
    }

    /**
     * Gets all cached requirements (for debugging).
     */
    public Map<ResourceLocation, MobRequirements> getCache() {
        return Collections.unmodifiableMap(cache);
    }

    /**
     * Clears the cache (forces re-computation on next access).
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Gets the detector for direct access (useful for debugging).
     */
    public MobRequirementsDetector getDetector() {
        return detector;
    }

    /**
     * Gets the loader for direct access.
     */
    @Nullable
    public MobRequirementsLoader getLoader() {
        return loader;
    }

    /**
     * Returns whether the registry is initialized.
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Pre-caches requirements for all registered entity types.
     * Call this during server start for best performance.
     */
    public void preCacheAll() {
        LOGGER.info("Pre-caching mob requirements for all entity types...");
        int count = 0;

        for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            ResourceLocation mobId = entry.getKey().location();
            try {
                get(mobId);
                count++;
            } catch (Exception e) {
                LOGGER.debug("Could not cache requirements for {}: {}", mobId, e.getMessage());
            }
        }

        LOGGER.info("Pre-cached {} mob requirements", count);
    }

    /**
     * Pre-caches requirements for all registered entity types using server access.
     */
    public void preCacheAll(MinecraftServer server) {
        LOGGER.info("Pre-caching mob requirements with server access...");
        int count = 0;

        for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            ResourceLocation mobId = entry.getKey().location();
            try {
                getWithServer(mobId, server);
                count++;
            } catch (Exception e) {
                LOGGER.debug("Could not cache requirements for {}: {}", mobId, e.getMessage());
            }
        }

        LOGGER.info("Pre-cached {} mob requirements with enhanced biome detection", count);
    }

    /**
     * Generates example JSON files for common mobs that benefit from overrides.
     * Call this once to create starter config files.
     */
    public void generateExampleOverrides() {
        MobRequirementsLoader currentLoader = loader;
        if (currentLoader == null) {
            LOGGER.warn("Cannot generate examples - registry not initialized");
            return;
        }

        List<String> exampleMobs = List.of(
            "minecraft:warden",
            "minecraft:blaze",
            "minecraft:ghast",
            "minecraft:enderman",
            "minecraft:wither",
            "minecraft:ender_dragon",
            "minecraft:elder_guardian",
            "minecraft:wither_skeleton",
            "minecraft:stray",
            "minecraft:drowned"
        );

        for (String mob : exampleMobs) {
            try {
                currentLoader.generateExample(ResourceLocation.parse(Objects.requireNonNull(mob)));
            } catch (Exception e) {
                LOGGER.error("Failed to generate example for {}", mob, e);
            }
        }
    }
}
