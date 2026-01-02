package com.devmod.network;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MobConfigConfirmPayload(
    boolean success,
    boolean isGlobal,
    @Nonnull String mobTypeName,
    int affectedCount,
    @Nonnull String message
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<MobConfigConfirmPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "mob_config_confirm"))
    );

    public static final StreamCodec<ByteBuf, MobConfigConfirmPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            ByteBufCodecs.BOOL.encode(buffer, val.success());
            ByteBufCodecs.BOOL.encode(buffer, val.isGlobal());
            ByteBufCodecs.STRING_UTF8.encode(buffer, val.mobTypeName());
            ByteBufCodecs.VAR_INT.encode(buffer, val.affectedCount());
            ByteBufCodecs.STRING_UTF8.encode(buffer, val.message());
        },
        buffer -> new MobConfigConfirmPayload(
            ByteBufCodecs.BOOL.decode(buffer),
            ByteBufCodecs.BOOL.decode(buffer),
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buffer)),
            ByteBufCodecs.VAR_INT.decode(buffer),
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buffer))
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
        size += 1; // isGlobal
        size += estimatedUtfSize(mobTypeName);
        size += varIntSize(affectedCount);
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

    /**
     * Create a success confirmation.
     */
    public static MobConfigConfirmPayload success(boolean isGlobal, @Nonnull String mobTypeName, int affectedCount) {
        String message = isGlobal
            ? "Global config saved for " + mobTypeName + " (" + affectedCount + " mobs updated)"
            : "Config applied to " + mobTypeName;
        return new MobConfigConfirmPayload(true, isGlobal, mobTypeName, affectedCount, message);
    }

    /**
     * Create a failure confirmation.
     */
    public static MobConfigConfirmPayload failure(@Nonnull String reason) {
        return new MobConfigConfirmPayload(false, false, "", 0, reason);
    }
}
