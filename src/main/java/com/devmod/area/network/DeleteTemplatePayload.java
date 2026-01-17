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
 * Client -> Server: Request to delete a template.
 * Only the template creator or admin (OP level 4) can delete templates.
 */
public record DeleteTemplatePayload(UUID templateId) implements CustomPacketPayload {

    public static final Type<DeleteTemplatePayload> TYPE =
        new Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "delete_template")));

    public static final StreamCodec<FriendlyByteBuf, DeleteTemplatePayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), DeleteTemplatePayload::templateId,
            DeleteTemplatePayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
