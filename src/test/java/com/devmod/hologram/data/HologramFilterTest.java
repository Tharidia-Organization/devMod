package com.devmod.hologram.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.block.Blocks;

import com.devmod.TestBootstrap;

@DisplayName("HologramFilter")
class HologramFilterTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("matches")
    class Matches {
        @Test
        @DisplayName("DIAMONDS matches diamond ore")
        void diamondOre() {
            assertTrue(HologramFilter.DIAMONDS.matches(Blocks.DIAMOND_ORE.defaultBlockState()));
        }

        @Test
        @DisplayName("DIAMONDS matches deepslate diamond ore")
        void deepslateDiamondOre() {
            assertTrue(HologramFilter.DIAMONDS.matches(Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState()));
        }

        @Test
        @DisplayName("DIAMONDS does not match iron ore")
        void diamondDoesNotMatchIron() {
            assertFalse(HologramFilter.DIAMONDS.matches(Blocks.IRON_ORE.defaultBlockState()));
        }

        @Test
        @DisplayName("CHESTS matches chest")
        void chestsMatchChest() {
            assertTrue(HologramFilter.CHESTS.matches(Blocks.CHEST.defaultBlockState()));
        }

        @Test
        @DisplayName("SPAWNERS matches spawner")
        void spawnersMatchSpawner() {
            assertTrue(HologramFilter.SPAWNERS.matches(Blocks.SPAWNER.defaultBlockState()));
        }

        @Test
        @DisplayName("COAL matches coal ore")
        void coalMatchesCoalOre() {
            assertTrue(HologramFilter.COAL.matches(Blocks.COAL_ORE.defaultBlockState()));
        }

        @Test
        @DisplayName("no filter matches stone")
        void stoneMatchesNothing() {
            for (HologramFilter filter : HologramFilter.values()) {
                assertFalse(filter.matches(Blocks.STONE.defaultBlockState()),
                    filter.name() + " should not match stone");
            }
        }
    }

    @Nested
    @DisplayName("getMatchingFilter")
    class GetMatchingFilter {
        @Test
        @DisplayName("returns matching filter from active set")
        void returnsMatching() {
            EnumSet<HologramFilter> active = EnumSet.of(HologramFilter.DIAMONDS, HologramFilter.GOLD);
            HologramFilter result = HologramFilter.getMatchingFilter(
                Blocks.DIAMOND_ORE.defaultBlockState(), active);
            assertEquals(HologramFilter.DIAMONDS, result);
        }

        @Test
        @DisplayName("returns null when no filter matches")
        void returnsNullNoMatch() {
            EnumSet<HologramFilter> active = EnumSet.of(HologramFilter.DIAMONDS);
            assertNull(HologramFilter.getMatchingFilter(
                Blocks.STONE.defaultBlockState(), active));
        }

        @Test
        @DisplayName("returns null for empty filter set")
        void returnsNullEmptySet() {
            assertNull(HologramFilter.getMatchingFilter(
                Blocks.DIAMOND_ORE.defaultBlockState(), EnumSet.noneOf(HologramFilter.class)));
        }
    }

    @Nested
    @DisplayName("factory sets")
    class FactorySets {
        @Test
        @DisplayName("getDefaultFilters contains diamonds and chests")
        void defaultContainsDiamondsAndChests() {
            EnumSet<HologramFilter> defaults = HologramFilter.getDefaultFilters();
            assertTrue(defaults.contains(HologramFilter.DIAMONDS));
            assertTrue(defaults.contains(HologramFilter.CHESTS));
            assertTrue(defaults.contains(HologramFilter.SPAWNERS));
        }

        @Test
        @DisplayName("getAllOres contains all ore types")
        void allOresContainsOres() {
            EnumSet<HologramFilter> ores = HologramFilter.getAllOres();
            assertTrue(ores.contains(HologramFilter.DIAMONDS));
            assertTrue(ores.contains(HologramFilter.GOLD));
            assertTrue(ores.contains(HologramFilter.IRON));
            assertTrue(ores.contains(HologramFilter.COAL));
            assertTrue(ores.contains(HologramFilter.NETHERITE));
            assertFalse(ores.contains(HologramFilter.CHESTS));
            assertFalse(ores.contains(HologramFilter.SPAWNERS));
        }

        @Test
        @DisplayName("none returns empty set")
        void noneIsEmpty() {
            assertTrue(HologramFilter.none().isEmpty());
        }

        @Test
        @DisplayName("all returns full set")
        void allIsFull() {
            assertEquals(HologramFilter.values().length, HologramFilter.all().size());
        }
    }

    @Test
    @DisplayName("all filters have non-empty display names")
    void displayNames() {
        for (HologramFilter filter : HologramFilter.values()) {
            assertNotNull(filter.getDisplayName());
            assertFalse(filter.getDisplayName().isEmpty());
        }
    }

    @Test
    @DisplayName("all filters have non-zero highlight colors")
    void highlightColors() {
        for (HologramFilter filter : HologramFilter.values()) {
            assertTrue(filter.getHighlightColor() != 0,
                filter.name() + " should have non-zero highlight color");
        }
    }

    @Test
    @DisplayName("has 11 filter types")
    void filterCount() {
        assertEquals(11, HologramFilter.values().length);
    }
}
