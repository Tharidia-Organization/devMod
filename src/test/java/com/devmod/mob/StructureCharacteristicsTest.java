package com.devmod.mob;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import static org.junit.jupiter.api.Assertions.*;

class StructureCharacteristicsTest {

    // === Registry ===

    @Test
    @DisplayName("Vanilla structures are registered in static initializer")
    void vanillaStructuresRegistered() {
        assertTrue(StructureCharacteristics.isRegistered("minecraft:stronghold"));
        assertTrue(StructureCharacteristics.isRegistered("minecraft:fortress"));
        assertTrue(StructureCharacteristics.isRegistered("minecraft:mansion"));
        assertTrue(StructureCharacteristics.isRegistered("minecraft:ocean_monument"));
        assertTrue(StructureCharacteristics.isRegistered("minecraft:end_city"));
        assertTrue(StructureCharacteristics.isRegistered("minecraft:ancient_city"));
        assertTrue(StructureCharacteristics.isRegistered("minecraft:trial_chambers"));
    }

    @Test
    @DisplayName("Modded structures are registered")
    void moddedStructuresRegistered() {
        assertTrue(StructureCharacteristics.isRegistered("graveyard:crypt"));
        assertTrue(StructureCharacteristics.isRegistered("twilightforest:lich_tower"));
        assertTrue(StructureCharacteristics.isRegistered("iceandfire:fire_dragon_cave"));
        assertTrue(StructureCharacteristics.isRegistered("cataclysm:burning_arena"));
    }

    @Test
    @DisplayName("get returns Optional for known structure")
    void getReturnsOptionalForKnown() {
        Optional<StructureCharacteristics> result = StructureCharacteristics.get("minecraft:stronghold");
        assertTrue(result.isPresent());
        assertEquals("Stronghold", result.get().displayName());
    }

