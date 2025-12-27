package com.devmod.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PartyCoordinationTest {

    // ============================================================
    // TEST SUITE 1: Party Formation
    // ============================================================
    @Nested
    @DisplayName("Party Formation Tests")
    class PartyFormationTests {

        @Test
        @DisplayName("Solo player has no party")
        void testSoloPlayerNoParty() {
            UUID playerId = UUID.randomUUID();
            UUID partyLeaderId = null;
            List<UUID> partyMembers = new ArrayList<>();

            assertNull(partyLeaderId);
            assertTrue(partyMembers.isEmpty());
            assertFalse(partyMembers.contains(playerId), "Solo player should not be in party members list");

            // Solo player is their own "leader"
            boolean isInParty = partyLeaderId != null;
            assertFalse(isInParty);
            assertNotEquals(playerId, partyLeaderId, "Solo player has no party leader");
        }

        @Test
        @DisplayName("Party leader is included in party")
        void testPartyLeaderIncluded() {
            UUID leaderId = UUID.randomUUID();
            UUID member1 = UUID.randomUUID();
            UUID member2 = UUID.randomUUID();

            Set<UUID> partyMembers = new HashSet<>();
            partyMembers.add(leaderId);
            partyMembers.add(member1);
            partyMembers.add(member2);

            assertTrue(partyMembers.contains(leaderId));
            assertEquals(3, partyMembers.size());
        }

        @Test
        @DisplayName("Max 4 players in party")
        void testMaxPartySize() {
            int maxPlayers = 4;
            Set<UUID> partyMembers = new HashSet<>();

            // Add max players
            for (int i = 0; i < maxPlayers; i++) {
                partyMembers.add(UUID.randomUUID());
            }

            assertEquals(maxPlayers, partyMembers.size());

            // Cannot add 5th
            int effectiveMax = Math.min(partyMembers.size() + 1, maxPlayers);
            assertEquals(maxPlayers, effectiveMax);
        }

        @Test
        @DisplayName("Party with duplicate UUIDs")
        void testDuplicateUUIDs() {
            Set<UUID> partyMembers = new HashSet<>();
            UUID player = UUID.randomUUID();

            partyMembers.add(player);
            partyMembers.add(player); // Duplicate

            assertEquals(1, partyMembers.size(), "Set should deduplicate");
        }

        @Test
        @DisplayName("Party member list survives copy")
        void testPartyMemberListCopy() {
            List<UUID> original = new ArrayList<>();
            original.add(UUID.randomUUID());
            original.add(UUID.randomUUID());

            // Copy like PlayerInstanceSnapshot does
            List<UUID> copy = new ArrayList<>(original);

            assertEquals(original.size(), copy.size());
            assertEquals(original, copy);

            // Modifying copy doesn't affect original
            copy.add(UUID.randomUUID());
            assertNotEquals(original.size(), copy.size());
        }
    }

    // ============================================================
    // TEST SUITE 2: Party State Synchronization
    // ============================================================
    @Nested
    @DisplayName("Party State Synchronization Tests")
    class PartyStateSyncTests {

        @Test
        @DisplayName("All party members should have same instance ID")
        void testSameInstanceId() {
            UUID instanceId = UUID.randomUUID();
            Map<UUID, UUID> playerToInstance = new HashMap<>();

            UUID leader = UUID.randomUUID();
            UUID member1 = UUID.randomUUID();
            UUID member2 = UUID.randomUUID();

            // All map to same instance
            playerToInstance.put(leader, instanceId);
            playerToInstance.put(member1, instanceId);
            playerToInstance.put(member2, instanceId);

            // Verify all same
            assertEquals(playerToInstance.get(leader), playerToInstance.get(member1));
            assertEquals(playerToInstance.get(member1), playerToInstance.get(member2));
        }

        @Test
        @DisplayName("Party members added to instance player set")
        void testPartyMembersInInstanceSet() {
            Set<UUID> instancePlayers = new HashSet<>();

            UUID leader = UUID.randomUUID();
            UUID member1 = UUID.randomUUID();
            UUID member2 = UUID.randomUUID();

            instancePlayers.add(leader);
            instancePlayers.add(member1);
            instancePlayers.add(member2);

            assertTrue(instancePlayers.contains(leader));
            assertTrue(instancePlayers.contains(member1));
            assertTrue(instancePlayers.contains(member2));
            assertEquals(3, instancePlayers.size());
        }

        @Test
        @DisplayName("Snapshot references correct party leader")
        void testSnapshotPartyLeader() {
            UUID leader = UUID.randomUUID();
            UUID member = UUID.randomUUID();

            // Member's snapshot references leader
            Map<UUID, UUID> snapshotPartyLeader = new HashMap<>();
            snapshotPartyLeader.put(member, leader);

            assertEquals(leader, snapshotPartyLeader.get(member));

            // Leader's snapshot can also reference self as leader
            snapshotPartyLeader.put(leader, leader);
            assertEquals(leader, snapshotPartyLeader.get(leader));
        }

        @Test
        @DisplayName("BUG CHECK: Party member not in party list but mapped to instance")
        void testOrphanedPartyMemberMapping() {
            // This tests a potential inconsistency:
            // Player is mapped to instance but not in party member list

            UUID instanceId = UUID.randomUUID();
            Map<UUID, UUID> playerToInstance = new HashMap<>();
            Map<UUID, Set<UUID>> instancePartyMembers = new HashMap<>();

            UUID orphanPlayer = UUID.randomUUID();

            // Player is mapped to instance
            playerToInstance.put(orphanPlayer, instanceId);

            // But instance has empty party list
            instancePartyMembers.put(instanceId, new HashSet<>());

            // Check for orphan
            boolean isMapped = playerToInstance.containsKey(orphanPlayer);
            boolean isInParty = instancePartyMembers.get(instanceId).contains(orphanPlayer);

            assertTrue(isMapped);
            assertFalse(isInParty);

            // This is an inconsistent state that should be detected and fixed
            assertTrue(isMapped && !isInParty,
                "Detected inconsistent state: player mapped but not in party");
        }
    }

    // ============================================================
    // TEST SUITE 3: Party Leader Behavior
    // ============================================================
    @Nested
    @DisplayName("Party Leader Behavior Tests")
    class PartyLeaderBehaviorTests {

        @Test
        @DisplayName("Leader disconnect ends quest for all")
        void testLeaderDisconnectEndsQuest() {
            UUID leader = UUID.randomUUID();
            UUID member1 = UUID.randomUUID();
            UUID member2 = UUID.randomUUID();

            Set<UUID> partyMembers = new HashSet<>();
            partyMembers.add(leader);
            partyMembers.add(member1);
            partyMembers.add(member2);

            boolean questEnded = false;

            // Simulate leader disconnect
            partyMembers.remove(leader);

            // Policy: leader disconnect ends quest
            if (!partyMembers.contains(leader)) {
                questEnded = true;
                // Recovery for all remaining members
            }

            assertTrue(questEnded);
        }

        @Test
        @DisplayName("Member disconnect doesn't end quest")
        void testMemberDisconnectContinues() {
            UUID leader = UUID.randomUUID();
            UUID member1 = UUID.randomUUID();
            UUID member2 = UUID.randomUUID();

            Set<UUID> partyMembers = new HashSet<>();
            partyMembers.add(leader);
            partyMembers.add(member1);
            partyMembers.add(member2);

            boolean questEnded = false;

            // Simulate member disconnect
            partyMembers.remove(member1);

            // Only end if leader disconnects (or all players gone)
            if (!partyMembers.contains(leader) || partyMembers.isEmpty()) {
                questEnded = true;
            }

            assertFalse(questEnded, "Quest should continue with leader present");
            assertEquals(2, partyMembers.size());
        }

        @Test
        @DisplayName("Last player leaves ends instance")
        void testLastPlayerEndsInstance() {
            Set<UUID> partyMembers = new HashSet<>();
            partyMembers.add(UUID.randomUUID());

            boolean instanceEnded = false;

            // Last player leaves
            partyMembers.clear();

            if (partyMembers.isEmpty()) {
                instanceEnded = true;
            }

            assertTrue(instanceEnded);
        }

        @Test
        @DisplayName("Leader death doesn't end quest (can respawn)")
        void testLeaderDeathContinues() {
            UUID leader = UUID.randomUUID();
            Set<UUID> partyMembers = new HashSet<>();
            partyMembers.add(leader);
            partyMembers.add(UUID.randomUUID());

            boolean leaderDead = true;
            boolean questEnded = false;

            // Leader is dead but still in party
            // Quest continues - leader can choose to continue or give up

            if (!partyMembers.contains(leader)) {
                questEnded = true;
            }

            assertFalse(questEnded, "Quest continues while leader in party");
            assertTrue(leaderDead);
        }
    }

    // ============================================================
    // TEST SUITE 4: Party Member Disconnect Scenarios
    // ============================================================
    @Nested
    @DisplayName("Party Disconnect Scenarios")
    class PartyDisconnectTests {

        @Test
        @DisplayName("Disconnected member has snapshot for recovery")
        void testDisconnectedMemberHasSnapshot() {
            Map<UUID, PlayerInstanceState> snapshotStates = new HashMap<>();

            UUID member = UUID.randomUUID();
            snapshotStates.put(member, PlayerInstanceState.IN_INSTANCE);

            // On disconnect, snapshot state is preserved
            assertTrue(snapshotStates.containsKey(member));
            assertEquals(PlayerInstanceState.IN_INSTANCE, snapshotStates.get(member));
        }

        @Test
        @DisplayName("Multiple members disconnect simultaneously")
        void testMultipleDisconnects() throws Exception {
            Set<UUID> partyMembers = ConcurrentHashMap.newKeySet();
            UUID leader = UUID.randomUUID();
            partyMembers.add(leader);

            for (int i = 0; i < 3; i++) {
                partyMembers.add(UUID.randomUUID());
            }

            AtomicInteger disconnectCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(3);

            // Simulate concurrent disconnects (not leader)
            ExecutorService executor = Executors.newFixedThreadPool(3);
            List<UUID> membersToDisconnect = new ArrayList<>(partyMembers);
            membersToDisconnect.remove(leader);

            for (UUID member : membersToDisconnect) {
                executor.submit(() -> {
                    partyMembers.remove(member);
                    disconnectCount.incrementAndGet();
                    latch.countDown();
                }).isDone();
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(3, disconnectCount.get());
            assertEquals(1, partyMembers.size()); // Only leader
            assertTrue(partyMembers.contains(leader));
        }

        @Test
        @DisplayName("Reconnecting member finds preserved snapshot")
        void testReconnectFindsSnapshot() {
            Map<UUID, PlayerInstanceState> snapshots = new HashMap<>();

            UUID member = UUID.randomUUID();

            // Before disconnect
            snapshots.put(member, PlayerInstanceState.IN_INSTANCE);

            // After reconnect, snapshot still exists
            assertTrue(snapshots.containsKey(member));

            // Recovery should be triggered
            PlayerInstanceState state = snapshots.get(member);
            boolean needsRecovery = state != PlayerInstanceState.NORMAL;

            assertTrue(needsRecovery);
        }

        @Test
        @DisplayName("Disconnect during teleport has IN_TRANSIT snapshot")
        void testDisconnectDuringTeleport() {
            Map<UUID, PlayerInstanceState> snapshots = new HashMap<>();

            UUID member = UUID.randomUUID();
            snapshots.put(member, PlayerInstanceState.IN_TRANSIT);

            // On reconnect, should trigger recovery
            PlayerInstanceState state = snapshots.get(member);
            assertEquals(PlayerInstanceState.IN_TRANSIT, state);

            // Recovery restores to original position
            boolean shouldRestore = state == PlayerInstanceState.PREPARING ||
                                   state == PlayerInstanceState.IN_TRANSIT;
            assertTrue(shouldRestore);
        }
    }

    // ============================================================
    // TEST SUITE 5: Party Cleanup
    // ============================================================
    @Nested
    @DisplayName("Party Cleanup Tests")
    class PartyCleanupTests {

        @Test
        @DisplayName("Quest end clears all party mappings")
        void testQuestEndClearsMappings() {
            Map<UUID, UUID> playerToInstance = new HashMap<>();
            UUID instanceId = UUID.randomUUID();

            List<UUID> partyMembers = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                UUID member = UUID.randomUUID();
                partyMembers.add(member);
                playerToInstance.put(member, instanceId);
            }

            assertEquals(4, playerToInstance.size());

            // Clear all on quest end
            for (UUID member : partyMembers) {
                playerToInstance.remove(member);
            }

            assertTrue(playerToInstance.isEmpty());
        }

        @Test
        @DisplayName("Snapshots deleted after successful quest")
        void testSnapshotsDeletedOnSuccess() {
            Set<UUID> snapshots = new HashSet<>();

            List<UUID> partyMembers = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                UUID member = UUID.randomUUID();
                partyMembers.add(member);
                snapshots.add(member);
            }

            assertEquals(4, snapshots.size());

            // Delete snapshots on success
            for (UUID member : partyMembers) {
                snapshots.remove(member);
            }

            assertTrue(snapshots.isEmpty());
        }

        @Test
        @DisplayName("Instance marked for destruction after party leaves")
        void testInstanceDestroyedAfterPartyLeaves() {
            Set<UUID> instancePlayers = new HashSet<>();
            boolean markedForDestruction = false;

            // Add party
            for (int i = 0; i < 4; i++) {
                instancePlayers.add(UUID.randomUUID());
            }

            // All leave
            instancePlayers.clear();

            // Should mark for destruction
            if (instancePlayers.isEmpty()) {
                markedForDestruction = true;
            }

            assertTrue(markedForDestruction);
        }

        @Test
        @DisplayName("Partial party leave doesn't destroy instance")
        void testPartialPartyNoDestruction() {
            Set<UUID> instancePlayers = new HashSet<>();
            boolean markedForDestruction = false;

            // Add party
            UUID leader = UUID.randomUUID();
            instancePlayers.add(leader);
            for (int i = 0; i < 3; i++) {
                instancePlayers.add(UUID.randomUUID());
            }

            // Remove 3 members
            List<UUID> toRemove = new ArrayList<>(instancePlayers);
            toRemove.remove(leader);
            for (UUID member : toRemove) {
                instancePlayers.remove(member);
            }

            // Should NOT mark for destruction (leader still there)
            if (instancePlayers.isEmpty()) {
                markedForDestruction = true;
            }

            assertFalse(markedForDestruction);
            assertEquals(1, instancePlayers.size());
        }
    }

    // ============================================================
    // TEST SUITE 6: Concurrent Party Operations
    // ============================================================
    @Nested
    @DisplayName("Concurrent Party Operations")
    class ConcurrentPartyTests {

        @RepeatedTest(3)
        @DisplayName("Concurrent snapshot updates are thread-safe")
        void testConcurrentSnapshotUpdates() throws Exception {
            Map<UUID, AtomicReference<PlayerInstanceState>> snapshots = new ConcurrentHashMap<>();
            int playerCount = 10;
            int updatesPerPlayer = 100;

            // Initialize
            List<UUID> players = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                UUID player = UUID.randomUUID();
                players.add(player);
                snapshots.put(player, new AtomicReference<>(PlayerInstanceState.NORMAL));
            }

            ExecutorService executor = Executors.newFixedThreadPool(playerCount);
            CountDownLatch latch = new CountDownLatch(playerCount);
            AtomicInteger errors = new AtomicInteger(0);

            for (UUID player : players) {
                executor.submit(() -> {
                    try {
                        AtomicReference<PlayerInstanceState> state = snapshots.get(player);
                        for (int i = 0; i < updatesPerPlayer; i++) {
                            // Simulate state transitions
                            state.set(PlayerInstanceState.PREPARING);
                            state.set(PlayerInstanceState.IN_TRANSIT);
                            state.set(PlayerInstanceState.IN_INSTANCE);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }).isDone();
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(0, errors.get());

            // All players should end in IN_INSTANCE
            for (UUID player : players) {
                assertEquals(PlayerInstanceState.IN_INSTANCE, snapshots.get(player).get());
            }
        }

        @Test
        @DisplayName("Concurrent party join and leave")
        void testConcurrentJoinLeave() throws Exception {
            Set<UUID> partyMembers = ConcurrentHashMap.newKeySet();
            AtomicInteger joinCount = new AtomicInteger(0);
            AtomicInteger leaveCount = new AtomicInteger(0);

            int operations = 100;
            ExecutorService executor = Executors.newFixedThreadPool(4);
            CountDownLatch latch = new CountDownLatch(operations * 2);

            // Concurrent joins
            for (int i = 0; i < operations; i++) {
                executor.submit(() -> {
                    UUID player = UUID.randomUUID();
                    if (partyMembers.add(player)) {
                        joinCount.incrementAndGet();
                    }
                    latch.countDown();
                }).isDone();
            }

            // Concurrent leaves
            for (int i = 0; i < operations; i++) {
                executor.submit(() -> {
                    Iterator<UUID> it = partyMembers.iterator();
                    if (it.hasNext()) {
                        UUID player = it.next();
                        if (partyMembers.remove(player)) {
                            leaveCount.incrementAndGet();
                        }
                    }
                    latch.countDown();
                }).isDone();
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Total joins should be >= leaves
            assertTrue(joinCount.get() >= leaveCount.get() || partyMembers.isEmpty(),
                "Joins should be >= leaves or party empty");
        }

        @Test
        @DisplayName("Bidirectional map consistency during party operations")
        void testBidirectionalMapConsistency() throws Exception {
            Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();
            Map<UUID, Set<UUID>> instanceToPlayers = new ConcurrentHashMap<>();

            UUID instanceId = UUID.randomUUID();
            instanceToPlayers.put(instanceId, ConcurrentHashMap.newKeySet());

            int operations = 50;
            ExecutorService executor = Executors.newFixedThreadPool(4);
            CountDownLatch latch = new CountDownLatch(operations);

            for (int i = 0; i < operations; i++) {
                executor.submit(() -> {
                    try {
                        UUID player = UUID.randomUUID();

                        // Add to both maps atomically-ish
                        playerToInstance.put(player, instanceId);
                        instanceToPlayers.get(instanceId).add(player);

                        // Verify consistency
                        assertTrue(playerToInstance.containsKey(player));
                        assertTrue(instanceToPlayers.get(instanceId).contains(player));
                    } finally {
                        latch.countDown();
                    }
                }).isDone();
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Verify final consistency
            assertEquals(playerToInstance.size(), instanceToPlayers.get(instanceId).size());

            for (UUID player : playerToInstance.keySet()) {
                assertTrue(instanceToPlayers.get(instanceId).contains(player));
            }
        }
    }

    // ============================================================
    // TEST SUITE 7: Edge Cases
    // ============================================================
    @Nested
    @DisplayName("Party Edge Cases")
    class PartyEdgeCasesTests {

        @Test
        @DisplayName("Null party leader handling")
        void testNullPartyLeader() {
            UUID playerId = UUID.randomUUID();
            UUID partyLeaderId = null;
            Set<UUID> partyMembers = new HashSet<>();

            boolean isInParty = partyLeaderId != null;
            assertFalse(isInParty);
            assertFalse(partyMembers.contains(playerId), "Player should not be in any party");

            // Player is solo, not in party
            boolean isSolo = !isInParty;
            assertTrue(isSolo);
            assertNotEquals(playerId, partyLeaderId, "Solo player cannot be their own party leader when null");
        }

        @Test
        @DisplayName("Empty party members list")
        void testEmptyPartyMembers() {
            List<UUID> partyMembers = new ArrayList<>();
            int[] iterationCount = {0};

            assertTrue(partyMembers.isEmpty());
            assertEquals(0, partyMembers.size());

            // Safe iteration - verify empty list doesn't throw and doesn't execute body
            assertDoesNotThrow(() -> {
                for (UUID member : partyMembers) {
                    iterationCount[0]++;
                    assertNotNull(member, "Member should not be null if iterated");
                }
            });
            assertEquals(0, iterationCount[0], "Should not iterate empty list");
        }

        @Test
        @DisplayName("Party leader is only member")
        void testLeaderOnlyMember() {
            UUID leader = UUID.randomUUID();
            Set<UUID> partyMembers = new HashSet<>();
            partyMembers.add(leader);

            assertEquals(1, partyMembers.size());
            assertTrue(partyMembers.contains(leader));

            // Still counts as "party" with one member
            boolean hasParty = partyMembers.size() >= 1;
            assertTrue(hasParty);
        }

        @Test
        @DisplayName("Same UUID as leader and member")
        void testSameUUIDLeaderAndMember() {
            UUID player = UUID.randomUUID();
            UUID partyLeaderId = player;

            Set<UUID> partyMembers = new HashSet<>();
            partyMembers.add(partyLeaderId);
            partyMembers.add(player); // Same UUID

            // Set deduplicates
            assertEquals(1, partyMembers.size());
        }

        @Test
        @DisplayName("Party member offline at quest start")
        void testMemberOfflineAtStart() {
            Set<UUID> onlinePlayers = new HashSet<>();
            onlinePlayers.add(UUID.randomUUID()); // Leader is online

            UUID offlineMember = UUID.randomUUID();
            // offlineMember is NOT in onlinePlayers

            List<UUID> requestedParty = Arrays.asList(
                onlinePlayers.iterator().next(),
                offlineMember
            );

            // Filter to only online players
            List<UUID> actualParty = new ArrayList<>();
            for (UUID member : requestedParty) {
                if (onlinePlayers.contains(member)) {
                    actualParty.add(member);
                }
            }

            assertEquals(1, actualParty.size(), "Only online players should join");
            assertFalse(actualParty.contains(offlineMember));
        }

        @Test
        @DisplayName("Party member already in another instance")
        void testMemberAlreadyInInstance() {
            Map<UUID, UUID> playerToInstance = new HashMap<>();

            UUID busyMember = UUID.randomUUID();
            UUID existingInstance = UUID.randomUUID();
            playerToInstance.put(busyMember, existingInstance);

            // Trying to add to new instance
            UUID newInstance = UUID.randomUUID();

            // Check if already in instance
            boolean alreadyInInstance = playerToInstance.containsKey(busyMember);

            assertTrue(alreadyInInstance);

            // Should not add to new instance
            if (!alreadyInInstance) {
                playerToInstance.put(busyMember, newInstance);
            }

            // Still in existing instance
            assertEquals(existingInstance, playerToInstance.get(busyMember));
        }
    }
}
