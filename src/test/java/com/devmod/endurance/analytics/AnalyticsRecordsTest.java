package com.devmod.endurance.analytics;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnalyticsRecordsTest {

    @Nested
    @DisplayName("CombatMetrics")
    class CombatMetricsTests {

        @Test
        @DisplayName("empty() creates zeroed metrics")
        void emptyMetrics() {
            CombatMetrics m = CombatMetrics.empty();
            assertEquals(0L, m.sessionDurationMs());
            assertEquals(1, m.currentWave());
            assertEquals(0f, m.totalDamageDealt(), 0.001f);
            assertEquals(0, m.totalKills());
            assertEquals(0, m.deaths());
            assertEquals("D", m.comboRank());
            assertEquals(1.0f, m.healthPercent(), 0.001f);
        }

        @Test
        @DisplayName("isStruggling when 2+ deaths")
        void strugglingDeaths() {
            CombatMetrics m = new CombatMetrics(
                10000L, 1, 5000L,
                100f, 50f, 50f, 25f, 10f, 10f,
                10, 5, 2, 0.2f, 10f,
                5, 3, 2, 2000f,
                2, 0.5f,
                5, "B", 5,
                "minecraft:sword", 100f, "minecraft:sword", Map.of(),
                1, 0.1f, Map.of()
            );
            assertTrue(m.isStruggling());
        }

        @Test
        @DisplayName("isStruggling when low DPS after 30s")
        void strugglingLowDps() {
            CombatMetrics m = new CombatMetrics(
                40000L, 1, 40000L,
                10f, 50f, 10f, 50f, 2f, 2f,
                5, 10, 0, 0f, 2f,
                2, 1, 5, 5000f,
                0, 0.8f,
                1, "D", 1,
                "minecraft:sword", 10f, "minecraft:sword", Map.of(),
                0, 0f, Map.of()
            );
            assertTrue(m.isStruggling());
        }

        @Test
        @DisplayName("isStruggling when taking > 50% of damage dealt")
        void strugglingHighDamageTaken() {
            CombatMetrics m = new CombatMetrics(
                10000L, 1, 5000L,
                100f, 60f, 50f, 30f, 10f, 10f,
                10, 5, 1, 0.1f, 10f,
                5, 3, 2, 2000f,
                0, 0.5f,
                3, "C", 3,
                "minecraft:sword", 100f, "minecraft:sword", Map.of(),
                0, 0f, Map.of()
            );
            assertTrue(m.isStruggling());
        }

        @Test
        @DisplayName("isPerformingWell requires 0 deaths, high crit, headshot, S+ rank")
        void performingWellCriteria() {
            CombatMetrics m = new CombatMetrics(
                30000L, 3, 10000L,
                500f, 50f, 200f, 20f, 20f, 20f,
                50, 3, 15, 0.3f, 10f,
                30, 10, 5, 1000f,
                0, 0.9f,
                25, "SS", 25,
                "minecraft:sword", 500f, "minecraft:sword", Map.of(),
                20, 0.4f, Map.of("HEAD", 20)
            );
            assertTrue(m.isPerformingWell());
        }

        @Test
        @DisplayName("isPerformingWell is false with deaths")
        void notPerformingWellWithDeaths() {
            CombatMetrics m = new CombatMetrics(
                30000L, 3, 10000L,
                500f, 50f, 200f, 20f, 20f, 20f,
                50, 3, 15, 0.3f, 10f,
                30, 10, 5, 1000f,
                1, 0.9f,
                25, "SSS", 25,
                "minecraft:sword", 500f, "minecraft:sword", Map.of(),
                20, 0.4f, Map.of("HEAD", 20)
            );
            assertFalse(m.isPerformingWell());
        }

        @Test
        @DisplayName("getPerformanceScore returns 0-100 range")
        void performanceScoreRange() {
            CombatMetrics empty = CombatMetrics.empty();
            int score = empty.getPerformanceScore();
            assertTrue(score >= 0 && score <= 100);
        }

        @Test
        @DisplayName("Deaths reduce performance score")
        void deathsReduceScore() {
            CombatMetrics noDeaths = new CombatMetrics(
                10000L, 1, 5000L,
                100f, 10f, 50f, 5f, 10f, 10f,
                10, 2, 2, 0.2f, 10f,
                5, 3, 2, 2000f,
                0, 0.8f,
                5, "A", 5,
                "minecraft:sword", 100f, "minecraft:sword", Map.of(),
                3, 0.3f, Map.of("HEAD", 3)
            );
            CombatMetrics withDeaths = new CombatMetrics(
                10000L, 1, 5000L,
                100f, 10f, 50f, 5f, 10f, 10f,
                10, 2, 2, 0.2f, 10f,
                5, 3, 2, 2000f,
                3, 0.8f,
                5, "A", 5,
                "minecraft:sword", 100f, "minecraft:sword", Map.of(),
                3, 0.3f, Map.of("HEAD", 3)
            );
            assertTrue(noDeaths.getPerformanceScore() > withDeaths.getPerformanceScore());
        }

        @Test
        @DisplayName("SSS combo rank gives highest score contribution")
        void sssRankBestContribution() {
            CombatMetrics sssRank = new CombatMetrics(
                10000L, 1, 5000L,
                0f, 0f, 0f, 0f, 0f, 0f,
                0, 0, 0, 0f, 0f,
                0, 0, 0, 0f,
                0, 1.0f,
                0, "SSS", 0,
                "minecraft:air", 0f, "minecraft:air", Map.of(),
                0, 0f, Map.of()
            );
            CombatMetrics dRank = new CombatMetrics(
                10000L, 1, 5000L,
                0f, 0f, 0f, 0f, 0f, 0f,
                0, 0, 0, 0f, 0f,
                0, 0, 0, 0f,
                0, 1.0f,
                0, "D", 0,
                "minecraft:air", 0f, "minecraft:air", Map.of(),
                0, 0f, Map.of()
            );
            assertTrue(sssRank.getPerformanceScore() > dRank.getPerformanceScore());
        }
    }

    @Nested
    @DisplayName("WaveSummary")
    class WaveSummaryTests {

        @Test
        @DisplayName("empty() creates zeroed summary")
        void emptySummary() {
            WaveSummary s = WaveSummary.empty(1);
            assertEquals(1, s.waveNumber());
            assertEquals(0L, s.completionTimeMs());
            assertEquals(0, s.kills());
            assertEquals("D", s.maxComboRank());
        }

        @Test
        @DisplayName("getDPS calculates correctly")
        void dpsCalculation() {
            WaveSummary s = new WaveSummary(1, 10000L, 100f, 30f, 5, 0, 2, 10, 5, "B", "minecraft:sword", Map.of());
            // 100 damage / 10 seconds = 10 DPS
            assertEquals(10f, s.getDPS(), 0.001f);
        }

        @Test
        @DisplayName("getDPS returns 0 for zero completion time")
        void dpsZeroTime() {
            WaveSummary s = WaveSummary.empty(1);
            assertEquals(0f, s.getDPS(), 0.001f);
        }

        @Test
        @DisplayName("getCritRate calculates correctly")
        void critRateCalc() {
            WaveSummary s = new WaveSummary(1, 5000L, 50f, 10f, 3, 0, 3, 10, 3, "C", "minecraft:sword", Map.of());
            assertEquals(0.3f, s.getCritRate(), 0.001f);
        }

        @Test
        @DisplayName("getCritRate returns 0 for zero hits")
        void critRateZeroHits() {
            WaveSummary s = WaveSummary.empty(1);
            assertEquals(0f, s.getCritRate(), 0.001f);
        }

        @Test
        @DisplayName("getHeadshotRate uses HEAD body part hits")
        void headshotRateCalc() {
            WaveSummary s = new WaveSummary(1, 5000L, 50f, 10f, 3, 0, 1, 10, 3, "C", "minecraft:sword",
                Map.of("HEAD", 4, "BODY", 6));
            assertEquals(0.4f, s.getHeadshotRate(), 0.001f);
        }

        @Test
        @DisplayName("getHeadshotRate returns 0 with no HEAD hits")
        void headshotRateNoHeads() {
            WaveSummary s = new WaveSummary(1, 5000L, 50f, 10f, 3, 0, 1, 10, 3, "C", "minecraft:sword",
                Map.of("BODY", 10));
            assertEquals(0f, s.getHeadshotRate(), 0.001f);
        }

        @Test
        @DisplayName("isCleanWave returns true for 0 deaths")
        void cleanWaveCheck() {
            WaveSummary clean = new WaveSummary(1, 5000L, 50f, 10f, 3, 0, 1, 10, 3, "C", "minecraft:sword", Map.of());
            WaveSummary dirty = new WaveSummary(1, 5000L, 50f, 10f, 3, 1, 1, 10, 3, "C", "minecraft:sword", Map.of());
            assertTrue(clean.isCleanWave());
            assertFalse(dirty.isCleanWave());
        }

        @Test
        @DisplayName("wasStruggling when 2+ deaths")
        void strugglingDeaths() {
            WaveSummary s = new WaveSummary(1, 5000L, 50f, 10f, 3, 2, 1, 10, 3, "C", "minecraft:sword", Map.of());
            assertTrue(s.wasStruggling());
        }

        @Test
        @DisplayName("wasStruggling when damage taken > 70% of dealt")
        void strugglingHighDamage() {
            WaveSummary s = new WaveSummary(1, 5000L, 100f, 80f, 3, 0, 1, 10, 3, "C", "minecraft:sword", Map.of());
            assertTrue(s.wasStruggling());
        }

        @Test
        @DisplayName("getGrade returns valid grade letters")
        void gradeLetters() {
            WaveSummary s = WaveSummary.empty(1);
            String grade = s.getGrade();
            assertTrue(grade.matches("[SABCDF]"), "Grade should be S/A/B/C/D/F, got: " + grade);
        }

        @Test
        @DisplayName("High performance wave gets S grade")
        void highPerformanceSGrade() {
            WaveSummary s = new WaveSummary(1, 5000L, 200f, 10f, 10, 0, 8, 10, 30, "SSS", "minecraft:sword",
                Map.of("HEAD", 5));
            // DPS=40 (40pts), clean (20pts), crit=0.8 (16pts), SSS (20pts) = 96 -> S
            assertEquals("S", s.getGrade());
        }
    }
}
