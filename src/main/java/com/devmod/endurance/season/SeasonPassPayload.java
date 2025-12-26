package com.devmod.endurance.season;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import io.netty.buffer.ByteBuf;
public record SeasonPassPayload(
    // Season info
    int seasonNumber,
    String seasonName,
    long remainingDays,
    boolean seasonActive,
    // Player progress
    int currentTier,
    int currentXP,
    int xpToNextTier,
    float tierProgress,
    boolean premium,
    int unclaimedRewards,
    // XP boost
    boolean hasBoost,
    float boostMultiplier,
    long boostRemainingSeconds,
    // Visible rewards (next 10 tiers)
    List<RewardEntry> upcomingRewards
) implements CustomPacketPayload {

    private static final int MAX_STRING_LENGTH = 128;
    private static final int MAX_REWARDS = 20;

    public static final Type<SeasonPassPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "season_pass"))
    );

    /**
     * A reward entry for display.
     */
    public record RewardEntry(
        int tier,
        String freeRewardName,
        String premiumRewardName,
        boolean freeUnlocked,
        boolean freeClaimed,
        boolean premiumUnlocked,
        boolean premiumClaimed
    ) {}

    public static final StreamCodec<ByteBuf, SeasonPassPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SeasonPassPayload decode(@Nonnull ByteBuf buf) {
            int seasonNumber = buf.readInt();
            String seasonName = readString(buf);
            long remainingDays = buf.readLong();
            boolean seasonActive = buf.readBoolean();

            int currentTier = buf.readInt();
            int currentXP = buf.readInt();
            int xpToNextTier = buf.readInt();
            float tierProgress = buf.readFloat();
            boolean premium = buf.readBoolean();
            int unclaimedRewards = buf.readInt();

            boolean hasBoost = buf.readBoolean();
            float boostMultiplier = buf.readFloat();
            long boostRemainingSeconds = buf.readLong();

            int rewardCount = Math.min(buf.readInt(), MAX_REWARDS);
            List<RewardEntry> rewards = new ArrayList<>();
            for (int i = 0; i < rewardCount; i++) {
                rewards.add(decodeRewardEntry(buf));
            }

            return new SeasonPassPayload(
                seasonNumber, seasonName, remainingDays, seasonActive,
                currentTier, currentXP, xpToNextTier, tierProgress,
                premium, unclaimedRewards,
                hasBoost, boostMultiplier, boostRemainingSeconds,
                rewards
            );
        }

        @Override
        public void encode(@Nonnull ByteBuf buf, @Nonnull SeasonPassPayload payload) {
            buf.writeInt(payload.seasonNumber);
            writeString(buf, payload.seasonName);
            buf.writeLong(payload.remainingDays);
            buf.writeBoolean(payload.seasonActive);

            buf.writeInt(payload.currentTier);
            buf.writeInt(payload.currentXP);
            buf.writeInt(payload.xpToNextTier);
            buf.writeFloat(payload.tierProgress);
            buf.writeBoolean(payload.premium);
            buf.writeInt(payload.unclaimedRewards);

            buf.writeBoolean(payload.hasBoost);
            buf.writeFloat(payload.boostMultiplier);
            buf.writeLong(payload.boostRemainingSeconds);

            int rewardCount = Math.min(payload.upcomingRewards.size(), MAX_REWARDS);
            buf.writeInt(rewardCount);
            for (int i = 0; i < rewardCount; i++) {
                encodeRewardEntry(buf, payload.upcomingRewards.get(i));
            }
        }

        private RewardEntry decodeRewardEntry(ByteBuf buf) {
            int tier = buf.readInt();
            String freeName = readString(buf);
            String premiumName = readString(buf);
            boolean freeUnlocked = buf.readBoolean();
            boolean freeClaimed = buf.readBoolean();
            boolean premiumUnlocked = buf.readBoolean();
            boolean premiumClaimed = buf.readBoolean();
            return new RewardEntry(tier, freeName, premiumName,
                freeUnlocked, freeClaimed, premiumUnlocked, premiumClaimed);
        }

        private void encodeRewardEntry(ByteBuf buf, RewardEntry entry) {
            buf.writeInt(entry.tier);
            writeString(buf, entry.freeRewardName);
            writeString(buf, entry.premiumRewardName);
            buf.writeBoolean(entry.freeUnlocked);
            buf.writeBoolean(entry.freeClaimed);
            buf.writeBoolean(entry.premiumUnlocked);
            buf.writeBoolean(entry.premiumClaimed);
        }

        private String readString(ByteBuf buf) {
            int length = buf.readInt();
            if (length < 0 || length > MAX_STRING_LENGTH) {
                length = 0;
            }
            if (length == 0) return "";
            return buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
        }

        private void writeString(ByteBuf buf, String str) {
            if (str == null) str = "";
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            int length = Math.min(bytes.length, MAX_STRING_LENGTH);
            buf.writeInt(length);
            buf.writeBytes(bytes, 0, length);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Create payload from system data for a player.
     */
    public static SeasonPassPayload create(java.util.UUID playerId) {
        SeasonPassSystem system = SeasonPassSystem.getInstance();
        SeasonPassSystem.SeasonInfo info = system.getSeasonInfo();
        PlayerSeasonProgress progress = system.getOrCreateProgress(playerId);
        PlayerSeasonProgress.ProgressSummary summary = progress.getSummary();

        // Get upcoming rewards (current tier to current + 10)
        int startTier = Math.max(1, summary.currentTier() - 2);
        int endTier = Math.min(SeasonPassSystem.MAX_TIER, summary.currentTier() + 10);
        List<SeasonPassSystem.TierRewardInfo> rewardInfos = system.getRewardsForTiers(startTier, endTier);

        List<RewardEntry> rewards = new ArrayList<>();
        for (SeasonPassSystem.TierRewardInfo ri : rewardInfos) {
            String freeName = ri.freeReward() != null ? ri.freeReward().getDisplayName() : "";
            String premiumName = ri.premiumReward() != null ? ri.premiumReward().getDisplayName() : "";

            boolean freeUnlocked = progress.canClaimFreeReward(ri.tier()) ||
                progress.getClaimedFreeRewards().contains(ri.tier());
            boolean freeClaimed = progress.getClaimedFreeRewards().contains(ri.tier());
            boolean premiumUnlocked = progress.canClaimPremiumReward(ri.tier()) ||
                progress.getClaimedPremiumRewards().contains(ri.tier());
            boolean premiumClaimed = progress.getClaimedPremiumRewards().contains(ri.tier());

            rewards.add(new RewardEntry(ri.tier(), freeName, premiumName,
                freeUnlocked, freeClaimed, premiumUnlocked, premiumClaimed));
        }

        return new SeasonPassPayload(
            info.seasonNumber(),
            info.name(),
            info.remainingDays(),
            info.active(),
            summary.currentTier(),
            summary.totalXP(),
            summary.xpToNextTier(),
            summary.tierProgress(),
            summary.premium(),
            summary.unclaimedRewards(),
            summary.hasBoost(),
            summary.boostMultiplier(),
            summary.boostRemainingSeconds(),
            rewards
        );
    }
}
