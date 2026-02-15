package com.devmod.mob;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import static org.junit.jupiter.api.Assertions.*;

class EnhancedMobRequirementsTest {

    private static final ResourceLocation TEST_MOB = ResourceLocation.withDefaultNamespace("zombie");
    private static final MobRequirements DEFAULT_REQS = MobRequirements.defaultFor(TEST_MOB);

    // === Factory Methods ===

    @Test
    @DisplayName("fromBase creates natural-only with default confidence")
    void fromBaseCreatesNaturalOnly() {
        EnhancedMobRequirements enhanced = EnhancedMobRequirements.fromBase(DEFAULT_REQS);

        assertEquals(SpawnSource.NATURAL_ONLY, enhanced.spawnSource());
        assertTrue(enhanced.structureIds().isEmpty());
        assertNull(enhanced.primaryStructure());
        assertEquals(0.5f, enhanced.spawnSourceConfidence());
        assertFalse(enhanced.isOverridden());
    }

    @Test
    @DisplayName("structureOnly creates with STRUCTURE_ONLY source")
    void structureOnlyCreatesCorrectly() {
        List<String> structures = List.of("minecraft:stronghold");
        StructureCharacteristics primary = StructureCharacteristics.get("minecraft:stronghold").orElse(null);

        EnhancedMobRequirements enhanced = EnhancedMobRequirements.structureOnly(
            DEFAULT_REQS, structures, primary, 0.95f
        );

        assertEquals(SpawnSource.STRUCTURE_ONLY, enhanced.spawnSource());
        assertEquals(structures, enhanced.structureIds());
        assertNotNull(enhanced.primaryStructure());
        assertEquals(0.95f, enhanced.spawnSourceConfidence());
    }

    @Test
    @DisplayName("naturalAndStructure creates with NATURAL_AND_STRUCTURE source")
    void naturalAndStructureCreatesCorrectly() {
        List<String> structures = List.of("minecraft:fortress");

        EnhancedMobRequirements enhanced = EnhancedMobRequirements.naturalAndStructure(
            DEFAULT_REQS, structures, null, 0.9f
        );

        assertEquals(SpawnSource.NATURAL_AND_STRUCTURE, enhanced.spawnSource());
        assertEquals(structures, enhanced.structureIds());
        assertEquals(0.9f, enhanced.spawnSourceConfidence());
    }

    // === withOverride ===

    @Test
    @DisplayName("withOverride sets max confidence and isOverridden flag")
    void withOverrideSetsMaxConfidence() {
        EnhancedMobRequirements base = EnhancedMobRequirements.fromBase(DEFAULT_REQS);
        EnhancedMobRequirements overridden = base.withOverride(null, SpawnSource.STRUCTURE_ONLY, null, null);

        assertTrue(overridden.isOverridden());
        assertEquals(1.0f, overridden.spawnSourceConfidence());
        assertEquals(SpawnSource.STRUCTURE_ONLY, overridden.spawnSource());
    }

    @Test
    @DisplayName("withOverride preserves non-null base fields when override is null")
    void withOverridePreservesNullFields() {
        List<String> originalStructures = List.of("minecraft:stronghold");
        EnhancedMobRequirements base = EnhancedMobRequirements.structureOnly(
            DEFAULT_REQS, originalStructures, null, 0.8f
        );

        EnhancedMobRequirements overridden = base.withOverride(null, null, null, null);
        assertEquals(originalStructures, overridden.structureIds());
        assertEquals(SpawnSource.STRUCTURE_ONLY, overridden.spawnSource());
    }

    // === Decision Methods ===

    @Test
    @DisplayName("getEffectiveRequirements returns base when natural only")
    void effectiveRequirementsNaturalOnly() {
        EnhancedMobRequirements enhanced = EnhancedMobRequirements.fromBase(DEFAULT_REQS);
        MobRequirements effective = enhanced.getEffectiveRequirements("dungeon");
        assertSame(DEFAULT_REQS, effective);
    }

    @Test
    @DisplayName("getEffectiveRequirements returns merged when structure-only with primary")
    void effectiveRequirementsMergesWithStructure() {
        StructureCharacteristics primary = StructureCharacteristics.get("minecraft:stronghold").orElse(null);
        assertNotNull(primary);

        EnhancedMobRequirements enhanced = EnhancedMobRequirements.structureOnly(
            DEFAULT_REQS, List.of("minecraft:stronghold"), primary, 0.95f
        );

        MobRequirements effective = enhanced.getEffectiveRequirements(null);
        // Structure-only always uses structure, so should be merged
        assertEquals(MobRequirements.RequirementSource.MERGED, effective.source());
    }

    @Test
    @DisplayName("getEffectiveFloorMaterial uses structure floor when applicable")
    void effectiveFloorMaterialUsesStructure() {
        StructureCharacteristics primary = StructureCharacteristics.get("minecraft:stronghold").orElse(null);
        assertNotNull(primary);

        EnhancedMobRequirements enhanced = EnhancedMobRequirements.structureOnly(
            DEFAULT_REQS, List.of("minecraft:stronghold"), primary, 0.95f
        );

        ResourceLocation floor = enhanced.getEffectiveFloorMaterial(null);
        assertEquals("stone_bricks", floor.getPath());
    }

