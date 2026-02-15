package com.devmod.mob;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import com.devmod.mob.MobRequirements.*;

import static org.junit.jupiter.api.Assertions.*;

class MobRequirementsTest {

    private static final ResourceLocation TEST_MOB = ResourceLocation.withDefaultNamespace("zombie");

    // === defaultFor ===

    @Test
    @DisplayName("defaultFor creates requirements with default sub-records")
    void defaultForCreatesDefaults() {
        MobRequirements reqs = MobRequirements.defaultFor(TEST_MOB);

        assertEquals(TEST_MOB, reqs.mobId());
        assertEquals(BiomeRequirement.ANY, reqs.biome());
        assertEquals(LightRequirement.ANY, reqs.light());
        assertEquals(FloorRequirement.SOLID_GROUND, reqs.floor());
        assertEquals(SpaceRequirement.DEFAULT, reqs.space());
        assertEquals(TimeRequirement.ANY, reqs.time());
        assertEquals(BossRequirement.NOT_BOSS, reqs.boss());
        assertEquals(RequirementSource.DEFAULT, reqs.source());
        assertEquals(0.0f, reqs.confidence());
    }

    // === merge ===

    @Test
    @DisplayName("merge with null returns this")
    void mergeWithNullReturnsSelf() {
        MobRequirements reqs = MobRequirements.defaultFor(TEST_MOB);
        assertSame(reqs, reqs.merge(null));
    }

    @Test
    @DisplayName("merge prefers non-default override values")
    void mergeOverridesNonDefaultValues() {
        MobRequirements base = MobRequirements.defaultFor(TEST_MOB);
        LightRequirement darkLight = LightRequirement.DARK;
        MobRequirements override = new MobRequirements(
            TEST_MOB, BiomeRequirement.ANY, darkLight, FloorRequirement.SOLID_GROUND,
            SpaceRequirement.DEFAULT, TimeRequirement.ANY, BossRequirement.NOT_BOSS,
            RequirementSource.JSON_OVERRIDE, 0.8f
        );

        MobRequirements merged = base.merge(override);
        assertEquals(darkLight, merged.light());
        assertEquals(RequirementSource.MERGED, merged.source());
        assertEquals(0.8f, merged.confidence());
    }

    @Test
    @DisplayName("merge keeps base values when override is default")
    void mergeKeepsBaseWhenOverrideDefault() {
        LightRequirement dark = LightRequirement.DARK;
        MobRequirements base = new MobRequirements(
            TEST_MOB, BiomeRequirement.ANY, dark, FloorRequirement.SOLID_GROUND,
            SpaceRequirement.DEFAULT, TimeRequirement.NIGHT, BossRequirement.NOT_BOSS,
            RequirementSource.AUTO_DETECTED, 0.5f
        );
        MobRequirements override = MobRequirements.defaultFor(TEST_MOB);

        MobRequirements merged = base.merge(override);
        assertEquals(dark, merged.light());
        assertEquals(TimeRequirement.NIGHT, merged.time());
    }

    @Test
    @DisplayName("merge takes maximum confidence")
    void mergeTakesMaxConfidence() {
        MobRequirements base = new MobRequirements(
            TEST_MOB, BiomeRequirement.ANY, LightRequirement.ANY, FloorRequirement.SOLID_GROUND,
            SpaceRequirement.DEFAULT, TimeRequirement.ANY, BossRequirement.NOT_BOSS,
            RequirementSource.AUTO_DETECTED, 0.9f
        );
        MobRequirements override = new MobRequirements(
            TEST_MOB, BiomeRequirement.ANY, LightRequirement.ANY, FloorRequirement.SOLID_GROUND,
            SpaceRequirement.DEFAULT, TimeRequirement.ANY, BossRequirement.NOT_BOSS,
            RequirementSource.JSON_OVERRIDE, 0.3f
        );

        assertEquals(0.9f, base.merge(override).confidence());
    }

    @Test
    @DisplayName("merge prefers boss override when isBoss is true")
    void mergePrefersBossOverride() {
        MobRequirements base = MobRequirements.defaultFor(TEST_MOB);
        BossRequirement boss = BossRequirement.simpleBoss();
        MobRequirements override = new MobRequirements(
            TEST_MOB, BiomeRequirement.ANY, LightRequirement.ANY, FloorRequirement.SOLID_GROUND,
            SpaceRequirement.DEFAULT, TimeRequirement.ANY, boss,
            RequirementSource.JSON_OVERRIDE, 1.0f
        );

        MobRequirements merged = base.merge(override);
        assertTrue(merged.boss().isBoss());
    }

