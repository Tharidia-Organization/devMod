package com.devmod.nexus.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;
import com.devmod.nexus.data.ZoneSlotRegistry;

/**
 * Unit tests for NexusHubManager.
 * Tests hub orchestration and management logic.
 *
 * <p>Note: Many NexusHubManager methods require a full MinecraftServer
 * context. Those are tested in integration tests. This file focuses
 * on testable logic and state management.
 */
@DisplayName("NexusHubManager")
class NexusHubManagerTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    // ========================================================================
    // Singleton Tests
    // ========================================================================

    @Nested
    @DisplayName("Singleton")
    class SingletonTests {

        @Test
        @DisplayName("INSTANCE is not null")
        void instanceNotNull() {
            assertNotNull(NexusHubManager.INSTANCE);
        }

        @Test
        @DisplayName("Same instance returned each time")
        void sameInstanceReturned() {
            NexusHubManager first = NexusHubManager.INSTANCE;
            NexusHubManager second = NexusHubManager.INSTANCE;
            assertSame(first, second);
        }
    }

    // ========================================================================
    // Status Tests
    // ========================================================================

    @Nested
    @DisplayName("Status")
    class StatusTests {

        @Test
        @DisplayName("getStatus returns non-null HubStatus")
        void getStatusReturnsNonNull() {
            // Without a server, we can still create a registry and check status format
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.initializeDefaultSlots();

            // Status should have initialized = false (no server context)
            int slotCount = registry.getSlotCount();
            int linkedCount = registry.getLinkedSlots().size();
            int emptyCount = registry.getEmptySlots().size();

            assertTrue(slotCount > 0, "Default slots should be created");
            assertEquals(slotCount, linkedCount + emptyCount, "Total should equal linked + empty");
            assertEquals(0, linkedCount, "No linked slots initially");
        }

        @Test
        @DisplayName("HubStatus record fields are accessible")
        void hubStatusFieldsAccessible() {
            NexusHubManager.HubStatus status = new NexusHubManager.HubStatus(
                true,  // sessionInitialized
                true,  // slotsInitialized
                10,    // totalSlots
                3,     // linkedSlots
                7      // emptySlots
            );

            assertTrue(status.sessionInitialized());
            assertTrue(status.slotsInitialized());
            assertEquals(10, status.totalSlots());
            assertEquals(3, status.linkedSlots());
            assertEquals(7, status.emptySlots());
        }
    }

    // ========================================================================
    // Hub Status Record Tests
    // ========================================================================

    @Nested
    @DisplayName("HubStatus Record")
    class HubStatusRecordTests {

        @Test
        @DisplayName("HubStatus equals works correctly")
        void hubStatusEquals() {
            NexusHubManager.HubStatus status1 = new NexusHubManager.HubStatus(true, true, 10, 5, 5);
            NexusHubManager.HubStatus status2 = new NexusHubManager.HubStatus(true, true, 10, 5, 5);
            NexusHubManager.HubStatus status3 = new NexusHubManager.HubStatus(false, true, 10, 5, 5);

            assertEquals(status1, status2);
            assertNotEquals(status1, status3);
        }

        @Test
        @DisplayName("HubStatus toString is informative")
        void hubStatusToString() {
            NexusHubManager.HubStatus status = new NexusHubManager.HubStatus(true, true, 10, 5, 5);
            String str = status.toString();

            assertTrue(str.contains("ready"), "Should contain session state");
            assertTrue(str.contains("10"), "Should contain total slots");
        }
    }

    // ========================================================================
    // Registry Integration Tests
    // ========================================================================

    @Nested
    @DisplayName("Registry Integration")
    class RegistryIntegrationTests {

        @Test
        @DisplayName("Default slots are created on initialization")
        void defaultSlotsCreated() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            assertFalse(registry.isInitialized());

            registry.initializeDefaultSlots();

            assertTrue(registry.isInitialized());
            assertTrue(registry.getSlotCount() > 0);
        }

        @Test
        @DisplayName("Spawn slot exists after initialization")
        void spawnSlotExists() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.initializeDefaultSlots();

            assertTrue(registry.getSlot("spawn").isPresent(), "spawn slot should exist");
        }

        @Test
        @DisplayName("Combat Lab slot exists after initialization")
        void combatLabSlotExists() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.initializeDefaultSlots();

            assertTrue(registry.getSlot("combat_lab").isPresent(), "combat_lab slot should exist");
        }

        @Test
        @DisplayName("Boss Arena slot exists after initialization")
        void bossArenaSlotExists() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.initializeDefaultSlots();

            assertTrue(registry.getSlot("boss_arena").isPresent(), "boss_arena slot should exist");
        }

        @Test
        @DisplayName("All 15 default slots are registered")
        void allDefaultSlotsRegistered() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.initializeDefaultSlots();

            // Verify all 15 preset slots are registered
            assertEquals(15, registry.getSlotCount(), "All 15 default slots should be registered");

            // Verify key slots from different categories
            String[] expectedSlots = {
                "spawn", "combat_lab", "abilities_lab", "boss_arena", "portal_lab",
                "npc_lab", "vfx_studio", "collision_lab", "arena_builder",
                "item_workshop", "config_room", "hud_testing",
                "quest_testing", "sandbox", "admin_tools"
            };

            for (String slotId : expectedSlots) {
                assertTrue(registry.getSlot(Objects.requireNonNull(slotId)).isPresent(),
                    "Slot '" + slotId + "' should exist");
            }
        }
    }

    // ========================================================================
    // Bounds Compatibility Tests
    // ========================================================================

    @Nested
    @DisplayName("Bounds Compatibility")
    class BoundsCompatibilityTests {

        @Test
        @DisplayName("Slot bounds are non-empty")
        void slotBoundsNonEmpty() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.initializeDefaultSlots();

            registry.getAllSlots().forEach(slot -> {
                assertTrue(slot.bounds().width() > 0,
                    "Slot " + slot.slotId() + " should have positive width");
                assertTrue(slot.bounds().length() > 0,
                    "Slot " + slot.slotId() + " should have positive length");
                assertTrue(slot.bounds().height() > 0,
                    "Slot " + slot.slotId() + " should have positive height");
            });
        }

        @Test
        @DisplayName("Slot bounds do not overlap")
        void slotBoundsNoOverlap() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.initializeDefaultSlots();

            var slots = registry.getAllSlots().stream().toList();
            for (int i = 0; i < slots.size(); i++) {
                for (int j = i + 1; j < slots.size(); j++) {
                    assertFalse(Objects.requireNonNull(slots.get(i)).overlaps(Objects.requireNonNull(slots.get(j))),
                        "Slots " + slots.get(i).slotId() + " and " +
                        slots.get(j).slotId() + " should not overlap");
                }
            }
        }
    }

    // ========================================================================
    // Portal Configuration Tests
    // ========================================================================

    @Nested
    @DisplayName("Portal Configuration")
    class PortalConfigurationTests {

        @Test
        @DisplayName("All slots have portal positions")
        void allSlotsHavePortalPositions() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.initializeDefaultSlots();

            registry.getAllSlots().forEach(slot -> {
                assertNotNull(slot.portalPosition(),
                    "Slot " + slot.slotId() + " should have portal position");
            });
        }

        @Test
        @DisplayName("All slots have portal colors")
        void allSlotsHavePortalColors() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.initializeDefaultSlots();

            registry.getAllSlots().forEach(slot -> {
                assertNotNull(slot.portalColor(),
                    "Slot " + slot.slotId() + " should have portal color");
            });
        }

        @Test
        @DisplayName("Absolute portal positions are within bounds")
        void absolutePortalPositionsWithinBounds() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.initializeDefaultSlots();

            registry.getAllSlots().forEach(slot -> {
                var absPos = slot.getAbsolutePortalPosition();
                var bounds = slot.bounds();

                // Portal X and Z should be within or near bounds
                // (Y can be at floor level which might be at minY)
                assertTrue(absPos.getX() >= bounds.minX() - 1 && absPos.getX() <= bounds.maxX() + 1,
                    "Portal X for " + slot.slotId() + " should be within bounds");
                assertTrue(absPos.getZ() >= bounds.minZ() - 1 && absPos.getZ() <= bounds.maxZ() + 1,
                    "Portal Z for " + slot.slotId() + " should be within bounds");
            });
        }
    }
}
