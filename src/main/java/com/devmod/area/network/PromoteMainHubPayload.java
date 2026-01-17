package com.devmod.area.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Client -> Server: Request to promote an area to main hub status.
 * Only admins (OP level 2+) can promote areas.
 * The current main hub (if any) will be demoted to regular area.
 */
public record PromoteMainHubPayload(UUID areaId) implements CustomPacketPayload {

    public static final Type<PromoteMainHubPayload> TYPE =
        new Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "promote_main_hub")));

    public static final StreamCodec<FriendlyByteBuf, PromoteMainHubPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), PromoteMainHubPayload::areaId,
            PromoteMainHubPayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