    // === BiomeRequirement ===

    @Nested
    class BiomeRequirementTests {

        @Test
        @DisplayName("ANY is default")
        void anyIsDefault() {
            assertTrue(BiomeRequirement.ANY.isDefault());
        }

        @Test
        @DisplayName("preferred creates non-default requirement")
        void preferredIsNotDefault() {
            BiomeRequirement req = BiomeRequirement.preferred(ResourceLocation.withDefaultNamespace("desert"));
            assertFalse(req.isDefault());
            assertTrue(req.preferredBiome().isPresent());
            assertEquals(1, req.validBiomes().size());
        }

        @Test
        @DisplayName("required marks biome as required")
        void requiredSetsFlagTrue() {
            BiomeRequirement req = BiomeRequirement.required(ResourceLocation.withDefaultNamespace("swamp"));
            assertTrue(req.required());
        }

        @Test
        @DisplayName("anyOf uses first biome as preferred")
        void anyOfUsesFirstAsPreferred() {
            List<ResourceLocation> biomes = List.of(
                ResourceLocation.withDefaultNamespace("forest"),
                ResourceLocation.withDefaultNamespace("plains")
            );
            BiomeRequirement req = BiomeRequirement.anyOf(biomes);
            assertEquals(Optional.of(ResourceLocation.withDefaultNamespace("forest")), req.preferredBiome());
            assertEquals(2, req.validBiomes().size());
        }

        @Test
        @DisplayName("anyOf with empty list has no preferred")
        void anyOfEmptyHasNoPreferred() {
            BiomeRequirement req = BiomeRequirement.anyOf(List.of());
            assertTrue(req.preferredBiome().isEmpty());
        }
    }

    // === LightRequirement ===

    @Nested
    class LightRequirementTests {

        @Test
        @DisplayName("ANY is default with range 0-15")
        void anyIsDefault() {
            assertTrue(LightRequirement.ANY.isDefault());
            assertEquals(0, LightRequirement.ANY.minLight());
            assertEquals(15, LightRequirement.ANY.maxLight());
        }

        @Test
        @DisplayName("DARK prefers dark and has range 0-7")
        void darkPrefersDark() {
            assertTrue(LightRequirement.DARK.prefersDark());
            assertFalse(LightRequirement.DARK.prefersLight());
            assertEquals(0, LightRequirement.DARK.minLight());
            assertEquals(7, LightRequirement.DARK.maxLight());
        }

        @Test
        @DisplayName("BRIGHT prefers light and has range 8-15")
        void brightPrefersLight() {
            assertFalse(LightRequirement.BRIGHT.prefersDark());
            assertTrue(LightRequirement.BRIGHT.prefersLight());
            assertEquals(8, LightRequirement.BRIGHT.minLight());
            assertEquals(15, LightRequirement.BRIGHT.maxLight());
        }

        @Test
        @DisplayName("range auto-detects dark/light preference")
        void rangeAutoDetectsPreference() {
            LightRequirement dark = LightRequirement.range(0, 5);
            assertTrue(dark.prefersDark());
            assertFalse(dark.prefersLight());

            LightRequirement bright = LightRequirement.range(10, 15);
            assertFalse(bright.prefersDark());
            assertTrue(bright.prefersLight());
        }

        @Test
        @DisplayName("optimalLight returns min for dark, max for light, midpoint for any")
        void optimalLightCorrect() {
            assertEquals(0, LightRequirement.DARK.optimalLight());
            assertEquals(15, LightRequirement.BRIGHT.optimalLight());
            assertEquals(7, LightRequirement.ANY.optimalLight()); // (0+15)/2
            assertEquals(0, LightRequirement.PITCH_BLACK.optimalLight());
        }
    }

    // === FloorRequirement ===

    @Nested
    class FloorRequirementTests {

        @Test
        @DisplayName("SOLID_GROUND is default")
        void solidGroundIsDefault() {
            assertTrue(FloorRequirement.SOLID_GROUND.isDefault());
            assertTrue(FloorRequirement.SOLID_GROUND.requiresSolid());
        }

        @Test
        @DisplayName("WATER has water block and can spawn on liquid")
        void waterHasWaterBlock() {
            assertTrue(FloorRequirement.WATER.canSpawnOnLiquid());
            assertFalse(FloorRequirement.WATER.requiresSolid());
            assertTrue(FloorRequirement.WATER.preferredBlock().isPresent());
        }

