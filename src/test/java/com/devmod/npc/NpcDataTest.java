package com.devmod.npc;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.npc.data.NpcAppearance;
import com.devmod.npc.data.NpcBehavior;
import com.devmod.npc.data.NpcConfiguration;
import com.devmod.npc.data.NpcVisitData;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NPC data records: NpcConfiguration, NpcAppearance, NpcBehavior, NpcVisitData.
 * Validates creation, defaults, wither methods, and field constraints.
 */
class NpcDataTest {

    // ========================================================================
    // NpcBehavior tests
    // ========================================================================

    @Nested
    @DisplayName("NpcBehavior")
    class NpcBehaviorTests {

        @Test
        @DisplayName("DEFAULT has expected values")
        void defaultHasExpectedValues() {
            NpcBehavior def = NpcBehavior.DEFAULT;
            assertTrue(def.floating());
            assertEquals(0.3f, def.floatAmplitude(), 0.001f);
            assertEquals(0.05f, def.floatSpeed(), 0.001f);
            assertTrue(def.lookAtPlayer());
            assertTrue(def.invulnerable());
        }

        @Test
        @DisplayName("withFloating returns new instance with changed value")
        void withFloatingChangesValue() {
            NpcBehavior original = NpcBehavior.DEFAULT;
            NpcBehavior modified = original.withFloating(false);
            assertFalse(modified.floating());
            // other fields unchanged
            assertEquals(original.floatAmplitude(), modified.floatAmplitude());
            assertEquals(original.floatSpeed(), modified.floatSpeed());
            assertEquals(original.lookAtPlayer(), modified.lookAtPlayer());
            assertEquals(original.invulnerable(), modified.invulnerable());
        }

        @Test
        @DisplayName("withFloatAmplitude returns new instance with changed value")
        void withFloatAmplitudeChangesValue() {
            NpcBehavior modified = NpcBehavior.DEFAULT.withFloatAmplitude(0.8f);
            assertEquals(0.8f, modified.floatAmplitude(), 0.001f);
            assertTrue(modified.floating()); // unchanged
        }

        @Test
        @DisplayName("withFloatSpeed returns new instance with changed value")
        void withFloatSpeedChangesValue() {
            NpcBehavior modified = NpcBehavior.DEFAULT.withFloatSpeed(0.1f);
            assertEquals(0.1f, modified.floatSpeed(), 0.001f);
        }

        @Test
        @DisplayName("withLookAtPlayer returns new instance with changed value")
        void withLookAtPlayerChangesValue() {
            NpcBehavior modified = NpcBehavior.DEFAULT.withLookAtPlayer(false);
            assertFalse(modified.lookAtPlayer());
        }

        @Test
        @DisplayName("withInvulnerable returns new instance with changed value")
        void withInvulnerableChangesValue() {
            NpcBehavior modified = NpcBehavior.DEFAULT.withInvulnerable(false);
            assertFalse(modified.invulnerable());
        }

