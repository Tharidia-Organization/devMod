package com.devmod.debug;

import com.devmod.debug.client.DebugRenderBools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DebugRenderBools")
class DebugRenderBoolsTest {

    @AfterEach
    void tearDown() {
        DebugRenderBools.clearAll();
    }

    @Test
    @DisplayName("All flags default to false")
    void allDefaultFalse() {
        DebugRenderBools.clearAll();
        assertFalse(DebugRenderBools.isEntityPathing());
        assertFalse(DebugRenderBools.isEntityGoals());
        assertFalse(DebugRenderBools.isEntityBrains());
        assertFalse(DebugRenderBools.isPoi());
        assertFalse(DebugRenderBools.isBlockUpdates());
        assertFalse(DebugRenderBools.isStructures());
        assertFalse(DebugRenderBools.isRaids());
        assertFalse(DebugRenderBools.isGameEvents());
        assertFalse(DebugRenderBools.isBees());
        assertFalse(DebugRenderBools.isWater());
        assertFalse(DebugRenderBools.isHeightmap());
        assertFalse(DebugRenderBools.isCollision());
        assertFalse(DebugRenderBools.isLight());
        assertFalse(DebugRenderBools.isSolidFaces());
        assertFalse(DebugRenderBools.isChunk());
        assertFalse(DebugRenderBools.isSpawnChunks());
    }

    @Test
    @DisplayName("Set and get entityPathing")
    void setGetEntityPathing() {
        DebugRenderBools.setEntityPathing(true);
        assertTrue(DebugRenderBools.isEntityPathing());
        DebugRenderBools.setEntityPathing(false);
        assertFalse(DebugRenderBools.isEntityPathing());
    }

    @Test
    @DisplayName("Set and get entityGoals")
    void setGetEntityGoals() {
        DebugRenderBools.setEntityGoals(true);
        assertTrue(DebugRenderBools.isEntityGoals());
    }

    @Test
    @DisplayName("Set and get blockUpdates")
    void setGetBlockUpdates() {
        DebugRenderBools.setBlockUpdates(true);
        assertTrue(DebugRenderBools.isBlockUpdates());
        DebugRenderBools.setBlockUpdates(false);
        assertFalse(DebugRenderBools.isBlockUpdates());
    }

    @Test
    @DisplayName("Set and get poi")
    void setGetPoi() {
        DebugRenderBools.setPoi(true);
        assertTrue(DebugRenderBools.isPoi());
    }

    @Test
    @DisplayName("Set and get raids")
    void setGetRaids() {
        DebugRenderBools.setRaids(true);
        assertTrue(DebugRenderBools.isRaids());
    }

    @Test
    @DisplayName("Set and get light")
    void setGetLight() {
        DebugRenderBools.setLight(true);
        assertTrue(DebugRenderBools.isLight());
    }

    @Test
    @DisplayName("Set and get collision")
    void setGetCollision() {
        DebugRenderBools.setCollision(true);
        assertTrue(DebugRenderBools.isCollision());
    }

    @Test
    @DisplayName("Set and get spawnChunks")
    void setGetSpawnChunks() {
        DebugRenderBools.setSpawnChunks(true);
        assertTrue(DebugRenderBools.isSpawnChunks());
    }

    @Test
    @DisplayName("clearAll resets all flags to false")
    void clearAllResetsAllFlags() {
        DebugRenderBools.setEntityPathing(true);
        DebugRenderBools.setEntityGoals(true);
        DebugRenderBools.setEntityBrains(true);
        DebugRenderBools.setPoi(true);
        DebugRenderBools.setBlockUpdates(true);
        DebugRenderBools.setStructures(true);
        DebugRenderBools.setRaids(true);
        DebugRenderBools.setGameEvents(true);
        DebugRenderBools.setBees(true);
        DebugRenderBools.setWater(true);
        DebugRenderBools.setHeightmap(true);
        DebugRenderBools.setCollision(true);
        DebugRenderBools.setLight(true);
        DebugRenderBools.setSolidFaces(true);
        DebugRenderBools.setChunk(true);
        DebugRenderBools.setSpawnChunks(true);

        DebugRenderBools.clearAll();

        assertFalse(DebugRenderBools.isEntityPathing());
        assertFalse(DebugRenderBools.isEntityGoals());
        assertFalse(DebugRenderBools.isEntityBrains());
        assertFalse(DebugRenderBools.isPoi());
        assertFalse(DebugRenderBools.isBlockUpdates());
        assertFalse(DebugRenderBools.isStructures());
        assertFalse(DebugRenderBools.isRaids());
        assertFalse(DebugRenderBools.isGameEvents());
        assertFalse(DebugRenderBools.isBees());
        assertFalse(DebugRenderBools.isWater());
        assertFalse(DebugRenderBools.isHeightmap());
        assertFalse(DebugRenderBools.isCollision());
        assertFalse(DebugRenderBools.isLight());
        assertFalse(DebugRenderBools.isSolidFaces());
        assertFalse(DebugRenderBools.isChunk());
        assertFalse(DebugRenderBools.isSpawnChunks());
    }

    @Test
    @DisplayName("Multiple flags can be independently set")
    void independentFlags() {
        DebugRenderBools.setRaids(true);
        DebugRenderBools.setLight(true);

        assertTrue(DebugRenderBools.isRaids());
        assertTrue(DebugRenderBools.isLight());
        assertFalse(DebugRenderBools.isPoi());
        assertFalse(DebugRenderBools.isCollision());
    }
}
