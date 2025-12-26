package com.devmod.endurance.season;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import io.netty.buffer.ByteBuf;

/**
 * Payload sent to client when a player reaches a new season pass tier.
 * Used to trigger the tier-up toast notification overlay.
 */
public record SeasonTierUpPayload(
    int newTier,
    boolean hasFreeReward,
    boolean hasPremiumReward,
    @Nonnull String freeRewardName,
    @Nonnull String premiumRewardName,
    boolean isPremium
) implements CustomPacketPayload {

    public static final Type<SeasonTierUpPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "season_tier_up"))
    );

    public static final StreamCodec<ByteBuf, SeasonTierUpPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            ByteBufCodecs.VAR_INT.encode(buffer, val.newTier());
            ByteBufCodecs.BOOL.encode(buffer, val.hasFreeReward());
            ByteBufCodecs.BOOL.encode(buffer, val.hasPremiumReward());
            ByteBufCodecs.STRING_UTF8.encode(buffer, val.freeRewardName());
            ByteBufCodecs.STRING_UTF8.encode(buffer, val.premiumRewardName());
            ByteBufCodecs.BOOL.encode(buffer, val.isPremium());
        },
        buffer -> new SeasonTierUpPayload(
            ByteBufCodecs.VAR_INT.decode(buffer),
            ByteBufCodecs.BOOL.decode(buffer),
            ByteBufCodecs.BOOL.decode(buffer),
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buffer)),
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buffer)),
            ByteBufCodecs.BOOL.decode(buffer)
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Check if any rewards are available at this tier.
     */
    public boolean hasAnyReward() {
        return hasFreeReward || hasPremiumReward;
    }

    /**
     * Get display text for available rewards.
     */
    @Nonnull
    public String getRewardDisplayText() {
        if (hasFreeReward && hasPremiumReward && isPremium) {
            return "Free + Premium rewards unlocked!";
        } else if (hasFreeReward) {
            return "Free reward unlocked!";
        } else if (hasPremiumReward && isPremium) {
            return "Premium reward unlocked!";
        }
        return "";
    }

    /**
     * Create a tier-up payload from progress data.
     */
    public static SeasonTierUpPayload create(
            int newTier,
            boolean hasFreeReward,
            boolean hasPremiumReward,
            @Nullable String freeRewardName,
            @Nullable String premiumRewardName,
            boolean isPremium) {
        return new SeasonTierUpPayload(
            newTier,
            hasFreeReward,
            hasPremiumReward,
            freeRewardName != null ? freeRewardName : "",
            premiumRewardName != null ? premiumRewardName : "",
            isPremium
        );
    }
}