        @Test
        @DisplayName("Record equality works correctly")
        void recordEqualityWorks() {
            NpcBehavior a = new NpcBehavior(true, 0.3f, 0.05f, true, true);
            NpcBehavior b = new NpcBehavior(true, 0.3f, 0.05f, true, true);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    // ========================================================================
    // NpcAppearance tests
    // ========================================================================

    @Nested
    @DisplayName("NpcAppearance")
    class NpcAppearanceTests {

        @Test
        @DisplayName("DEFAULT has expected values")
        void defaultHasExpectedValues() {
            NpcAppearance def = NpcAppearance.DEFAULT;
            assertTrue(def.particlesEnabled());
            assertNotNull(def.particleTypeId());
            assertEquals("minecraft:end_rod", def.particleTypeId().toString());
            assertEquals(10, def.particleInterval());
            assertFalse(def.glowEffect());
        }

        @Test
        @DisplayName("withParticlesEnabled toggles particles")
        void withParticlesEnabledToggles() {
            NpcAppearance modified = NpcAppearance.DEFAULT.withParticlesEnabled(false);
            assertFalse(modified.particlesEnabled());
            // other fields unchanged
            assertEquals(NpcAppearance.DEFAULT.particleTypeId(), modified.particleTypeId());
            assertEquals(NpcAppearance.DEFAULT.particleInterval(), modified.particleInterval());
            assertEquals(NpcAppearance.DEFAULT.glowEffect(), modified.glowEffect());
        }

        @Test
        @DisplayName("withParticleInterval changes interval")
        void withParticleIntervalChanges() {
            NpcAppearance modified = NpcAppearance.DEFAULT.withParticleInterval(5);
            assertEquals(5, modified.particleInterval());
        }

        @Test
        @DisplayName("withGlowEffect toggles glow")
        void withGlowEffectToggles() {
            NpcAppearance modified = NpcAppearance.DEFAULT.withGlowEffect(true);
            assertTrue(modified.glowEffect());
        }
    }

    // ========================================================================
    // NpcConfiguration tests
    // ========================================================================

    @Nested
    @DisplayName("NpcConfiguration")
    class NpcConfigurationTests {

        private final UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        @Test
        @DisplayName("createDefault produces valid configuration")
        void createDefaultProducesValidConfig() {
            NpcConfiguration config = NpcConfiguration.createDefault("TestNPC", ownerId);
            assertNotNull(config.id());
            assertEquals("TestNPC", config.displayName());
            assertNull(config.skinPlayerUUID());
            assertNull(config.skinPlayerName());
            assertEquals(NpcBehavior.DEFAULT, config.behavior());
            assertEquals(NpcAppearance.DEFAULT, config.appearance());
            assertNull(config.dialogSetId());
            assertEquals(ownerId, config.ownerUUID());
            assertNull(config.spawnPosition());
            assertNull(config.dimension());
            assertTrue(config.createdAt() > 0);
            assertTrue(config.updatedAt() > 0);
            assertEquals(0, config.revision());
        }

        @Test
        @DisplayName("Compact constructor rejects null id")
        @SuppressWarnings("NullAway")
        void compactConstructorRejectsNullId() {
            assertThrows(NullPointerException.class, () ->
                new NpcConfiguration(null, "name", null, null,
                    NpcBehavior.DEFAULT, NpcAppearance.DEFAULT, null,
                    ownerId, null, null, 0, 0, 0));
        }

        @Test
        @DisplayName("Compact constructor rejects null displayName")
        @SuppressWarnings("NullAway")
        void compactConstructorRejectsNullDisplayName() {
            assertThrows(NullPointerException.class, () ->
                new NpcConfiguration(UUID.randomUUID(), null, null, null,
                    NpcBehavior.DEFAULT, NpcAppearance.DEFAULT, null,
                    ownerId, null, null, 0, 0, 0));
        }

        @Test
        @DisplayName("Compact constructor rejects null behavior")
        @SuppressWarnings("NullAway")
        void compactConstructorRejectsNullBehavior() {
            assertThrows(NullPointerException.class, () ->
                new NpcConfiguration(UUID.randomUUID(), "name", null, null,
                    null, NpcAppearance.DEFAULT, null,
                    ownerId, null, null, 0, 0, 0));
        }

        @Test
        @DisplayName("displayName is truncated to MAX_DISPLAY_NAME_LENGTH")
        void displayNameIsTruncated() {
            String longName = "A".repeat(100);
            NpcConfiguration config = NpcConfiguration.createDefault(longName, ownerId);
            assertEquals(NpcConfiguration.MAX_DISPLAY_NAME_LENGTH, config.displayName().length());
        }

        @Test
        @DisplayName("skinPlayerName is truncated to MAX_SKIN_NAME_LENGTH")
        void skinPlayerNameIsTruncated() {
            String longSkinName = "B".repeat(100);
            UUID npcId = UUID.randomUUID();
            UUID skinUuid = UUID.randomUUID();
            NpcConfiguration config = new NpcConfiguration(
                npcId, "test", skinUuid, longSkinName,
                NpcBehavior.DEFAULT, NpcAppearance.DEFAULT, null,
                ownerId, null, null, 0, 0, 0);
            assertEquals(NpcConfiguration.MAX_SKIN_NAME_LENGTH, config.skinPlayerName().length());
        }

        @Test
        @DisplayName("dialogSetId is truncated to MAX_DIALOG_SET_ID_LENGTH")
        void dialogSetIdIsTruncated() {
            String longDialogId = "C".repeat(100);
            UUID npcId = UUID.randomUUID();
            NpcConfiguration config = new NpcConfiguration(
                npcId, "test", null, null,
                NpcBehavior.DEFAULT, NpcAppearance.DEFAULT, longDialogId,
                ownerId, null, null, 0, 0, 0);
            assertEquals(NpcConfiguration.MAX_DIALOG_SET_ID_LENGTH, config.dialogSetId().length());
        }

        @Test
        @DisplayName("withDisplayName creates copy with new name and incremented revision")
        void withDisplayNameCreatesNewCopy() {
            NpcConfiguration original = NpcConfiguration.createDefault("Original", ownerId);
            NpcConfiguration modified = original.withDisplayName("Modified");
            assertEquals("Modified", modified.displayName());
            assertEquals(original.id(), modified.id());
            assertEquals(original.revision() + 1, modified.revision());
        }

        @Test
        @DisplayName("withSkin creates copy with new skin info")
        void withSkinUpdatesCorrectly() {
            NpcConfiguration original = NpcConfiguration.createDefault("Test", ownerId);
            UUID skinUuid = UUID.randomUUID();
            NpcConfiguration modified = original.withSkin(skinUuid, "SkinPlayer");
            assertEquals(skinUuid, modified.skinPlayerUUID());
            assertEquals("SkinPlayer", modified.skinPlayerName());
            assertEquals(original.id(), modified.id());
        }

        @Test
        @DisplayName("withDialogSetId creates copy with new dialog set")
        void withDialogSetIdUpdatesCorrectly() {
            NpcConfiguration original = NpcConfiguration.createDefault("Test", ownerId);
            NpcConfiguration modified = original.withDialogSetId("my_dialog");
            assertEquals("my_dialog", modified.dialogSetId());
            assertTrue(modified.hasDialogSet());
        }

        @Test
        @DisplayName("hasSkinUUID returns false when null")
        void hasSkinUuidFalseWhenNull() {
            NpcConfiguration config = NpcConfiguration.createDefault("Test", ownerId);
            assertFalse(config.hasSkinUUID());
        }

        @Test
        @DisplayName("hasDialogSet returns false when null or empty")
        void hasDialogSetFalseWhenNullOrEmpty() {
            NpcConfiguration config = NpcConfiguration.createDefault("Test", ownerId);
            assertFalse(config.hasDialogSet());

            NpcConfiguration withEmpty = config.withDialogSetId("");
            assertFalse(withEmpty.hasDialogSet());
        }

        @Test
        @DisplayName("isOwnedBy returns true for matching UUID")
        void isOwnedByMatchesUuid() {
            NpcConfiguration config = NpcConfiguration.createDefault("Test", ownerId);
            assertTrue(config.isOwnedBy(ownerId));
            assertFalse(config.isOwnedBy(UUID.randomUUID()));
        }

        @Test
        @DisplayName("hasSpawnPosition returns false by default")
        void hasSpawnPositionDefaultFalse() {
            NpcConfiguration config = NpcConfiguration.createDefault("Test", ownerId);
            assertFalse(config.hasSpawnPosition());
        }

        @Test
        @DisplayName("touch increments revision")
        void touchIncrementsRevision() {
            NpcConfiguration config = NpcConfiguration.createDefault("Test", ownerId);
            NpcConfiguration touched = config.touch();
            assertEquals(config.revision() + 1, touched.revision());
            assertEquals(config.id(), touched.id());
            assertEquals(config.displayName(), touched.displayName());
        }
    }

    // ========================================================================
    // NpcVisitData tests
    // ========================================================================

    @Nested
    @DisplayName("NpcVisitData")
    class NpcVisitDataTests {

        private final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        private final UUID npcId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        @Test
        @DisplayName("createFirstVisit sets visit count to 1")
        void createFirstVisitHasCountOne() {
            NpcVisitData data = NpcVisitData.createFirstVisit(playerId, npcId);
            assertEquals(1, data.visitCount());
            assertEquals(playerId, data.playerId());
            assertEquals(npcId, data.npcId());
            assertTrue(data.firstVisitedAt() > 0);
            assertTrue(data.lastVisitedAt() > 0);
        }

        @Test
        @DisplayName("createFirstVisit rejects null playerId")
        @SuppressWarnings("NullAway")
        void createFirstVisitRejectsNullPlayer() {
            assertThrows(NullPointerException.class, () ->
                NpcVisitData.createFirstVisit(null, npcId));
        }

        @Test
        @DisplayName("createFirstVisit rejects null npcId")
        @SuppressWarnings("NullAway")
        void createFirstVisitRejectsNullNpc() {
            assertThrows(NullPointerException.class, () ->
                NpcVisitData.createFirstVisit(playerId, null));
        }

        @Test
        @DisplayName("touch increments visit count")
        void touchIncrementsVisitCount() {
            NpcVisitData data = NpcVisitData.createFirstVisit(playerId, npcId);
            NpcVisitData touched = data.touch();
            assertEquals(2, touched.visitCount());
            // firstVisitedAt should remain the same
            assertEquals(data.firstVisitedAt(), touched.firstVisitedAt());
            // lastVisitedAt should be updated
            assertTrue(touched.lastVisitedAt() >= data.lastVisitedAt());
        }

        @Test
        @DisplayName("touch preserves player and NPC IDs")
        void touchPreservesIds() {
            NpcVisitData data = NpcVisitData.createFirstVisit(playerId, npcId);
            NpcVisitData touched = data.touch();
            assertEquals(playerId, touched.playerId());
            assertEquals(npcId, touched.npcId());
        }

        @Test
        @DisplayName("timeSinceFirstVisit returns non-negative value")
        void timeSinceFirstVisitIsNonNegative() {
            NpcVisitData data = NpcVisitData.createFirstVisit(playerId, npcId);
            assertTrue(data.timeSinceFirstVisit() >= 0);
        }

        @Test
        @DisplayName("timeSinceLastVisit returns non-negative value")
        void timeSinceLastVisitIsNonNegative() {
            NpcVisitData data = NpcVisitData.createFirstVisit(playerId, npcId);
            assertTrue(data.timeSinceLastVisit() >= 0);
        }
    }
}
