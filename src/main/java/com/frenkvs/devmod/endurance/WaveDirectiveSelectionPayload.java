package com.frenkvs.devmod.endurance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Network payload sent from client to server when player selects a wave directive.
 */
public record WaveDirectiveSelectionPayload(String directiveId, int waveNumber) implements CustomPacketPayload {

    private static final int MAX_STRING_LENGTH = 128;

    public static final Type<WaveDirectiveSelectionPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "directive_selection"))
    );

    public static final StreamCodec<ByteBuf, WaveDirectiveSelectionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WaveDirectiveSelectionPayload decode(@Nonnull ByteBuf buf) {
            int waveNumber = buf.readInt();
            int length = buf.readInt();
            if (length < 0 || length > MAX_STRING_LENGTH) {
                length = 0;
            }
            String directiveId = length > 0
                ? buf.readCharSequence(length, StandardCharsets.UTF_8).toString()
                : "";
            return new WaveDirectiveSelectionPayload(directiveId, waveNumber);
        }

        @Override
        public void encode(@Nonnull ByteBuf buf, @Nonnull WaveDirectiveSelectionPayload payload) {
            buf.writeInt(payload.waveNumber);
            byte[] bytes = payload.directiveId.getBytes(StandardCharsets.UTF_8);
            int length = Math.min(bytes.length, MAX_STRING_LENGTH);
            buf.writeInt(length);
            if (length > 0) {
                buf.writeBytes(bytes, 0, length);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public boolean isSkip() {
        return directiveId == null || directiveId.isEmpty();
    }
}
