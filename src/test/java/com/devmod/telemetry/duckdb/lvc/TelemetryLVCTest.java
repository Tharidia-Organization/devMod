package com.devmod.telemetry.duckdb.lvc;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.telemetry.duckdb.lvc.TelemetryLVC.PlayerDamageSummary;
import com.devmod.telemetry.duckdb.lvc.TelemetryLVC.PlayerKillSummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TelemetryLVC (Last Value Cache).
 */
class TelemetryLVCTest {

    private TelemetryLVC lvc;
    private UUID testPlayerId;

    @BeforeEach
    void setUp() {
        lvc = TelemetryLVC.INSTANCE;
        lvc.clear(); // Start fresh
        testPlayerId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        lvc.clear();
    }

    // ============================================
    // ENTRY LIFECYCLE TESTS
    // ============================================

    @Test
    @DisplayName("createEntry creates new player entry")
    void testCreateEntry() {
        assertFalse(lvc.hasEntry(testPlayerId));

        lvc.createEntry(testPlayerId);

        assertTrue(lvc.hasEntry(testPlayerId));
        assertNotNull(lvc.getEntry(testPlayerId));
    }

    @Test
    @DisplayName("removeEntry removes player entry")
    void testRemoveEntry() {
        lvc.createEntry(testPlayerId);
        assertTrue(lvc.hasEntry(testPlayerId));

        PlayerLVCEntry removed = lvc.removeEntry(testPlayerId);

        assertNotNull(removed);
        assertEquals(testPlayerId, removed.getPlayerId());
        assertFalse(lvc.hasEntry(testPlayerId));
    }

    @Test
    @DisplayName("getOrCreateEntry creates if not exists")
    void testGetOrCreateEntry() {
        assertFalse(lvc.hasEntry(testPlayerId));

        PlayerLVCEntry entry = lvc.getOrCreateEntry(testPlayerId);

        assertNotNull(entry);
        assertTrue(lvc.hasEntry(testPlayerId));
    }

    @Test
    @DisplayName("clear removes all entries")
    void testClear() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        lvc.createEntry(player1);
        lvc.createEntry(player2);

        lvc.clear();

