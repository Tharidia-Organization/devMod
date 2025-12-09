package com.frenkvs.devmod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multiplayer data isolation regression tests.
 *
 * These tests verify that per-player data (like ImpactData) is correctly
 * isolated between different players in a multiplayer environment.
 *
 * The actual ImpactData class uses ConcurrentHashMap<UUID, ImpactData> for
 * player isolation. These tests verify the pattern works correctly.
 */
public class MultiplayerDataIsolationTest {

    /**
     * Mock ImpactData storage that mirrors the real implementation pattern.
     * Real code: private static final Map<UUID, ImpactData> IMPACTS_BY_PLAYER = new ConcurrentHashMap<>();
     */
    static class MockPlayerImpactStorage {
        private static final Map<UUID, MockImpactData> IMPACTS_BY_PLAYER = new ConcurrentHashMap<>();
        private static final long EXPIRE_MS = 3000;

        public static void store(UUID playerUUID, MockImpactData data) {
            if (playerUUID == null || data == null) return;
            IMPACTS_BY_PLAYER.put(playerUUID, data);
        }

        public static MockImpactData get(UUID playerUUID) {
            if (playerUUID == null) return null;
            MockImpactData data = IMPACTS_BY_PLAYER.get(playerUUID);
            if (data != null && data.isExpired()) {
                IMPACTS_BY_PLAYER.remove(playerUUID, data);
                return null;
            }
            return data;
        }

        public static void clearForPlayer(UUID playerUUID) {
            if (playerUUID != null) {
                IMPACTS_BY_PLAYER.remove(playerUUID);
            }
        }

        public static void clearAll() {
            IMPACTS_BY_PLAYER.clear();
        }

        public static int getPlayerCount() {
            return IMPACTS_BY_PLAYER.size();
        }
    }

    /**
     * Mock impact data for testing.
     */
    static class MockImpactData {
        private final UUID attackerUUID;
        private final String targetName;
        private final float damage;
        private final long timestamp;

        public MockImpactData(UUID attackerUUID, String targetName, float damage) {
            this.attackerUUID = attackerUUID;
            this.targetName = targetName;
            this.damage = damage;
            this.timestamp = System.currentTimeMillis();
        }

