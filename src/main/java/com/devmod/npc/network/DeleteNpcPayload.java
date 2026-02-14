package com.devmod.npc.network;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Client -> Server payload to delete an NPC.
 * Removes the NPC from the registry and despawns the entity.
 */
public record DeleteNpcPayload(
    UUID npcId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "delete_npc")
    );
    public static final Type<DeleteNpcPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, DeleteNpcPayload> STREAM_CODEC =
        StreamCodec.of(DeleteNpcPayload::encode, DeleteNpcPayload::decode);

    private static void encode(FriendlyByteBuf buf, DeleteNpcPayload payload) {
        buf.writeUUID(payload.npcId);
    }

    private static DeleteNpcPayload decode(FriendlyByteBuf buf) {
        return new DeleteNpcPayload(buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 16; // UUID size
    }
}