        @Test
        @DisplayName("FLYING can spawn in air")
        void flyingCanSpawnInAir() {
            assertTrue(FloorRequirement.FLYING.canSpawnInAir());
            assertFalse(FloorRequirement.FLYING.requiresSolid());
        }

        @Test
        @DisplayName("specificBlocks creates requirement with blocks list")
        void specificBlocksCreatesRequirement() {
            List<ResourceLocation> blocks = List.of(
                ResourceLocation.withDefaultNamespace("stone"),
                ResourceLocation.withDefaultNamespace("cobblestone")
            );
            FloorRequirement req = FloorRequirement.specificBlocks(blocks);
            assertEquals(2, req.validBlocks().size());
            assertTrue(req.preferredBlock().isPresent());
            assertEquals(blocks.get(0), req.preferredBlock().get());
        }
    }

    // === SpaceRequirement ===

    @Nested
    class SpaceRequirementTests {

        @Test
        @DisplayName("DEFAULT is 1x2 with 1.0 clearance")
        void defaultIs1x2() {
            assertTrue(SpaceRequirement.DEFAULT.isDefault());
            assertEquals(1.0f, SpaceRequirement.DEFAULT.width());
            assertEquals(2.0f, SpaceRequirement.DEFAULT.height());
        }

        @Test
        @DisplayName("fromDimensions calculates clearance")
        void fromDimensionsCalculatesClearance() {
            SpaceRequirement req = SpaceRequirement.fromDimensions(3.0f, 5.0f);
            assertEquals(3.0f, req.width());
            assertEquals(5.0f, req.height());
            assertEquals(2.5f, req.clearanceAbove()); // max(1.0, 5.0*0.5)
            assertEquals(1.5f, req.clearanceAround()); // max(1.0, 3.0*0.5)
        }

        @Test
        @DisplayName("fromDimensions enforces minimum clearance of 1.0")
        void fromDimensionsMinClearance() {
            SpaceRequirement req = SpaceRequirement.fromDimensions(0.5f, 1.0f);
            assertEquals(1.0f, req.clearanceAbove()); // max(1.0, 1.0*0.5) = 1.0
            assertEquals(1.0f, req.clearanceAround()); // max(1.0, 0.5*0.5) = 1.0
        }

        @Test
        @DisplayName("minArenaRadius scales with dimensions")
        void minArenaRadiusScales() {
            // DEFAULT: ceil(max(1.0, 1.0) * 4) = 4
            assertEquals(4, SpaceRequirement.DEFAULT.minArenaRadius());
            // SMALL: ceil(max(0.5, 0.5) * 4) = 2
            assertEquals(2, SpaceRequirement.SMALL.minArenaRadius());
            // LARGE: ceil(max(2.0, 2.0) * 4) = 8
            assertEquals(8, SpaceRequirement.LARGE.minArenaRadius());
            // BOSS: ceil(max(3.0, 4.0) * 4) = 16
            assertEquals(16, SpaceRequirement.BOSS.minArenaRadius());
        }

        @Test
        @DisplayName("BOSS has large dimensions")
        void bossHasLargeDimensions() {
            assertEquals(3.0f, SpaceRequirement.BOSS.width());
            assertEquals(4.0f, SpaceRequirement.BOSS.height());
            assertFalse(SpaceRequirement.BOSS.isDefault());
        }
    }

    // === TimeRequirement ===

    @Nested
    class TimeRequirementTests {

        @Test
        @DisplayName("ANY is valid at all times")
        void anyValidAtAllTimes() {
            assertTrue(TimeRequirement.ANY.isValidAt(0));
            assertTrue(TimeRequirement.ANY.isValidAt(6000));
            assertTrue(TimeRequirement.ANY.isValidAt(12000));
            assertTrue(TimeRequirement.ANY.isValidAt(18000));
            assertTrue(TimeRequirement.ANY.isValidAt(23999));
        }

        @Test
        @DisplayName("DAY is valid during daytime 0-11999")
        void dayValidDuringDay() {
            assertTrue(TimeRequirement.DAY.isValidAt(0));
            assertTrue(TimeRequirement.DAY.isValidAt(6000));
            assertTrue(TimeRequirement.DAY.isValidAt(11999));
            assertFalse(TimeRequirement.DAY.isValidAt(12000));
            assertFalse(TimeRequirement.DAY.isValidAt(18000));
        }

