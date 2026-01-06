package com.devmod.runtime.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Server -> Client payload to open the Nexus dialog screen.
 */
public record NexusDialogPayload(
    String speakerName,
    List<String> lines,
    List<DialogOptionData> options
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "nexus_dialog")
    );
    public static final Type<NexusDialogPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, NexusDialogPayload> STREAM_CODEC =
        StreamCodec.of(NexusDialogPayload::encode, NexusDialogPayload::decode);

    /**
     * A dialog option that can be selected.
     */
    public record DialogOptionData(
        String id,
        String label,
        String icon,
        String nextDialogType
    ) {
        public static void encode(FriendlyByteBuf buf, DialogOptionData opt) {
            buf.writeUtf(Objects.requireNonNull(opt.id));
            buf.writeUtf(Objects.requireNonNull(opt.label));
            buf.writeUtf(Objects.requireNonNull(opt.icon));
            buf.writeUtf(Objects.requireNonNull(opt.nextDialogType));
        }

        public static DialogOptionData decode(FriendlyByteBuf buf) {
            return new DialogOptionData(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf()
            );
        }

        public int estimatedSize() {
            return estimatedUtfSize(id) + estimatedUtfSize(label) +
                   estimatedUtfSize(icon) + estimatedUtfSize(nextDialogType);
        }
    }

    private static void encode(FriendlyByteBuf buf, NexusDialogPayload payload) {
        buf.writeUtf(Objects.requireNonNull(payload.speakerName));

        // Encode lines
        buf.writeVarInt(payload.lines.size());
        for (String line : payload.lines) {
            buf.writeUtf(Objects.requireNonNull(line));
        }

        // Encode options
        buf.writeVarInt(payload.options.size());
        for (DialogOptionData opt : payload.options) {
            DialogOptionData.encode(buf, opt);
        }
    }

    private static NexusDialogPayload decode(FriendlyByteBuf buf) {
        String speakerName = buf.readUtf();

        // Decode lines
        int lineCount = buf.readVarInt();
        List<String> lines = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            lines.add(buf.readUtf());
        }

        // Decode options
        int optCount = buf.readVarInt();
        List<DialogOptionData> options = new ArrayList<>(optCount);
        for (int i = 0; i < optCount; i++) {
            options.add(DialogOptionData.decode(buf));
        }

        return new NexusDialogPayload(speakerName, lines, options);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = estimatedUtfSize(speakerName);
        size += varIntSize(lines.size());
        for (String line : lines) {
            size += estimatedUtfSize(line);
        }
        size += varIntSize(options.size());
        for (DialogOptionData opt : options) {
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
