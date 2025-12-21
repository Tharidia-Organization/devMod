package com.devmod.arena.registry;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SpawnSlotRuntimeValidationTest {

    @Test
    void rejectsWhenBelowNotSolid() {
        ArenaTemplate.SpawnSlot slot = new ArenaTemplate.SpawnSlot(
            new int[]{0, 1, 0},
            ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR,
            List.of("player"),
            new ArenaTemplate.SpawnSlot.Validation(true, 1, 0)
        );

        BlockPos absPos = new BlockPos(0, 64, 0);
        SpawnSlotValidator.BlockQuery query = new SpawnSlotValidator.BlockQuery() {
            @Override
            public boolean isAir(BlockPos pos) {
                return true;
            }

            @Override
            public boolean isSolid(BlockPos pos) {
                return false;
            }
        };

        SpawnSlotValidator validator = new SpawnSlotValidator();
        assertFalse(validator.validateAtRuntime("test_template", slot, query, absPos));
    }

    @Test
    void rejectsWhenSpawnNotAir() {
        ArenaTemplate.SpawnSlot slot = new ArenaTemplate.SpawnSlot(
            new int[]{0, 1, 0},
            ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR,
            List.of("player"),
            new ArenaTemplate.SpawnSlot.Validation(true, 1, 0)
        );

        BlockPos absPos = new BlockPos(0, 64, 0);
        BlockPos below = absPos.below();
        SpawnSlotValidator.BlockQuery query = new SpawnSlotValidator.BlockQuery() {
            @Override
            public boolean isAir(BlockPos pos) {
                return !pos.equals(absPos);
            }

            @Override
            public boolean isSolid(BlockPos pos) {
                return pos.equals(below);
            }
        };

        SpawnSlotValidator validator = new SpawnSlotValidator();
        assertFalse(validator.validateAtRuntime("test_template", slot, query, absPos));
    }
}
