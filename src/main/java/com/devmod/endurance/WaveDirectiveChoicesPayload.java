package com.devmod.endurance;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

public record WaveDirectiveChoicesPayload(
    int waveNumber,
    long expiresAt,
    List<DirectiveChoice> choices
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    private static final int MAX_CHOICES = 4;
    private static final int MAX_STRING_LENGTH = 256;

    public static final Type<WaveDirectiveChoicesPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "directive_choices"))
    );

    public record DirectiveChoice(
        String id,
        String name,
        String description,
        float rewardMultiplier
    ) {
        public static DirectiveChoice from(WaveDirective directive) {
            return new DirectiveChoice(
                directive.id(),
                directive.name(),
                directive.description(),
                directive.rewardMultiplier()
            );
        }
    }

    public static final StreamCodec<ByteBuf, WaveDirectiveChoicesPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WaveDirectiveChoicesPayload decode(@Nonnull ByteBuf buf) {
            int waveNumber = buf.readInt();
            long expiresAt = buf.readLong();
            int choiceCount = Math.min(buf.readInt(), MAX_CHOICES);

            List<DirectiveChoice> choices = new ArrayList<>();
            for (int i = 0; i < choiceCount; i++) {
                choices.add(decodeChoice(buf));
            }

            return new WaveDirectiveChoicesPayload(waveNumber, expiresAt, choices);
        }

        @Override
        public void encode(@Nonnull ByteBuf buf, @Nonnull WaveDirectiveChoicesPayload payload) {
            buf.writeInt(payload.waveNumber);
            buf.writeLong(payload.expiresAt);
            buf.writeInt(Math.min(payload.choices.size(), MAX_CHOICES));

            for (int i = 0; i < Math.min(payload.choices.size(), MAX_CHOICES); i++) {
                encodeChoice(buf, payload.choices.get(i));
            }
        }

        private DirectiveChoice decodeChoice(ByteBuf buf) {
            String id = readString(buf);
            String name = readString(buf);
            String description = readString(buf);
            float rewardMultiplier = buf.readFloat();
            return new DirectiveChoice(id, name, description, rewardMultiplier);
        }

        private void encodeChoice(ByteBuf buf, DirectiveChoice choice) {
            writeString(buf, choice.id);
            writeString(buf, choice.name);
            writeString(buf, choice.description);
            buf.writeFloat(choice.rewardMultiplier);
        }

        private String readString(ByteBuf buf) {
            int length = buf.readInt();
            if (length < 0 || length > MAX_STRING_LENGTH) {
                length = 0;
            }
            if (length == 0) return "";
            return buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
        }

        private void writeString(ByteBuf buf, String str) {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            int length = Math.min(bytes.length, MAX_STRING_LENGTH);
            buf.writeInt(length);
            buf.writeBytes(bytes, 0, length);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 0;
        size += 4; // waveNumber
        size += 8; // expiresAt
        int count = Math.min(choices.size(), MAX_CHOICES);
        size += 4; // choice count
        for (int i = 0; i < count; i++) {
            size += estimateChoiceSize(choices.get(i));
        }
        return size;
    }

    private static int estimateChoiceSize(DirectiveChoice choice) {
        if (choice == null) {
            return 0;
        }
        int size = 0;
        size += estimatedFixedUtfSize(choice.id);
        size += estimatedFixedUtfSize(choice.name);
        size += estimatedFixedUtfSize(choice.description);
        size += 4; // rewardMultiplier
        return size;
    }

    private static int estimatedFixedUtfSize(String value) {
        if (value == null || value.isEmpty()) {
            return 4;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return 4 + bytes.length;
    }
}
