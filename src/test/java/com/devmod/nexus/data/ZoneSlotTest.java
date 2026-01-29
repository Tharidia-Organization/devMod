package com.devmod.nexus.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.devmod.TestBootstrap;
import com.devmod.portal.PortalColor;
import com.devmod.zone.data.ZoneBounds;

@DisplayName("ZoneSlot")
class ZoneSlotTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    // ========================================================================
    // Test Data Helpers
    // ========================================================================

    private static ZoneSlot createValidSlot() {
        return createValidSlot("test_slot");
    }

    private static ZoneSlot createValidSlot(String slotId) {
        return new ZoneSlot(
            slotId,
            "Test Slot",
            new ZoneBounds(0, 64, 64, 80, 0, 64),
            null, // no linked area
            null, // no linked zone
            SlotType.EDITABLE,
            SlotPermissions.DEFAULT,
            new BlockPos(32, 65, 32),
            PortalColor.BLUE,
            null, // no template
            0,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            null, // no warp node
            List.of(), // no NPC spawn points
            null // no hologram position
        );
    }

    private static ZoneSlot createLinkedSlot() {
        return new ZoneSlot(
            "linked_slot",
            "Linked Slot",
            new ZoneBounds(100, 164, 64, 80, 100, 164),
            UUID.randomUUID(), // linked area
            UUID.randomUUID(), // linked zone
            SlotType.EDITABLE,
            SlotPermissions.DEFAULT,
            new BlockPos(32, 65, 32),
            PortalColor.GREEN,
            "devmod:test_template",
            1,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            "warp_node_1", // warp node
            List.of(new BlockPos(5, 1, 5), new BlockPos(10, 1, 10)), // NPC spawn points
            new BlockPos(32, 70, 32) // hologram position
        );
    }

    // ========================================================================
    // Validation Tests
    // ========================================================================

    @Nested
    @DisplayName("Validation")
    @SuppressWarnings("NullAway")
    class ValidationTests {

        @Test
        @DisplayName("Valid slot creation succeeds")
        void validSlotCreation() {
            ZoneSlot slot = createValidSlot();
            assertNotNull(slot);
            assertEquals("test_slot", slot.slotId());
            assertEquals("Test Slot", slot.displayName());
        }

        @Test
        @DisplayName("Null slotId throws NullPointerException")
        void nullSlotIdThrows() {
            assertThrows(NullPointerException.class, () -> new ZoneSlot(
                null, "Test", new ZoneBounds(0, 1, 0, 1, 0, 1),
                null, null, SlotType.EDITABLE, SlotPermissions.DEFAULT,
                BlockPos.ZERO, PortalColor.BLUE, null, 0, 0, 0,
                null, List.of(), null
            ));
        }

        @Test
        @DisplayName("Blank slotId throws IllegalArgumentException")
        void blankSlotIdThrows() {
            assertThrows(IllegalArgumentException.class, () -> new ZoneSlot(
                "  ", "Test", new ZoneBounds(0, 1, 0, 1, 0, 1),
                null, null, SlotType.EDITABLE, SlotPermissions.DEFAULT,
                BlockPos.ZERO, PortalColor.BLUE, null, 0, 0, 0,
                null, List.of(), null
            ));
        }

        @Test
        @DisplayName("Negative priority throws IllegalArgumentException")
        void negativePriorityThrows() {
            assertThrows(IllegalArgumentException.class, () -> new ZoneSlot(
                "test", "Test", new ZoneBounds(0, 1, 0, 1, 0, 1),
                null, null, SlotType.EDITABLE, SlotPermissions.DEFAULT,
                BlockPos.ZERO, PortalColor.BLUE, null, -1, 0, 0,
                null, List.of(), null
            ));
        }

        @Test
        @DisplayName("Null bounds throws NullPointerException")
        void nullBoundsThrows() {
            assertThrows(NullPointerException.class, () -> new ZoneSlot(
                "test", "Test", null,
                null, null, SlotType.EDITABLE, SlotPermissions.DEFAULT,
                BlockPos.ZERO, PortalColor.BLUE, null, 0, 0, 0,
                null, List.of(), null
            ));
        }

        @Test
        @DisplayName("Null type throws NullPointerException")
        void nullTypeThrows() {
            assertThrows(NullPointerException.class, () -> new ZoneSlot(
                "test", "Test", new ZoneBounds(0, 1, 0, 1, 0, 1),
                null, null, null, SlotPermissions.DEFAULT,
                BlockPos.ZERO, PortalColor.BLUE, null, 0, 0, 0,
                null, List.of(), null
            ));
        }

        @Test
        @DisplayName("Null npcSpawnPoints is converted to empty list")
        void nullNpcSpawnPointsBecomesEmptyList() {
            ZoneSlot slot = new ZoneSlot(
                "test", "Test", new ZoneBounds(0, 1, 0, 1, 0, 1),
                null, null, SlotType.EDITABLE, SlotPermissions.DEFAULT,
                BlockPos.ZERO, PortalColor.BLUE, null, 0, 0, 0,
                null, null, null
            );
            assertNotNull(slot.npcSpawnPoints());
            assertTrue(slot.npcSpawnPoints().isEmpty());
        }
    }

    // ========================================================================
    // Query Method Tests
    // ========================================================================

    @Nested
    @DisplayName("Query Methods")
    class QueryMethodTests {

        @Test
        @DisplayName("hasLinkedArea returns false for empty slot")
        void hasLinkedAreaFalseForEmpty() {
            ZoneSlot slot = createValidSlot();
            assertFalse(slot.hasLinkedArea());
        }

        @Test
        @DisplayName("hasLinkedArea returns true for linked slot")
        void hasLinkedAreaTrueForLinked() {
            ZoneSlot slot = createLinkedSlot();
            assertTrue(slot.hasLinkedArea());
        }

        @Test
        @DisplayName("isEmpty returns true for unlinked slot")
        void isEmptyTrueForUnlinked() {
            ZoneSlot slot = createValidSlot();
            assertTrue(slot.isEmpty());
        }

        @Test
        @DisplayName("isEmpty returns false for linked slot")
        void isEmptyFalseForLinked() {
            ZoneSlot slot = createLinkedSlot();
            assertFalse(slot.isEmpty());
        }

        @Test
        @DisplayName("isPlayerEditable reflects SlotType")
        void isPlayerEditableReflectsType() {
            ZoneSlot editable = createValidSlot();
            assertTrue(editable.isPlayerEditable());

            ZoneSlot fixed = new ZoneSlot(
                "fixed", "Fixed", new ZoneBounds(0, 1, 0, 1, 0, 1),
                null, null, SlotType.FIXED, SlotPermissions.DEFAULT,
                BlockPos.ZERO, PortalColor.BLUE, null, 0, 0, 0,
                null, List.of(), null
            );
            assertFalse(fixed.isPlayerEditable());
        }

        @Test
        @DisplayName("contains checks position within bounds")
        void containsChecksPosition() {
            ZoneSlot slot = createValidSlot();
            assertTrue(slot.contains(new BlockPos(32, 70, 32)));
            assertFalse(slot.contains(new BlockPos(200, 70, 200)));
        }

        @Test
        @DisplayName("getAbsolutePortalPosition calculates correctly")
        void absolutePortalPositionCalculated() {
            ZoneSlot slot = new ZoneSlot(
                "test", "Test", new ZoneBounds(100, 164, 64, 80, 100, 164),
                null, null, SlotType.EDITABLE, SlotPermissions.DEFAULT,
                new BlockPos(10, 1, 10), PortalColor.BLUE, null, 0, 0, 0,
                null, List.of(), null
            );

            BlockPos absolute = slot.getAbsolutePortalPosition();
            assertEquals(110, absolute.getX()); // 100 + 10
            assertEquals(65, absolute.getY());  // 64 + 1
            assertEquals(110, absolute.getZ()); // 100 + 10
        }

        @Test
        @DisplayName("overlaps detects overlapping slots")
        void overlapsDetectsOverlap() {
            ZoneSlot slot1 = new ZoneSlot(
                "slot1", "Slot 1", new ZoneBounds(0, 64, 64, 80, 0, 64),
                null, null, SlotType.EDITABLE, SlotPermissions.DEFAULT,
                BlockPos.ZERO, PortalColor.BLUE, null, 0, 0, 0,
                null, List.of(), null
            );
            ZoneSlot slot2 = new ZoneSlot(
                "slot2", "Slot 2", new ZoneBounds(32, 96, 64, 80, 32, 96),
                null, null, SlotType.EDITABLE, SlotPermissions.DEFAULT,
                BlockPos.ZERO, PortalColor.GREEN, null, 0, 0, 0,
                null, List.of(), null
            );
            ZoneSlot slot3 = new ZoneSlot(
                "slot3", "Slot 3", new ZoneBounds(200, 264, 64, 80, 200, 264),
                null, null, SlotType.EDITABLE, SlotPermissions.DEFAULT,
                BlockPos.ZERO, PortalColor.RED, null, 0, 0, 0,
                null, List.of(), null
            );

            assertTrue(slot1.overlaps(slot2));  // Overlapping
            assertFalse(slot1.overlaps(slot3)); // Separate
        }

        @Test
        @DisplayName("hasWarpNode returns correct value")
        void hasWarpNodeReturnsCorrect() {
            ZoneSlot slotWithoutWarp = createValidSlot();
            assertFalse(slotWithoutWarp.hasWarpNode());

            ZoneSlot slotWithWarp = createLinkedSlot();
            assertTrue(slotWithWarp.hasWarpNode());
        }

        @Test
        @DisplayName("hasNpcSpawnPoints returns correct value")
        void hasNpcSpawnPointsReturnsCorrect() {
            ZoneSlot slotWithoutNpcs = createValidSlot();
            assertFalse(slotWithoutNpcs.hasNpcSpawnPoints());

            ZoneSlot slotWithNpcs = createLinkedSlot();
            assertTrue(slotWithNpcs.hasNpcSpawnPoints());
            assertEquals(2, slotWithNpcs.npcSpawnPoints().size());
        }

        @Test
        @DisplayName("hasHologramPosition returns correct value")
        void hasHologramPositionReturnsCorrect() {
            ZoneSlot slotWithoutHolo = createValidSlot();
            assertFalse(slotWithoutHolo.hasHologramPosition());

            ZoneSlot slotWithHolo = createLinkedSlot();
            assertTrue(slotWithHolo.hasHologramPosition());
        }

        @Test
        @DisplayName("getAbsoluteNpcSpawnPositions calculates correctly")
        void absoluteNpcSpawnPositionsCalculated() {
            ZoneSlot slot = createLinkedSlot();
            List<BlockPos> absolutePositions = slot.getAbsoluteNpcSpawnPositions();

            assertEquals(2, absolutePositions.size());
            // Slot bounds start at (100, 64, 100), NPC relative positions are (5, 1, 5) and (10, 1, 10)
            assertEquals(new BlockPos(105, 65, 105), absolutePositions.get(0));
            assertEquals(new BlockPos(110, 65, 110), absolutePositions.get(1));
        }

        @Test
        @DisplayName("getAbsoluteHologramPosition calculates correctly")
        void absoluteHologramPositionCalculated() {
            ZoneSlot slot = createLinkedSlot();
            BlockPos absolutePos = slot.getAbsoluteHologramPosition();

            assertNotNull(absolutePos);
            // Slot bounds start at (100, 64, 100), hologram relative is (32, 70, 32)
            assertEquals(new BlockPos(132, 134, 132), absolutePos);
        }

        @Test
        @DisplayName("getAbsoluteHologramPosition returns null when no hologram")
        void absoluteHologramPositionNullWhenNone() {
            ZoneSlot slot = createValidSlot();
            assertNull(slot.getAbsoluteHologramPosition());
        }
    }

    // ========================================================================
    // Builder Method Tests
    // ========================================================================

    @Nested
    @DisplayName("Builder Methods")
    class BuilderMethodTests {

        @Test
        @DisplayName("withLinkedArea creates new slot with area")
        void withLinkedAreaCreatesNew() {
            ZoneSlot original = createValidSlot();
            UUID areaId = UUID.randomUUID();

            ZoneSlot linked = original.withLinkedArea(areaId);

            assertNotSame(original, linked);
            assertNull(original.linkedAreaId());
            assertEquals(areaId, linked.linkedAreaId());
            assertEquals(original.slotId(), linked.slotId());
        }

        @Test
        @DisplayName("withLinkedZone creates new slot with zone")
        void withLinkedZoneCreatesNew() {
            ZoneSlot original = createValidSlot();
            UUID zoneId = UUID.randomUUID();

            ZoneSlot linked = original.withLinkedZone(zoneId);

            assertNotSame(original, linked);
            assertNull(original.linkedZoneId());
            assertEquals(zoneId, linked.linkedZoneId());
        }

        @Test
        @DisplayName("withLinkedArea(null) clears area link")
        void withLinkedAreaNullClears() {
            ZoneSlot linked = createLinkedSlot();
            assertNotNull(linked.linkedAreaId());

            ZoneSlot cleared = linked.withLinkedArea(null);
            assertNull(cleared.linkedAreaId());
        }

        @Test
        @DisplayName("Builder methods update timestamp")
        void builderMethodsUpdateTimestamp() throws InterruptedException {
            ZoneSlot original = createValidSlot();
            long originalUpdated = original.updatedAt();

            Thread.sleep(10); // Ensure time passes

            ZoneSlot linked = original.withLinkedArea(UUID.randomUUID());
            assertTrue(linked.updatedAt() > originalUpdated);
        }

        @Test
        @DisplayName("withWarpNodeId creates new slot with warp node")
        void withWarpNodeIdCreatesNew() {
            ZoneSlot original = createValidSlot();
            assertNull(original.warpNodeId());

            ZoneSlot withWarp = original.withWarpNodeId("my_warp_node");

            assertNotSame(original, withWarp);
            assertNull(original.warpNodeId());
            assertEquals("my_warp_node", withWarp.warpNodeId());
            assertEquals(original.slotId(), withWarp.slotId());
        }

        @Test
        @DisplayName("withNpcSpawnPoints creates new slot with spawn points")
        void withNpcSpawnPointsCreatesNew() {
            ZoneSlot original = createValidSlot();
            assertTrue(original.npcSpawnPoints().isEmpty());

            List<BlockPos> spawnPoints = List.of(new BlockPos(1, 1, 1), new BlockPos(2, 2, 2));
            ZoneSlot withNpcs = original.withNpcSpawnPoints(Objects.requireNonNull(spawnPoints));

            assertNotSame(original, withNpcs);
            assertTrue(original.npcSpawnPoints().isEmpty());
            assertEquals(2, withNpcs.npcSpawnPoints().size());
        }

        @Test
        @DisplayName("withHologramPosition creates new slot with hologram")
        void withHologramPositionCreatesNew() {
            ZoneSlot original = createValidSlot();
            assertNull(original.hologramPosition());

            BlockPos holoPos = new BlockPos(10, 20, 30);
            ZoneSlot withHolo = original.withHologramPosition(holoPos);

            assertNotSame(original, withHolo);
            assertNull(original.hologramPosition());
            assertEquals(holoPos, withHolo.hologramPosition());
        }

        @Test
        @DisplayName("withWarpNodeId(null) clears warp node")
        void withWarpNodeIdNullClears() {
            ZoneSlot withWarp = createLinkedSlot();
            assertNotNull(withWarp.warpNodeId());

            ZoneSlot cleared = withWarp.withWarpNodeId(null);
            assertNull(cleared.warpNodeId());
        }
    }

    // ========================================================================
    // SlotType Tests
    // ========================================================================

    @Nested
    @DisplayName("SlotType")
    class SlotTypeTests {

        @Test
        @DisplayName("FIXED is not player editable or removable")
        void fixedNotEditableOrRemovable() {
            assertFalse(SlotType.FIXED.isPlayerEditable());
            assertFalse(SlotType.FIXED.isRemovable());
        }

        @Test
        @DisplayName("EDITABLE is player editable but not removable")
        void editableIsEditableNotRemovable() {
            assertTrue(SlotType.EDITABLE.isPlayerEditable());
            assertFalse(SlotType.EDITABLE.isRemovable());
        }

        @Test
        @DisplayName("TEST_AREA is removable but not player editable")
        void testAreaIsRemovableNotPlayerEditable() {
            // TEST_AREA is admin-editable only, not player-editable
            assertFalse(SlotType.TEST_AREA.isPlayerEditable());
            assertTrue(SlotType.TEST_AREA.isAdminEditable());
            assertTrue(SlotType.TEST_AREA.isRemovable());
        }

        @Test
        @DisplayName("RESTRICTED is not player editable or removable")
        void restrictedNotEditableOrRemovable() {
            assertFalse(SlotType.RESTRICTED.isPlayerEditable());
            assertFalse(SlotType.RESTRICTED.isRemovable());
        }
    }

    // ========================================================================
    // SlotPermissions Tests
    // ========================================================================

    @Nested
    @DisplayName("SlotPermissions")
    class SlotPermissionsTests {

        @Test
        @DisplayName("DEFAULT allows portals but not editing by default")
        void defaultPermissions() {
            SlotPermissions perms = SlotPermissions.DEFAULT;
            assertTrue(perms.canUsePortals());
            assertFalse(perms.canEdit());
            assertFalse(perms.canPlace());
            assertFalse(perms.canBreak());
            assertEquals(0, perms.minPermissionLevel());
        }

        @Test
        @DisplayName("ADMIN_ONLY allows all operations with OP level 2")
        void adminOnlyPermissions() {
            SlotPermissions perms = SlotPermissions.ADMIN_ONLY;
            assertTrue(perms.canUsePortals());
            assertTrue(perms.canEdit());
            assertTrue(perms.canPlace());
            assertTrue(perms.canBreak());
            assertTrue(perms.canSpawnMobs());
            assertEquals(2, perms.minPermissionLevel());
        }

        @Test
        @DisplayName("FULL_ACCESS allows all operations without OP")
        void fullAccessPermissions() {
            SlotPermissions perms = SlotPermissions.FULL_ACCESS;
            assertTrue(perms.canUsePortals());
            assertTrue(perms.canEdit());
            assertTrue(perms.canPlace());
            assertTrue(perms.canBreak());
            assertEquals(0, perms.minPermissionLevel());
        }

        @Test
        @DisplayName("forSlotType returns appropriate permissions")
        void forSlotTypeReturnsAppropriate() {
            assertEquals(SlotPermissions.DEFAULT, SlotPermissions.forSlotType(SlotType.FIXED));
            assertEquals(SlotPermissions.FULL_ACCESS, SlotPermissions.forSlotType(SlotType.EDITABLE));
            assertEquals(SlotPermissions.ADMIN_ONLY, SlotPermissions.forSlotType(SlotType.RESTRICTED));
        }
    }
}
