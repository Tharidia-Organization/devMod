package com.devmod.network;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EditorApplyConfirmPayload(
    boolean success,
    boolean global,
    @Nonnull String scope,   // "weapon" | "armor" | other
    @Nonnull String itemId,  // registry id or display name
    @Nonnull String message
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<EditorApplyConfirmPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "editor_apply_confirm")));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorApplyConfirmPayload> STREAM_CODEC = StreamCodec.of(
        (buf, val) -> {
            ByteBufCodecs.BOOL.encode(buf, val.success());
            ByteBufCodecs.BOOL.encode(buf, val.global());
            ByteBufCodecs.STRING_UTF8.encode(buf, Objects.requireNonNull(val.scope()));
            ByteBufCodecs.STRING_UTF8.encode(buf, Objects.requireNonNull(val.itemId()));
            ByteBufCodecs.STRING_UTF8.encode(buf, Objects.requireNonNull(val.message()));
        },
        buf -> new EditorApplyConfirmPayload(
            Objects.requireNonNull(ByteBufCodecs.BOOL.decode(buf)),
            Objects.requireNonNull(ByteBufCodecs.BOOL.decode(buf)),
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buf)),
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
        size += 1; // global
        size += estimatedUtfSize(scope);
        size += estimatedUtfSize(itemId);
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
}