    @Test
    @DisplayName("get returns empty for unknown structure")
    void getReturnsEmptyForUnknown() {
        Optional<StructureCharacteristics> result = StructureCharacteristics.get("unknown:structure");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("get with ResourceLocation delegates correctly")
    void getWithResourceLocationDelegates() {
        Optional<StructureCharacteristics> result = StructureCharacteristics.get(
            ResourceLocation.withDefaultNamespace("fortress"));
        assertTrue(result.isPresent());
        assertEquals("Nether Fortress", result.get().displayName());
    }

    // === findByTag ===

    @Test
    @DisplayName("findByTag returns structures matching tag")
    void findByTagReturnsMatches() {
        Map<String, StructureCharacteristics> dungeons = StructureCharacteristics.findByTag("dungeon");
        assertFalse(dungeons.isEmpty());
        assertTrue(dungeons.containsKey("minecraft:stronghold"));
        assertTrue(dungeons.containsKey("minecraft:mineshaft"));
        assertTrue(dungeons.containsKey("minecraft:ancient_city"));
    }

    @Test
    @DisplayName("findByTag returns empty for unknown tag")
    void findByTagReturnsEmptyForUnknown() {
        Map<String, StructureCharacteristics> result = StructureCharacteristics.findByTag("nonexistent_tag");
        assertTrue(result.isEmpty());
    }

    // === getAll ===

    @Test
    @DisplayName("getAll returns all registered structures")
    void getAllReturnsAll() {
        Map<String, StructureCharacteristics> all = StructureCharacteristics.getAll();
        assertFalse(all.isEmpty());
        assertTrue(all.size() >= 10); // At least vanilla structures
    }

    @Test
    @DisplayName("getAll returns unmodifiable copy")
    void getAllReturnsUnmodifiable() {
        Map<String, StructureCharacteristics> all = StructureCharacteristics.getAll();
        assertThrows(UnsupportedOperationException.class,
            () -> all.put("test:structure", null));
    }

    // === Derived Methods ===

    @Test
    @DisplayName("floorBlock returns parsed ResourceLocation")
    void floorBlockReturnsResourceLocation() {
        StructureCharacteristics stronghold = StructureCharacteristics.get("minecraft:stronghold").orElseThrow();
        ResourceLocation floor = stronghold.floorBlock();
        assertEquals("minecraft", floor.getNamespace());
        assertEquals("stone_bricks", floor.getPath());
    }

    @Test
    @DisplayName("isDungeon returns true for dungeon-tagged or underground/enclosed structures")
    void isDungeonReturnsCorrectly() {
        StructureCharacteristics stronghold = StructureCharacteristics.get("minecraft:stronghold").orElseThrow();
        assertTrue(stronghold.isDungeon()); // Has "dungeon" tag

        StructureCharacteristics mansion = StructureCharacteristics.get("minecraft:mansion").orElseThrow();
        assertTrue(mansion.isDungeon()); // Is enclosed
    }

    @Test
    @DisplayName("isBossStructure returns true for boss-tagged structures")
    void isBossStructureReturnsCorrectly() {
        StructureCharacteristics lichTower = StructureCharacteristics.get("twilightforest:lich_tower").orElseThrow();
        assertTrue(lichTower.isBossStructure());

        StructureCharacteristics mineshaft = StructureCharacteristics.get("minecraft:mineshaft").orElseThrow();
        assertFalse(mineshaft.isBossStructure());
    }

    @Test
    @DisplayName("describeEnvironment includes key properties")
    void describeEnvironmentIncludesKeyProperties() {
        StructureCharacteristics stronghold = StructureCharacteristics.get("minecraft:stronghold").orElseThrow();
        String desc = stronghold.describeEnvironment();
        assertTrue(desc.contains("underground"));
        assertTrue(desc.contains("dark"));
        assertTrue(desc.contains("enclosed"));
        assertTrue(desc.contains("floor="));
        assertTrue(desc.contains("light="));
    }

    // === YRange ===

    @Nested
    class YRangeTests {

        @Test
        @DisplayName("SURFACE covers Y 60-100")
        void surfaceCoversExpectedRange() {
            assertTrue(StructureCharacteristics.YRange.SURFACE.contains(60));
            assertTrue(StructureCharacteristics.YRange.SURFACE.contains(80));
            assertTrue(StructureCharacteristics.YRange.SURFACE.contains(100));
            assertFalse(StructureCharacteristics.YRange.SURFACE.contains(59));
            assertFalse(StructureCharacteristics.YRange.SURFACE.contains(101));
        }

        @Test
        @DisplayName("UNDERGROUND covers Y 0-60")
        void undergroundCoversExpectedRange() {
            assertTrue(StructureCharacteristics.YRange.UNDERGROUND.contains(0));
            assertTrue(StructureCharacteristics.YRange.UNDERGROUND.contains(30));
            assertTrue(StructureCharacteristics.YRange.UNDERGROUND.contains(60));
            assertFalse(StructureCharacteristics.YRange.UNDERGROUND.contains(-1));
            assertFalse(StructureCharacteristics.YRange.UNDERGROUND.contains(61));
        }

        @Test
        @DisplayName("DEEP_UNDERGROUND covers Y -64 to 0")
        void deepUndergroundCoversExpectedRange() {
            assertTrue(StructureCharacteristics.YRange.DEEP_UNDERGROUND.contains(-64));
            assertTrue(StructureCharacteristics.YRange.DEEP_UNDERGROUND.contains(-32));
            assertTrue(StructureCharacteristics.YRange.DEEP_UNDERGROUND.contains(0));
            assertFalse(StructureCharacteristics.YRange.DEEP_UNDERGROUND.contains(1));
        }

        @Test
        @DisplayName("ANY covers full build range -64 to 320")
        void anyCoversFullRange() {
            assertTrue(StructureCharacteristics.YRange.ANY.contains(-64));
            assertTrue(StructureCharacteristics.YRange.ANY.contains(0));
            assertTrue(StructureCharacteristics.YRange.ANY.contains(320));
        }
    }

    // === Structure Properties ===

    @Test
    @DisplayName("Stronghold is underground, dark, and enclosed")
    void strongholdProperties() {
        StructureCharacteristics sc = StructureCharacteristics.get("minecraft:stronghold").orElseThrow();
        assertTrue(sc.underground());
        assertTrue(sc.dark());
        assertTrue(sc.enclosed());
        assertEquals(3, sc.lightLevel());
    }

    @Test
    @DisplayName("Pillager outpost is surface, not dark, not enclosed")
    void pillagerOutpostProperties() {
        StructureCharacteristics sc = StructureCharacteristics.get("minecraft:pillager_outpost").orElseThrow();
        assertFalse(sc.underground());
        assertFalse(sc.dark());
        assertFalse(sc.enclosed());
        assertEquals(15, sc.lightLevel());
    }

    @Test
    @DisplayName("Ancient City has deep_dark biome hint")
    void ancientCityBiomeHint() {
        StructureCharacteristics sc = StructureCharacteristics.get("minecraft:ancient_city").orElseThrow();
        assertEquals("minecraft:deep_dark", sc.biomeHint());
    }
}