        @Test
        @DisplayName("NIGHT is valid during nighttime 12000-23999")
        void nightValidDuringNight() {
            assertFalse(TimeRequirement.NIGHT.isValidAt(0));
            assertFalse(TimeRequirement.NIGHT.isValidAt(6000));
            assertTrue(TimeRequirement.NIGHT.isValidAt(12000));
            assertTrue(TimeRequirement.NIGHT.isValidAt(18000));
            assertTrue(TimeRequirement.NIGHT.isValidAt(23999));
        }

        @Test
        @DisplayName("DAWN wraps around midnight (23000-1000)")
        void dawnWrapsAroundMidnight() {
            assertTrue(TimeRequirement.DAWN.isValidAt(23000));
            assertTrue(TimeRequirement.DAWN.isValidAt(23500));
            assertTrue(TimeRequirement.DAWN.isValidAt(0));
            assertTrue(TimeRequirement.DAWN.isValidAt(500));
            assertFalse(TimeRequirement.DAWN.isValidAt(1000));
            assertFalse(TimeRequirement.DAWN.isValidAt(12000));
        }

        @Test
        @DisplayName("DUSK is valid during dusk 11000-12999")
        void duskValidDuringDusk() {
            assertTrue(TimeRequirement.DUSK.isValidAt(11000));
            assertTrue(TimeRequirement.DUSK.isValidAt(12000));
            assertTrue(TimeRequirement.DUSK.isValidAt(12999));
            assertFalse(TimeRequirement.DUSK.isValidAt(6000));
            assertFalse(TimeRequirement.DUSK.isValidAt(18000));
        }

        @Test
        @DisplayName("isValidAt handles world time modulo correctly")
        void isValidAtHandlesModulo() {
            // World time can exceed 24000 (day 2, 3, etc.)
            assertTrue(TimeRequirement.DAY.isValidAt(24000)); // 24000 % 24000 = 0
            assertTrue(TimeRequirement.DAY.isValidAt(30000)); // 30000 % 24000 = 6000
            assertTrue(TimeRequirement.NIGHT.isValidAt(36000)); // 36000 % 24000 = 12000
        }

        @Test
        @DisplayName("optimalTick returns expected values")
        void optimalTickReturnsExpected() {
            assertEquals(6000, TimeRequirement.DAY.optimalTick());
            assertEquals(18000, TimeRequirement.NIGHT.optimalTick());
            assertEquals(12000, TimeRequirement.DUSK.optimalTick());
            assertEquals(23000, TimeRequirement.DAWN.optimalTick());
            assertEquals(6000, TimeRequirement.ANY.optimalTick());
        }
    }

    // === BossRequirement ===

    @Nested
    class BossRequirementTests {

        @Test
        @DisplayName("NOT_BOSS is not a boss")
        void notBossIsNotBoss() {
            assertFalse(BossRequirement.NOT_BOSS.isBoss());
            assertEquals(1, BossRequirement.NOT_BOSS.minPlayers());
        }

        @Test
        @DisplayName("simpleBoss is a boss with default settings")
        void simpleBossIsBoss() {
            BossRequirement boss = BossRequirement.simpleBoss();
            assertTrue(boss.isBoss());
            assertEquals(1, boss.minPlayers());
            assertFalse(boss.requiresActivation());
        }

        @Test
        @DisplayName("withMinPlayers sets minimum player count")
        void withMinPlayersSetsCount() {
            BossRequirement boss = BossRequirement.withMinPlayers(4);
            assertTrue(boss.isBoss());
            assertEquals(4, boss.minPlayers());
        }

        @Test
        @DisplayName("withActivation requires activation item")
        void withActivationRequiresItem() {
            ResourceLocation item = ResourceLocation.withDefaultNamespace("nether_star");
            BossRequirement boss = BossRequirement.withActivation(item);
            assertTrue(boss.isBoss());
            assertTrue(boss.requiresActivation());
            assertEquals(Optional.of(item), boss.activationItem());
        }

        @Test
        @DisplayName("miniBoss is flagged as boss")
        void miniBossIsBoss() {
            BossRequirement mini = BossRequirement.miniBoss();
            assertTrue(mini.isBoss());
        }
    }

    // === RequirementSource ===

    @Test
    @DisplayName("RequirementSource has 4 values")
    void requirementSourceHasFourValues() {
        assertEquals(4, RequirementSource.values().length);
    }
}
