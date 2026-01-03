package com.devmod.client.season;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.endurance.season.SeasonPassPayload;

/**
 * Client-side cache for Season Pass data.
 * Updated via network sync from server.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSeasonPassCache {

    public static final ClientSeasonPassCache INSTANCE = new ClientSeasonPassCache();

    // Season info
    private int seasonNumber = 1;
    private String seasonName = "Season 1";
    private long remainingDays = 0;
    private boolean seasonActive = false;

    // Player progress
    private int currentTier = 1;
    private int currentXP = 0;
    private int xpToNextTier = 1000;
    private float tierProgress = 0.0f;
    private boolean premium = false;
    private int unclaimedRewards = 0;

    // XP boost
    private boolean hasBoost = false;
    private float boostMultiplier = 1.0f;
    private long boostRemainingSeconds = 0;

    // Visible rewards
    private List<RewardEntry> upcomingRewards = new ArrayList<>();

    // Sync state
    private boolean synced = false;
    private long lastSyncTime = 0;

    private ClientSeasonPassCache() {}

    /**
     * Update cache from network payload.
     */
    public void update(@Nonnull SeasonPassPayload payload) {
        Objects.requireNonNull(payload, "payload");

        this.seasonNumber = payload.seasonNumber();
        this.seasonName = payload.seasonName() != null ? payload.seasonName() : "Season " + seasonNumber;
        this.remainingDays = payload.remainingDays();
        this.seasonActive = payload.seasonActive();

        this.currentTier = payload.currentTier();
        this.currentXP = payload.currentXP();
        this.xpToNextTier = payload.xpToNextTier();
        this.tierProgress = payload.tierProgress();
        this.premium = payload.premium();
        this.unclaimedRewards = payload.unclaimedRewards();

        this.hasBoost = payload.hasBoost();
        this.boostMultiplier = payload.boostMultiplier();
        this.boostRemainingSeconds = payload.boostRemainingSeconds();

        // Convert reward entries
        this.upcomingRewards = new ArrayList<>();
        if (payload.upcomingRewards() != null) {
            for (SeasonPassPayload.RewardEntry entry : payload.upcomingRewards()) {
                upcomingRewards.add(new RewardEntry(
                    entry.tier(),
                    entry.freeRewardName(),
                    entry.premiumRewardName(),
                    entry.freeUnlocked(),
                    entry.freeClaimed(),
                    entry.premiumUnlocked(),
                    entry.premiumClaimed()
                ));
            }
        }

        this.synced = true;
        this.lastSyncTime = System.currentTimeMillis();
    }

    /**
     * Clear cache (on disconnect).
     */
    public void clear() {
        this.seasonNumber = 1;
        this.seasonName = "Season 1";
        this.remainingDays = 0;
        this.seasonActive = false;
        this.currentTier = 1;
        this.currentXP = 0;
        this.xpToNextTier = 1000;
        this.tierProgress = 0.0f;
        this.premium = false;
        this.unclaimedRewards = 0;
        this.hasBoost = false;
        this.boostMultiplier = 1.0f;
        this.boostRemainingSeconds = 0;
        this.upcomingRewards = new ArrayList<>();
        this.synced = false;
        this.lastSyncTime = 0;
    }

    // === Getters ===

    public int getSeasonNumber() { return seasonNumber; }
    @Nonnull public String getSeasonName() { return seasonName != null ? seasonName : ""; }
    public long getRemainingDays() { return remainingDays; }
    public boolean isSeasonActive() { return seasonActive; }
    public int getCurrentTier() { return currentTier; }
    public int getCurrentXP() { return currentXP; }
    public int getXpToNextTier() { return xpToNextTier; }
    public float getTierProgress() { return tierProgress; }
    public boolean isPremium() { return premium; }
    public int getUnclaimedRewards() { return unclaimedRewards; }
    public boolean hasBoost() { return hasBoost; }
    public float getBoostMultiplier() { return boostMultiplier; }
    public long getBoostRemainingSeconds() { return boostRemainingSeconds; }
    @Nonnull public List<RewardEntry> getUpcomingRewards() { return Objects.requireNonNull(upcomingRewards); }
    public boolean isSynced() { return synced; }
    public long getLastSyncTime() { return lastSyncTime; }

    /**
     * Get XP within current tier (for progress bar).
     */
    public int getCurrentTierXP() {
        return xpToNextTier > 0 ? (int) (tierProgress * xpToNextTier) : 0;
    }

    /**
     * Get XP needed per tier (for display).
     */
    public int getXpPerTier() {
        // Calculate from progress if available
        if (tierProgress > 0 && xpToNextTier > 0) {
            return (int) (xpToNextTier / (1.0f - tierProgress));
        }
        return xpToNextTier > 0 ? xpToNextTier : 1000;
    }

    /**
     * Reward entry for client display.
     */
    public record RewardEntry(
        int tier,
        String freeRewardName,
        String premiumRewardName,
        boolean freeUnlocked,
        boolean freeClaimed,
        boolean premiumUnlocked,
        boolean premiumClaimed
    ) {
        @Nonnull
        public String getSafeFreeRewardName() {
            return freeRewardName != null ? freeRewardName : "";
        }

        @Nonnull
        public String getSafePremiumRewardName() {
            return premiumRewardName != null ? premiumRewardName : "";
        }
    }
}
