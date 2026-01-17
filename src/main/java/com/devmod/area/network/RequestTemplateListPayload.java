package com.devmod.area.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Client -> Server: Request template list for TemplateListWidget.
 * Empty payload - server responds with TemplateListPayload.
 */
public record RequestTemplateListPayload() implements CustomPacketPayload {

    public static final Type<RequestTemplateListPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "request_template_list")));

    public static final StreamCodec<FriendlyByteBuf, RequestTemplateListPayload> STREAM_CODEC =
        StreamCodec.unit(new RequestTemplateListPayload());

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
