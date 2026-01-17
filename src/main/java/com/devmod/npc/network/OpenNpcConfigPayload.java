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
import com.devmod.npc.data.NpcConfiguration;

/**
 * Server -> Client payload to open the NPC configuration screen.
 * Used when creating a new NPC or editing an existing one.
 */
public record OpenNpcConfigPayload(
    @Nullable NpcConfiguration config,
    @Nullable InteractionHand hand,
    @Nullable UUID existingNpcId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "open_npc_config")
    );
    public static final Type<OpenNpcConfigPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, OpenNpcConfigPayload> STREAM_CODEC =
        StreamCodec.of(OpenNpcConfigPayload::encode, OpenNpcConfigPayload::decode);

    private static void encode(FriendlyByteBuf buf, OpenNpcConfigPayload payload) {
        // Config (optional)
        buf.writeBoolean(payload.config != null);
        if (payload.config != null) {
            NpcConfiguration.STREAM_CODEC.encode(buf, payload.config);
        }

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
    }

    private static OpenNpcConfigPayload decode(FriendlyByteBuf buf) {
        NpcConfiguration config = buf.readBoolean()
            ? NpcConfiguration.STREAM_CODEC.decode(buf)
            : null;

        InteractionHand hand = buf.readBoolean()
            ? buf.readEnum(InteractionHand.class)
            : null;

        UUID existingNpcId = buf.readBoolean()
            ? buf.readUUID()
            : null;

        return new OpenNpcConfigPayload(config, hand, existingNpcId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 3; // 3 booleans
        if (config != null) {
            size += 512; // Estimate for NpcConfiguration
        }
        if (hand != null) {
            size += 1;
        }
        if (existingNpcId != null) {
            size += 16;
        }
        return size;
    }

    /**
     * Returns true if this is for creating a new NPC (no existing ID).
     */
    public boolean isNewNpc() {
        return existingNpcId == null;
    }

    /**
     * Returns true if this is for editing an existing NPC.
     */
    public boolean isEditingNpc() {
        return existingNpcId != null;
    }
}
