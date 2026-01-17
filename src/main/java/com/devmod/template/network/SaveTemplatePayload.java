package com.devmod.template.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.template.data.RoomTemplate;

/**
 * Client to Server payload for saving a custom template.
 */
public record SaveTemplatePayload(
    @Nonnull RoomTemplate template,
    int expectedRevision
) implements CustomPacketPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "template_save"));
    public static final Type<SaveTemplatePayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, SaveTemplatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SaveTemplatePayload decode(@Nonnull FriendlyByteBuf buf) {
            RoomTemplate template = RoomTemplate.STREAM_CODEC.decode(Objects.requireNonNull(buf));
            int expectedRevision = buf.readVarInt();
            return new SaveTemplatePayload(template, expectedRevision);
        }

        @Override
        public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull SaveTemplatePayload payload) {
            RoomTemplate.STREAM_CODEC.encode(Objects.requireNonNull(buf), payload.template);
            buf.writeVarInt(payload.expectedRevision);
        }
    };

    public SaveTemplatePayload {
        Objects.requireNonNull(template, "template");
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
