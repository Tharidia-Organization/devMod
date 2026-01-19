package com.devmod.nexus.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.devmod.TestBootstrap;
import com.devmod.portal.PortalColor;
import com.devmod.zone.data.ZoneBounds;

/**
 * Unit tests for ZoneSlotRegistry.
 * Tests CRUD operations, linking, and query methods.
 *
 * <p>Note: These tests use a direct instance without MinecraftServer
 * to test the core logic. Integration tests with SavedData
 * are done separately.
 */
@DisplayName("ZoneSlotRegistry")
class ZoneSlotRegistryTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    // ========================================================================
    // Test Data Helpers
    // ========================================================================

    private static ZoneSlot createSlot(String slotId, int x, int z) {
        return new ZoneSlot(
            slotId,
            "Test " + slotId,
            new ZoneBounds(x, x + 64, 64, 80, z, z + 64),
            null,
            null,
            SlotType.EDITABLE,
            SlotPermissions.DEFAULT,
            new BlockPos(32, 65, 32),
            PortalColor.BLUE,
            null,
            0,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            null, // no warp node
            List.of(), // no NPC spawn points
            null // no hologram position
        );
    }

    private static ZoneSlot createTestAreaSlot(String slotId, int x, int z) {
        return new ZoneSlot(
            slotId,
            "Test Area " + slotId,
            new ZoneBounds(x, x + 64, 64, 80, z, z + 64),
            null,
            null,
            SlotType.TEST_AREA, // Removable
            SlotPermissions.DEFAULT,
            new BlockPos(32, 65, 32),
            PortalColor.GREEN,
            null,
            0,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            null, // no warp node
            List.of(), // no NPC spawn points
            null // no hologram position
        );
    }

    private static ZoneSlot createFixedSlot(String slotId, int x, int z) {
        return new ZoneSlot(
            slotId,
            "Fixed " + slotId,
            new ZoneBounds(x, x + 64, 64, 80, z, z + 64),
            null,
            null,
            SlotType.FIXED, // Not removable
            SlotPermissions.ADMIN_ONLY,
            new BlockPos(32, 65, 32),
            PortalColor.RED,
            null,
            10, // High priority
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            null, // no warp node
            List.of(), // no NPC spawn points
            null // no hologram position
        );
    }

    // ========================================================================
    // Registration Tests
    // ========================================================================

    @Nested
    @DisplayName("Register Slot")
    class RegisterSlotTests {

        @Test
        @DisplayName("Registers new slot successfully")
        void registersNewSlot() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            ZoneSlot slot = createSlot("test1", 0, 0);

            registry.registerSlot(Objects.requireNonNull(slot));

            assertTrue(registry.getSlot("test1").isPresent());
            assertEquals(slot, registry.getSlot("test1").get());
        }

        @Test
        @DisplayName("Throws on duplicate slot ID")
        void throwsOnDuplicateId() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            ZoneSlot slot1 = createSlot("duplicate", 0, 0);
            ZoneSlot slot2 = createSlot("duplicate", 200, 200);

            registry.registerSlot(Objects.requireNonNull(slot1));
            assertThrows(IllegalArgumentException.class, () -> registry.registerSlot(Objects.requireNonNull(slot2)));
        }

        @Test
        @DisplayName("Throws on overlapping bounds")
        void throwsOnOverlappingBounds() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            ZoneSlot slot1 = createSlot("slot1", 0, 0);
            // Overlapping slot (starts at 32,32 which is within slot1's 0-64 range)
            ZoneSlot slot2 = createSlot("slot2", 32, 32);

            registry.registerSlot(Objects.requireNonNull(slot1));
            assertThrows(IllegalArgumentException.class, () -> registry.registerSlot(Objects.requireNonNull(slot2)));
        }

        @Test
        @DisplayName("Allows non-overlapping slots")
        void allowsNonOverlappingSlots() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            ZoneSlot slot1 = createSlot("slot1", 0, 0);
            ZoneSlot slot2 = createSlot("slot2", 200, 200); // No overlap

            registry.registerSlot(Objects.requireNonNull(slot1));
            assertDoesNotThrow(() -> registry.registerSlot(Objects.requireNonNull(slot2)));

            assertEquals(2, registry.getAllSlots().size());
        }

        @Test
        @DisplayName("Increments modification version")
        void incrementsModificationVersion() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            long initialVersion = registry.getModificationVersion();

            registry.registerSlot(Objects.requireNonNull(createSlot("test", 0, 0)));

            assertTrue(registry.getModificationVersion() > initialVersion);
        }
    }

    // ========================================================================
    // Update Tests
    // ========================================================================

    @Nested
    @DisplayName("Update Slot")
    class UpdateSlotTests {

        @Test
        @DisplayName("Updates existing slot")
        void updatesExistingSlot() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            ZoneSlot original = createSlot("test", 0, 0);
            registry.registerSlot(Objects.requireNonNull(original));

            ZoneSlot updated = original.withLinkedArea(UUID.randomUUID());
            boolean result = registry.updateSlot(Objects.requireNonNull(updated));

            assertTrue(result);
            assertTrue(registry.getSlot("test").get().hasLinkedArea());
        }

        @Test
        @DisplayName("Returns false for non-existent slot")
        void returnsFalseForNonExistent() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            ZoneSlot slot = createSlot("nonexistent", 0, 0);

            boolean result = registry.updateSlot(Objects.requireNonNull(slot));

            assertFalse(result);
        }

        @Test
        @DisplayName("Updates reverse indices correctly")
        void updatesReverseIndices() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            ZoneSlot slot = createSlot("test", 0, 0);
            registry.registerSlot(Objects.requireNonNull(slot));

            UUID areaId = UUID.randomUUID();
            ZoneSlot linked = slot.withLinkedArea(Objects.requireNonNull(areaId));
            registry.updateSlot(Objects.requireNonNull(linked));

            Optional<ZoneSlot> byArea = registry.getSlotByArea(areaId);
            assertTrue(byArea.isPresent());
            assertEquals("test", byArea.get().slotId());
        }
    }

    // ========================================================================
    // Delete Tests
    // ========================================================================

    @Nested
    @DisplayName("Delete Slot")
    class DeleteSlotTests {

        @Test
        @DisplayName("Deletes TEST_AREA slot")
        void deletesTestAreaSlot() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            ZoneSlot slot = createTestAreaSlot("test", 0, 0);
            registry.registerSlot(Objects.requireNonNull(slot));

            boolean result = registry.deleteSlot("test");

            assertTrue(result);
            assertFalse(registry.getSlot("test").isPresent());
        }

        @Test
        @DisplayName("Refuses to delete FIXED slot")
        void refusesToDeleteFixedSlot() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            ZoneSlot slot = createFixedSlot("spawn", 0, 0);
            registry.registerSlot(Objects.requireNonNull(slot));

            boolean result = registry.deleteSlot("spawn");

            assertFalse(result);
            assertTrue(registry.getSlot("spawn").isPresent());
        }

        @Test
        @DisplayName("Refuses to delete EDITABLE slot")
        void refusesToDeleteEditableSlot() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            ZoneSlot slot = createSlot("editable", 0, 0);
            registry.registerSlot(Objects.requireNonNull(slot));

            boolean result = registry.deleteSlot("editable");

            assertFalse(result);
        }

        @Test
        @DisplayName("Returns false for non-existent slot")
        void returnsFalseForNonExistent() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();

            boolean result = registry.deleteSlot("nonexistent");

            assertFalse(result);
        }

        @Test
        @DisplayName("Cleans up reverse indices on delete")
        void cleansUpIndicesOnDelete() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            UUID areaId = UUID.randomUUID();

            ZoneSlot slot = new ZoneSlot(
                "test", "Test", new ZoneBounds(0, 64, 64, 80, 0, 64),
                areaId, null, SlotType.TEST_AREA, SlotPermissions.DEFAULT,
                BlockPos.ZERO, PortalColor.BLUE, null, 0,
                System.currentTimeMillis(), System.currentTimeMillis(),
                null, List.of(), null
            );
            registry.registerSlot(Objects.requireNonNull(slot));

            assertTrue(registry.getSlotByArea(Objects.requireNonNull(areaId)).isPresent());

            registry.deleteSlot("test");

            assertFalse(registry.getSlotByArea(areaId).isPresent());
        }
    }

    // ========================================================================
    // Query Tests
    // ========================================================================

    @Nested
    @DisplayName("Query Methods")
    class QueryMethodTests {

        @Test
        @DisplayName("getAllSlots returns all registered slots")
        void getAllSlotsReturnsAll() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.registerSlot(Objects.requireNonNull(createSlot("slot1", 0, 0)));
            registry.registerSlot(Objects.requireNonNull(createSlot("slot2", 200, 0)));
            registry.registerSlot(Objects.requireNonNull(createSlot("slot3", 0, 200)));

            Collection<ZoneSlot> slots = registry.getAllSlots();

            assertEquals(3, slots.size());
        }

        @Test
        @DisplayName("getSlot returns Optional.empty for unknown ID")
        void getSlotReturnsEmptyForUnknown() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();

            Optional<ZoneSlot> slot = registry.getSlot("unknown");

            assertTrue(slot.isEmpty());
        }

        @Test
        @DisplayName("getSlotByArea finds correct slot")
        void getSlotByAreaFindsSlot() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            UUID areaId = UUID.randomUUID();

            ZoneSlot slot = Objects.requireNonNull(createSlot("test", 0, 0)).withLinkedArea(Objects.requireNonNull(areaId));
            registry.registerSlot(Objects.requireNonNull(slot));

            Optional<ZoneSlot> found = registry.getSlotByArea(areaId);

            assertTrue(found.isPresent());
            assertEquals("test", found.get().slotId());
        }

        @Test
        @DisplayName("getSlotAt finds slot containing position")
        void getSlotAtFindsContainingSlot() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.registerSlot(Objects.requireNonNull(createSlot("test", 0, 0))); // Bounds: 0,64,0 to 64,80,64

            Optional<ZoneSlot> found = registry.getSlotAt(new BlockPos(32, 70, 32));

            assertTrue(found.isPresent());
            assertEquals("test", found.get().slotId());
        }

        @Test
        @DisplayName("getSlotAt returns empty for outside position")
        void getSlotAtReturnsEmptyForOutside() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.registerSlot(Objects.requireNonNull(createSlot("test", 0, 0)));

            Optional<ZoneSlot> found = registry.getSlotAt(new BlockPos(500, 70, 500));

            assertTrue(found.isEmpty());
        }

        @Test
        @DisplayName("getEmptySlots returns only unlinked slots")
        void getEmptySlotsReturnsUnlinked() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.registerSlot(Objects.requireNonNull(createSlot("empty1", 0, 0)));
            registry.registerSlot(Objects.requireNonNull(createSlot("empty2", 200, 0)));

            // Register a linked slot with an area already linked
            ZoneSlot linked = Objects.requireNonNull(createSlot("linked", 0, 200)).withLinkedArea(UUID.randomUUID());
            registry.registerSlot(Objects.requireNonNull(linked));

            // empty1 and empty2 should be empty, linked should have area
            List<ZoneSlot> emptySlots = registry.getEmptySlots();
            List<ZoneSlot> linkedSlots = registry.getLinkedSlots();

            assertEquals(2, emptySlots.size(), "Should have 2 empty slots");
            assertEquals(1, linkedSlots.size(), "Should have 1 linked slot");
        }
    }

    // ========================================================================
    // Initialization Tests
    // ========================================================================

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("New registry is not initialized")
        void newRegistryNotInitialized() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            assertFalse(registry.isInitialized());
        }

        @Test
        @DisplayName("initializeDefaults creates preset slots")
        void initializeDefaultsCreatesPresets() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            assertFalse(registry.isInitialized());
            assertEquals(0, registry.getAllSlots().size());

            registry.initializeDefaultSlots();

            assertTrue(registry.isInitialized());
            assertTrue(registry.getAllSlots().size() > 0);
        }

        @Test
        @DisplayName("initializeDefaults is idempotent")
        void initializeDefaultsIdempotent() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.initializeDefaultSlots();
            int firstCount = registry.getAllSlots().size();

            registry.initializeDefaultSlots();
            int secondCount = registry.getAllSlots().size();

            assertEquals(firstCount, secondCount);
        }
    }

    // ========================================================================
    // Slot Count Tests
    // ========================================================================

    @Nested
    @DisplayName("Slot Counts")
    class SlotCountTests {

        @Test
        @DisplayName("getSlotCount returns correct count")
        void getSlotCountReturnsCorrect() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            assertEquals(0, registry.getSlotCount());

            registry.registerSlot(Objects.requireNonNull(createSlot("slot1", 0, 0)));
            assertEquals(1, registry.getSlotCount());

            registry.registerSlot(Objects.requireNonNull(createSlot("slot2", 200, 0)));
            assertEquals(2, registry.getSlotCount());
        }

        @Test
        @DisplayName("getLinkedSlots returns correct count")
        void getLinkedSlotsReturnsCorrect() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.registerSlot(Objects.requireNonNull(createSlot("empty", 0, 0)));

            ZoneSlot linked = Objects.requireNonNull(createSlot("linked", 200, 0)).withLinkedArea(UUID.randomUUID());
            registry.registerSlot(Objects.requireNonNull(linked));

            assertEquals(1, registry.getLinkedSlots().size());
        }

        @Test
        @DisplayName("getEmptySlots returns correct count")
        void getEmptySlotsReturnsCorrect() {
            ZoneSlotRegistry registry = new ZoneSlotRegistry();
            registry.registerSlot(Objects.requireNonNull(createSlot("empty1", 0, 0)));
            registry.registerSlot(Objects.requireNonNull(createSlot("empty2", 200, 0)));

            ZoneSlot linked = Objects.requireNonNull(createSlot("linked", 0, 200)).withLinkedArea(UUID.randomUUID());
            registry.registerSlot(Objects.requireNonNull(linked));

            assertEquals(2, registry.getEmptySlots().size());
        }
    }
}
