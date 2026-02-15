package com.devmod.nexus.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SlotPermissions Extended")
class SlotPermissionsExtendedTest {

    // ========================================================================
    // withPermissionLevel Tests
    // ========================================================================

    @Nested
    @DisplayName("withPermissionLevel")
    class WithPermissionLevelTests {

        @Test
        @DisplayName("Changes the permission level")
        void changesLevel() {
            SlotPermissions perms = SlotPermissions.DEFAULT.withPermissionLevel(3);
            assertEquals(3, perms.minPermissionLevel());
        }

        @Test
        @DisplayName("Clamps negative level to 0")
        void clampsNegativeToZero() {
            SlotPermissions perms = SlotPermissions.DEFAULT.withPermissionLevel(-1);
            assertEquals(0, perms.minPermissionLevel());
        }

        @Test
        @DisplayName("Clamps level above 4 to 4")
        void clampsAboveFourToFour() {
            SlotPermissions perms = SlotPermissions.DEFAULT.withPermissionLevel(10);
            assertEquals(4, perms.minPermissionLevel());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 3, 4})
        @DisplayName("Accepts valid levels 0-4")
        void acceptsValidLevels(int level) {
            SlotPermissions perms = SlotPermissions.DEFAULT.withPermissionLevel(level);
            assertEquals(level, perms.minPermissionLevel());
        }

        @Test
        @DisplayName("Preserves other permission flags")
        void preservesOtherFlags() {
            SlotPermissions perms = SlotPermissions.ADMIN_ONLY.withPermissionLevel(4);
            assertTrue(perms.canEdit());
            assertTrue(perms.canPlace());
            assertTrue(perms.canBreak());
            assertTrue(perms.canSpawnMobs());
            assertTrue(perms.canUsePortals());
            assertEquals(4, perms.minPermissionLevel());
        }
    }

    // ========================================================================
    // withCanEdit Tests
    // ========================================================================

    @Nested
    @DisplayName("withCanEdit")
    class WithCanEditTests {

        @Test
        @DisplayName("Enables editing on DEFAULT")
        void enablesEditingOnDefault() {
            SlotPermissions perms = SlotPermissions.DEFAULT.withCanEdit(true);
            assertTrue(perms.canEdit());
        }

        @Test
        @DisplayName("Disables editing on FULL_ACCESS")
        void disablesEditingOnFullAccess() {
            SlotPermissions perms = SlotPermissions.FULL_ACCESS.withCanEdit(false);
            assertFalse(perms.canEdit());
        }

        @Test
        @DisplayName("Preserves other fields")
        void preservesOtherFields() {
            SlotPermissions original = SlotPermissions.ADMIN_ONLY;
            SlotPermissions modified = original.withCanEdit(false);
            assertFalse(modified.canEdit());
            assertEquals(original.canPlace(), modified.canPlace());
            assertEquals(original.canBreak(), modified.canBreak());
            assertEquals(original.canSpawnMobs(), modified.canSpawnMobs());
            assertEquals(original.canUsePortals(), modified.canUsePortals());
            assertEquals(original.minPermissionLevel(), modified.minPermissionLevel());
        }
    }

    // ========================================================================
    // BUILDER Preset Tests
    // ========================================================================

    @Nested
    @DisplayName("BUILDER Preset")
    class BuilderPresetTests {

        @Test
        @DisplayName("BUILDER can edit and place")
        void builderCanEditAndPlace() {
            assertTrue(SlotPermissions.BUILDER.canEdit());
            assertTrue(SlotPermissions.BUILDER.canPlace());
            assertTrue(SlotPermissions.BUILDER.canBreak());
        }

        @Test
        @DisplayName("BUILDER cannot spawn mobs")
        void builderCannotSpawnMobs() {
            assertFalse(SlotPermissions.BUILDER.canSpawnMobs());
        }

        @Test
        @DisplayName("BUILDER has no OP requirement")
        void builderNoOpRequired() {
            assertEquals(0, SlotPermissions.BUILDER.minPermissionLevel());
        }
    }

    // ========================================================================
    // forSlotType Coverage
    // ========================================================================

    @ParameterizedTest
    @EnumSource(SlotType.class)
    @DisplayName("forSlotType returns non-null for all types")
    void forSlotTypeReturnsNonNull(SlotType type) {
        SlotPermissions perms = SlotPermissions.forSlotType(type);
        assertNotNull(perms);
    }

    @Test
    @DisplayName("TEST_AREA permissions equal FULL_ACCESS")
    void testAreaPermissionsEqualFullAccess() {
        assertSame(SlotPermissions.TEST_AREA, SlotPermissions.FULL_ACCESS);
    }

    @Test
    @DisplayName("forSlotType TEST_AREA returns TEST_AREA permissions")
    void forSlotTypeTestAreaReturnsTestArea() {
        SlotPermissions perms = SlotPermissions.forSlotType(SlotType.TEST_AREA);
        assertSame(SlotPermissions.TEST_AREA, perms);
    }

    // ========================================================================
    // SlotType Extended Tests
    // ========================================================================

    @Nested
    @DisplayName("SlotType isInfrastructure")
    class SlotTypeInfrastructureTests {

        @Test
        @DisplayName("FIXED is infrastructure")
        void fixedIsInfrastructure() {
            assertTrue(SlotType.FIXED.isInfrastructure());
        }

        @Test
        @DisplayName("EDITABLE is not infrastructure")
        void editableIsNotInfrastructure() {
            assertFalse(SlotType.EDITABLE.isInfrastructure());
        }

        @Test
        @DisplayName("TEST_AREA is not infrastructure")
        void testAreaIsNotInfrastructure() {
            assertFalse(SlotType.TEST_AREA.isInfrastructure());
        }

        @Test
        @DisplayName("RESTRICTED is not infrastructure")
        void restrictedIsNotInfrastructure() {
            assertFalse(SlotType.RESTRICTED.isInfrastructure());
        }
    }

    @Nested
    @DisplayName("SlotType isAdminEditable")
    class SlotTypeAdminEditableTests {

        @Test
        @DisplayName("FIXED is not admin editable")
        void fixedIsNotAdminEditable() {
            assertFalse(SlotType.FIXED.isAdminEditable());
        }

        @Test
        @DisplayName("All other types are admin editable")
        void otherTypesAreAdminEditable() {
            assertTrue(SlotType.EDITABLE.isAdminEditable());
            assertTrue(SlotType.TEST_AREA.isAdminEditable());
            assertTrue(SlotType.RESTRICTED.isAdminEditable());
        }
    }
}
