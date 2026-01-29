package com.devmod.npc.network;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;
import com.devmod.npc.dialog.DialogSet;
import com.devmod.npc.dialog.DialogLimits;
import com.devmod.network.PayloadSizeUtil;

/**
 * Server -> Client payload to open the dialog editor screen.
 */
public record OpenDialogEditorPayload(
    String dialogSetId,
    @Nullable DialogSet dialogSet
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "open_dialog_editor")
    );
    public static final Type<OpenDialogEditorPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, OpenDialogEditorPayload> STREAM_CODEC =
        StreamCodec.of(OpenDialogEditorPayload::encode, OpenDialogEditorPayload::decode);

    private static void encode(FriendlyByteBuf buf, OpenDialogEditorPayload payload) {
        buf.writeUtf(DialogLimits.truncate(Objects.requireNonNull(payload.dialogSetId), DialogLimits.MAX_DIALOG_ID_LENGTH),
            DialogLimits.MAX_DIALOG_ID_LENGTH);

        buf.writeBoolean(payload.dialogSet != null);
        if (payload.dialogSet != null) {
            DialogSet.STREAM_CODEC.encode(buf, payload.dialogSet);
        }
    }

    private static OpenDialogEditorPayload decode(FriendlyByteBuf buf) {
        String dialogSetId = buf.readUtf(DialogLimits.MAX_DIALOG_ID_LENGTH);
        DialogSet dialogSet = buf.readBoolean()
            ? DialogSet.STREAM_CODEC.decode(buf)
            : null;

        return new OpenDialogEditorPayload(dialogSetId, dialogSet);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.estimatedUtfSize(dialogSetId);
        if (dialogSet != null) {
            size += DialogPayloadSizing.estimateDialogSetSize(dialogSet);
        }
        return PayloadSizeUtil.clampToInt(size);
    }

    /**
     * Returns true if this is for creating a new dialog set.
     */
    public boolean isNewDialogSet() {
        return dialogSet == null;
    }

    /**
     * Returns true if this is for editing an existing dialog set.
     */
    public boolean isEditingDialogSet() {
        return dialogSet != null;
    }
}
