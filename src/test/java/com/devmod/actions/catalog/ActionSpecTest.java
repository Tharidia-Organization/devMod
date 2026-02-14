package com.devmod.actions.catalog;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.catalog.ActionSpec.BindingMeta;
import com.devmod.actions.catalog.ActionSpec.PolicyMeta;
import com.devmod.actions.catalog.ActionSpec.TelemetryMeta;
import com.devmod.actions.catalog.ActionSpec.UiMeta;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ActionSpec: builder, validation, equality, and inner records.
 */
@DisplayName("ActionSpec - Canonical Action Specification")
class ActionSpecTest {

    private static final String VALID_ID = "devmod.ui.radial.open";

    // =========================================================================
    // Builder tests
    // =========================================================================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Builder creates valid spec with minimal configuration")
        void builderCreatesValidSpec() {
            ActionSpec spec = ActionSpec.builder(VALID_ID)
                .category(ActionCategory.UI)
                .ui("label.key", "desc.key")
                .build();

            assertEquals(VALID_ID, spec.id());
            assertEquals(ActionSpec.CURRENT_SCHEMA_VERSION, spec.schemaVersion());
            assertEquals(ActionChannel.CLIENT, spec.channel());
            assertEquals(ActionCategory.UI, spec.category());
            assertNull(spec.actionType());
            assertEquals(0, spec.permissionLevel());
            assertEquals("label.key", spec.uiMeta().labelKey());
            assertEquals("desc.key", spec.uiMeta().descKey());
            assertEquals(PolicyMeta.DEFAULT, spec.policyMeta());
            assertEquals(TelemetryMeta.DEFAULT, spec.telemetryMeta());
            assertEquals(BindingMeta.NONE, spec.bindingMeta());
        }

        @Test
        @DisplayName("Builder creates fully configured spec")
        void builderCreatesFullSpec() {
            ActionSpec spec = ActionSpec.builder("devmod.command.heal")
                .schemaVersion(1)
                .channel(ActionChannel.SERVER)
                .allowedOrigins(ActionOrigin.RADIAL, ActionOrigin.COMMAND)
                .permissionLevel(2)
                .handlerRef("com.devmod.actions.HealHandler")
                .category(ActionCategory.TOOLS)
                .actionType(ActionType.RUN_SERVER_COMMAND)
                .ui("label", "desc", "minecraft:golden_apple", "Root/Tools/Heal", false)
                .policy(false, 5, "requiresPlayer")
                .telemetryTags("heal", "command")
                .binding(null, "heal", null)
                .build();

            assertEquals("devmod.command.heal", spec.id());
            assertEquals(ActionChannel.SERVER, spec.channel());
            assertEquals(2, spec.permissionLevel());
            assertEquals(ActionCategory.TOOLS, spec.category());
            assertEquals(ActionType.RUN_SERVER_COMMAND, spec.actionType());
            assertEquals("minecraft:golden_apple", spec.uiMeta().iconItemId());
            assertEquals("Root/Tools/Heal", spec.uiMeta().menuPath());
            assertEquals(5, spec.policyMeta().rateLimitPerSec());
            assertEquals("requiresPlayer", spec.policyMeta().preconditionRef());
            assertEquals(List.of("heal", "command"), spec.telemetryMeta().tags());
            assertEquals("heal", spec.bindingMeta().commandHint());
        }

