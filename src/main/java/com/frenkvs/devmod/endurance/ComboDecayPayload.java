package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.DevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/**
 * Payload sent from server to client when combo is lost or rank drops.
 * Triggers visual/audio feedback for combo miss.
 */
@SuppressWarnings({"null", "unused"})
public record ComboDecayPayload(int lostCombo, int previousRankOrdinal, int newRankOrdinal) implements CustomPacketPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "combo_decay"));
    public static final Type<ComboDecayPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, ComboDecayPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(StreamCodec.of(
                (buf, value) -> buf.writeInt(value.intValue()),
                FriendlyByteBuf::readInt
            )),
            payload -> payload.lostCombo,
            Objects.requireNonNull(StreamCodec.of(
                (buf, value) -> buf.writeInt(value.intValue()),
                FriendlyByteBuf::readInt
            )),
            payload -> payload.previousRankOrdinal,
            Objects.requireNonNull(StreamCodec.of(
                (buf, value) -> buf.writeInt(value.intValue()),
                FriendlyByteBuf::readInt
            )),
            payload -> payload.newRankOrdinal,
            (lost, prev, next) -> new ComboDecayPayload(lost.intValue(), prev.intValue(), next.intValue())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public boolean isRankDown() {
        return newRankOrdinal < previousRankOrdinal;
    }
}
