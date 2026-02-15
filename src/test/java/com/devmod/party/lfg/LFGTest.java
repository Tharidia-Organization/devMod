package com.devmod.party.lfg;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LFG Module")
class LFGTest {

    // ==========================================
    // LFGActivity Tests
    // ==========================================

    @Nested
    @DisplayName("LFGActivity")
    class LFGActivityTests {

        @ParameterizedTest
        @EnumSource(LFGActivity.class)
        @DisplayName("All activities have non-null display name and positive color")
        void allActivitiesHaveDisplayNameAndColor(LFGActivity activity) {
            assertNotNull(activity.getDisplayName());
            assertFalse(activity.getDisplayName().isEmpty());
            // ARGB color should have alpha channel
            assertTrue((activity.getColor() & 0xFF000000) != 0, "Color should have alpha channel");
        }

        @Test
        @DisplayName("fromOrdinal returns correct activity")
        void fromOrdinalCorrect() {
            for (LFGActivity activity : LFGActivity.values()) {
                assertEquals(activity, LFGActivity.fromOrdinal(activity.ordinal()));
            }
        }

        @Test
        @DisplayName("fromOrdinal returns CUSTOM for invalid ordinal")
        void fromOrdinalInvalid() {
            assertEquals(LFGActivity.CUSTOM, LFGActivity.fromOrdinal(-1));
            assertEquals(LFGActivity.CUSTOM, LFGActivity.fromOrdinal(999));
        }

        @Test
        @DisplayName("ARENA activities have expected names")
        void arenaActivitiesNames() {
            assertTrue(LFGActivity.ARENA_EASY.getDisplayName().contains("Easy"));
            assertTrue(LFGActivity.ARENA_NORMAL.getDisplayName().contains("Normal"));
            assertTrue(LFGActivity.ARENA_HARD.getDisplayName().contains("Hard"));
        }
    }

    // ==========================================
    // LFGListing Tests
    // ==========================================

    @Nested
    @DisplayName("LFGListing")
    class LFGListingTests {

        @Test
        @DisplayName("create() generates random ID")
        void createGeneratesRandomId() {
            UUID leaderId = UUID.randomUUID();
            LFGListing listing1 = LFGListing.create(leaderId, "Leader", LFGActivity.QUEST,
                "Description", 2, 4, 1, false, 100L);
            LFGListing listing2 = LFGListing.create(leaderId, "Leader", LFGActivity.QUEST,
                "Description", 2, 4, 1, false, 100L);
            assertNotEquals(listing1.listingId(), listing2.listingId());
        }

        @Test
        @DisplayName("create() stores all fields correctly")
        void createStoresFields() {
            UUID leaderId = UUID.randomUUID();
            LFGListing listing = LFGListing.create(leaderId, "TestLeader", LFGActivity.BOSS_FIGHT,
                "Kill the boss", 4, 8, 3, true, 500L);
            assertEquals(leaderId, listing.leaderId());
            assertEquals("TestLeader", listing.leaderName());
            assertEquals(LFGActivity.BOSS_FIGHT, listing.activity());
            assertEquals("Kill the boss", listing.description());
            assertEquals(4, listing.minPlayers());
            assertEquals(8, listing.maxPlayers());
            assertEquals(3, listing.currentPlayers());
            assertTrue(listing.autoAccept());
            assertEquals(500L, listing.createdTick());
        }

        @Test
        @DisplayName("withCurrentPlayers returns copy with updated count")
        void withCurrentPlayersUpdatesCount() {
            UUID leaderId = UUID.randomUUID();
            LFGListing original = LFGListing.create(leaderId, "Leader", LFGActivity.QUEST,
                "Desc", 2, 4, 1, false, 100L);
            LFGListing updated = original.withCurrentPlayers(3);
            assertEquals(3, updated.currentPlayers());
            assertEquals(1, original.currentPlayers()); // original unchanged
            assertEquals(original.listingId(), updated.listingId()); // same ID
            assertEquals(original.leaderId(), updated.leaderId());
        }
    }

    // ==========================================
    // LFGActionPayload Tests
    // ==========================================

    @Nested
    @DisplayName("LFGActionPayload")
    class LFGActionPayloadTests {

        @Nested
        @DisplayName("Action enum")
        class ActionEnum {

            @Test
            @DisplayName("fromOrdinal returns correct action")
            void fromOrdinalCorrect() {
                for (LFGActionPayload.Action action : LFGActionPayload.Action.values()) {
                    assertEquals(action, LFGActionPayload.Action.fromOrdinal(action.ordinal()));
                }
            }

            @Test
            @DisplayName("fromOrdinal returns REFRESH for invalid")
            void fromOrdinalInvalid() {
                assertEquals(LFGActionPayload.Action.REFRESH, LFGActionPayload.Action.fromOrdinal(-1));
                assertEquals(LFGActionPayload.Action.REFRESH, LFGActionPayload.Action.fromOrdinal(999));
            }
        }

