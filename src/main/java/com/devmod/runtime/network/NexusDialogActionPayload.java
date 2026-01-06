package com.devmod.runtime.network;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Client -> Server payload when player selects a dialog option.
 */
public record NexusDialogActionPayload(
    String optionId,
    String nextDialogType
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "nexus_dialog_action")
    );
    public static final Type<NexusDialogActionPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, NexusDialogActionPayload> STREAM_CODEC =
        StreamCodec.of(NexusDialogActionPayload::encode, NexusDialogActionPayload::decode);

    private static void encode(FriendlyByteBuf buf, NexusDialogActionPayload payload) {
        buf.writeUtf(Objects.requireNonNull(payload.optionId));
        buf.writeUtf(Objects.requireNonNull(payload.nextDialogType));
    }

    private static NexusDialogActionPayload decode(FriendlyByteBuf buf) {
        return new NexusDialogActionPayload(
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
        return estimatedUtfSize(optionId) + estimatedUtfSize(nextDialogType);
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
