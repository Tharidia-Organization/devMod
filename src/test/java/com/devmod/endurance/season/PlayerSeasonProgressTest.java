package com.devmod.endurance.season;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSeasonProgressTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private PlayerSeasonProgress progress;

    @BeforeEach
    void setUp() {
        progress = new PlayerSeasonProgress(PLAYER, 1);
    }

    @Nested
    @DisplayName("XP and tier management")
    class XPTests {
        @Test
        @DisplayName("Starts at tier 1 with 0 XP")
        void initialState() {
            assertEquals(0, progress.getTotalXP());
            assertEquals(1, progress.getCurrentTier());
            assertEquals(0, progress.getCurrentTierXP());
        }

        @Test
        @DisplayName("Adding XP increases total")
        void addXpIncreasesTotal() {
            progress.addXP(500);
            assertEquals(500, progress.getTotalXP());
        }

        @Test
        @DisplayName("Adding 0 or negative XP is ignored")
        void addZeroOrNegativeIgnored() {
            progress.addXP(0);
            assertEquals(0, progress.getTotalXP());
            progress.addXP(-100);
            assertEquals(0, progress.getTotalXP());
        }

        @Test
        @DisplayName("Tier advances at XP_PER_TIER boundary")
        void tierAdvancesAtBoundary() {
            progress.addXP(SeasonPassSystem.XP_PER_TIER); // Exactly 1 tier
            assertEquals(2, progress.getCurrentTier());
            assertEquals(0, progress.getCurrentTierXP());
        }

        @Test
        @DisplayName("Tier is clamped at MAX_TIER")
        void tierClampedAtMax() {
            progress.addXP(SeasonPassSystem.XP_PER_TIER * 200); // Way past max
            assertEquals(SeasonPassSystem.MAX_TIER, progress.getCurrentTier());
        }

        @Test
        @DisplayName("getTierProgress returns 0.0-1.0")
        void tierProgressRange() {
            assertEquals(0f, progress.getTierProgress(), 0.001f);
            progress.addXP(SeasonPassSystem.XP_PER_TIER / 2);
            assertEquals(0.5f, progress.getTierProgress(), 0.001f);
        }

        @Test
        @DisplayName("getXPToNextTier calculates remaining")
        void xpToNextTier() {
            assertEquals(SeasonPassSystem.XP_PER_TIER, progress.getXPToNextTier());
            progress.addXP(300);
            assertEquals(SeasonPassSystem.XP_PER_TIER - 300, progress.getXPToNextTier());
        }

        @Test
        @DisplayName("getXPToNextTier returns 0 at max tier")
        void xpToNextTierAtMax() {
            progress.addXP(SeasonPassSystem.XP_PER_TIER * 200);
            assertEquals(0, progress.getXPToNextTier());
        }
    }

    @Nested
    @DisplayName("Reward management")
    class RewardTests {
        @Test
        @DisplayName("Free rewards can be unlocked and claimed")
        void freeRewardFlow() {
            assertFalse(progress.canClaimFreeReward(1));
            progress.unlockFreeReward(1);
            assertTrue(progress.canClaimFreeReward(1));
            progress.claimFreeReward(1);
            assertFalse(progress.canClaimFreeReward(1));
        }

        @Test
        @DisplayName("Premium rewards require premium status")
        void premiumRewardRequiresPremium() {
            progress.unlockPremiumReward(1);
            assertFalse(progress.canClaimPremiumReward(1)); // Not premium

            progress.setPremium(true);
            assertTrue(progress.canClaimPremiumReward(1));

            progress.claimPremiumReward(1);
            assertFalse(progress.canClaimPremiumReward(1));
        }

        @Test
        @DisplayName("getUnclaimedFreeRewardCount tracks unclaimed")
        void unclaimedFreeCount() {
            assertEquals(0, progress.getUnclaimedFreeRewardCount());
            progress.unlockFreeReward(1);
            progress.unlockFreeReward(2);
            assertEquals(2, progress.getUnclaimedFreeRewardCount());
            progress.claimFreeReward(1);
            assertEquals(1, progress.getUnclaimedFreeRewardCount());
        }

        @Test
        @DisplayName("getUnclaimedPremiumRewardCount returns 0 without premium")
        void unclaimedPremiumWithoutPremium() {
            progress.unlockPremiumReward(1);
            assertEquals(0, progress.getUnclaimedPremiumRewardCount());
        }

        @Test
        @DisplayName("getTotalUnclaimedRewards sums both tracks")
        void totalUnclaimed() {
            progress.setPremium(true);
            progress.unlockFreeReward(1);
            progress.unlockPremiumReward(1);
            assertEquals(2, progress.getTotalUnclaimedRewards());
        }

        @Test
        @DisplayName("getClaimedFreeRewards returns immutable copy")
        void claimedFreeRewardsImmutable() {
            progress.unlockFreeReward(1);
            progress.claimFreeReward(1);
            var claimed = progress.getClaimedFreeRewards();
            assertTrue(claimed.contains(1));
            assertThrows(UnsupportedOperationException.class, () -> claimed.add(999));
        }
    }

    @Nested
    @DisplayName("Stats tracking")
    class StatsTests {
        @Test
        @DisplayName("Kill tracking accumulates")
        void killsAccumulate() {
            progress.addKills(5);
            progress.addKills(3);
            assertEquals(8, progress.getTotalKills());
        }

        @Test
        @DisplayName("Wave tracking counts up")
        void wavesCounted() {
            progress.addWave();
            progress.addWave();
            assertEquals(2, progress.getTotalWaves());
        }

        @Test
        @DisplayName("Boss kills tracked")
        void bossKills() {
            progress.addBossKill();
            assertEquals(1, progress.getBossKills());
        }

        @Test
        @DisplayName("Challenges completed tracked")
        void challengesCounted() {
            progress.addChallengeCompleted();
            progress.addChallengeCompleted();
            assertEquals(2, progress.getChallengesCompleted());
        }

        @Test
        @DisplayName("Highest combo tracks max only")
        void highestComboTracksMax() {
            progress.updateHighestCombo(10);
            progress.updateHighestCombo(5);   // lower, should not update
            progress.updateHighestCombo(15);
            assertEquals(15, progress.getHighestCombo());
        }
    }

    @Nested
    @DisplayName("Style rank comparison")
    class StyleRankTests {
        @Test
        @DisplayName("Best style rank updates when better")
        void bestRankUpdates() {
            progress.updateBestStyleRank("C");
            assertEquals("C", progress.getBestStyleRank());
            progress.updateBestStyleRank("A");
            assertEquals("A", progress.getBestStyleRank());
        }

        @Test
        @DisplayName("Best style rank does not downgrade")
        void bestRankNoDowngrade() {
            progress.updateBestStyleRank("SS");
            progress.updateBestStyleRank("B");
            assertEquals("SS", progress.getBestStyleRank());
        }

        @Test
        @DisplayName("SSS is highest possible rank")
        void sssIsHighest() {
            progress.updateBestStyleRank("SSS");
            progress.updateBestStyleRank("SS");
            assertEquals("SSS", progress.getBestStyleRank());
        }

        @Test
        @DisplayName("Null or empty rank does not update")
        void nullOrEmptyIgnored() {
            progress.updateBestStyleRank("A");
            progress.updateBestStyleRank(null);
            assertEquals("A", progress.getBestStyleRank());
            progress.updateBestStyleRank("");
            assertEquals("A", progress.getBestStyleRank());
        }

        @Test
        @DisplayName("Case insensitive rank comparison")
        void caseInsensitiveRank() {
            progress.updateBestStyleRank("b");
            progress.updateBestStyleRank("A");
            assertEquals("A", progress.getBestStyleRank());
        }
    }

    @Nested
    @DisplayName("Summary and stats")
    class SummaryTests {
        @Test
        @DisplayName("getSummary creates correct ProgressSummary")
        void summaryCorrect() {
            progress.addXP(1500);
            progress.setPremium(true);
            progress.unlockFreeReward(1);

            PlayerSeasonProgress.ProgressSummary summary = progress.getSummary();
            assertEquals(PLAYER, summary.playerId());
            assertEquals(1, summary.seasonNumber());
            assertTrue(summary.premium());
            assertEquals(1, summary.unclaimedRewards());
        }

        @Test
        @DisplayName("getSeasonStats captures all tracked values")
        void seasonStats() {
            progress.addKills(100);
            progress.addWave();
            progress.addBossKill();
            progress.addChallengeCompleted();
            progress.updateHighestCombo(50);
            progress.updateBestStyleRank("S");
            progress.addXP(5000);

            PlayerSeasonProgress.SeasonStats stats = progress.getSeasonStats();
            assertEquals(100, stats.totalKills());
            assertEquals(1, stats.totalWaves());
            assertEquals(1, stats.bossKills());
            assertEquals(1, stats.challengesCompleted());
            assertEquals(50, stats.highestCombo());
            assertEquals("S", stats.bestStyleRank());
            assertEquals(5000, stats.totalXP());
        }
    }

    @Test
    @DisplayName("XP boost is initially inactive")
    void xpBoostInitiallyInactive() {
        assertFalse(progress.hasXpBoost());
        assertEquals(1.0f, progress.getXpBoostMultiplier(), 0.001f);
        assertEquals(0L, progress.getXpBoostRemainingSeconds());
    }

    @Test
    @DisplayName("XP boost activates with multiplier and duration")
    void xpBoostActivates() {
        progress.applyXPBoost(2.0f, 30); // 2x for 30 minutes
        assertTrue(progress.hasXpBoost());
        assertEquals(2.0f, progress.getXpBoostMultiplier(), 0.001f);
        assertTrue(progress.getXpBoostRemainingSeconds() > 0);
    }
}
