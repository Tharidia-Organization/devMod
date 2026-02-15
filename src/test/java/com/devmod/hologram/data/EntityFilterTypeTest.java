package com.devmod.hologram.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EntityFilterType")
class EntityFilterTypeTest {

    @Nested
    @DisplayName("bitmask serialization")
    class BitmaskSerialization {
        @Test
        @DisplayName("empty set produces zero bitmask")
        void emptySetZero() {
            assertEquals(0, EntityFilterType.toBitmask(EnumSet.noneOf(EntityFilterType.class)));
        }

        @Test
        @DisplayName("single element produces correct bit")
        void singleElement() {
            int mask = EntityFilterType.toBitmask(EnumSet.of(EntityFilterType.HOSTILE));
            assertEquals(1, mask); // ordinal 0 -> bit 0
        }

        @Test
        @DisplayName("PLAYER has correct bit position")
        void playerBit() {
            int mask = EntityFilterType.toBitmask(EnumSet.of(EntityFilterType.PLAYER));
            assertEquals(1 << EntityFilterType.PLAYER.ordinal(), mask);
        }

        @Test
        @DisplayName("all filters produce full bitmask")
        void allFilters() {
            int mask = EntityFilterType.toBitmask(EnumSet.allOf(EntityFilterType.class));
            int expected = (1 << EntityFilterType.values().length) - 1;
            assertEquals(expected, mask);
        }

        @Test
        @DisplayName("round-trip through bitmask preserves set")
        void roundTrip() {
            EnumSet<EntityFilterType> original = EnumSet.of(
                EntityFilterType.HOSTILE, EntityFilterType.PLAYER, EntityFilterType.ITEM);
            int mask = EntityFilterType.toBitmask(original);
            EnumSet<EntityFilterType> restored = EntityFilterType.fromBitmask(mask);
            assertEquals(original, restored);
        }

        @Test
        @DisplayName("fromBitmask with zero returns empty set")
        void fromBitmaskZero() {
            assertTrue(EntityFilterType.fromBitmask(0).isEmpty());
        }

        @Test
        @DisplayName("round-trip for all filters")
        void roundTripAll() {
            EnumSet<EntityFilterType> all = EntityFilterType.all();
            assertEquals(all, EntityFilterType.fromBitmask(EntityFilterType.toBitmask(all)));
        }
    }

    @Nested
    @DisplayName("factory sets")
    class FactorySets {
        @Test
        @DisplayName("getDefaultFilters returns all types")
        void defaultIsAll() {
            assertEquals(EnumSet.allOf(EntityFilterType.class), EntityFilterType.getDefaultFilters());
        }

        @Test
        @DisplayName("none returns empty set")
        void noneIsEmpty() {
            assertTrue(EntityFilterType.none().isEmpty());
        }

        @Test
        @DisplayName("all returns full set")
        void allIsFull() {
            assertEquals(EntityFilterType.values().length, EntityFilterType.all().size());
        }
    }

    @Test
    @DisplayName("all types have non-null display names")
    void displayNames() {
        for (EntityFilterType type : EntityFilterType.values()) {
            assertNotNull(type.getDisplayName());
            assertFalse(type.getDisplayName().isEmpty());
        }
    }

    @Test
    @DisplayName("all types have non-zero colors")
    void colors() {
        for (EntityFilterType type : EntityFilterType.values()) {
            assertTrue(type.getColor() != 0, type.name() + " should have non-zero color");
        }
    }

    @Test
    @DisplayName("has exactly 7 types")
    void typeCount() {
        assertEquals(7, EntityFilterType.values().length);
    }
}
