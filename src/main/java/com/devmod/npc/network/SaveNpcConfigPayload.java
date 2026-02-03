package com.devmod.npc.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.npc.data.NpcConfiguration;

/**
 * Client -> Server payload to save NPC configuration.
 * Used when creating a new NPC or updating an existing one.
 */
public record SaveNpcConfigPayload(
    NpcConfiguration config,
    @Nullable InteractionHand hand,
    @Nullable UUID existingNpcId,
    int expectedRevision
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "save_npc_config")
    );
    public static final Type<SaveNpcConfigPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, SaveNpcConfigPayload> STREAM_CODEC =
        StreamCodec.of(SaveNpcConfigPayload::encode, SaveNpcConfigPayload::decode);

    private static void encode(FriendlyByteBuf buf, SaveNpcConfigPayload payload) {
        // Config (required)
        NpcConfiguration.STREAM_CODEC.encode(buf, payload.config);

        // Hand (optional)
        buf.writeBoolean(payload.hand != null);
        if (payload.hand != null) {
            buf.writeEnum(payload.hand);
        }

        // Existing NPC ID (optional)
        buf.writeBoolean(payload.existingNpcId != null);
        if (payload.existingNpcId != null) {
            buf.writeUUID(payload.existingNpcId);
        }

        // Expected revision for optimistic locking
        buf.writeVarInt(payload.expectedRevision);
    }

    private static SaveNpcConfigPayload decode(FriendlyByteBuf buf) {
        NpcConfiguration config = NpcConfiguration.STREAM_CODEC.decode(buf);

        InteractionHand hand = buf.readBoolean()
            ? buf.readEnum(InteractionHand.class)
            : null;

        UUID existingNpcId = buf.readBoolean()
            ? buf.readUUID()
            : null;

        int expectedRevision = buf.readVarInt();

        return new SaveNpcConfigPayload(config, hand, existingNpcId, expectedRevision);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 512; // Estimate for NpcConfiguration
        size += 2; // 2 booleans
        if (hand != null) {
            size += PayloadSizeUtil.varIntSize(hand.ordinal());
        }
        if (existingNpcId != null) {
            size += 16;
        }
        size += PayloadSizeUtil.varIntSize(expectedRevision);
        return size;
    }

    /**
     * Returns true if this is for creating a new NPC (no existing ID).
     */
    public boolean isNewNpc() {
        return existingNpcId == null;
    }

    /**
     * Returns true if this is for updating an existing NPC.
     */
    public boolean isUpdatingNpc() {
        return existingNpcId != null;
    }
}
