package com.devmod.telemetry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomDefinitionDirectTest {

    @Test
    @DisplayName("RoomDefinition defaults to overworld when dimension is null or blank")
    void roomDefinitionDefaultsDimension() {
        BlockPos min = new BlockPos(0, 0, 0);
        BlockPos max = new BlockPos(1, 1, 1);

        RoomDefinition nullDim = new RoomDefinition("room", null, min, max);
        assertEquals("minecraft:overworld", nullDim.dimension());

        RoomDefinition blankDim = new RoomDefinition("room", " ", min, max);
        assertEquals("minecraft:overworld", blankDim.dimension());

        RoomDefinition defaultCtor = new RoomDefinition("room", min, max);
        assertEquals("minecraft:overworld", defaultCtor.dimension());
    }

    @Test
    @DisplayName("RoomDefinition keeps explicit dimension")
    void roomDefinitionKeepsExplicitDimension() {
        BlockPos min = new BlockPos(-5, 60, -5);
        BlockPos max = new BlockPos(5, 80, 5);

        RoomDefinition netherRoom = new RoomDefinition("room", "minecraft:the_nether", min, max);
        assertEquals("minecraft:the_nether", netherRoom.dimension());
    }
}
