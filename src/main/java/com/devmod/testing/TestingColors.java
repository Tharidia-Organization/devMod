package com.devmod.testing;

import com.devmod.shared.SharedColorTokens;

/**
 * Shared color palette for testing systems (server-safe).
 */
public final class TestingColors {
    private TestingColors() {}

    public static final class Status {
        public static final int PENDING = SharedColorTokens.Testing.Status.PENDING;
        public static final int IN_PROGRESS = SharedColorTokens.Testing.Status.IN_PROGRESS;
        public static final int PASSED = SharedColorTokens.Testing.Status.PASSED;
        public static final int FAILED = SharedColorTokens.Testing.Status.FAILED;
        public static final int SKIPPED = SharedColorTokens.Testing.Status.SKIPPED;

        private Status() {}
    }

    public static final class Priority {
        public static final int CRITICAL = SharedColorTokens.Testing.Priority.CRITICAL;
        public static final int HIGH = SharedColorTokens.Testing.Priority.HIGH;
        public static final int MEDIUM = SharedColorTokens.Testing.Priority.MEDIUM;
        public static final int LOW = SharedColorTokens.Testing.Priority.LOW;

        private Priority() {}
    }

    public static final class Level {
        private Level() {}

    public static int forLevel(int level) {
        return SharedColorTokens.Testing.Level.forLevel(level);
    }
    }

    public static final class AchievementCategory {
        public static final int COMBAT = SharedColorTokens.Testing.AchievementCategory.COMBAT;
        public static final int PRECISION = SharedColorTokens.Testing.AchievementCategory.PRECISION;
        public static final int RANGED = SharedColorTokens.Testing.AchievementCategory.RANGED;
        public static final int SURVIVAL = SharedColorTokens.Testing.AchievementCategory.SURVIVAL;
        public static final int EXPLOSION = SharedColorTokens.Testing.AchievementCategory.EXPLOSION;
        public static final int ALCHEMY = SharedColorTokens.Testing.AchievementCategory.ALCHEMY;
        public static final int EXPLORER = SharedColorTokens.Testing.AchievementCategory.EXPLORER;
        public static final int DEDICATION = SharedColorTokens.Testing.AchievementCategory.DEDICATION;
        public static final int TESTING = SharedColorTokens.Testing.AchievementCategory.TESTING;
        public static final int SPECIAL = SharedColorTokens.Testing.AchievementCategory.SPECIAL;

        private AchievementCategory() {}
    }

    public static final class Badge {
        public static final int BRONZE_TESTER = SharedColorTokens.Testing.Badge.BRONZE_TESTER;
        public static final int SILVER_TESTER = SharedColorTokens.Testing.Badge.SILVER_TESTER;
        public static final int GOLD_TESTER = SharedColorTokens.Testing.Badge.GOLD_TESTER;
        public static final int DIAMOND_TESTER = SharedColorTokens.Testing.Badge.DIAMOND_TESTER;
        public static final int COMBAT_SPECIALIST = SharedColorTokens.Testing.Badge.COMBAT_SPECIALIST;
        public static final int PRECISION_EXPERT = SharedColorTokens.Testing.Badge.PRECISION_EXPERT;
        public static final int COMPLETIONIST = SharedColorTokens.Testing.Badge.COMPLETIONIST;

        private Badge() {}
    }
}
