package com.devmod.npc.network;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Client -> Server payload when player selects a dialog option.
 */
public record NpcDialogActionPayload(
    UUID npcId,
    String dialogSetId,
    String nodeId,
    String optionId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "npc_dialog_action")
    );
    public static final Type<NpcDialogActionPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, NpcDialogActionPayload> STREAM_CODEC =
        StreamCodec.of(NpcDialogActionPayload::encode, NpcDialogActionPayload::decode);

    private static void encode(FriendlyByteBuf buf, NpcDialogActionPayload payload) {
        buf.writeUUID(payload.npcId);
        buf.writeUtf(Objects.requireNonNull(payload.dialogSetId));
        buf.writeUtf(Objects.requireNonNull(payload.nodeId));
        buf.writeUtf(Objects.requireNonNull(payload.optionId));
    }

    private static NpcDialogActionPayload decode(FriendlyByteBuf buf) {
        return new NpcDialogActionPayload(
            buf.readUUID(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 16 + // UUID
               estimatedUtfSize(dialogSetId) +
               estimatedUtfSize(nodeId) +
               estimatedUtfSize(optionId);
    }

    private static int estimatedUtfSize(String value) {
        if (value == null || value.isEmpty()) {
            return varIntSize(0);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return varIntSize(bytes.length) + bytes.length;
    }

    private static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }
}
