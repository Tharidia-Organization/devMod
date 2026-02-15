package com.devmod.endurance;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import static org.junit.jupiter.api.Assertions.*;

class WaveSpawnPositionResolverTest {

    private final WaveSpawnPositionResolver resolver = WaveSpawnPositionResolver.INSTANCE;

    // ========== resolveSpawnRole ==========

    @Nested
    @DisplayName("resolveSpawnRole")
    class ResolveSpawnRole {

        @Test
        @DisplayName("null affix returns ANY")
        void nullReturnsAny() {
            assertEquals(WaveDirector.SpawnRole.ANY, resolver.resolveSpawnRole(null));
        }

        @Test
        @DisplayName("BASE affix returns ANY")
        void baseReturnsAny() {
            assertEquals(WaveDirector.SpawnRole.ANY, resolver.resolveSpawnRole(SpawnAffix.BASE));
        }

        @Test
        @DisplayName("SNIPER returns RANGED")
        void sniperReturnsRanged() {
            assertEquals(WaveDirector.SpawnRole.RANGED, resolver.resolveSpawnRole(SpawnAffix.SNIPER));
        }

        @Test
        @DisplayName("RUSH returns MELEE")
        void rushReturnsMelee() {
            assertEquals(WaveDirector.SpawnRole.MELEE, resolver.resolveSpawnRole(SpawnAffix.RUSH));
        }

        @Test
        @DisplayName("BRUTE returns MELEE")
        void bruteReturnsMelee() {
            assertEquals(WaveDirector.SpawnRole.MELEE, resolver.resolveSpawnRole(SpawnAffix.BRUTE));
        }

        @Test
        @DisplayName("ELITE returns CORNER")
        void eliteReturnsCorner() {
            assertEquals(WaveDirector.SpawnRole.CORNER, resolver.resolveSpawnRole(SpawnAffix.ELITE));
        }

        @Test
        @DisplayName("OBJECTIVE_ELITE returns CORNER")
        void objectiveEliteReturnsCorner() {
            assertEquals(WaveDirector.SpawnRole.CORNER, resolver.resolveSpawnRole(SpawnAffix.OBJECTIVE_ELITE));
        }
    }

    // ========== resolvePoolTag ==========

    @Nested
    @DisplayName("resolvePoolTag")
    class ResolvePoolTag {

        @Test
        @DisplayName("identifies ranged pool by reference")
        void rangedPool() {
            List<BlockPos> ranged = List.of(BlockPos.ZERO);
            WaveManager.SpawnPools pools = new WaveManager.SpawnPools(
                List.of(BlockPos.ZERO), List.of(), ranged, List.of()
            );
            assertEquals("ranged", resolver.resolvePoolTag(ranged, pools));
        }

        @Test
        @DisplayName("identifies melee pool by reference")
        void meleePool() {
            List<BlockPos> melee = List.of(BlockPos.ZERO);
            WaveManager.SpawnPools pools = new WaveManager.SpawnPools(
                List.of(BlockPos.ZERO), melee, List.of(), List.of()
            );
            assertEquals("melee", resolver.resolvePoolTag(melee, pools));
        }

        @Test
        @DisplayName("identifies corner pool by reference")
        void cornerPool() {
            List<BlockPos> corner = List.of(BlockPos.ZERO);
            WaveManager.SpawnPools pools = new WaveManager.SpawnPools(
                List.of(BlockPos.ZERO), List.of(), List.of(), corner
            );
            assertEquals("corner", resolver.resolvePoolTag(corner, pools));
        }

        @Test
        @DisplayName("unknown pool returns all")
        void unknownReturnsAll() {
            List<BlockPos> unknown = List.of(BlockPos.ZERO);
            WaveManager.SpawnPools pools = new WaveManager.SpawnPools(
                List.of(BlockPos.ZERO), List.of(), List.of(), List.of()
            );
            assertEquals("all", resolver.resolvePoolTag(unknown, pools));
        }
    }

    // ========== buildSpawnPools ==========

    @Nested
    @DisplayName("buildSpawnPools")
    class BuildSpawnPools {

        @Test
        @DisplayName("empty positions produce empty pools")
        void emptyPositions() {
            WaveManager.SpawnPools pools = resolver.buildSpawnPools(List.of(), Map.of());
            assertTrue(pools.all().isEmpty());
            assertTrue(pools.melee().isEmpty());
            assertTrue(pools.ranged().isEmpty());
            assertTrue(pools.corner().isEmpty());
        }

        @Test
        @DisplayName("positions without slot map go only to all pool")
        void noSlotMap() {
            BlockPos pos = new BlockPos(1, 2, 3);
            WaveManager.SpawnPools pools = resolver.buildSpawnPools(List.of(pos), Map.of());
            assertEquals(1, pools.all().size());
            assertTrue(pools.melee().isEmpty());
            assertTrue(pools.ranged().isEmpty());
            assertTrue(pools.corner().isEmpty());
        }
    }
}