        assertFalse(lvc.hasEntry(player1));
        assertFalse(lvc.hasEntry(player2));
        assertEquals(0, lvc.getActivePlayerCount());
    }

    // ============================================
    // RECORD HIT TESTS
    // ============================================

    @Test
    @DisplayName("recordHit updates damage and kill counters")
    void testRecordHit_updatesCounters() {
        lvc.createEntry(testPlayerId);

        lvc.recordHit(testPlayerId, 25.0, false, "sword");
        lvc.recordHit(testPlayerId, 30.0, true, "sword"); // kill

        assertEquals(55.0, lvc.getTotalDamageDealt(testPlayerId), 0.1);
        assertEquals(1, lvc.getKillCount(testPlayerId));
    }

    @Test
    @DisplayName("recordHit with critical flag tracks crits")
    void testRecordHit_tracksCriticals() {
        lvc.createEntry(testPlayerId);

        lvc.recordHit(testPlayerId, 20.0, false, false, "sword");
        lvc.recordHit(testPlayerId, 40.0, true, true, "sword"); // crit + kill

        PlayerLVCEntry entry = lvc.getEntry(testPlayerId);
        assertNotNull(entry);
        assertEquals(1, entry.getCriticalHitCount());
    }

    @Test
    @DisplayName("recordHit updates server totals")
    void testRecordHit_updatesServerTotals() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        lvc.recordHit(player1, 100.0, true, "sword");
        lvc.recordHit(player2, 50.0, false, "axe");

        assertEquals(150.0, lvc.getServerTotalDamage(), 0.1);
        assertEquals(1, lvc.getServerTotalKills());
    }

    // ============================================
    // RECORD DEATH TESTS
    // ============================================

    @Test
    @DisplayName("recordDeath increments death counter")
    void testRecordDeath() {
        lvc.createEntry(testPlayerId);

        lvc.recordDeath(testPlayerId);
        lvc.recordDeath(testPlayerId);

        assertEquals(2, lvc.getDeathCount(testPlayerId));
        assertEquals(2, lvc.getServerTotalDeaths());
    }

    // ============================================
    // COMBO TESTS
    // ============================================

    @Test
    @DisplayName("kills increment combo")
    void testCombo_incrementsOnKill() {
        lvc.createEntry(testPlayerId);

        lvc.recordHit(testPlayerId, 10.0, true, "sword"); // kill
        lvc.recordHit(testPlayerId, 10.0, true, "sword"); // kill
        lvc.recordHit(testPlayerId, 10.0, true, "sword"); // kill

        assertEquals(3, lvc.getCurrentCombo(testPlayerId));
        assertEquals(3, lvc.getMaxCombo(testPlayerId));
    }

    @Test
    @DisplayName("recordComboBreak resets current combo")
    void testComboBreak() {
        lvc.createEntry(testPlayerId);
        lvc.recordHit(testPlayerId, 10.0, true, "sword");
        lvc.recordHit(testPlayerId, 10.0, true, "sword");

        assertEquals(2, lvc.getCurrentCombo(testPlayerId));

        lvc.recordComboBreak(testPlayerId);

        assertEquals(0, lvc.getCurrentCombo(testPlayerId));
        assertEquals(2, lvc.getMaxCombo(testPlayerId)); // Max preserved
    }

    // ============================================
    // ABILITY TESTS
    // ============================================

    @Test
    @DisplayName("recordAbility tracks ability usage")
    void testRecordAbility() {
        lvc.createEntry(testPlayerId);

        lvc.recordAbility(testPlayerId, "dash", true, 10.0, 0.0);
        lvc.recordAbility(testPlayerId, "dash", true, 10.0, 0.0);
        lvc.recordAbility(testPlayerId, "dodge", true, 5.0, 15.0);

        assertEquals(2, lvc.getAbilityUsage(testPlayerId, "dash"));
        assertEquals(1, lvc.getAbilityUsage(testPlayerId, "dodge"));
    }

    // ============================================
    // XP TESTS
    // ============================================

    @Test
    @DisplayName("recordXp tracks XP earned")
    void testRecordXp() {
        lvc.createEntry(testPlayerId);

        lvc.recordXp(testPlayerId, 100);
        lvc.recordXp(testPlayerId, 50);

        PlayerLVCEntry entry = lvc.getEntry(testPlayerId);
        assertNotNull(entry);
        assertEquals(150, entry.getTotalXpEarned());
        assertEquals(150, lvc.getServerTotalXp());
    }

    @Test
    @DisplayName("recordXp ignores zero and negative values")
    void testRecordXp_ignoresInvalid() {
        lvc.createEntry(testPlayerId);

        lvc.recordXp(testPlayerId, 100);
        lvc.recordXp(testPlayerId, 0);
        lvc.recordXp(testPlayerId, -50);

        PlayerLVCEntry entry = lvc.getEntry(testPlayerId);
        assertNotNull(entry);
        assertEquals(100, entry.getTotalXpEarned());
    }

    // ============================================
    // WAVE TESTS
    // ============================================

    @Test
    @DisplayName("recordWaveComplete tracks waves")
    void testRecordWaveComplete() {
        lvc.createEntry(testPlayerId);

        lvc.recordWaveComplete(testPlayerId, 1);
        lvc.recordWaveComplete(testPlayerId, 2);
        lvc.recordWaveComplete(testPlayerId, 3);

        assertEquals(3, lvc.getHighestWave(testPlayerId));
        assertEquals(4, lvc.getCurrentWave(testPlayerId)); // Next wave
    }

    @Test
    @DisplayName("recordWaveStart sets current wave")
    void testRecordWaveStart() {
        lvc.createEntry(testPlayerId);

        lvc.recordWaveStart(testPlayerId, 5);

        assertEquals(5, lvc.getCurrentWave(testPlayerId));
        assertEquals(5, lvc.getHighestWave(testPlayerId));
    }

    // ============================================
    // LEADERBOARD TESTS
    // ============================================

    @Test
    @DisplayName("getTopDamageDealers returns sorted list")
    void testGetTopDamageDealers_sorted() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();

        lvc.recordHit(player1, 100.0, false, "sword");
        lvc.recordHit(player2, 300.0, false, "sword");
        lvc.recordHit(player3, 200.0, false, "sword");

        List<PlayerDamageSummary> top = lvc.getTopDamageDealers(10);

        assertEquals(3, top.size());
        assertEquals(player2, top.get(0).playerId()); // 300 damage - first
        assertEquals(player3, top.get(1).playerId()); // 200 damage - second
        assertEquals(player1, top.get(2).playerId()); // 100 damage - third
    }

    @Test
    @DisplayName("getTopDamageDealers respects limit")
    void testGetTopDamageDealers_respectsLimit() {
        for (int i = 0; i < 10; i++) {
            lvc.recordHit(UUID.randomUUID(), i * 10.0, false, "sword");
        }

        List<PlayerDamageSummary> top = lvc.getTopDamageDealers(3);

        assertEquals(3, top.size());
    }

    @Test
    @DisplayName("getTopKillers returns sorted list")
    void testGetTopKillers_sorted() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        lvc.recordHit(player1, 10.0, true, "sword");
        lvc.recordHit(player1, 10.0, true, "sword"); // 2 kills

        lvc.recordHit(player2, 10.0, true, "sword");
        lvc.recordHit(player2, 10.0, true, "sword");
        lvc.recordHit(player2, 10.0, true, "sword"); // 3 kills

        List<PlayerKillSummary> top = lvc.getTopKillers(10);

        assertEquals(2, top.size());
        assertEquals(player2, top.get(0).playerId()); // 3 kills - first
        assertEquals(player1, top.get(1).playerId()); // 2 kills - second
    }

    // ============================================
    // SNAPSHOT TESTS
    // ============================================

    @Test
    @DisplayName("getPlayerSnapshot returns full stats")
    void testGetPlayerSnapshot() {
        lvc.createEntry(testPlayerId);
        lvc.recordHit(testPlayerId, 50.0, true, "sword");
        lvc.recordXp(testPlayerId, 200);
        lvc.recordWaveComplete(testPlayerId, 2);

        var snapshot = lvc.getPlayerSnapshot(testPlayerId);

        assertNotNull(snapshot);
        assertEquals(testPlayerId, snapshot.playerId());
        assertEquals(50.0, snapshot.totalDamage(), 0.1);
        assertEquals(1, snapshot.killCount());
        assertEquals(200, snapshot.totalXpEarned());
        assertEquals(2, snapshot.highestWave());
    }

    @Test
    @DisplayName("getPlayerSnapshot returns null for unknown player")
    void testGetPlayerSnapshot_nullForUnknown() {
        var snapshot = lvc.getPlayerSnapshot(UUID.randomUUID());
        assertNull(snapshot);
    }

    // ============================================
    // SERVER STATS TESTS
    // ============================================

    @Test
    @DisplayName("getServerCombatSummary aggregates all players")
    void testGetServerCombatSummary() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        lvc.recordHit(player1, 100.0, true, "sword");
        lvc.recordHit(player2, 200.0, false, "axe");
        lvc.recordDeath(player1);
        lvc.recordXp(player2, 500);

        var summary = lvc.getServerCombatSummary();

        assertEquals(300.0, summary.totalDamage(), 0.1);
        assertEquals(1, summary.totalKills());
        assertEquals(1, summary.totalDeaths());
        assertEquals(500, summary.totalXp());
        assertEquals(2, summary.activePlayerCount());
    }

    @Test
    @DisplayName("getActivePlayerIds returns all player UUIDs")
    void testGetActivePlayerIds() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        lvc.createEntry(player1);
        lvc.createEntry(player2);

        List<UUID> ids = lvc.getActivePlayerIds();

        assertEquals(2, ids.size());
        assertTrue(ids.contains(player1));
        assertTrue(ids.contains(player2));
    }

    // ============================================
    // MEMORY ESTIMATION TESTS
    // ============================================

    @Test
    @DisplayName("getEstimatedMemoryUsage scales with player count")
    void testGetEstimatedMemoryUsage() {
        assertEquals(0, lvc.getEstimatedMemoryUsage());

        lvc.createEntry(UUID.randomUUID());
        assertEquals(1500, lvc.getEstimatedMemoryUsage()); // ~1.5KB per player

        lvc.createEntry(UUID.randomUUID());
        assertEquals(3000, lvc.getEstimatedMemoryUsage());
    }

    // ============================================
    // THREAD SAFETY TESTS
    // ============================================

    @Test
    @DisplayName("concurrent access is thread-safe")
    void testConcurrentAccess() throws InterruptedException {
        final int THREADS = 10;
        final int OPS_PER_THREAD = 100;
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    UUID playerId = UUID.randomUUID();
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        lvc.recordHit(playerId, 1.0, i % 10 == 0, "weapon" + threadId);
                        if (i % 5 == 0) {
                            lvc.recordAbility(playerId, "dash", true, 1.0, 0.0);
                        }
                        if (i % 20 == 0) {
                            lvc.getTopDamageDealers(5);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify no exceptions and data integrity
        assertEquals(THREADS, lvc.getActivePlayerCount());
        assertTrue(lvc.getServerTotalDamage() > 0);
    }

    // ============================================
    // WEAPON STATS TESTS
    // ============================================

    @Test
    @DisplayName("getWeaponKills tracks per-weapon kills")
    void testGetWeaponKills() {
        lvc.createEntry(testPlayerId);

        lvc.recordHit(testPlayerId, 10.0, true, "diamond_sword");
        lvc.recordHit(testPlayerId, 10.0, true, "diamond_sword");
        lvc.recordHit(testPlayerId, 10.0, true, "bow");

        Map<String, Integer> weaponKills = lvc.getWeaponKills(testPlayerId);

        assertEquals(2, weaponKills.get("diamond_sword"));
        assertEquals(1, weaponKills.get("bow"));
    }

    // ============================================
    // ACCURACY TESTS
    // ============================================

    @Test
    @DisplayName("getAccuracy returns hit ratio")
    void testGetAccuracy() {
        lvc.createEntry(testPlayerId);
        PlayerLVCEntry entry = Objects.requireNonNull(lvc.getEntry(testPlayerId), "entry");

        // 3 hits
        lvc.recordHit(testPlayerId, 10.0, false, "sword");
        lvc.recordHit(testPlayerId, 10.0, false, "sword");
        lvc.recordHit(testPlayerId, 10.0, false, "sword");

        // 2 misses
        entry.recordMiss();
        entry.recordMiss();

        // Accuracy = 3 / (3 + 2) = 0.6
        assertEquals(0.6, lvc.getAccuracy(testPlayerId), 0.01);
    }

    @Test
    @DisplayName("getKDRatio returns kills per death")
    void testGetKDRatio() {
        lvc.createEntry(testPlayerId);

        lvc.recordHit(testPlayerId, 10.0, true, "sword");
        lvc.recordHit(testPlayerId, 10.0, true, "sword");
        lvc.recordHit(testPlayerId, 10.0, true, "sword"); // 3 kills
        lvc.recordDeath(testPlayerId); // 1 death

        // K/D = 3 / 1 = 3.0
        PlayerLVCEntry entry = lvc.getEntry(testPlayerId);
        assertNotNull(entry);
        assertEquals(3.0, entry.getKDRatio(), 0.01);
    }
}
