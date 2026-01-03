package com.devmod.endurance;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

/**
 * Server -> Client confirmation for kit sync requests.
 */
public record KitSyncConfirmPayload(
    boolean success,
    boolean temporary,
    @Nonnull String kitId,
    @Nonnull String message
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<KitSyncConfirmPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "kit_sync_confirm"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, KitSyncConfirmPayload> STREAM_CODEC = StreamCodec.of(
        (buf, val) -> {
            ByteBufCodecs.BOOL.encode(buf, val.success());
            ByteBufCodecs.BOOL.encode(buf, val.temporary());
            ByteBufCodecs.STRING_UTF8.encode(buf, Objects.requireNonNull(val.kitId()));
            ByteBufCodecs.STRING_UTF8.encode(buf, Objects.requireNonNull(val.message()));
        },
        buf -> new KitSyncConfirmPayload(
            Objects.requireNonNull(ByteBufCodecs.BOOL.decode(buf)),
            Objects.requireNonNull(ByteBufCodecs.BOOL.decode(buf)),
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buf)),
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buf))
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 0;
        size += 1; // success
        size += 1; // temporary
        size += estimatedUtfSize(kitId);
        size += estimatedUtfSize(message);
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

    public static KitSyncConfirmPayload success(boolean temporary, String kitId, String message) {
        return new KitSyncConfirmPayload(true, temporary,
            kitId != null ? kitId : "",
            message != null ? message : "");
    }

    public static KitSyncConfirmPayload failure(boolean temporary, String kitId, String message) {
        return new KitSyncConfirmPayload(false, temporary,
            kitId != null ? kitId : "",
            message != null ? message : "");
    }
}
