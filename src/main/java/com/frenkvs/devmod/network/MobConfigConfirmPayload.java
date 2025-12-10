package com.frenkvs.devmod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Server-to-client confirmation payload sent after mob configuration is applied.
 * Provides feedback to the user that their changes were successfully saved.
 */
public record MobConfigConfirmPayload(
    boolean success,
    boolean isGlobal,
    @Nonnull String mobTypeName,
    int affectedCount,
    @Nonnull String message
) implements CustomPacketPayload {

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
