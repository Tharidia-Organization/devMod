package com.devmod.npc.network;

import java.util.Objects;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;
import com.devmod.npc.dialog.DialogSet;

/**
 * Client -> Server payload to save a dialog set.
 */
public record SaveDialogPayload(
    DialogSet dialogSet,
    int expectedRevision
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "save_dialog")
    );
    public static final Type<SaveDialogPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, SaveDialogPayload> STREAM_CODEC =
        StreamCodec.of(SaveDialogPayload::encode, SaveDialogPayload::decode);

    private static void encode(FriendlyByteBuf buf, SaveDialogPayload payload) {
        DialogSet.STREAM_CODEC.encode(buf, payload.dialogSet);
        buf.writeVarInt(payload.expectedRevision);
    }

    private static SaveDialogPayload decode(FriendlyByteBuf buf) {
        DialogSet dialogSet = DialogSet.STREAM_CODEC.decode(buf);
        int expectedRevision = buf.readVarInt();
        return new SaveDialogPayload(dialogSet, expectedRevision);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 2048 + 5; // DialogSet estimate + VarInt
    }
}