        @Test
        @DisplayName("Factory: create()")
        void factoryCreate() {
            LFGActionPayload payload = LFGActionPayload.create(LFGActivity.ARENA_HARD, "Hard arena run", 3, 6, true);
            assertEquals(LFGActionPayload.Action.CREATE, payload.action());
            assertEquals(LFGActivity.ARENA_HARD.ordinal(), payload.activityOrdinal());
            assertEquals("Hard arena run", payload.description());
            assertEquals(3, payload.minPlayers());
            assertEquals(6, payload.maxPlayers());
            assertTrue(payload.autoAccept());
            assertNull(payload.targetListingId());
        }

        @Test
        @DisplayName("Factory: remove()")
        void factoryRemove() {
            LFGActionPayload payload = LFGActionPayload.remove();
            assertEquals(LFGActionPayload.Action.REMOVE, payload.action());
        }

        @Test
        @DisplayName("Factory: apply()")
        void factoryApply() {
            UUID listingId = UUID.randomUUID();
            LFGActionPayload payload = LFGActionPayload.apply(listingId);
            assertEquals(LFGActionPayload.Action.APPLY, payload.action());
            assertEquals(listingId, payload.targetListingId());
        }

        @Test
        @DisplayName("Factory: refresh()")
        void factoryRefresh() {
            LFGActionPayload payload = LFGActionPayload.refresh();
            assertEquals(LFGActionPayload.Action.REFRESH, payload.action());
        }

        @Test
        @DisplayName("getActivity() maps ordinal correctly")
        void getActivityMapsOrdinal() {
            LFGActionPayload payload = LFGActionPayload.create(LFGActivity.EXPLORATION, "Explore", 2, 4, false);
            assertEquals(LFGActivity.EXPLORATION, payload.getActivity());
        }

        @Test
        @DisplayName("estimatedSize is positive")
        void estimatedSizePositive() {
            LFGActionPayload payload = LFGActionPayload.create(LFGActivity.QUEST, "Test", 1, 4, false);
            assertTrue(payload.estimatedSize() > 0);
        }
    }

    // ==========================================
    // LFGSyncPayload Tests
    // ==========================================

    @Nested
    @DisplayName("LFGSyncPayload")
    class LFGSyncPayloadTests {

        @Test
        @DisplayName("empty() creates payload with no listings")
        void emptyPayload() {
            LFGSyncPayload payload = LFGSyncPayload.empty();
            assertTrue(payload.listings().isEmpty());
        }

        @Test
        @DisplayName("fromListings converts listings correctly")
        void fromListingsConverts() {
            UUID leaderId = UUID.randomUUID();
            LFGListing listing = LFGListing.create(leaderId, "Leader", LFGActivity.BOSS_FIGHT,
                "Boss run", 4, 8, 2, true, 1000L);
            LFGSyncPayload payload = LFGSyncPayload.fromListings(List.of(listing));

            assertEquals(1, payload.listings().size());
            LFGSyncPayload.LFGListingEntry entry = payload.listings().get(0);
            assertEquals(listing.listingId(), entry.listingId());
            assertEquals(leaderId, entry.leaderId());
            assertEquals("Leader", entry.leaderName());
            assertEquals(LFGActivity.BOSS_FIGHT.ordinal(), entry.activityOrdinal());
            assertEquals("Boss run", entry.description());
            assertEquals(4, entry.minPlayers());
            assertEquals(8, entry.maxPlayers());
            assertEquals(2, entry.currentPlayers());
            assertTrue(entry.autoAccept());
        }

        @Test
        @DisplayName("LFGListingEntry.getActivity() maps ordinal")
        void entryGetActivity() {
            LFGSyncPayload.LFGListingEntry entry = new LFGSyncPayload.LFGListingEntry(
                UUID.randomUUID(), UUID.randomUUID(), "Test",
                LFGActivity.EXPLORATION.ordinal(), "Desc", 2, 4, 1, false
            );
            assertEquals(LFGActivity.EXPLORATION, entry.getActivity());
        }

        @Test
        @DisplayName("estimatedSize increases with more listings")
        void estimatedSizeGrows() {
            LFGSyncPayload empty = LFGSyncPayload.empty();
            LFGListing listing = LFGListing.create(UUID.randomUUID(), "Leader", LFGActivity.QUEST,
                "Desc", 2, 4, 1, false, 100L);
            LFGSyncPayload withOne = LFGSyncPayload.fromListings(List.of(listing));
            assertTrue(withOne.estimatedSize() > empty.estimatedSize());
        }
    }

    // ==========================================
    // LFGManager.ApplyResult Tests
    // ==========================================

    @Nested
    @DisplayName("LFGManager.ApplyResult")
    class ApplyResultTests {

        @ParameterizedTest
        @EnumSource(LFGManager.ApplyResult.class)
        @DisplayName("All apply results have non-empty message")
        void allResultsHaveMessage(LFGManager.ApplyResult result) {
            assertNotNull(result.getMessage());
            assertFalse(result.getMessage().isEmpty());
        }

        @Test
        @DisplayName("INVITE_SENT message is positive")
        void inviteSentMessagePositive() {
            assertTrue(LFGManager.ApplyResult.INVITE_SENT.getMessage().contains("sent"));
        }
    }
}
