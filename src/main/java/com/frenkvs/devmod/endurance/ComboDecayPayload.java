package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.DevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload sent from server to client when combo is lost or rank drops.
 * Triggers visual/audio feedback for combo miss.
 */
public record ComboDecayPayload(int lostCombo, int previousRankOrdinal, int newRankOrdinal) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "combo_decay");
    public static final Type<ComboDecayPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ComboDecayPayload> STREAM_CODEC =
        StreamCodec.composite(
            StreamCodec.of(FriendlyByteBuf::writeInt, FriendlyByteBuf::readInt),
            ComboDecayPayload::lostCombo,
            StreamCodec.of(FriendlyByteBuf::writeInt, FriendlyByteBuf::readInt),
            ComboDecayPayload::previousRankOrdinal,
            StreamCodec.of(FriendlyByteBuf::writeInt, FriendlyByteBuf::readInt),
            ComboDecayPayload::newRankOrdinal,
            ComboDecayPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public boolean isRankDown() {
        return newRankOrdinal < previousRankOrdinal;
    }
}
