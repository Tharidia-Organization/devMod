package com.devmod.mob;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Enhanced registry that combines MobRequirements with SpawnSource detection.
 * This is the main entry point for arena generation that needs to understand
 * both spawn conditions AND whether the mob is structure-based.
 *
 * Usage:
 *   EnhancedMobRequirements enhanced = EnhancedMobRequirementsRegistry.INSTANCE.get(mobId);
 *   MobRequirements effective = enhanced.getEffectiveRequirements(questType);
 */
public class EnhancedMobRequirementsRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnhancedMobRequirementsRegistry.class);

    /** Singleton instance */
    public static final EnhancedMobRequirementsRegistry INSTANCE = new EnhancedMobRequirementsRegistry();

    /** Cache of enhanced requirements */
    private final Map<ResourceLocation, EnhancedMobRequirements> cache = new ConcurrentHashMap<>();

    /** Whether verbose logging is enabled */
    private boolean loggingEnabled = false;

    private EnhancedMobRequirementsRegistry() {}

    // ============== Configuration ==============

    /**
     * Enable or disable verbose logging.
     */
    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
    }

    // ============== Get Methods ==============

    /**
     * Gets enhanced requirements for a mob by ResourceLocation.
     *
     * @param mobId The mob's resource location
     * @return Enhanced requirements (never null)
     */
    public EnhancedMobRequirements get(ResourceLocation mobId) {
        return cache.computeIfAbsent(mobId, this::compute);
    }

    /**
     * Gets enhanced requirements for a mob by EntityType.
     *
     * @param entityType The entity type
     * @return Enhanced requirements (never null)
     */
    @SuppressWarnings("unchecked")
    public EnhancedMobRequirements get(EntityType<?> entityType) {
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(Objects.requireNonNull(entityType));

        // For spawn source detection, we need EntityType<? extends Mob>
        // Try to detect if this is a Mob type
        if (Mob.class.isAssignableFrom(entityType.getBaseClass())) {
            return getWithSpawnDetection(mobId, (EntityType<? extends Mob>) entityType);
        }

        // Non-mob entity - just use base requirements
        return cache.computeIfAbsent(mobId, this::compute);
    }

    /**
     * Gets enhanced requirements with server access for better detection.
     *
     * @param mobId  The mob's resource location
     * @param server The Minecraft server (can be null)
     * @return Enhanced requirements
     */
    public EnhancedMobRequirements getWithServer(ResourceLocation mobId, @Nullable MinecraftServer server) {
        // Check cache first
        EnhancedMobRequirements cached = cache.get(mobId);
        if (cached != null && cached.spawnSourceConfidence() >= 0.8f) {
            return cached;
        }

        // Compute with server access
        MobRequirements base = MobRequirementsRegistry.INSTANCE.getWithServer(mobId, server);
        EnhancedMobRequirements enhanced = computeWithBase(mobId, base);

        cache.put(mobId, enhanced);
        return enhanced;
    }

    /**
     * Gets enhanced requirements with spawn detection for a known Mob EntityType.
     */
    private EnhancedMobRequirements getWithSpawnDetection(
        ResourceLocation mobId,
        EntityType<? extends Mob> entityType
    ) {
        EnhancedMobRequirements cached = cache.get(mobId);
        if (cached != null && cached.spawnSourceConfidence() >= 0.7f) {
            return cached;
        }

        // Get base requirements
        MobRequirements base = MobRequirementsRegistry.INSTANCE.get(mobId);

        // Detect spawn source
        SpawnSourceDetector.DetectionResult detection = SpawnSourceDetector.detect(entityType);

        EnhancedMobRequirements enhanced = new EnhancedMobRequirements(
            base,
            detection.spawnSource(),
            detection.structureIds(),
            detection.primaryStructure(),
            detection.confidence(),
            false
        );

        if (loggingEnabled) {
            LOGGER.info("[EnhancedRegistry] {} -> {}",
                mobId, detection.describe());
        }

        cache.put(mobId, enhanced);
        return enhanced;
    }

    // ============== Compute Methods ==============

    /**
     * Computes enhanced requirements from just a mobId.
     */
    private EnhancedMobRequirements compute(ResourceLocation mobId) {
        MobRequirements base = MobRequirementsRegistry.INSTANCE.get(mobId);
        return computeWithBase(mobId, base);
    }

    /**
     * Computes enhanced requirements with a known base.
     */
    private EnhancedMobRequirements computeWithBase(ResourceLocation mobId, MobRequirements base) {
        String mobIdStr = mobId.toString();

        // Try to detect spawn source from hardcoded mappings first
        if (SpawnSourceDetector.getStructureOnlyMobs().contains(mobIdStr)) {
            var structures = SpawnSourceDetector.getStructuresForMob(mobIdStr);
            var primary = structures.isEmpty() ? null :
                StructureCharacteristics.get(structures.get(0)).orElse(null);

            if (loggingEnabled) {
                LOGGER.info("[EnhancedRegistry] {} -> STRUCTURE_ONLY (hardcoded) structures={}",
                    mobId, structures);
            }

            return EnhancedMobRequirements.structureOnly(base, structures, primary, 0.95f);
        }

        // Check for structure-also mappings
        var structuresAlso = SpawnSourceDetector.getStructuresForMob(mobIdStr);
        if (!structuresAlso.isEmpty()) {
            var primary = StructureCharacteristics.get(structuresAlso.get(0)).orElse(null);

            if (loggingEnabled) {
                LOGGER.info("[EnhancedRegistry] {} -> NATURAL_AND_STRUCTURE (hardcoded) structures={}",
                    mobId, structuresAlso);
            }

            return EnhancedMobRequirements.naturalAndStructure(base, structuresAlso, primary, 0.9f);
        }

        // Try to find entity type for spawn placement detection
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(mobId);
        if (entityType != null && Mob.class.isAssignableFrom(entityType.getBaseClass())) {
            @SuppressWarnings("unchecked")
            EntityType<? extends Mob> mobType = (EntityType<? extends Mob>) entityType;
            SpawnSourceDetector.DetectionResult detection = SpawnSourceDetector.detect(mobType);

            if (loggingEnabled) {
                LOGGER.info("[EnhancedRegistry] {} -> {}", mobId, detection.describe());
            }

            return new EnhancedMobRequirements(
                base,
                detection.spawnSource(),
                detection.structureIds(),
                detection.primaryStructure(),
                detection.confidence(),
                false
            );
        }

        // Fallback to basic natural spawn assumption
        if (loggingEnabled) {
            LOGGER.info("[EnhancedRegistry] {} -> NATURAL_ONLY (fallback)", mobId);
        }

        return EnhancedMobRequirements.fromBase(base);
    }

    // ============== Cache Management ==============

    /**
     * Clears the cache.
     */
    public void clearCache() {
        cache.clear();
        LOGGER.info("Enhanced mob requirements cache cleared");
    }

    /**
     * Clears cache for a specific mob.
     */
    public void clearCache(ResourceLocation mobId) {
        cache.remove(mobId);
    }

    /**
     * Reloads everything - clears cache and reloads base registry.
     */
    public void reload() {
        cache.clear();
        MobRequirementsRegistry.INSTANCE.reload();
        LOGGER.info("Enhanced mob requirements registry reloaded");
    }

    /**
     * Gets cache statistics.
     */
    public CacheStats getCacheStats() {
        int total = cache.size();
        int structureOnly = 0;
        int naturalAndStructure = 0;
        int naturalOnly = 0;
        int unknown = 0;
        int overridden = 0;

        for (EnhancedMobRequirements req : cache.values()) {
            switch (req.spawnSource()) {
                case STRUCTURE_ONLY -> structureOnly++;
                case NATURAL_AND_STRUCTURE -> naturalAndStructure++;
                case NATURAL_ONLY -> naturalOnly++;
                case UNKNOWN -> unknown++;
            }
            if (req.isOverridden()) {
                overridden++;
            }
        }

        return new CacheStats(total, structureOnly, naturalAndStructure, naturalOnly, unknown, overridden);
    }

    public record CacheStats(
        int total,
        int structureOnly,
        int naturalAndStructure,
        int naturalOnly,
        int unknown,
        int overridden
    ) {
        @Override
        public String toString() {
            return String.format(
                "Cached: %d (structure_only=%d, natural_and_structure=%d, natural_only=%d, unknown=%d, overridden=%d)",
                total, structureOnly, naturalAndStructure, naturalOnly, unknown, overridden
            );
        }
    }

    // ============== Bulk Operations ==============

    /**
     * Pre-caches requirements for all registered entity types.
     */
    public void preCacheAll() {
        LOGGER.info("Pre-caching enhanced mob requirements...");
        int count = 0;

        for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            ResourceLocation mobId = entry.getKey().location();
            EntityType<?> entityType = entry.getValue();

            if (Mob.class.isAssignableFrom(entityType.getBaseClass())) {
                try {
                    get(entityType);
                    count++;
                } catch (Exception e) {
                    LOGGER.debug("Could not cache enhanced requirements for {}: {}", mobId, e.getMessage());
                }
            }
        }

        LOGGER.info("Pre-cached {} enhanced mob requirements. {}", count, getCacheStats());
    }

    /**
     * Pre-caches requirements with server access.
     */
    public void preCacheAll(MinecraftServer server) {
        LOGGER.info("Pre-caching enhanced mob requirements with server access...");
        int count = 0;

        for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            ResourceLocation mobId = entry.getKey().location();
            try {
                getWithServer(mobId, server);
                count++;
            } catch (Exception e) {
                LOGGER.debug("Could not cache enhanced requirements for {}: {}", mobId, e.getMessage());
            }
        }

        LOGGER.info("Pre-cached {} enhanced mob requirements. {}", count, getCacheStats());
    }

    /**
     * Gets all cached requirements.
     */
    public Map<ResourceLocation, EnhancedMobRequirements> getCache() {
        return Collections.unmodifiableMap(cache);
    }

    // ============== Override Support ==============

    /**
     * Registers an override for a mob's spawn source.
     */
    public void registerSpawnSourceOverride(
        ResourceLocation mobId,
        SpawnSource source,
        java.util.List<String> structures
    ) {
        SpawnSourceDetector.registerOverride(mobId.toString(),
            new SpawnSourceDetector.SpawnSourceOverride(source, structures, 1.0f));

        // Clear cache to pick up the override
        cache.remove(mobId);

        LOGGER.info("Registered spawn source override for {}: {} structures={}",
            mobId, source, structures);
    }
}
