package com.devmod.clone;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import com.devmod.clone.data.BioscanData;

class BioscanDataTest {

    private static final ResourceLocation ZOMBIE_TYPE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "zombie");
    private static final ResourceLocation PLAYER_TYPE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "player");

    @Nested
    @DisplayName("Record construction")
    class ConstructionTests {

        @Test
        @DisplayName("Player bioscan has UUID and no NBT")
        void playerBioscan() {
            UUID uuid = UUID.randomUUID();
            BioscanData data = new BioscanData(PLAYER_TYPE, "Steve", uuid, null, 12345L);

            assertEquals(PLAYER_TYPE, data.entityTypeId());
            assertEquals("Steve", data.entityName());
            assertEquals(uuid, data.playerUUID());
            assertNull(data.entityNBT());
            assertEquals(12345L, data.scanTime());
        }

        @Test
        @DisplayName("Mob bioscan has NBT and no UUID")
        void mobBioscan() {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("id", "minecraft:zombie");
            nbt.putInt("Health", 20);

            BioscanData data = new BioscanData(ZOMBIE_TYPE, "Zombie", null, nbt, 99999L);

            assertEquals(ZOMBIE_TYPE, data.entityTypeId());
            assertEquals("Zombie", data.entityName());
            assertNull(data.playerUUID());
            assertNotNull(data.entityNBT());
            assertEquals(99999L, data.scanTime());
        }
    }

    @Nested
    @DisplayName("isPlayer")
    class IsPlayerTests {

        @Test
        @DisplayName("Returns true when playerUUID is present")
        void trueWithUUID() {
            BioscanData data = new BioscanData(PLAYER_TYPE, "Alex", UUID.randomUUID(), null, 0);
            assertTrue(data.isPlayer());
        }

        @Test
        @DisplayName("Returns false when playerUUID is null")
        void falseWithoutUUID() {
            BioscanData data = new BioscanData(ZOMBIE_TYPE, "Zombie", null, new CompoundTag(), 0);
            assertFalse(data.isPlayer());
        }
    }

    @Nested
    @DisplayName("hasEntityNBT")
    class HasEntityNBTTests {

        @Test
        @DisplayName("Returns true for non-empty NBT")
        void trueForNonEmptyNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("Health", 20);
            BioscanData data = new BioscanData(ZOMBIE_TYPE, "Zombie", null, nbt, 0);
            assertTrue(data.hasEntityNBT());
        }

        @Test
        @DisplayName("Returns false for null NBT")
        void falseForNullNbt() {
            BioscanData data = new BioscanData(PLAYER_TYPE, "Steve", UUID.randomUUID(), null, 0);
            assertFalse(data.hasEntityNBT());
        }

        @Test
        @DisplayName("Returns false for empty NBT")
        void falseForEmptyNbt() {
            BioscanData data = new BioscanData(ZOMBIE_TYPE, "Zombie", null, new CompoundTag(), 0);
            assertFalse(data.hasEntityNBT());
        }
    }

    @Nested
    @DisplayName("Codec serialization roundtrip")
    class CodecRoundtripTests {

        @Test
        @DisplayName("Player data survives toTag/fromTag roundtrip")
        void playerRoundtrip() {
            UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
            BioscanData original = new BioscanData(PLAYER_TYPE, "TestPlayer", uuid, null, 42L);

            CompoundTag tag = original.toTag();
            assertNotNull(tag);
            assertFalse(tag.isEmpty(), "Serialized tag should not be empty");

            BioscanData restored = BioscanData.fromTag(tag);
            assertNotNull(restored, "fromTag should return non-null for valid data");
            assertEquals(original.entityTypeId(), restored.entityTypeId());
            assertEquals(original.entityName(), restored.entityName());
            assertEquals(original.playerUUID(), restored.playerUUID());
            assertNull(restored.entityNBT());
            assertEquals(original.scanTime(), restored.scanTime());
        }

        @Test
        @DisplayName("Mob data with NBT survives roundtrip")
        void mobRoundtrip() {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("CustomName", "Bob");
            nbt.putFloat("Health", 15.5f);
            nbt.putBoolean("IsBaby", true);

            BioscanData original = new BioscanData(ZOMBIE_TYPE, "Zombie", null, nbt, 100L);

            CompoundTag tag = original.toTag();
            BioscanData restored = BioscanData.fromTag(tag);

            assertNotNull(restored);
            assertEquals(ZOMBIE_TYPE, restored.entityTypeId());
            assertEquals("Zombie", restored.entityName());
            assertNull(restored.playerUUID());
            assertNotNull(restored.entityNBT());
            assertEquals("Bob", restored.entityNBT().getString("CustomName"));
            assertEquals(15.5f, restored.entityNBT().getFloat("Health"), 0.001f);
            assertTrue(restored.entityNBT().getBoolean("IsBaby"));
            assertEquals(100L, restored.scanTime());
        }

        @Test
        @DisplayName("fromTag returns null for empty tag")
        void fromTagEmptyReturnsNull() {
            BioscanData result = BioscanData.fromTag(new CompoundTag());
            assertNull(result);
        }

        @Test
        @DisplayName("fromTag returns null for garbage tag")
        void fromTagGarbageReturnsNull() {
            CompoundTag garbage = new CompoundTag();
            garbage.putString("random", "data");
            BioscanData result = BioscanData.fromTag(garbage);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Empty entity name is allowed")
        void emptyName() {
            BioscanData data = new BioscanData(ZOMBIE_TYPE, "", null, null, 0);
            assertEquals("", data.entityName());
        }

        @Test
        @DisplayName("Scan time of zero works")
        void zeroScanTime() {
            BioscanData data = new BioscanData(ZOMBIE_TYPE, "Z", null, null, 0);
            assertEquals(0L, data.scanTime());
        }

        @Test
        @DisplayName("Negative scan time works")
        void negativeScanTime() {
            BioscanData data = new BioscanData(ZOMBIE_TYPE, "Z", null, null, -1L);
            assertEquals(-1L, data.scanTime());
        }

        @Test
        @DisplayName("Both UUID and NBT present simultaneously")
        void bothUuidAndNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("test", 1);
            UUID uuid = UUID.randomUUID();
            BioscanData data = new BioscanData(PLAYER_TYPE, "Hybrid", uuid, nbt, 0);

            assertTrue(data.isPlayer());
            assertTrue(data.hasEntityNBT());
        }

        @Test
        @DisplayName("Record equality works correctly")
        void recordEquality() {
            UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
            BioscanData a = new BioscanData(PLAYER_TYPE, "Steve", uuid, null, 100L);
            BioscanData b = new BioscanData(PLAYER_TYPE, "Steve", uuid, null, 100L);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Different records are not equal")
        void recordInequality() {
            BioscanData a = new BioscanData(PLAYER_TYPE, "Steve", UUID.randomUUID(), null, 100L);
            BioscanData b = new BioscanData(ZOMBIE_TYPE, "Zombie", null, null, 100L);
            assertNotEquals(a, b);
        }
    }
}