        public UUID getAttackerUUID() { return attackerUUID; }
        public String getTargetName() { return targetName; }
        public float getDamage() { return damage; }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > MockPlayerImpactStorage.EXPIRE_MS;
        }
    }

    // Test UUIDs representing different players
    private static final UUID PLAYER_1 = UUID.randomUUID();
    private static final UUID PLAYER_2 = UUID.randomUUID();
    private static final UUID PLAYER_3 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockPlayerImpactStorage.clearAll();
    }

    // ============================================
    // Basic Isolation Tests
    // ============================================

    @Nested
    @DisplayName("Basic Player Data Isolation")
    class BasicIsolationTests {

        @Test
        @DisplayName("Each player has separate impact data")
        void testSeparateDataPerPlayer() {
            MockImpactData impact1 = new MockImpactData(PLAYER_1, "Zombie", 10.0f);
            MockImpactData impact2 = new MockImpactData(PLAYER_2, "Skeleton", 15.0f);

            MockPlayerImpactStorage.store(PLAYER_1, impact1);
            MockPlayerImpactStorage.store(PLAYER_2, impact2);

            // Verify player 1 sees their own data
            MockImpactData retrieved1 = MockPlayerImpactStorage.get(PLAYER_1);
            assertNotNull(retrieved1);
            assertEquals("Zombie", retrieved1.getTargetName());
            assertEquals(10.0f, retrieved1.getDamage());

            // Verify player 2 sees their own data
            MockImpactData retrieved2 = MockPlayerImpactStorage.get(PLAYER_2);
            assertNotNull(retrieved2);
            assertEquals("Skeleton", retrieved2.getTargetName());
            assertEquals(15.0f, retrieved2.getDamage());

            // Verify they are different objects
            assertNotSame(retrieved1, retrieved2);
        }

        @Test
        @DisplayName("Player cannot see another player's impact data")
        void testNoDataLeakage() {
            MockImpactData impact1 = new MockImpactData(PLAYER_1, "Creeper", 20.0f);
            MockPlayerImpactStorage.store(PLAYER_1, impact1);

            // Player 2 should see null (no data stored for them)
            MockImpactData retrieved2 = MockPlayerImpactStorage.get(PLAYER_2);
            assertNull(retrieved2);

            // Player 1 still sees their data
            MockImpactData retrieved1 = MockPlayerImpactStorage.get(PLAYER_1);
            assertNotNull(retrieved1);
            assertEquals("Creeper", retrieved1.getTargetName());
        }

        @Test
        @DisplayName("Clearing one player's data doesn't affect others")
        void testIndependentClearing() {
            MockPlayerImpactStorage.store(PLAYER_1, new MockImpactData(PLAYER_1, "Zombie", 10.0f));
            MockPlayerImpactStorage.store(PLAYER_2, new MockImpactData(PLAYER_2, "Skeleton", 15.0f));

            // Clear player 1's data
            MockPlayerImpactStorage.clearForPlayer(PLAYER_1);

            // Player 1 should have no data
            assertNull(MockPlayerImpactStorage.get(PLAYER_1));

            // Player 2 should still have their data
            MockImpactData retrieved2 = MockPlayerImpactStorage.get(PLAYER_2);
            assertNotNull(retrieved2);
            assertEquals("Skeleton", retrieved2.getTargetName());
        }

        @Test
        @DisplayName("New impact overwrites old for same player")
        void testOverwriteSamePlayer() {
            MockImpactData impact1 = new MockImpactData(PLAYER_1, "Zombie", 10.0f);
            MockImpactData impact2 = new MockImpactData(PLAYER_1, "Enderman", 25.0f);

            MockPlayerImpactStorage.store(PLAYER_1, impact1);
            MockPlayerImpactStorage.store(PLAYER_1, impact2);

            MockImpactData retrieved = MockPlayerImpactStorage.get(PLAYER_1);
            assertNotNull(retrieved);
            assertEquals("Enderman", retrieved.getTargetName());
            assertEquals(25.0f, retrieved.getDamage());
        }

        @Test
        @DisplayName("Null UUID is handled gracefully")
        void testNullUUIDHandling() {
            MockImpactData impact = new MockImpactData(PLAYER_1, "Test", 10.0f);

            // Store with null should not throw
            assertDoesNotThrow(() -> MockPlayerImpactStorage.store(null, impact));

            // Get with null should return null
            assertNull(MockPlayerImpactStorage.get(null));

            // Clear with null should not throw
            assertDoesNotThrow(() -> MockPlayerImpactStorage.clearForPlayer(null));
        }
    }

    // ============================================
    // Concurrent Access Tests
    // ============================================

    @Nested
    @DisplayName("Concurrent Access Safety")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("Multiple players can store/retrieve concurrently")
        void testConcurrentStoreRetrieve() throws InterruptedException {
            int numPlayers = 100;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(numPlayers * 2);
            AtomicInteger errors = new AtomicInteger(0);

            // Generate unique UUIDs for each player
            UUID[] playerUUIDs = new UUID[numPlayers];
            for (int i = 0; i < numPlayers; i++) {
                playerUUIDs[i] = UUID.randomUUID();
            }

            // Concurrent stores
            for (int i = 0; i < numPlayers; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        MockImpactData data = new MockImpactData(
                            playerUUIDs[idx],
                            "Target_" + idx,
                            idx * 1.5f
                        );
                        MockPlayerImpactStorage.store(playerUUIDs[idx], data);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Concurrent retrieves
            for (int i = 0; i < numPlayers; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        // Small delay to ensure some stores have completed
                        Thread.sleep(10);
                        MockImpactData data = MockPlayerImpactStorage.get(playerUUIDs[idx]);
                        // Data might not exist yet due to race condition, that's OK
                        if (data != null) {
                            assertEquals("Target_" + idx, data.getTargetName());
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            executor.shutdown();
            assertEquals(0, errors.get(), "No errors should occur during concurrent access");
        }

        @Test
        @DisplayName("Concurrent clear doesn't cause race conditions")
        void testConcurrentClear() throws InterruptedException {
            int iterations = 50;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch latch = new CountDownLatch(iterations * 3);
            AtomicInteger errors = new AtomicInteger(0);

            for (int i = 0; i < iterations; i++) {
                // Store
                executor.submit(() -> {
                    try {
                        MockPlayerImpactStorage.store(PLAYER_1,
                            new MockImpactData(PLAYER_1, "Test", 10.0f));
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });

                // Retrieve
                executor.submit(() -> {
                    try {
                        MockPlayerImpactStorage.get(PLAYER_1);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });

                // Clear
                executor.submit(() -> {
                    try {
                        MockPlayerImpactStorage.clearForPlayer(PLAYER_1);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            executor.shutdown();
            assertEquals(0, errors.get());
        }

        @Test
        @DisplayName("ClearAll during concurrent access is safe")
        void testClearAllDuringAccess() throws InterruptedException {
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(200);
            AtomicInteger errors = new AtomicInteger(0);

            // Mix of operations
            for (int i = 0; i < 50; i++) {
                final UUID uuid = UUID.randomUUID();

                executor.submit(() -> {
                    try {
                        MockPlayerImpactStorage.store(uuid, new MockImpactData(uuid, "Test", 10.0f));
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });

                executor.submit(() -> {
                    try {
                        MockPlayerImpactStorage.get(uuid);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });

                executor.submit(() -> {
                    try {
                        MockPlayerImpactStorage.clearAll();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });

                executor.submit(() -> {
                    try {
                        MockPlayerImpactStorage.getPlayerCount();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            executor.shutdown();
            assertEquals(0, errors.get());
        }
    }

    // ============================================
    // UUID Uniqueness Tests
    // ============================================

    @Nested
    @DisplayName("UUID Uniqueness Verification")
    class UUIDUniquenessTests {

        @Test
        @DisplayName("Same UUID always retrieves same player's data")
        void testUUIDConsistency() {
            UUID player = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

            MockImpactData impact = new MockImpactData(player, "Warden", 100.0f);
            MockPlayerImpactStorage.store(player, impact);

            // Multiple retrieves should return same data
            for (int i = 0; i < 10; i++) {
                MockImpactData retrieved = MockPlayerImpactStorage.get(player);
                assertNotNull(retrieved);
                assertEquals("Warden", retrieved.getTargetName());
                assertEquals(100.0f, retrieved.getDamage());
            }
        }

        @Test
        @DisplayName("Different UUID strings create isolated storage")
        void testUUIDIsolation() {
            UUID player1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
            UUID player2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

            MockPlayerImpactStorage.store(player1, new MockImpactData(player1, "Mob1", 10.0f));
            MockPlayerImpactStorage.store(player2, new MockImpactData(player2, "Mob2", 20.0f));

            assertEquals("Mob1", MockPlayerImpactStorage.get(player1).getTargetName());
            assertEquals("Mob2", MockPlayerImpactStorage.get(player2).getTargetName());
        }
    }

    // ============================================
    // Memory Management Tests
    // ============================================

    @Nested
    @DisplayName("Memory Management")
    class MemoryManagementTests {

        @Test
        @DisplayName("Player count tracks active entries")
        void testPlayerCountTracking() {
            assertEquals(0, MockPlayerImpactStorage.getPlayerCount());

            MockPlayerImpactStorage.store(PLAYER_1, new MockImpactData(PLAYER_1, "A", 10.0f));
            assertEquals(1, MockPlayerImpactStorage.getPlayerCount());

            MockPlayerImpactStorage.store(PLAYER_2, new MockImpactData(PLAYER_2, "B", 20.0f));
            assertEquals(2, MockPlayerImpactStorage.getPlayerCount());

            MockPlayerImpactStorage.clearForPlayer(PLAYER_1);
            assertEquals(1, MockPlayerImpactStorage.getPlayerCount());

            MockPlayerImpactStorage.clearAll();
            assertEquals(0, MockPlayerImpactStorage.getPlayerCount());
        }

        @Test
        @DisplayName("Many players don't cause issues")
        void testManyPlayers() {
            int numPlayers = 1000;

            for (int i = 0; i < numPlayers; i++) {
                UUID uuid = UUID.randomUUID();
                MockPlayerImpactStorage.store(uuid, new MockImpactData(uuid, "Target_" + i, i));
            }

            assertEquals(numPlayers, MockPlayerImpactStorage.getPlayerCount());

            MockPlayerImpactStorage.clearAll();
            assertEquals(0, MockPlayerImpactStorage.getPlayerCount());
        }
    }

    // ============================================
    // Expiration Tests
    // ============================================

    @Nested
    @DisplayName("Data Expiration")
    class ExpirationTests {

        @Test
        @DisplayName("Expired data returns null")
        void testExpiredDataReturnsNull() throws InterruptedException {
            // Create an already-expired mock for testing
            MockImpactData impact = new MockImpactData(PLAYER_1, "Test", 10.0f) {
                @Override
                public boolean isExpired() {
                    return true; // Force expired
                }
            };

            MockPlayerImpactStorage.store(PLAYER_1, impact);

            // Should return null because data is expired
            assertNull(MockPlayerImpactStorage.get(PLAYER_1));
        }

        @Test
        @DisplayName("Non-expired data is returned")
        void testNonExpiredDataReturned() {
            MockImpactData impact = new MockImpactData(PLAYER_1, "Fresh", 15.0f);
            MockPlayerImpactStorage.store(PLAYER_1, impact);

            MockImpactData retrieved = MockPlayerImpactStorage.get(PLAYER_1);
            assertNotNull(retrieved);
            assertEquals("Fresh", retrieved.getTargetName());
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Store null data is ignored")
        void testStoreNullData() {
            MockPlayerImpactStorage.store(PLAYER_1, null);
            assertNull(MockPlayerImpactStorage.get(PLAYER_1));
        }

        @Test
        @DisplayName("Same data object stored for multiple players")
        void testSharedDataObject() {
            // This shouldn't happen in practice, but test that UUIDs are independent
            MockImpactData sharedData = new MockImpactData(PLAYER_1, "Shared", 50.0f);

            MockPlayerImpactStorage.store(PLAYER_1, sharedData);
            MockPlayerImpactStorage.store(PLAYER_2, sharedData);

            // Both should retrieve (even if same object)
            assertNotNull(MockPlayerImpactStorage.get(PLAYER_1));
            assertNotNull(MockPlayerImpactStorage.get(PLAYER_2));

            // Clearing one doesn't affect the other
            MockPlayerImpactStorage.clearForPlayer(PLAYER_1);
            assertNull(MockPlayerImpactStorage.get(PLAYER_1));
            assertNotNull(MockPlayerImpactStorage.get(PLAYER_2));
        }

        @Test
        @DisplayName("Repeated clears are safe")
        void testRepeatedClears() {
            MockPlayerImpactStorage.store(PLAYER_1, new MockImpactData(PLAYER_1, "Test", 10.0f));

            // Multiple clears should be safe
            for (int i = 0; i < 10; i++) {
                assertDoesNotThrow(() -> MockPlayerImpactStorage.clearForPlayer(PLAYER_1));
            }

            assertNull(MockPlayerImpactStorage.get(PLAYER_1));
        }

        @Test
        @DisplayName("Get before any store returns null")
        void testGetBeforeStore() {
            UUID newPlayer = UUID.randomUUID();
            assertNull(MockPlayerImpactStorage.get(newPlayer));
        }
    }
}