    @Test
    @DisplayName("getEffectiveFloorMaterial falls back to stone_bricks for natural mobs")
    void effectiveFloorMaterialFallback() {
        EnhancedMobRequirements enhanced = EnhancedMobRequirements.fromBase(DEFAULT_REQS);
        ResourceLocation floor = enhanced.getEffectiveFloorMaterial(null);
        assertEquals("stone_bricks", floor.getPath());
    }

    @Test
    @DisplayName("getEffectiveLightLevel returns structure light level when applicable")
    void effectiveLightLevelUsesStructure() {
        StructureCharacteristics primary = StructureCharacteristics.get("minecraft:stronghold").orElse(null);
        assertNotNull(primary);

        EnhancedMobRequirements enhanced = EnhancedMobRequirements.structureOnly(
            DEFAULT_REQS, List.of("minecraft:stronghold"), primary, 0.95f
        );

        int light = enhanced.getEffectiveLightLevel(null);
        assertEquals(3, light); // Stronghold light level
    }

    @Test
    @DisplayName("shouldBeEnclosed returns true for dark-preferring mobs by default")
    void shouldBeEnclosedForDarkMobs() {
        MobRequirements darkReqs = new MobRequirements(
            TEST_MOB, MobRequirements.BiomeRequirement.ANY, MobRequirements.LightRequirement.DARK,
            MobRequirements.FloorRequirement.SOLID_GROUND, MobRequirements.SpaceRequirement.DEFAULT,
            MobRequirements.TimeRequirement.NIGHT, MobRequirements.BossRequirement.NOT_BOSS,
            MobRequirements.RequirementSource.AUTO_DETECTED, 0.5f
        );

        EnhancedMobRequirements enhanced = EnhancedMobRequirements.fromBase(darkReqs);
        assertTrue(enhanced.shouldBeEnclosed(null));
    }

    @Test
    @DisplayName("getBiomeHint returns structure biome hint when applicable")
    void biomeHintUsesStructure() {
        StructureCharacteristics primary = StructureCharacteristics.get("minecraft:ancient_city").orElse(null);
        assertNotNull(primary);

        EnhancedMobRequirements enhanced = EnhancedMobRequirements.structureOnly(
            DEFAULT_REQS, List.of("minecraft:ancient_city"), primary, 0.95f
        );

        String hint = enhanced.getBiomeHint(null);
        assertEquals("minecraft:deep_dark", hint);
    }

    // === Utility ===

    @Test
    @DisplayName("mobId returns base mob ID")
    void mobIdReturnsBaseId() {
        EnhancedMobRequirements enhanced = EnhancedMobRequirements.fromBase(DEFAULT_REQS);
        assertEquals(TEST_MOB, enhanced.mobId());
    }

    @Test
    @DisplayName("describeSpawnSource includes spawn source and confidence")
    void describeSpawnSourceIncludesDetails() {
        EnhancedMobRequirements enhanced = EnhancedMobRequirements.structureOnly(
            DEFAULT_REQS, List.of("minecraft:stronghold"), null, 0.95f
        );

        String desc = enhanced.describeSpawnSource();
        assertTrue(desc.contains("structure only"));
        assertTrue(desc.contains("95%"));
        assertTrue(desc.contains("minecraft:stronghold"));
    }

    @Test
    @DisplayName("describeSpawnSource shows OVERRIDE for overridden requirements")
    void describeSpawnSourceShowsOverride() {
        EnhancedMobRequirements base = EnhancedMobRequirements.fromBase(DEFAULT_REQS);
        EnhancedMobRequirements overridden = base.withOverride(null, null, null, null);

        String desc = overridden.describeSpawnSource();
        assertTrue(desc.contains("[OVERRIDE]"));
    }

    @Test
    @DisplayName("isPrimarilyStructureMob for STRUCTURE_ONLY")
    void isPrimarilyStructureMobForStructureOnly() {
        EnhancedMobRequirements enhanced = EnhancedMobRequirements.structureOnly(
            DEFAULT_REQS, List.of(), null, 0.5f
        );
        assertTrue(enhanced.isPrimarilyStructureMob());
    }

    @Test
    @DisplayName("isPrimarilyStructureMob for NATURAL_AND_STRUCTURE with high confidence")
    void isPrimarilyStructureMobForHighConfidence() {
        EnhancedMobRequirements high = EnhancedMobRequirements.naturalAndStructure(
            DEFAULT_REQS, List.of(), null, 0.8f
        );
        assertTrue(high.isPrimarilyStructureMob());

        EnhancedMobRequirements low = EnhancedMobRequirements.naturalAndStructure(
            DEFAULT_REQS, List.of(), null, 0.5f
        );
        assertFalse(low.isPrimarilyStructureMob());
    }
}
