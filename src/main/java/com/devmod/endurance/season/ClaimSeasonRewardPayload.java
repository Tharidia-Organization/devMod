package com.devmod.endurance.season;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

/**
 * Client-to-server request to claim a season pass reward.
 */
public record ClaimSeasonRewardPayload(
    int tier,
    boolean premium
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<ClaimSeasonRewardPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "claim_season_reward"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimSeasonRewardPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public ClaimSeasonRewardPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
                int tier = buf.readInt();
                boolean premium = buf.readBoolean();
                return new ClaimSeasonRewardPayload(tier, premium);
            }

            @Override
            public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull ClaimSeasonRewardPayload payload) {
                buf.writeInt(payload.tier);
                buf.writeBoolean(payload.premium);
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 4 + 1; // int tier + boolean premium
    }
}
