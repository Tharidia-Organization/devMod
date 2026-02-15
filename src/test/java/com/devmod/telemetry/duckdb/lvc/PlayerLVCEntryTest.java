package com.devmod.telemetry.duckdb.lvc;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PlayerLVCEntry")
class PlayerLVCEntryTest {

    private PlayerLVCEntry entry;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        entry = new PlayerLVCEntry(playerId);
    }

    @Test
    @DisplayName("constructor initializes with correct defaults")
    void constructorInitializesDefaults() {
        assertEquals(playerId, entry.getPlayerId());
        assertNotNull(entry.getSessionStart());
        assertNotNull(entry.getSessionId());
        assertEquals(0.0, entry.getTotalDamage(), 0.001);
        assertEquals(0, entry.getHitCount());
        assertEquals(0, entry.getKillCount());
        assertEquals(0, entry.getMissCount());
        assertEquals(0, entry.getDeathCount());
        assertEquals(0, entry.getCriticalHitCount());
        assertEquals(0, entry.getCurrentCombo());
        assertEquals(0, entry.getMaxCombo());
        assertEquals(0, entry.getHighestWave());
        assertEquals(0, entry.getCurrentWave());
        assertEquals(0, entry.getWavesCompleted());
        assertEquals(0, entry.getTotalXpEarned());
        assertNull(entry.getActiveQuestId());
    }

    // ============================================
    // recordHit tests
    // ============================================

    @Test
    @DisplayName("recordHit accumulates damage")
    void recordHitAccumulatesDamage() {
        entry.recordHit(10.5, false, false, "sword");
        entry.recordHit(20.3, false, false, "sword");

        assertEquals(30.8, entry.getTotalDamage(), 0.01);
        assertEquals(2, entry.getHitCount());
    }

    @Test
    @DisplayName("recordHit tracks kills and increments combo")
    void recordHitTracksKillsAndCombo() {
        entry.recordHit(10.0, true, false, "sword");
        entry.recordHit(10.0, true, false, "sword");

        assertEquals(2, entry.getKillCount());
        assertEquals(2, entry.getCurrentCombo());
        assertEquals(2, entry.getMaxCombo());
    }

    @Test
    @DisplayName("recordHit tracks critical hits")
    void recordHitTracksCriticals() {
        entry.recordHit(10.0, false, true, "sword");
        entry.recordHit(10.0, false, false, "sword");

        assertEquals(1, entry.getCriticalHitCount());
    }

    @Test
    @DisplayName("recordHit tracks per-weapon kills")
    void recordHitTracksWeaponKills() {
        entry.recordHit(10.0, true, false, "diamond_sword");
        entry.recordHit(15.0, true, false, "diamond_sword");
        entry.recordHit(20.0, true, false, "bow");

        Map<String, Integer> weaponKills = entry.getWeaponKills();
        assertEquals(2, weaponKills.get("diamond_sword"));
        assertEquals(1, weaponKills.get("bow"));
    }

    @Test
    @DisplayName("recordHit ignores null/empty weapon for weapon stats")
    void recordHitIgnoresNullWeapon() {
        entry.recordHit(10.0, true, false, null);
        entry.recordHit(10.0, true, false, "");

        assertTrue(entry.getWeaponKills().isEmpty());
        assertEquals(2, entry.getKillCount()); // kills still counted
    }

    @Test
    @DisplayName("weapon stats LRU evicts beyond max")
    void weaponStatsLRUEviction() {
        // AggregationConfig.MAX_WEAPONS_PER_PLAYER = 8
        for (int i = 0; i < 12; i++) {
            entry.recordHit(10.0, true, false, "weapon_" + i);
        }

        Map<String, Integer> weaponKills = entry.getWeaponKills();
        assertTrue(weaponKills.size() <= 8, "Should evict beyond max weapons");
    }

    // ============================================
    // recordMiss tests
    // ============================================

    @Test
    @DisplayName("recordMiss increments miss count")
    void recordMissIncrementsCount() {
        entry.recordMiss();
        entry.recordMiss();
        assertEquals(2, entry.getMissCount());
    }

    // ============================================
    // recordDeath tests
    // ============================================

    @Test
    @DisplayName("recordDeath increments death count and breaks combo")
    void recordDeathIncrementsAndBreaksCombo() {
        entry.recordHit(10.0, true, false, "sword");
        entry.recordHit(10.0, true, false, "sword");
        assertEquals(2, entry.getCurrentCombo());

        entry.recordDeath();
        assertEquals(1, entry.getDeathCount());
        assertEquals(0, entry.getCurrentCombo());
        assertEquals(2, entry.getMaxCombo()); // max preserved
    }

    // ============================================
    // Combo tests
    // ============================================

    @Test
    @DisplayName("max combo is preserved after break")
    void maxComboPreservedAfterBreak() {
        // Build combo to 5
        for (int i = 0; i < 5; i++) {
            entry.recordHit(10.0, true, false, "sword");
        }
        assertEquals(5, entry.getMaxCombo());

        entry.onComboBreak();
        assertEquals(0, entry.getCurrentCombo());
        assertEquals(5, entry.getMaxCombo());

        // New combo to 3
        for (int i = 0; i < 3; i++) {
            entry.recordHit(10.0, true, false, "sword");
        }
        assertEquals(3, entry.getCurrentCombo());
        assertEquals(5, entry.getMaxCombo()); // max still 5
    }

    // ============================================
    // Ability tests
    // ============================================

    @Test
    @DisplayName("recordAbility tracks usage counts")
    void recordAbilityTracksUsage() {
        entry.recordAbility("dash", true, 10.0, 0.0);
        entry.recordAbility("dash", false, 10.0, 0.0);
        entry.recordAbility("dodge", true, 5.0, 15.0);

        assertEquals(2, entry.getAbilityUsage("dash"));
        assertEquals(1, entry.getAbilityUsage("dodge"));
        assertEquals(0, entry.getAbilityUsage("nonexistent"));
    }

    @Test
    @DisplayName("recordAbility tracks successes separately")
    void recordAbilityTracksSuccesses() {
        entry.recordAbility("dash", true, 10.0, 0.0);
        entry.recordAbility("dash", false, 10.0, 0.0);

        assertEquals(1, entry.getAbilitySuccesses("dash"));
    }

    @Test
    @DisplayName("recordAbility ignores null/empty type")
    void recordAbilityIgnoresNullEmpty() {
        entry.recordAbility(null, true, 10.0, 0.0);
        entry.recordAbility("", true, 10.0, 0.0);
        assertTrue(entry.getAllAbilityUsage().isEmpty());
    }

    @Test
    @DisplayName("recordAbility tracks stamina and damage negated")
    void recordAbilityTracksStaminaAndNegated() {
        entry.recordAbility("dash", true, 20.0, 0.0);
        entry.recordAbility("dodge", true, 0.0, 15.0);

        assertEquals(20.0, entry.getTotalStaminaSpent(), 0.01);
        assertEquals(15.0, entry.getTotalDamageNegated(), 0.01);
    }

    @Test
    @DisplayName("recordAbility ignores negative stamina/negated")
    void recordAbilityIgnoresNegative() {
        entry.recordAbility("dash", true, -10.0, -5.0);

        assertEquals(0.0, entry.getTotalStaminaSpent(), 0.001);
        assertEquals(0.0, entry.getTotalDamageNegated(), 0.001);
    }

    // ============================================
    // XP tests
    // ============================================

    @Test
    @DisplayName("recordXp accumulates XP")
    void recordXpAccumulates() {
        entry.recordXp(100);
        entry.recordXp(50);
        assertEquals(150, entry.getTotalXpEarned());
    }

    @Test
    @DisplayName("recordXp ignores zero and negative")
    void recordXpIgnoresInvalid() {
        entry.recordXp(100);
        entry.recordXp(0);
        entry.recordXp(-50);
        assertEquals(100, entry.getTotalXpEarned());
    }

    // ============================================
    // Wave tests
    // ============================================

    @Test
    @DisplayName("recordWaveComplete tracks waves")
    void recordWaveCompleteTracksWaves() {
        entry.recordWaveComplete(1);
        entry.recordWaveComplete(2);
        entry.recordWaveComplete(3);

        assertEquals(3, entry.getHighestWave());
        assertEquals(4, entry.getCurrentWave()); // next wave
        assertEquals(3, entry.getWavesCompleted());
    }

    @Test
    @DisplayName("setCurrentWave updates both current and highest")
    void setCurrentWaveUpdates() {
        entry.setCurrentWave(5);

        assertEquals(5, entry.getCurrentWave());
        assertEquals(5, entry.getHighestWave());
    }

    @Test
    @DisplayName("setCurrentWave does not lower highest wave")
    void setCurrentWaveDoesNotLowerHighest() {
        entry.setCurrentWave(5);
        entry.setCurrentWave(3);

        assertEquals(3, entry.getCurrentWave());
        assertEquals(5, entry.getHighestWave());
    }

    // ============================================
    // Derived metrics tests
    // ============================================

    @Test
    @DisplayName("getAccuracy computes hit ratio")
    void getAccuracyComputesRatio() {
        entry.recordHit(10.0, false, false, "sword");
        entry.recordHit(10.0, false, false, "sword");
        entry.recordMiss();

        assertEquals(2.0 / 3.0, entry.getAccuracy(), 0.01);
    }

    @Test
    @DisplayName("getAccuracy returns 0 when no hits or misses")
    void getAccuracyZeroWhenEmpty() {
        assertEquals(0.0, entry.getAccuracy(), 0.001);
    }

    @Test
    @DisplayName("getKDRatio computes kills per death")
    void getKDRatioComputes() {
        entry.recordHit(10.0, true, false, "sword");
        entry.recordHit(10.0, true, false, "sword");
        entry.recordHit(10.0, true, false, "sword");
        entry.recordDeath();

        assertEquals(3.0, entry.getKDRatio(), 0.01);
    }

    @Test
    @DisplayName("getKDRatio returns kills when no deaths")
    void getKDRatioReturnsKillsWhenNoDeaths() {
        entry.recordHit(10.0, true, false, "sword");
        entry.recordHit(10.0, true, false, "sword");

        assertEquals(2.0, entry.getKDRatio(), 0.01);
    }

    // ============================================
    // Quest and session tests
    // ============================================

    @Test
    @DisplayName("setActiveQuest sets quest and resets stats")
    void setActiveQuestSetsAndResets() {
        entry.recordHit(100.0, true, false, "sword");
        assertEquals(100.0, entry.getTotalDamage(), 0.01);

        UUID questId = UUID.randomUUID();
        entry.setActiveQuest(questId);

        assertEquals(questId, entry.getActiveQuestId());
        assertEquals(0.0, entry.getTotalDamage(), 0.001); // reset
        assertEquals(0, entry.getKillCount()); // reset
    }

    @Test
    @DisplayName("resetSessionStats clears all counters")
    void resetSessionStatsClearsAll() {
        entry.recordHit(50.0, true, true, "sword");
        entry.recordMiss();
        entry.recordDeath();
        entry.recordAbility("dash", true, 10.0, 5.0);
        entry.recordXp(200);
        entry.recordWaveComplete(3);

        UUID oldSessionId = entry.getSessionId();
        entry.resetSessionStats();

        assertEquals(0.0, entry.getTotalDamage(), 0.001);
        assertEquals(0, entry.getHitCount());
        assertEquals(0, entry.getKillCount());
        assertEquals(0, entry.getMissCount());
        assertEquals(0, entry.getDeathCount());
        assertEquals(0, entry.getCriticalHitCount());
        assertEquals(0, entry.getCurrentCombo());
        assertEquals(0, entry.getMaxCombo());
        assertEquals(0, entry.getCurrentWave());
        assertEquals(0, entry.getHighestWave());
        assertEquals(0, entry.getWavesCompleted());
        assertEquals(0, entry.getTotalXpEarned());
        assertTrue(entry.getAllAbilityUsage().isEmpty());
        assertTrue(entry.getWeaponKills().isEmpty());
        assertFalse(oldSessionId.equals(entry.getSessionId())); // new session
    }

    // ============================================
    // Snapshot tests
    // ============================================

    @Test
    @DisplayName("snapshot creates immutable copy")
    void snapshotCreatesImmutableCopy() {
        entry.recordHit(50.0, true, true, "sword");
        entry.recordXp(100);

        PlayerLVCEntry.PlayerLVCSnapshot snapshot = entry.snapshot();

        assertNotNull(snapshot);
        assertEquals(playerId, snapshot.playerId());
        assertEquals(50.0, snapshot.totalDamage(), 0.01);
        assertEquals(1, snapshot.hitCount());
        assertEquals(1, snapshot.killCount());
        assertEquals(1, snapshot.criticalHitCount());
        assertEquals(100, snapshot.totalXpEarned());
        assertNotNull(snapshot.sessionStart());
    }

    // ============================================
    // Weapon damage tracking
    // ============================================

    @Test
    @DisplayName("getWeaponDamage tracks per-weapon damage")
    void getWeaponDamageTracksPerWeapon() {
        entry.recordHit(10.0, true, false, "sword");
        entry.recordHit(25.0, true, false, "bow");

        Map<String, Double> weaponDamage = entry.getWeaponDamage();
        assertEquals(10.0, weaponDamage.get("sword"), 0.01);
        assertEquals(25.0, weaponDamage.get("bow"), 0.01);
    }
}
