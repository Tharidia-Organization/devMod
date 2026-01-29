package com.devmod.npc.network;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;
import com.devmod.npc.dialog.DialogLimits;

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
        buf.writeUtf(DialogLimits.truncate(Objects.requireNonNull(payload.dialogSetId), DialogLimits.MAX_DIALOG_ID_LENGTH),
            DialogLimits.MAX_DIALOG_ID_LENGTH);
        buf.writeUtf(DialogLimits.truncate(Objects.requireNonNull(payload.nodeId), DialogLimits.MAX_NODE_ID_LENGTH),
            DialogLimits.MAX_NODE_ID_LENGTH);
        buf.writeUtf(DialogLimits.truncate(Objects.requireNonNull(payload.optionId), DialogLimits.MAX_OPTION_ID_LENGTH),
            DialogLimits.MAX_OPTION_ID_LENGTH);
    }

    private static NpcDialogActionPayload decode(FriendlyByteBuf buf) {
        return new NpcDialogActionPayload(
            buf.readUUID(),
            buf.readUtf(DialogLimits.MAX_DIALOG_ID_LENGTH),
            buf.readUtf(DialogLimits.MAX_NODE_ID_LENGTH),
            buf.readUtf(DialogLimits.MAX_OPTION_ID_LENGTH)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 16; // UUID
        size += PayloadSizeUtil.estimatedUtfSize(dialogSetId);
        size += PayloadSizeUtil.estimatedUtfSize(nodeId);
        size += PayloadSizeUtil.estimatedUtfSize(optionId);
        return size;
    }
}
