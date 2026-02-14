package com.devmod.transport.executor;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.transport.executor.CooldownManager.CooldownEntry;
import com.devmod.transport.executor.CooldownManager.CooldownType;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CooldownManager")
class CooldownManagerTest {

    private CooldownManager manager;
    private final UUID entityId = UUID.randomUUID();
    private final UUID nodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        manager = new CooldownManager();
    }

    // =========================================================================
    // CooldownEntry Record Tests
    // =========================================================================

    @Nested
    @DisplayName("CooldownEntry")
    class CooldownEntryTests {

        @Test
        @DisplayName("isExpired returns false before expiration tick")
        void isExpiredBeforeExpiration() {
            CooldownEntry entry = new CooldownEntry(nodeId, 100L, CooldownType.ARRIVAL);
            assertFalse(entry.isExpired(99L));
        }

        @Test
        @DisplayName("isExpired returns true at exact expiration tick")
        void isExpiredAtExactTick() {
            CooldownEntry entry = new CooldownEntry(nodeId, 100L, CooldownType.ARRIVAL);
            assertTrue(entry.isExpired(100L));
        }

        @Test
        @DisplayName("isExpired returns true after expiration tick")
        void isExpiredAfterExpiration() {
            CooldownEntry entry = new CooldownEntry(nodeId, 100L, CooldownType.ARRIVAL);
            assertTrue(entry.isExpired(150L));
        }

        @Test
        @DisplayName("getRemainingTicks returns correct value before expiration")
        void getRemainingTicksBeforeExpiration() {
            CooldownEntry entry = new CooldownEntry(nodeId, 100L, CooldownType.REUSE);
            assertEquals(30L, entry.getRemainingTicks(70L));
        }

        @Test
        @DisplayName("getRemainingTicks returns 0 after expiration")
        void getRemainingTicksAfterExpiration() {
            CooldownEntry entry = new CooldownEntry(nodeId, 100L, CooldownType.REUSE);
            assertEquals(0L, entry.getRemainingTicks(150L));
        }

        @Test
        @DisplayName("getRemainingTicks returns 0 at exact expiration")
        void getRemainingTicksAtExactExpiration() {
            CooldownEntry entry = new CooldownEntry(nodeId, 100L, CooldownType.REUSE);
            assertEquals(0L, entry.getRemainingTicks(100L));
        }
    }

    // =========================================================================
    // CooldownType Default Ticks
    // =========================================================================

    @Nested
    @DisplayName("CooldownType defaults")
    class CooldownTypeDefaults {

        @Test
        @DisplayName("ARRIVAL default is 60 ticks")
        void arrivalDefault() {
            assertEquals(60, CooldownType.ARRIVAL.getDefaultTicks());
        }

        @Test
        @DisplayName("REUSE default is 100 ticks")
        void reuseDefault() {
            assertEquals(100, CooldownType.REUSE.getDefaultTicks());
        }

        @Test
        @DisplayName("GLOBAL default is 20 ticks")
        void globalDefault() {
            assertEquals(20, CooldownType.GLOBAL.getDefaultTicks());
        }
    }

    // =========================================================================
    // Set / Check Cooldowns
    // =========================================================================

    @Nested
    @DisplayName("setCooldown and isOnCooldown")
    class SetAndCheck {

        @Test
        @DisplayName("entity is on cooldown immediately after set")
        void onCooldownAfterSet() {
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            assertTrue(manager.isOnCooldown(entityId, nodeId, CooldownType.ARRIVAL, 30L));
        }

        @Test
        @DisplayName("entity is not on cooldown after expiration")
        void notOnCooldownAfterExpiration() {
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            assertFalse(manager.isOnCooldown(entityId, nodeId, CooldownType.ARRIVAL, 60L));
        }

        @Test
        @DisplayName("isOnCooldown returns false for unknown entity")
        void notOnCooldownForUnknown() {
            assertFalse(manager.isOnCooldown(UUID.randomUUID(), nodeId, CooldownType.ARRIVAL, 0L));
        }

        @Test
        @DisplayName("setCooldown with default duration uses type defaults")
        void defaultDurationUsesTypeDefaults() {
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 100L);
            // Default ARRIVAL = 60 ticks, so expires at tick 160
            assertTrue(manager.isOnCooldown(entityId, nodeId, CooldownType.ARRIVAL, 159L));
            assertFalse(manager.isOnCooldown(entityId, nodeId, CooldownType.ARRIVAL, 160L));
        }

        @Test
        @DisplayName("different cooldown types are independent")
        void typesAreIndependent() {
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            assertTrue(manager.isOnCooldown(entityId, nodeId, CooldownType.ARRIVAL, 30L));
            assertFalse(manager.isOnCooldown(entityId, nodeId, CooldownType.REUSE, 30L));
        }

        @Test
        @DisplayName("different nodes are independent")
        void nodesAreIndependent() {
            UUID otherNode = UUID.randomUUID();
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            assertTrue(manager.isOnCooldown(entityId, nodeId, CooldownType.ARRIVAL, 30L));
            assertFalse(manager.isOnCooldown(entityId, otherNode, CooldownType.ARRIVAL, 30L));
        }

        @Test
        @DisplayName("different entities are independent")
        void entitiesAreIndependent() {
            UUID otherEntity = UUID.randomUUID();
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            assertTrue(manager.isOnCooldown(entityId, nodeId, CooldownType.ARRIVAL, 30L));
            assertFalse(manager.isOnCooldown(otherEntity, nodeId, CooldownType.ARRIVAL, 30L));
        }
    }

    // =========================================================================
    // Global Cooldowns
    // =========================================================================

    @Nested
    @DisplayName("Global cooldowns")
    class GlobalCooldowns {

        @Test
        @DisplayName("hasGlobalCooldown detects global cooldown")
        void hasGlobalCooldown() {
            manager.setCooldown(entityId, null, CooldownType.GLOBAL, 0L, 20);
            assertTrue(manager.hasGlobalCooldown(entityId, 10L));
        }

        @Test
        @DisplayName("hasGlobalCooldown returns false when expired")
        void noGlobalCooldownWhenExpired() {
            manager.setCooldown(entityId, null, CooldownType.GLOBAL, 0L, 20);
            assertFalse(manager.hasGlobalCooldown(entityId, 20L));
        }

        @Test
        @DisplayName("hasGlobalCooldown returns false for other entity")
        void noGlobalCooldownForOtherEntity() {
            manager.setCooldown(entityId, null, CooldownType.GLOBAL, 0L, 20);
            assertFalse(manager.hasGlobalCooldown(UUID.randomUUID(), 10L));
        }
    }

    // =========================================================================
    // hasAnyCooldown
    // =========================================================================

    @Nested
    @DisplayName("hasAnyCooldown")
    class HasAnyCooldown {

        @Test
        @DisplayName("returns true when any cooldown type is active")
        void trueWhenAnyCooldown() {
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            assertTrue(manager.hasAnyCooldown(entityId, nodeId, 30L));
        }

        @Test
        @DisplayName("returns false when all cooldowns expired")
        void falseWhenAllExpired() {
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            assertFalse(manager.hasAnyCooldown(entityId, nodeId, 100L));
        }

        @Test
        @DisplayName("returns true when reuse but not arrival is active")
        void detectsReuseCooldown() {
            manager.setCooldown(entityId, nodeId, CooldownType.REUSE, 0L, 100);
            assertTrue(manager.hasAnyCooldown(entityId, nodeId, 50L));
        }
    }

    // =========================================================================
    // getCooldown
    // =========================================================================

    @Nested
    @DisplayName("getCooldown")
    class GetCooldown {

        @Test
        @DisplayName("returns entry when present")
        void returnsEntry() {
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            var entry = manager.getCooldown(entityId, nodeId, CooldownType.ARRIVAL);
            assertTrue(entry.isPresent());
            assertEquals(nodeId, entry.get().nodeId());
            assertEquals(60L, entry.get().expirationTick());
            assertEquals(CooldownType.ARRIVAL, entry.get().type());
        }

        @Test
        @DisplayName("returns empty when absent")
        void returnsEmpty() {
            assertTrue(manager.getCooldown(entityId, nodeId, CooldownType.ARRIVAL).isEmpty());
        }
    }

    // =========================================================================
    // Clear Operations
    // =========================================================================

    @Nested
    @DisplayName("Clear operations")
    class ClearOps {

        @Test
        @DisplayName("clearCooldowns removes all for entity")
        void clearCooldownsRemovesAll() {
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            manager.setCooldown(entityId, nodeId, CooldownType.REUSE, 0L, 100);
            manager.setCooldown(entityId, null, CooldownType.GLOBAL, 0L, 20);

            manager.clearCooldowns(entityId);

            assertFalse(manager.isOnCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L));
            assertFalse(manager.isOnCooldown(entityId, nodeId, CooldownType.REUSE, 0L));
            assertFalse(manager.hasGlobalCooldown(entityId, 0L));
        }

        @Test
        @DisplayName("clearCooldowns does not affect other entities")
        void clearCooldownsPreservesOther() {
            UUID other = UUID.randomUUID();
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            manager.setCooldown(other, nodeId, CooldownType.ARRIVAL, 0L, 60);

            manager.clearCooldowns(entityId);

            assertFalse(manager.isOnCooldown(entityId, nodeId, CooldownType.ARRIVAL, 30L));
            assertTrue(manager.isOnCooldown(other, nodeId, CooldownType.ARRIVAL, 30L));
        }

        @Test
        @DisplayName("clearCooldown removes specific cooldown")
        void clearSpecificCooldown() {
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            manager.setCooldown(entityId, nodeId, CooldownType.REUSE, 0L, 100);

            manager.clearCooldown(entityId, nodeId, CooldownType.ARRIVAL);

            assertFalse(manager.isOnCooldown(entityId, nodeId, CooldownType.ARRIVAL, 30L));
            assertTrue(manager.isOnCooldown(entityId, nodeId, CooldownType.REUSE, 30L));
        }

        @Test
        @DisplayName("clear removes everything")
        void clearRemovesEverything() {
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            manager.setCooldown(UUID.randomUUID(), nodeId, CooldownType.REUSE, 0L, 100);

            manager.clear();

            assertEquals(0, manager.size());
        }
    }

    // =========================================================================
    // Tick Processing
    // =========================================================================

    @Nested
    @DisplayName("tick")
    class TickProcessing {

        @Test
        @DisplayName("tick removes expired cooldowns")
        void removesExpired() {
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            assertEquals(1, manager.size());

            manager.tick(60L);
            assertEquals(0, manager.size());
        }

        @Test
        @DisplayName("tick preserves active cooldowns")
        void preservesActive() {
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
            manager.tick(30L);
            assertEquals(1, manager.size());
            assertTrue(manager.isOnCooldown(entityId, nodeId, CooldownType.ARRIVAL, 30L));
        }

        @Test
        @DisplayName("tick handles mixed expired and active")
        void handlesMixed() {
            UUID entity2 = UUID.randomUUID();
            manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 30);
            manager.setCooldown(entity2, nodeId, CooldownType.ARRIVAL, 0L, 90);

            manager.tick(50L);

            assertEquals(1, manager.size());
            assertFalse(manager.isOnCooldown(entityId, nodeId, CooldownType.ARRIVAL, 50L));
            assertTrue(manager.isOnCooldown(entity2, nodeId, CooldownType.ARRIVAL, 50L));
        }
    }

    // =========================================================================
    // Size
    // =========================================================================

    @Test
    @DisplayName("size tracks active cooldowns correctly")
    void sizeTracking() {
        assertEquals(0, manager.size());

        manager.setCooldown(entityId, nodeId, CooldownType.ARRIVAL, 0L, 60);
        assertEquals(1, manager.size());

        manager.setCooldown(entityId, nodeId, CooldownType.REUSE, 0L, 100);
        assertEquals(2, manager.size());
    }
}
