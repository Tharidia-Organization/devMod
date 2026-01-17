package com.devmod.template.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Client to Server payload for applying a template to a room.
 */
public record ApplyTemplatePayload(
    @Nonnull String templateId,
    int minX, int minZ, int maxX, int maxZ, int floorY,
    boolean clearFirst
) implements CustomPacketPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "template_apply"));
    public static final Type<ApplyTemplatePayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ApplyTemplatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ApplyTemplatePayload decode(FriendlyByteBuf buf) {
            String templateId = buf.readUtf();
            int minX = buf.readVarInt();
            int minZ = buf.readVarInt();
            int maxX = buf.readVarInt();
            int maxZ = buf.readVarInt();
            int floorY = buf.readVarInt();
            boolean clearFirst = buf.readBoolean();
            return new ApplyTemplatePayload(templateId, minX, minZ, maxX, maxZ, floorY, clearFirst);
        }

        @Override
        public void encode(FriendlyByteBuf buf, ApplyTemplatePayload payload) {
            buf.writeUtf(payload.templateId);
            buf.writeVarInt(payload.minX);
            buf.writeVarInt(payload.minZ);
            buf.writeVarInt(payload.maxX);
            buf.writeVarInt(payload.maxZ);
            buf.writeVarInt(payload.floorY);
            buf.writeBoolean(payload.clearFirst);
        }
    };

    public ApplyTemplatePayload {
        Objects.requireNonNull(templateId, "templateId");
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