        @Test
        @DisplayName("Builder with requiresConfirm policy")
        void builderWithConfirmPolicy() {
            ActionSpec spec = ActionSpec.builder("devmod.admin.rebuild")
                .category(ActionCategory.ADMIN)
                .ui("rebuild", "desc")
                .policy(true, 0, null)
                .permissionLevel(4)
                .channel(ActionChannel.SERVER)
                .build();

            assertTrue(spec.policyMeta().requiresConfirm());
            assertEquals(4, spec.permissionLevel());
        }
    }

    // =========================================================================
    // Compact constructor validation tests
    // =========================================================================

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Rejects null id")
        void rejectsNullId() {
            assertThrows(NullPointerException.class, () ->
                ActionSpec.builder(null));
        }

        @Test
        @DisplayName("Rejects empty id")
        void rejectsEmptyId() {
            assertThrows(IllegalArgumentException.class, () ->
                ActionSpec.builder("")
                    .category(ActionCategory.UI)
                    .ui("l", "d")
                    .build());
        }

        @Test
        @DisplayName("Rejects id without devmod prefix")
        void rejectsIdWithoutPrefix() {
            assertThrows(IllegalArgumentException.class, () ->
                ActionSpec.builder("other.ui.open")
                    .category(ActionCategory.UI)
                    .ui("l", "d")
                    .build());
        }

        @Test
        @DisplayName("Rejects id with only one segment after prefix")
        void rejectsIdWithSingleSegment() {
            assertThrows(IllegalArgumentException.class, () ->
                ActionSpec.builder("devmod.open")
                    .category(ActionCategory.UI)
                    .ui("l", "d")
                    .build());
        }

        @Test
        @DisplayName("Rejects id with uppercase letters")
        void rejectsIdWithUppercase() {
            assertThrows(IllegalArgumentException.class, () ->
                ActionSpec.builder("devmod.UI.open")
                    .category(ActionCategory.UI)
                    .ui("l", "d")
                    .build());
        }

        @Test
        @DisplayName("Rejects permission level below 0")
        void rejectsNegativePermissionLevel() {
            assertThrows(IllegalArgumentException.class, () ->
                ActionSpec.builder(VALID_ID)
                    .category(ActionCategory.UI)
                    .ui("l", "d")
                    .permissionLevel(-1)
                    .build());
        }

        @Test
        @DisplayName("Rejects permission level above 4")
        void rejectsPermissionLevelAbove4() {
            assertThrows(IllegalArgumentException.class, () ->
                ActionSpec.builder(VALID_ID)
                    .category(ActionCategory.UI)
                    .ui("l", "d")
                    .permissionLevel(5)
                    .build());
        }

        @Test
        @DisplayName("Rejects null category")
        void rejectsNullCategory() {
            assertThrows(NullPointerException.class, () ->
                ActionSpec.builder(VALID_ID)
                    .ui("l", "d")
                    .build());
        }

        @Test
        @DisplayName("Rejects negative rate limit")
        void rejectsNegativeRateLimit() {
            assertThrows(IllegalArgumentException.class, () ->
                new PolicyMeta(false, -1, null));
        }
    }

    // =========================================================================
    // Origin compatibility validation
    // =========================================================================

    @Nested
    @DisplayName("Origin Compatibility")
    class OriginCompatibilityTests {

        @Test
        @DisplayName("CLIENT channel cannot allow NETWORK origin")
        void clientCannotAllowNetwork() {
            assertThrows(IllegalArgumentException.class, () ->
                ActionSpec.builder(VALID_ID)
                    .channel(ActionChannel.CLIENT)
                    .allowAllOrigins()
                    .category(ActionCategory.UI)
                    .ui("l", "d")
                    .build());
        }

        @Test
        @DisplayName("SERVER channel can allow NETWORK origin")
        void serverCanAllowNetwork() {
            ActionSpec spec = ActionSpec.builder("devmod.server.action")
                .channel(ActionChannel.SERVER)
                .allowAllOrigins()
                .category(ActionCategory.ADMIN)
                .ui("l", "d")
                .build();

            assertTrue(spec.allowsOrigin(ActionOrigin.NETWORK));
        }

        @Test
        @DisplayName("BOTH channel can allow NETWORK origin")
        void bothCanAllowNetwork() {
            ActionSpec spec = ActionSpec.builder("devmod.rpc.action")
                .channel(ActionChannel.BOTH)
                .allowAllOrigins()
                .category(ActionCategory.MISC)
                .ui("l", "d")
                .build();

            assertTrue(spec.allowsOrigin(ActionOrigin.NETWORK));
        }

        @Test
        @DisplayName("Trusted-only origins exclude NETWORK and UNKNOWN")
        void trustedOriginsExcludeUntrusted() {
            ActionSpec spec = ActionSpec.builder(VALID_ID)
                .trustedOriginsOnly()
                .category(ActionCategory.UI)
                .ui("l", "d")
                .build();

            assertTrue(spec.allowsOrigin(ActionOrigin.RADIAL));
            assertTrue(spec.allowsOrigin(ActionOrigin.KEYBIND));
            assertTrue(spec.allowsOrigin(ActionOrigin.COMMAND));
            assertTrue(spec.allowsOrigin(ActionOrigin.UI));
            assertTrue(spec.allowsOrigin(ActionOrigin.EVENT));
            assertFalse(spec.allowsOrigin(ActionOrigin.NETWORK));
            assertFalse(spec.allowsOrigin(ActionOrigin.UNKNOWN));
        }

        @Test
        @DisplayName("Empty allowed origins set is valid (no origin allowed)")
        void emptyOriginsSetIsValid() {
            ActionSpec spec = ActionSpec.builder(VALID_ID)
                .allowedOrigins(Set.of())
                .category(ActionCategory.UI)
                .ui("l", "d")
                .build();

            assertFalse(spec.allowsOrigin(ActionOrigin.RADIAL));
        }
    }

    // =========================================================================
    // UiMeta tests
    // =========================================================================

    @Nested
    @DisplayName("UiMeta")
    class UiMetaTests {

        @Test
        @DisplayName("UiMeta.of creates minimal instance")
        void uiMetaOfCreatesMinimal() {
            UiMeta meta = UiMeta.of("label", "desc");
            assertEquals("label", meta.labelKey());
            assertEquals("desc", meta.descKey());
            assertNull(meta.iconItemId());
            assertNull(meta.menuPath());
            assertFalse(meta.isToggle());
        }

        @Test
        @DisplayName("UiMeta rejects null labelKey")
        void uiMetaRejectsNullLabel() {
            assertThrows(NullPointerException.class, () ->
                new UiMeta(null, "desc", null, null, false));
        }

        @Test
        @DisplayName("UiMeta rejects null descKey")
        void uiMetaRejectsNullDesc() {
            assertThrows(NullPointerException.class, () ->
                new UiMeta("label", null, null, null, false));
        }
    }

    // =========================================================================
    // PolicyMeta defaults test
    // =========================================================================

    @Nested
    @DisplayName("PolicyMeta")
    class PolicyMetaTests {

        @Test
        @DisplayName("DEFAULT has no confirmation, no rate limit, no precondition")
        void defaultPolicyMeta() {
            assertFalse(PolicyMeta.DEFAULT.requiresConfirm());
            assertEquals(0, PolicyMeta.DEFAULT.rateLimitPerSec());
            assertNull(PolicyMeta.DEFAULT.preconditionRef());
        }
    }

    // =========================================================================
    // TelemetryMeta defaults test
    // =========================================================================

    @Nested
    @DisplayName("TelemetryMeta")
    class TelemetryMetaTests {

        @Test
        @DisplayName("DEFAULT has empty tags and tracks duration")
        void defaultTelemetryMeta() {
            assertTrue(TelemetryMeta.DEFAULT.tags().isEmpty());
            assertTrue(TelemetryMeta.DEFAULT.trackDuration());
        }

        @Test
        @DisplayName("Tags are defensively copied")
        void tagsAreDefensivelyCopied() {
            TelemetryMeta meta = new TelemetryMeta(List.of("a", "b"), true);
            assertThrows(UnsupportedOperationException.class, () ->
                meta.tags().add("c"));
        }
    }

    // =========================================================================
    // equals/hashCode tests
    // =========================================================================

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("equals and hashCode based on id only")
        void equalsAndHashCodeById() {
            ActionSpec spec1 = ActionSpec.builder(VALID_ID)
                .category(ActionCategory.UI)
                .ui("label1", "desc1")
                .build();

            ActionSpec spec2 = ActionSpec.builder(VALID_ID)
                .category(ActionCategory.DEBUG)
                .ui("label2", "desc2")
                .permissionLevel(4)
                .channel(ActionChannel.SERVER)
                .build();

            assertEquals(spec1, spec2);
            assertEquals(spec1.hashCode(), spec2.hashCode());
        }

        @Test
        @DisplayName("Different ids are not equal")
        void differentIdsNotEqual() {
            ActionSpec spec1 = ActionSpec.builder("devmod.ui.open_a")
                .category(ActionCategory.UI)
                .ui("l", "d")
                .build();

            ActionSpec spec2 = ActionSpec.builder("devmod.ui.open_b")
                .category(ActionCategory.UI)
                .ui("l", "d")
                .build();

            assertNotEquals(spec1, spec2);
        }
    }

    // =========================================================================
    // toString test
    // =========================================================================

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString contains useful debug info")
        void toStringContainsInfo() {
            ActionSpec spec = ActionSpec.builder(VALID_ID)
                .category(ActionCategory.UI)
                .actionType(ActionType.NAVIGATE_SCREEN)
                .permissionLevel(2)
                .ui("l", "d")
                .build();

            String str = spec.toString();
            assertTrue(str.contains(VALID_ID));
            assertTrue(str.contains("CLIENT"));
            assertTrue(str.contains("UI"));
            assertTrue(str.contains("NAVIGATE_SCREEN"));
            assertTrue(str.contains("perm=2"));
        }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    @Nested
    @DisplayName("Helper Methods")
    class HelperTests {

        @Test
        @DisplayName("isClientOnly returns true for CLIENT channel")
        void isClientOnly() {
            ActionSpec spec = ActionSpec.builder(VALID_ID)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("l", "d")
                .build();

            assertTrue(spec.isClientOnly());
            assertFalse(spec.isServerOnly());
        }

        @Test
        @DisplayName("isServerOnly returns true for SERVER channel")
        void isServerOnly() {
            ActionSpec spec = ActionSpec.builder("devmod.server.action")
                .channel(ActionChannel.SERVER)
                .category(ActionCategory.ADMIN)
                .ui("l", "d")
                .build();

            assertTrue(spec.isServerOnly());
            assertFalse(spec.isClientOnly());
        }
    }
}
