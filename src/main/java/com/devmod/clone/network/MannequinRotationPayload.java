package com.devmod.clone.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Payload for updating mannequin rotation angle.
 * Sent from client to server when player scrolls while looking at a mannequin in MANUAL mode.
 *
 * @param pos Block position of the mannequin
 * @param rotationDelta Delta rotation to apply (positive = clockwise, negative = counter-clockwise)
 */
public record MannequinRotationPayload(
    BlockPos pos,
    float rotationDelta
) implements CustomPacketPayload {

    public static final Type<MannequinRotationPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "mannequin_rotation")));

    public static final StreamCodec<FriendlyByteBuf, MannequinRotationPayload> STREAM_CODEC =
        StreamCodec.of(MannequinRotationPayload::encode, MannequinRotationPayload::decode);

    private static void encode(FriendlyByteBuf buf, MannequinRotationPayload payload) {
        buf.writeBlockPos(Objects.requireNonNull(payload.pos));
        buf.writeFloat(payload.rotationDelta);
    }

    private static MannequinRotationPayload decode(FriendlyByteBuf buf) {
        return new MannequinRotationPayload(
            buf.readBlockPos(),
            buf.readFloat()
        );
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
