package com.devmod.zone.network;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Client -> Server payload to delete a zone.
 * Requires OP level 2+.
 */
public record DeleteZonePayload(
    UUID zoneId,
    boolean removeMarkers
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "delete_zone")
    );
    public static final Type<DeleteZonePayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteZonePayload> STREAM_CODEC =
        StreamCodec.of(DeleteZonePayload::encode, DeleteZonePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, DeleteZonePayload payload) {
        buf.writeUUID(Objects.requireNonNull(payload.zoneId));
        buf.writeBoolean(payload.removeMarkers);
    }

    private static DeleteZonePayload decode(RegistryFriendlyByteBuf buf) {
        UUID zoneId = buf.readUUID();
        boolean removeMarkers = buf.readBoolean();
        return new DeleteZonePayload(zoneId, removeMarkers);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 17; // UUID (16) + boolean (1)
    }
}
