package com.devmod.npc.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

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

    /**
     * A dialog option that can be selected.
     */
    public record NpcDialogOptionData(
        String id,
        String label,
        String icon
    ) {
        public static void encode(FriendlyByteBuf buf, NpcDialogOptionData opt) {
            buf.writeUtf(Objects.requireNonNull(opt.id));
            buf.writeUtf(Objects.requireNonNull(opt.label));
            buf.writeUtf(Objects.requireNonNull(opt.icon));
        }

        public static NpcDialogOptionData decode(FriendlyByteBuf buf) {
            return new NpcDialogOptionData(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf()
            );
        }

        public int estimatedSize() {
            return estimatedUtfSize(id) + estimatedUtfSize(label) + estimatedUtfSize(icon);
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

    private static void encode(FriendlyByteBuf buf, NpcDialogPayload payload) {
        buf.writeUUID(payload.npcId);
        buf.writeUtf(Objects.requireNonNull(payload.npcName));
        buf.writeUtf(Objects.requireNonNull(payload.dialogSetId));
        buf.writeUtf(Objects.requireNonNull(payload.currentNodeId));

        // Encode lines
        buf.writeVarInt(payload.lines.size());
        for (String line : payload.lines) {
            buf.writeUtf(Objects.requireNonNull(line));
        }

        // Encode options
        buf.writeVarInt(payload.options.size());
        for (NpcDialogOptionData opt : payload.options) {
            NpcDialogOptionData.encode(buf, opt);
        }
    }

    private static NpcDialogPayload decode(FriendlyByteBuf buf) {
        UUID npcId = buf.readUUID();
        String npcName = buf.readUtf();
        String dialogSetId = buf.readUtf();
        String currentNodeId = buf.readUtf();

        // Decode lines
        int lineCount = buf.readVarInt();
        List<String> lines = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            lines.add(buf.readUtf());
        }

        // Decode options
        int optCount = buf.readVarInt();
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
        size += estimatedUtfSize(npcName);
        size += estimatedUtfSize(dialogSetId);
        size += estimatedUtfSize(currentNodeId);
        size += varIntSize(lines.size());
        for (String line : lines) {
            size += estimatedUtfSize(line);
        }
        size += varIntSize(options.size());
        for (NpcDialogOptionData opt : options) {
            size += opt.estimatedSize();
        }
        return size;
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
