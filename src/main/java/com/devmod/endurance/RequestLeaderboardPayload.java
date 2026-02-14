package com.devmod.endurance;

import java.util.Objects;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

/**
 * Client -> Server request to fetch current leaderboard data.
 * Empty payload; the server responds with {@link LeaderboardSyncPayload}.
 */
public record RequestLeaderboardPayload() implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<RequestLeaderboardPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "request_leaderboard"))
    );

    public static final StreamCodec<ByteBuf, RequestLeaderboardPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RequestLeaderboardPayload decode(@Nonnull ByteBuf buf) {
            return new RequestLeaderboardPayload();
        }

        @Override
        public void encode(@Nonnull ByteBuf buf, @Nonnull RequestLeaderboardPayload payload) {
            // No data to encode
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 0;
    }
}
