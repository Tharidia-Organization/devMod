package com.devmod.mob;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * Combines standard MobRequirements with spawn source analysis.
 * This is the complete picture of a mob's spawn conditions,
 * including where it spawns (natural vs structure) and what
 * characteristics to use for arena generation.
 */
public record EnhancedMobRequirements(
    /** Base mob requirements (biome, light, floor, etc.) */
    MobRequirements base,

    /** How this mob spawns (natural, structure, or both) */
    SpawnSource spawnSource,

    /** Structure IDs where this mob spawns (empty if natural only) */
    List<String> structureIds,

    /** Primary structure characteristics (if structure spawning) */
    @Nullable StructureCharacteristics primaryStructure,

    /** Confidence in spawn source detection (0.0-1.0) */
    float spawnSourceConfidence,

    /** Whether this was manually overridden */
    boolean isOverridden
) {
    // ============== Factory Methods ==============

    /**
     * Creates EnhancedMobRequirements from base requirements only.
     * Assumes natural spawn with no structure data.
     */
    public static EnhancedMobRequirements fromBase(MobRequirements base) {
        return new EnhancedMobRequirements(
            base,
            SpawnSource.NATURAL_ONLY,
            List.of(),
            null,
            0.5f, // Default confidence
            false
        );
    }

    /**
     * Creates EnhancedMobRequirements for a structure-only mob.
     */
    public static EnhancedMobRequirements structureOnly(
        MobRequirements base,
        List<String> structureIds,
        @Nullable StructureCharacteristics primaryStructure,
        float confidence
    ) {
        return new EnhancedMobRequirements(
            base,
            SpawnSource.STRUCTURE_ONLY,
            structureIds,
            primaryStructure,
            confidence,
            false
        );
    }

    /**
     * Creates EnhancedMobRequirements for a mob with both spawn types.
     */
    public static EnhancedMobRequirements naturalAndStructure(
        MobRequirements base,
        List<String> structureIds,
        @Nullable StructureCharacteristics primaryStructure,
        float confidence
    ) {
        return new EnhancedMobRequirements(
            base,
            SpawnSource.NATURAL_AND_STRUCTURE,
            structureIds,
            primaryStructure,
            confidence,
            false
        );
    }

    /**
     * Creates an overridden version with manual configuration.
     */
    public EnhancedMobRequirements withOverride(
        MobRequirements overriddenBase,
        @Nullable SpawnSource overriddenSource,
        @Nullable List<String> overriddenStructures,
        @Nullable StructureCharacteristics overriddenPrimary
    ) {
        return new EnhancedMobRequirements(
            overriddenBase != null ? base.merge(overriddenBase) : base,
            overriddenSource != null ? overriddenSource : spawnSource,
            overriddenStructures != null ? overriddenStructures : structureIds,
            overriddenPrimary != null ? overriddenPrimary : primaryStructure,
            1.0f, // Manual override = max confidence
            true
        );
    }

    // ============== Decision Methods ==============

    /**
     * Determines the effective requirements for arena generation.
     *
     * @param questType The quest type (nullable, affects structure priority)
     * @return The MobRequirements to use, potentially modified by structure characteristics
     */
    public MobRequirements getEffectiveRequirements(@Nullable String questType) {
        // If we should use structure and have structure data, merge it
        if (spawnSource.shouldUseStructure(questType) && primaryStructure != null) {
            return mergeWithStructure(base, primaryStructure);
        }
        return base;
    }

    /**
     * Gets the floor material to use for arena generation.
     *
     * @param questType The quest type (nullable)
     * @return Floor material ResourceLocation
     */
    public ResourceLocation getEffectiveFloorMaterial(@Nullable String questType) {
        if (spawnSource.shouldUseStructure(questType) && primaryStructure != null) {
            return primaryStructure.floorBlock();
        }

        // Fall back to base requirements
        return base.floor().preferredBlock()
            .orElse(ResourceLocation.withDefaultNamespace("stone_bricks"));
    }

    /**
     * Gets the effective light level for arena generation.
     *
     * @param questType The quest type (nullable)
     * @return Light level (0-15)
     */
    public int getEffectiveLightLevel(@Nullable String questType) {
        if (spawnSource.shouldUseStructure(questType) && primaryStructure != null) {
            return primaryStructure.lightLevel();
        }
        return base.light().optimalLight();
    }

    /**
     * Determines if the arena should be enclosed (has ceiling).
     *
     * @param questType The quest type (nullable)
     * @return true if arena should be enclosed
     */
    public boolean shouldBeEnclosed(@Nullable String questType) {
        if (spawnSource.shouldUseStructure(questType) && primaryStructure != null) {
            return primaryStructure.enclosed();
        }
        // Default: not enclosed unless underground or dark-preferring
        return base.light().prefersDark();
    }

    /**
     * Gets the biome hint for arena generation.
     *
     * @param questType The quest type (nullable)
     * @return Biome ID string or null for default
     */
    @Nullable
    public String getBiomeHint(@Nullable String questType) {
        if (spawnSource.shouldUseStructure(questType) && primaryStructure != null) {
            return primaryStructure.biomeHint();
        }
        return base.biome().preferredBiome()
            .map(ResourceLocation::toString)
            .orElse(null);
    }

    // ============== Internal Helpers ==============

    /**
     * Merges base requirements with structure characteristics.
     */
    private static MobRequirements mergeWithStructure(
        MobRequirements base,
        StructureCharacteristics structure
    ) {
        // Create floor requirement from structure
        MobRequirements.FloorRequirement structureFloor =
            MobRequirements.FloorRequirement.preferredBlock(structure.floorBlock());

        // Create light requirement from structure
        int lightLevel = structure.lightLevel();
        MobRequirements.LightRequirement structureLight = structure.dark()
            ? MobRequirements.LightRequirement.range(0, Math.max(7, lightLevel))
            : MobRequirements.LightRequirement.range(lightLevel, 15);

        // Create biome requirement from structure hint
        MobRequirements.BiomeRequirement structureBiome = structure.biomeHint() != null
            ? MobRequirements.BiomeRequirement.preferred(ResourceLocation.parse(structure.biomeHint()))
            : base.biome();

        return new MobRequirements(
            base.mobId(),
            structureBiome,
            structureLight,
            structureFloor,
            base.space(), // Keep original space requirements
            base.time(),
            base.boss(),
            MobRequirements.RequirementSource.MERGED,
            Math.max(base.confidence(), 0.8f) // Boost confidence when using structure data
        );
    }

    // ============== Utility ==============

    /**
     * Get mob ID for convenience.
     */
    public ResourceLocation mobId() {
        return base.mobId();
    }

    /**
     * Get display-friendly description of spawn source.
     */
    public String describeSpawnSource() {
        StringBuilder sb = new StringBuilder();
        sb.append(spawnSource.name().toLowerCase().replace('_', ' '));

        if (!structureIds.isEmpty()) {
            sb.append(" [");
            sb.append(String.join(", ", structureIds));
            sb.append("]");
        }

        sb.append(" (").append(String.format("%.0f%%", spawnSourceConfidence * 100)).append(" confidence)");

        if (isOverridden) {
            sb.append(" [OVERRIDE]");
        }

        return sb.toString();
    }

    /**
     * Check if this mob is primarily a structure mob.
     */
    public boolean isPrimarilyStructureMob() {
        return spawnSource == SpawnSource.STRUCTURE_ONLY
            || (spawnSource == SpawnSource.NATURAL_AND_STRUCTURE && spawnSourceConfidence > 0.7f);
    }
}
