package com.devmod.clone;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import com.devmod.clone.block.entity.NeurocellAccess;
import com.devmod.clone.data.BioscanData;

/**
 * Tests the NeurocellAccess interface contract using stub implementations.
 * Validates that the interface methods work correctly in isolation.
 */
class NeurocellAccessContractTest {

    private static final ResourceLocation ZOMBIE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "zombie");

    /**
     * Stub implementation for testing the interface contract.
     */
    static class StubNeurocell implements NeurocellAccess {
        private boolean dataReady;
        private boolean emptyBioscanner;
        private BioscanData storedData;

        StubNeurocell(boolean dataReady, boolean emptyBioscanner, BioscanData data) {
            this.dataReady = dataReady;
            this.emptyBioscanner = emptyBioscanner;
            this.storedData = data;
        }

        @Override
        public boolean isDataReady() {
            return dataReady;
        }

        @Override
        public boolean hasEmptyBioscanner() {
            return emptyBioscanner;
        }

        @Override
        public BioscanData consumeData() {
            if (!dataReady || storedData == null) return null;
            BioscanData data = storedData;
            storedData = null;
            dataReady = false;
            return data;
        }

        @Override
        public void fillBioscannerFromImprinter(BioscanData data) {
            this.storedData = data;
            this.dataReady = true;
            this.emptyBioscanner = false;
        }
    }

    @Nested
    @DisplayName("isDataReady / consumeData")
    class DataReadyTests {

        @Test
        @DisplayName("Data-ready neurocell returns data on consume")
        void consumeReturnData() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("Health", 20);
            BioscanData data = new BioscanData(ZOMBIE, "Zombie", null, nbt, 100L);
            StubNeurocell cell = new StubNeurocell(true, false, data);

            assertTrue(cell.isDataReady());
            BioscanData consumed = cell.consumeData();
            assertNotNull(consumed);
            assertEquals(ZOMBIE, consumed.entityTypeId());
        }

        @Test
        @DisplayName("consumeData clears the data")
        void consumeClearsData() {
            BioscanData data = new BioscanData(ZOMBIE, "Z", null, null, 0);
            StubNeurocell cell = new StubNeurocell(true, false, data);

            assertNotNull(cell.consumeData());
            assertFalse(cell.isDataReady());
            assertNull(cell.consumeData());
        }

        @Test
        @DisplayName("Not-ready neurocell returns null on consume")
        void notReadyReturnsNull() {
            StubNeurocell cell = new StubNeurocell(false, true, null);
            assertFalse(cell.isDataReady());
            assertNull(cell.consumeData());
        }
    }

    @Nested
    @DisplayName("hasEmptyBioscanner / fillBioscannerFromImprinter")
    class BioscannerTests {

        @Test
        @DisplayName("Empty bioscanner can receive data")
        void fillEmptyBioscanner() {
            StubNeurocell cell = new StubNeurocell(false, true, null);
            assertTrue(cell.hasEmptyBioscanner());

            BioscanData data = new BioscanData(ZOMBIE, "Zombie", null, null, 42L);
            cell.fillBioscannerFromImprinter(data);

            assertFalse(cell.hasEmptyBioscanner());
            assertTrue(cell.isDataReady());
        }

        @Test
        @DisplayName("After filling, consumeData returns the same data")
        void fillAndConsume() {
            StubNeurocell cell = new StubNeurocell(false, true, null);
            BioscanData data = new BioscanData(ZOMBIE, "TestMob", null, null, 999L);

            cell.fillBioscannerFromImprinter(data);
            BioscanData consumed = cell.consumeData();

            assertNotNull(consumed);
            assertEquals("TestMob", consumed.entityName());
            assertEquals(999L, consumed.scanTime());
        }
    }

    @Nested
    @DisplayName("Player bioscan data flow")
    class PlayerDataFlowTests {

        @Test
        @DisplayName("Player data roundtrip through neurocell")
        void playerRoundtrip() {
            UUID uuid = UUID.randomUUID();
            ResourceLocation playerType = ResourceLocation.fromNamespaceAndPath("minecraft", "player");
            BioscanData playerData = new BioscanData(playerType, "Alex", uuid, null, 50L);

            StubNeurocell cell = new StubNeurocell(false, true, null);
            cell.fillBioscannerFromImprinter(playerData);

            BioscanData consumed = cell.consumeData();
            assertNotNull(consumed);
            assertTrue(consumed.isPlayer());
            assertEquals(uuid, consumed.playerUUID());
            assertEquals("Alex", consumed.entityName());
        }
    }
}
