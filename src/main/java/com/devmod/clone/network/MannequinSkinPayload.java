package com.devmod.clone.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Payload for updating mannequin skin UUID.
 * Sent from client to server when player sets a custom skin.
 *
 * @param pos Block position of the mannequin
 * @param skinUUID Player UUID for skin, or null to reset to Steve
 */
public record MannequinSkinPayload(
    BlockPos pos,
    @Nullable UUID skinUUID
) implements CustomPacketPayload {

    public static final Type<MannequinSkinPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "mannequin_skin")));

    public static final StreamCodec<FriendlyByteBuf, MannequinSkinPayload> STREAM_CODEC =
        StreamCodec.of(MannequinSkinPayload::encode, MannequinSkinPayload::decode);

    private static void encode(FriendlyByteBuf buf, MannequinSkinPayload payload) {
        buf.writeBlockPos(Objects.requireNonNull(payload.pos));
        buf.writeBoolean(payload.skinUUID != null);
        if (payload.skinUUID != null) {
            buf.writeUUID(payload.skinUUID);
        }
    }

    private static MannequinSkinPayload decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean hasSkin = buf.readBoolean();
        UUID skinUUID = hasSkin ? buf.readUUID() : null;
        return new MannequinSkinPayload(pos, skinUUID);
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
