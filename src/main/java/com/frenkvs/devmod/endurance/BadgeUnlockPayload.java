package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.DevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Payload sent from server to client when a badge is unlocked.
 * Triggers the badge popup overlay on the client.
 */
@SuppressWarnings({"null", "unused"})
public record BadgeUnlockPayload(String badgeName, String rarity) implements CustomPacketPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "badge_unlock"));
    public static final Type<BadgeUnlockPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, BadgeUnlockPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(StreamCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf)),
            BadgeUnlockPayload::badgeName,
            Objects.requireNonNull(StreamCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf)),
            BadgeUnlockPayload::rarity,
            BadgeUnlockPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
