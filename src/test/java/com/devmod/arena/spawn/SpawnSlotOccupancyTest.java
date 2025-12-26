package com.devmod.arena.spawn;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpawnSlotOccupancyTest {

    @Test
    void secondPickFailsWhenOnlyOneSlotAvailable() {
        SpawnOccupancyTracker occupied = new SpawnOccupancyTracker();
        List<BlockPos> positions = List.of(new BlockPos(0, 64, 0));

        BlockPos first = pickNext(positions, 0, occupied);
        BlockPos second = pickNext(positions, 0, occupied);

        assertNotNull(first);
        assertNull(second);
    }

    @Test
    void secondPickRepicksDifferentSlotWhenAvailable() {
        SpawnOccupancyTracker occupied = new SpawnOccupancyTracker();
        BlockPos firstPos = new BlockPos(0, 64, 0);
        BlockPos secondPos = new BlockPos(5, 64, 0);
        List<BlockPos> positions = List.of(firstPos, secondPos);

        BlockPos first = pickNext(positions, 0, occupied);
        BlockPos second = pickNext(positions, 0, occupied);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(secondPos, second);
    }

    private BlockPos pickNext(List<BlockPos> positions, int startIndex, SpawnOccupancyTracker occupied) {
        int size = positions.size();
        for (int offset = 0; offset < size; offset++) {
            BlockPos pos = positions.get((startIndex + offset) % size);
            if (occupied.isOccupied(pos)) {
                continue;
            }
            occupied.markOccupied(pos);
            return pos;
        }
        return null;
    }
}
