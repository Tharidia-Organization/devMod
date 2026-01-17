package com.devmod.template.network;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.template.data.RoomTemplate;

/**
 * Server to Client payload for opening the template editor.
 */
public record OpenTemplateEditorPayload(
    @Nonnull String zoneId,
    int minX, int minZ, int maxX, int maxZ, int floorY,
    @Nonnull List<RoomTemplate> availableTemplates,
    @Nonnull String currentTemplateId
) implements CustomPacketPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "template_editor_open"));
    public static final Type<OpenTemplateEditorPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, OpenTemplateEditorPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OpenTemplateEditorPayload decode(FriendlyByteBuf buf) {
            String zoneId = buf.readUtf();
            int minX = buf.readVarInt();
            int minZ = buf.readVarInt();
            int maxX = buf.readVarInt();
            int maxZ = buf.readVarInt();
            int floorY = buf.readVarInt();

            int templateCount = buf.readVarInt();
            List<RoomTemplate> templates = new java.util.ArrayList<>(templateCount);
            for (int i = 0; i < templateCount; i++) {
                templates.add(RoomTemplate.STREAM_CODEC.decode(buf));
            }

            String currentTemplateId = buf.readUtf();

            return new OpenTemplateEditorPayload(zoneId, minX, minZ, maxX, maxZ, floorY,
                templates, currentTemplateId);
        }

        @Override
        public void encode(FriendlyByteBuf buf, OpenTemplateEditorPayload payload) {
            buf.writeUtf(payload.zoneId);
            buf.writeVarInt(payload.minX);
            buf.writeVarInt(payload.minZ);
            buf.writeVarInt(payload.maxX);
            buf.writeVarInt(payload.maxZ);
            buf.writeVarInt(payload.floorY);

            buf.writeVarInt(payload.availableTemplates.size());
            for (RoomTemplate template : payload.availableTemplates) {
                RoomTemplate.STREAM_CODEC.encode(buf, template);
            }

            buf.writeUtf(payload.currentTemplateId);
        }
    };

    public OpenTemplateEditorPayload {
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(availableTemplates, "availableTemplates");
        Objects.requireNonNull(currentTemplateId, "currentTemplateId");
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
