package com.frenkvs.devmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Server-to-client confirmation for editor apply actions (weapon/armor).
 * Carries scope, item id, global flag and an optional message so the client
 * can log in the debug/session history.
 */
public record EditorApplyConfirmPayload(
    boolean success,
    boolean global,
    @Nonnull String scope,   // "weapon" | "armor" | other
    @Nonnull String itemId,  // registry id or display name
    @Nonnull String message
) implements CustomPacketPayload {

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
}
