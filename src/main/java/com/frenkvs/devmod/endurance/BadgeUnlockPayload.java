package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.DevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload sent from server to client when a badge is unlocked.
 * Triggers the badge popup overlay on the client.
 */
public record BadgeUnlockPayload(String badgeName, String rarity) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "badge_unlock");
    public static final Type<BadgeUnlockPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, BadgeUnlockPayload> STREAM_CODEC =
        StreamCodec.composite(
            StreamCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf),
            BadgeUnlockPayload::badgeName,
            StreamCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf),
            BadgeUnlockPayload::rarity,
            BadgeUnlockPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
