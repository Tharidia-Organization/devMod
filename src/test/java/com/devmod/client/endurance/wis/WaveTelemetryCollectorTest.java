package com.devmod.client.endurance.wis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

class WaveTelemetryCollectorTest {

    private WaveTelemetryCollector collector;
    private UUID questId;
    private static final int START_TICK = 1000;

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }

    @BeforeEach
    void setUp() {
        questId = UUID.randomUUID();
        collector = new WaveTelemetryCollector(questId, 1, START_TICK);
    }

    // ========== Initial state ==========

    @Test
    void initialState_isCollecting() {
        assertTrue(collector.isCollecting());
    }

    @Test
    void initialState_hasZeroKills() {
        assertEquals(0, collector.getTotalKills());
    }

    @Test
    void initialState_hasZeroDamage() {
        assertEquals(0f, collector.getTotalDamageDealt());
        assertEquals(0f, collector.getTotalDamageTaken());
    }

    @Test
    void initialState_hasCorrectWaveNumber() {
        assertEquals(1, collector.getWaveNumber());
    }

    @Test
    void initialState_hasCorrectQuestId() {
        assertEquals(questId, collector.getQuestId());
    }

    // ========== Kill recording ==========

    @Test
    void recordKill_incrementsKillCount() {
        recordSimpleKill("zombie", 5f, false, false, 1);
        assertEquals(1, collector.getTotalKills());
    }

    @Test
    void recordKill_tracksEliteKills() {
        recordSimpleKill("zombie", 5f, true, false, 1);
        assertEquals(1, collector.getEliteKills());
    }

    @Test
    void recordKill_tracksCriticalHits() {
        recordSimpleKill("zombie", 5f, false, true, 1);
        assertEquals(1, collector.getCriticalHits());
    }

    @Test
    void recordKill_addsDamageDealt() {
        recordSimpleKill("zombie", 7.5f, false, false, 1);
        assertEquals(7.5f, collector.getTotalDamageDealt(), 0.01f);
    }

    @Test
    void recordKill_tracksMaxCombo() {
        recordSimpleKill("zombie", 5f, false, false, 5);
        recordSimpleKill("zombie", 5f, false, false, 3);
        assertEquals(5, collector.getMaxCombo());
    }

    @Test
    void recordKill_addsToKillPositions() {
        recordSimpleKill("zombie", 5f, false, false, 1);
        assertEquals(1, collector.getKillPositions().size());
    }

    @Test
    void recordKill_addsEvent() {
        recordSimpleKill("zombie", 5f, false, false, 1);
        assertEquals(1, collector.getEventCount());
    }

    @Test
    void recordKill_updatesPerMobStats() {
        ResourceLocation zombieType = rl("zombie");
        recordKillWithType(zombieType, 5f, false, false, 1);
        recordKillWithType(zombieType, 8f, false, false, 2);

        var stats = collector.getMobStats().get(zombieType);
        assertNotNull(stats);
        assertEquals(2, stats.kills);
        assertEquals(13f, stats.totalDamageDealt, 0.01f);
    }

    @Test
    void recordKill_updatesPerWeaponStats() {
        collector.recordKill(UUID.randomUUID(), rl("zombie"), BlockPos.ZERO,
            10f, "diamond_sword", false, true, 1, 500, START_TICK + 10);

        var wStats = collector.getWeaponStats().get("diamond_sword");
        assertNotNull(wStats);
        assertEquals(1, wStats.kills);
        assertEquals(10f, wStats.totalDamage, 0.01f);
        assertEquals(1, wStats.criticalHits);
    }

    // ========== Damage recording ==========

    @Test
    void recordDamageDealt_addsToDamageTotal() {
        collector.recordDamageDealt(UUID.randomUUID(), rl("zombie"), 3f,
            "melee", false, BlockPos.ZERO, START_TICK + 5);
        assertEquals(3f, collector.getTotalDamageDealt(), 0.01f);
    }

    @Test
    void recordDamageDealt_tracksCriticals() {
        collector.recordDamageDealt(UUID.randomUUID(), rl("zombie"), 3f,
            "melee", true, BlockPos.ZERO, START_TICK + 5);
        assertEquals(1, collector.getCriticalHits());
    }

    @Test
    void recordDamageTaken_addsToDamageTotal() {
        collector.recordDamageTaken(UUID.randomUUID(), rl("zombie"), 4f,
            "mob_attack", 16f, BlockPos.ZERO, START_TICK + 5);
        assertEquals(4f, collector.getTotalDamageTaken(), 0.01f);
    }

    @Test
    void recordDamageTaken_addsToDamagePositions() {
        BlockPos pos = new BlockPos(10, 64, 20);
        collector.recordDamageTaken(UUID.randomUUID(), rl("zombie"), 4f,
            "mob_attack", 16f, pos, START_TICK + 5);
        assertEquals(1, collector.getDamagePositions().size());
        assertEquals(pos, collector.getDamagePositions().get(0));
    }

    @Test
    void recordDamageTaken_updatesPerMobDamageToPlayer() {
        ResourceLocation zombieType = rl("zombie");
        collector.recordDamageTaken(UUID.randomUUID(), zombieType, 6f,
            "mob_attack", 14f, BlockPos.ZERO, START_TICK + 5);

        var stats = collector.getMobStats().get(zombieType);
        assertNotNull(stats);
        assertEquals(6f, stats.totalDamageToPlayer, 0.01f);
    }

    // ========== Combo events ==========

    @Test
    void recordComboEvent_updatesMaxCombo() {
        collector.recordComboEvent(CombatEvent.ComboEvent.ComboEventType.MILESTONE_10,
            10, 500, "B", START_TICK + 20);
        assertEquals(10, collector.getMaxCombo());
    }

    @Test
    void recordComboEvent_updatesCurrentCombo() {
        collector.recordComboEvent(CombatEvent.ComboEvent.ComboEventType.COMBO_BREAK,
            0, 500, "B", START_TICK + 20);
        assertEquals(0, collector.getCurrentCombo());
    }

    // ========== Special actions ==========

    @Test
    void recordSpecialAction_tracksDodges() {
        collector.recordSpecialAction("dodge", 10, 5, BlockPos.ZERO, START_TICK + 10);
        assertEquals(1, collector.getDodges());
    }

    @Test
    void recordSpecialAction_tracksParries() {
        collector.recordSpecialAction("parry", 15, 10, BlockPos.ZERO, START_TICK + 10);
        assertEquals(1, collector.getParries());
    }

    @Test
    void recordSpecialAction_tracksPerfectDodge() {
        collector.recordSpecialAction("perfect_dodge", 20, 15, BlockPos.ZERO, START_TICK + 10);
        assertEquals(1, collector.getDodges());
    }

    @Test
    void recordSpecialAction_doesNotTrackUnknown() {
        collector.recordSpecialAction("block", 5, 2, BlockPos.ZERO, START_TICK + 10);
        assertEquals(0, collector.getDodges());
        assertEquals(0, collector.getParries());
    }

    // ========== Position snapshots ==========

    @Test
    void recordPositionSnapshot_addsToPositions() {
        collector.recordPositionSnapshot(new BlockPos(5, 64, 10), 20f, 3, true, START_TICK + 10);
        assertEquals(1, collector.getPlayerPositions().size());
    }

    @Test
    void recordPositionSnapshot_rateLimited() {
        // First snapshot
        collector.recordPositionSnapshot(BlockPos.ZERO, 20f, 3, true, START_TICK + 10);
        // Second snapshot too soon (interval is 10 ticks)
        collector.recordPositionSnapshot(BlockPos.ZERO, 20f, 3, true, START_TICK + 15);
        assertEquals(1, collector.getPlayerPositions().size());
    }

    @Test
    void recordPositionSnapshot_allowsAfterInterval() {
        collector.recordPositionSnapshot(BlockPos.ZERO, 20f, 3, true, START_TICK + 10);
        collector.recordPositionSnapshot(BlockPos.ZERO, 20f, 3, true, START_TICK + 21);
        assertEquals(2, collector.getPlayerPositions().size());
    }

    // ========== Mob spawn events ==========

    @Test
    void recordMobSpawn_updatesPerMobStats() {
        ResourceLocation zombieType = rl("zombie");
        collector.recordMobSpawn(UUID.randomUUID(), zombieType, BlockPos.ZERO, false, "", START_TICK + 1);

        var stats = collector.getMobStats().get(zombieType);
        assertNotNull(stats);
        assertEquals(1, stats.spawned);
    }

    @Test
    void recordMobSpawn_tracksElites() {
        ResourceLocation zombieType = rl("zombie");
        collector.recordMobSpawn(UUID.randomUUID(), zombieType, BlockPos.ZERO, true, "fire", START_TICK + 1);

        var stats = collector.getMobStats().get(zombieType);
        assertNotNull(stats);
        assertEquals(1, stats.eliteSpawned);
    }

    // ========== Player death ==========

    @Test
    void recordPlayerDeath_addsToDeathPositions() {
        BlockPos pos = new BlockPos(100, 64, 200);
        collector.recordPlayerDeath(pos);
        assertEquals(1, collector.getDeathPositions().size());
        assertEquals(pos, collector.getDeathPositions().get(0));
    }

    // ========== State control ==========

    @Test
    void stopCollecting_stopsAcceptingEvents() {
        collector.stopCollecting();
        assertFalse(collector.isCollecting());
        recordSimpleKill("zombie", 5f, false, false, 1);
        assertEquals(0, collector.getTotalKills());
    }

    @Test
    void clear_resetsAllState() {
        recordSimpleKill("zombie", 5f, true, true, 10);
        collector.recordDamageTaken(null, null, 3f, "fall", 17f, BlockPos.ZERO, START_TICK + 10);
        collector.recordPlayerDeath(BlockPos.ZERO);

        collector.clear();

        assertFalse(collector.isCollecting());
        assertEquals(0, collector.getTotalKills());
        assertEquals(0, collector.getEliteKills());
        assertEquals(0f, collector.getTotalDamageDealt());
        assertEquals(0f, collector.getTotalDamageTaken());
        assertEquals(0, collector.getMaxCombo());
        assertEquals(0, collector.getCriticalHits());
        assertEquals(0, collector.getDodges());
        assertEquals(0, collector.getParries());
        assertTrue(collector.getEvents().isEmpty());
        assertTrue(collector.getMobStats().isEmpty());
        assertTrue(collector.getWeaponStats().isEmpty());
        assertTrue(collector.getPlayerPositions().isEmpty());
        assertTrue(collector.getKillPositions().isEmpty());
        assertTrue(collector.getDeathPositions().isEmpty());
        assertTrue(collector.getDamagePositions().isEmpty());
    }

    // ========== Analysis getters ==========

    @Test
    void wasNoDamageWave_trueWhenNoDamageTaken() {
        recordSimpleKill("zombie", 5f, false, false, 1);
        assertTrue(collector.wasNoDamageWave());
    }

    @Test
    void wasNoDamageWave_falseWhenDamageTaken() {
        collector.recordDamageTaken(null, null, 1f, "fall", 19f, BlockPos.ZERO, START_TICK + 5);
        assertFalse(collector.wasNoDamageWave());
    }

    @Test
    void getAverageTTK_zeroWithNoKills() {
        assertEquals(0f, collector.getAverageTTK());
    }

    @Test
    void getAverageTTK_computesAverage() {
        collector.recordKill(UUID.randomUUID(), rl("zombie"), BlockPos.ZERO,
            5f, "sword", false, false, 1, 1000, START_TICK + 10);
        collector.recordKill(UUID.randomUUID(), rl("zombie"), BlockPos.ZERO,
            5f, "sword", false, false, 2, 3000, START_TICK + 20);
        // Average of 1000 and 3000 = 2000
        assertEquals(2000f, collector.getAverageTTK(), 0.1f);
    }

    @Test
    void getCriticalHitRate_zeroWithNoEvents() {
        assertEquals(0f, collector.getCriticalHitRate());
    }

    // ========== MobTypeStats ==========

    @Test
    void mobTypeStats_averageTTK() {
        var stats = new WaveTelemetryCollector.MobTypeStats();
        stats.kills = 2;
        stats.totalTimeToKillMs = 5000;
        assertEquals(2500f, stats.getAverageTimeToKill(), 0.1f);
    }

    @Test
    void mobTypeStats_killRate() {
        var stats = new WaveTelemetryCollector.MobTypeStats();
        stats.spawned = 10;
        stats.kills = 7;
        assertEquals(0.7f, stats.getKillRate(), 0.01f);
    }

    @Test
    void mobTypeStats_killRate_zeroSpawned() {
        var stats = new WaveTelemetryCollector.MobTypeStats();
        assertEquals(0f, stats.getKillRate());
    }

    // ========== WeaponStats ==========

    @Test
    void weaponStats_avgDamagePerKill() {
        var stats = new WaveTelemetryCollector.WeaponStats();
        stats.kills = 4;
        stats.totalDamage = 40f;
        assertEquals(10f, stats.getAverageDamagePerKill(), 0.01f);
    }

    @Test
    void weaponStats_criticalRate() {
        var stats = new WaveTelemetryCollector.WeaponStats();
        stats.kills = 10;
        stats.criticalHits = 3;
        assertEquals(0.3f, stats.getCriticalRate(), 0.01f);
    }

    @Test
    void weaponStats_zeroKills() {
        var stats = new WaveTelemetryCollector.WeaponStats();
        assertEquals(0f, stats.getAverageDamagePerKill());
        assertEquals(0f, stats.getCriticalRate());
    }

    // ========== Collections are unmodifiable ==========

    @Test
    void getEvents_returnsUnmodifiableList() {
        recordSimpleKill("zombie", 5f, false, false, 1);
        assertThrows(UnsupportedOperationException.class,
            () -> collector.getEvents().clear());
    }

    @Test
    void getMobStats_returnsUnmodifiableMap() {
        recordSimpleKill("zombie", 5f, false, false, 1);
        assertThrows(UnsupportedOperationException.class,
            () -> collector.getMobStats().clear());
    }

    @Test
    void getWeaponStats_returnsUnmodifiableMap() {
        collector.recordKill(UUID.randomUUID(), rl("zombie"), BlockPos.ZERO,
            5f, "sword", false, false, 1, 500, START_TICK + 10);
        assertThrows(UnsupportedOperationException.class,
            () -> collector.getWeaponStats().clear());
    }

    // ========== Helper methods ==========

    private void recordSimpleKill(String mobName, float damage, boolean isElite, boolean isCrit, int combo) {
        collector.recordKill(UUID.randomUUID(), rl(mobName), BlockPos.ZERO,
            damage, "sword", isElite, isCrit, combo, 500, START_TICK + 10);
    }

    private void recordKillWithType(ResourceLocation mobType, float damage, boolean isElite, boolean isCrit, int combo) {
        collector.recordKill(UUID.randomUUID(), mobType, BlockPos.ZERO,
            damage, "sword", isElite, isCrit, combo, 500, START_TICK + 10);
    }
}
