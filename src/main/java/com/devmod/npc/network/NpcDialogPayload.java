package com.devmod.npc.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;
import com.devmod.npc.data.NpcConfiguration;
import com.devmod.npc.dialog.DialogLimits;

/**
 * Server -> Client payload to open or update the NPC dialog screen.
 */
public record NpcDialogPayload(
    UUID npcId,
    String npcName,
    String dialogSetId,
    String currentNodeId,
    List<String> lines,
    List<NpcDialogOptionData> options
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "npc_dialog")
    );
    public static final Type<NpcDialogPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, NpcDialogPayload> STREAM_CODEC =
        StreamCodec.of(NpcDialogPayload::encode, NpcDialogPayload::decode);

    /** Maximum dialog lines to prevent DoS via unbounded allocation */
    private static final int MAX_LINES = DialogLimits.MAX_LINES_PER_NODE;
    /** Maximum dialog options */
    private static final int MAX_OPTIONS = DialogLimits.MAX_OPTIONS_PER_NODE;
    /** Maximum string length for names/IDs */
    private static final int MAX_STRING_LENGTH = DialogLimits.MAX_NODE_ID_LENGTH;
    /** Maximum line length */
    private static final int MAX_LINE_LENGTH = DialogLimits.MAX_LINE_LENGTH;

    /**
     * A dialog option that can be selected.
     */
    public record NpcDialogOptionData(
        String id,
        String label,
        String icon
    ) {
        public static void encode(FriendlyByteBuf buf, NpcDialogOptionData opt) {
            buf.writeUtf(DialogLimits.truncate(Objects.requireNonNull(opt.id), DialogLimits.MAX_OPTION_ID_LENGTH),
                DialogLimits.MAX_OPTION_ID_LENGTH);
            buf.writeUtf(DialogLimits.truncate(Objects.requireNonNull(opt.label), DialogLimits.MAX_OPTION_LABEL_LENGTH),
                DialogLimits.MAX_OPTION_LABEL_LENGTH);
            buf.writeUtf(DialogLimits.truncate(Objects.requireNonNull(opt.icon), DialogLimits.MAX_OPTION_ICON_LENGTH),
                DialogLimits.MAX_OPTION_ICON_LENGTH);
        }

        public static NpcDialogOptionData decode(FriendlyByteBuf buf) {
            return new NpcDialogOptionData(
                buf.readUtf(MAX_STRING_LENGTH),
                buf.readUtf(DialogLimits.MAX_OPTION_LABEL_LENGTH),
                buf.readUtf(DialogLimits.MAX_OPTION_ICON_LENGTH)
            );
        }

        public int estimatedSize() {
            return PayloadSizeUtil.estimatedUtfSize(id)
                + PayloadSizeUtil.estimatedUtfSize(label)
                + PayloadSizeUtil.estimatedUtfSize(icon);
        }
    }

    private static void encode(FriendlyByteBuf buf, NpcDialogPayload payload) {
        buf.writeUUID(payload.npcId);
        buf.writeUtf(DialogLimits.truncate(Objects.requireNonNull(payload.npcName), NpcConfiguration.MAX_DISPLAY_NAME_LENGTH),
            NpcConfiguration.MAX_DISPLAY_NAME_LENGTH);
        buf.writeUtf(DialogLimits.truncate(Objects.requireNonNull(payload.dialogSetId), DialogLimits.MAX_DIALOG_ID_LENGTH),
            DialogLimits.MAX_DIALOG_ID_LENGTH);
        buf.writeUtf(DialogLimits.truncate(Objects.requireNonNull(payload.currentNodeId), DialogLimits.MAX_NODE_ID_LENGTH),
            DialogLimits.MAX_NODE_ID_LENGTH);

        // Encode lines
        int lineCount = Math.min(payload.lines.size(), MAX_LINES);
        buf.writeVarInt(lineCount);
        int written = 0;
        for (String line : payload.lines) {
            if (written >= lineCount) {
                break;
            }
            buf.writeUtf(DialogLimits.truncate(Objects.requireNonNull(line), MAX_LINE_LENGTH), MAX_LINE_LENGTH);
            written++;
        }

        // Encode options
        int optCount = Math.min(payload.options.size(), MAX_OPTIONS);
        buf.writeVarInt(optCount);
        int writtenOptions = 0;
        for (NpcDialogOptionData opt : payload.options) {
            if (writtenOptions >= optCount) {
                break;
            }
            NpcDialogOptionData.encode(buf, opt);
            writtenOptions++;
        }
    }

    private static NpcDialogPayload decode(FriendlyByteBuf buf) {
        UUID npcId = buf.readUUID();
        String npcName = buf.readUtf(NpcConfiguration.MAX_DISPLAY_NAME_LENGTH);
        String dialogSetId = buf.readUtf(DialogLimits.MAX_DIALOG_ID_LENGTH);
        String currentNodeId = buf.readUtf(DialogLimits.MAX_NODE_ID_LENGTH);

        // Decode lines with bounds validation
        int lineCount = Math.min(buf.readVarInt(), MAX_LINES);
        if (lineCount < 0) lineCount = 0;
        List<String> lines = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            lines.add(buf.readUtf(MAX_LINE_LENGTH));
        }

        // Decode options with bounds validation
        int optCount = Math.min(buf.readVarInt(), MAX_OPTIONS);
        if (optCount < 0) optCount = 0;
        List<NpcDialogOptionData> options = new ArrayList<>(optCount);
        for (int i = 0; i < optCount; i++) {
            options.add(NpcDialogOptionData.decode(buf));
        }

        return new NpcDialogPayload(npcId, npcName, dialogSetId, currentNodeId, lines, options);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 16; // UUID
        size += PayloadSizeUtil.estimatedUtfSize(npcName);
        size += PayloadSizeUtil.estimatedUtfSize(dialogSetId);
        size += PayloadSizeUtil.estimatedUtfSize(currentNodeId);
        size += PayloadSizeUtil.varIntSize(lines.size());
        for (String line : lines) {
            size += PayloadSizeUtil.estimatedUtfSize(line);
        }
        size += PayloadSizeUtil.varIntSize(options.size());
        for (NpcDialogOptionData opt : options) {
            size += opt.estimatedSize();
        }
        return size;
    }
}
