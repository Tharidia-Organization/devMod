package com.frenkvs.devmod.endurance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Network payload sent from client to server when player selects a perk.
 * Empty perkId means the player chose to skip.
 */
public record PerkSelectionPayload(String perkId) implements CustomPacketPayload {

    private static final int MAX_STRING_LENGTH = 128;

    public static final Type<PerkSelectionPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "perk_selection"))
    );

    public static final StreamCodec<ByteBuf, PerkSelectionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PerkSelectionPayload decode(@Nonnull ByteBuf buf) {
            int length = buf.readInt();
            if (length < 0 || length > MAX_STRING_LENGTH) {
                length = 0;
            }
            String perkId = length > 0
                ? buf.readCharSequence(length, StandardCharsets.UTF_8).toString()
                : "";
            return new PerkSelectionPayload(perkId);
        }

        @Override
        public void encode(@Nonnull ByteBuf buf, @Nonnull PerkSelectionPayload payload) {
            byte[] bytes = payload.perkId.getBytes(StandardCharsets.UTF_8);
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

    /**
     * Check if this is a skip selection (no perk chosen).
     */
    public boolean isSkip() {
        return perkId == null || perkId.isEmpty();
    }
}
