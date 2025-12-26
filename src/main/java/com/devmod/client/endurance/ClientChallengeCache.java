package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.chat.Component;

import com.devmod.endurance.challenges.ChallengeSyncPayload;

public class ClientChallengeCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientChallengeCache.class);

    public static final ClientChallengeCache INSTANCE = new ClientChallengeCache();

    private final List<ChallengeDisplayData> challenges = new ArrayList<>();
    private long lastSyncTime = 0;

    private ClientChallengeCache() {}

    /**
     * Handle challenge sync from server.
     */
    public void handleSync(ChallengeSyncPayload payload) {
        challenges.clear();

        for (ChallengeSyncPayload.ChallengeData data : payload.challenges()) {
            challenges.add(new ChallengeDisplayData(
                    data.id(),
                    Component.translatable(data.nameKey()),
                    Component.translatable(data.descriptionKey(), data.targetValue()),
                    getDifficultyName(data.difficulty()),
                    getDifficultyColor(data.difficulty()),
                    data.targetValue(),
                    data.currentValue(),
                    data.tokenReward(),
                    data.prestigeReward(),
                    data.completed(),
                    data.rewarded()
            ));
        }

        lastSyncTime = System.currentTimeMillis();
        LOGGER.debug("[ClientChallengeCache] Synced {} challenges", challenges.size());
    }

    /**
     * Get all cached challenges.
     */
    public List<ChallengeDisplayData> getChallenges() {
        return Collections.unmodifiableList(challenges);
    }

    /**
     * Check if we have valid cached data.
     */
    public boolean hasCachedData() {
        return !challenges.isEmpty() && (System.currentTimeMillis() - lastSyncTime) < 300_000; // 5 min
    }

    /**
     * Clear cached data.
     */
    public void clear() {
        challenges.clear();
        lastSyncTime = 0;
    }

    private String getDifficultyName(int difficulty) {
        return switch (difficulty) {
            case 0 -> "Easy";
            case 1 -> "Medium";
            case 2 -> "Hard";
            case 3 -> "Extreme";
            default -> "Unknown";
        };
    }

    private int getDifficultyColor(int difficulty) {
        return switch (difficulty) {
            case 0 -> 0xFF55FF55; // Green
            case 1 -> 0xFFFFFF55; // Yellow
            case 2 -> 0xFFFF8800; // Orange
            case 3 -> 0xFFFF5555; // Red
            default -> 0xFFAAAAAA;
        };
    }

    /**
     * Display data for a single challenge.
     */
    public record ChallengeDisplayData(
            String id,
            Component name,
            Component description,
            String difficultyName,
            int difficultyColor,
            int targetValue,
            int currentValue,
            int tokenReward,
            int prestigeReward,
            boolean completed,
            boolean rewarded
    ) {
        /**
         * Get progress as percentage (0.0 - 1.0).
         */
        public float getProgress() {
            if (targetValue <= 0) return 1.0f;
            return Math.min(1.0f, (float) currentValue / targetValue);
        }

        /**
         * Get formatted progress string.
         */
        public String getProgressText() {
            if (completed) return "Complete!";
            return currentValue + " / " + targetValue;
        }

        /**
         * Get reward summary text.
         */
        public String getRewardText() {
            StringBuilder sb = new StringBuilder();
            if (tokenReward > 0) {
                sb.append(tokenReward).append(" Tokens");
            }
            if (prestigeReward > 0) {
                if (!sb.isEmpty()) sb.append(" + ");
                sb.append(prestigeReward).append(" Prestige");
            }
            return sb.toString();
        }
    }
}
