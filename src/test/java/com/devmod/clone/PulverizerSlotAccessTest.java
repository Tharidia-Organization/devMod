package com.devmod.clone;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.clone.block.entity.ClonePulverizerBlockEntity;

/**
 * Tests for ClonePulverizerBlockEntity slot constants and WorldlyContainer contracts.
 * These are pure constant/structural tests that don't require a running game.
 */
class PulverizerSlotAccessTest {

    @Nested
    @DisplayName("Slot index constants")
    class SlotIndexTests {

        @Test
        @DisplayName("SLOT_INPUT is 0")
        void slotInput() {
            assertEquals(0, ClonePulverizerBlockEntity.SLOT_INPUT);
        }

        @Test
        @DisplayName("SLOT_OUTPUT is 1")
        void slotOutput() {
            assertEquals(1, ClonePulverizerBlockEntity.SLOT_OUTPUT);
        }

        @Test
        @DisplayName("SLOT_GRINDER_LEFT is 2")
        void slotGrinderLeft() {
            assertEquals(2, ClonePulverizerBlockEntity.SLOT_GRINDER_LEFT);
        }

        @Test
        @DisplayName("SLOT_GRINDER_RIGHT is 3")
        void slotGrinderRight() {
            assertEquals(3, ClonePulverizerBlockEntity.SLOT_GRINDER_RIGHT);
        }

        @Test
        @DisplayName("All slot indices are unique")
        void allSlotsUnique() {
            var slots = java.util.Set.of(
                ClonePulverizerBlockEntity.SLOT_INPUT,
                ClonePulverizerBlockEntity.SLOT_OUTPUT,
                ClonePulverizerBlockEntity.SLOT_GRINDER_LEFT,
                ClonePulverizerBlockEntity.SLOT_GRINDER_RIGHT
            );
            assertEquals(4, slots.size());
        }

        @Test
        @DisplayName("Slot indices are within inventory size range [0, 3]")
        void slotsInRange() {
            assertTrue(ClonePulverizerBlockEntity.SLOT_INPUT >= 0);
            assertTrue(ClonePulverizerBlockEntity.SLOT_OUTPUT >= 0);
            assertTrue(ClonePulverizerBlockEntity.SLOT_GRINDER_LEFT >= 0);
            assertTrue(ClonePulverizerBlockEntity.SLOT_GRINDER_RIGHT >= 0);
            assertTrue(ClonePulverizerBlockEntity.SLOT_GRINDER_RIGHT < 4);
        }
    }

    @Nested
    @DisplayName("Grinder slot layout")
    class GrinderLayoutTests {

        @Test
        @DisplayName("Left grinder index is less than right")
        void leftBeforeRight() {
            assertTrue(ClonePulverizerBlockEntity.SLOT_GRINDER_LEFT < ClonePulverizerBlockEntity.SLOT_GRINDER_RIGHT);
        }

        @Test
        @DisplayName("Input comes before output")
        void inputBeforeOutput() {
            assertTrue(ClonePulverizerBlockEntity.SLOT_INPUT < ClonePulverizerBlockEntity.SLOT_OUTPUT);
        }

        @Test
        @DisplayName("Grinder slots come after input/output")
        void grindersAfterIo() {
            assertTrue(ClonePulverizerBlockEntity.SLOT_GRINDER_LEFT > ClonePulverizerBlockEntity.SLOT_OUTPUT);
        }
    }
}
