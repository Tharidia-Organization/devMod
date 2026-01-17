package com.devmod.zone.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;
import com.devmod.zone.data.ZoneDefinition;

/**
 * Client -> Server payload to save a zone configuration.
 * Used when creating a new zone or updating an existing one.
 */
public record SaveZonePayload(
    ZoneDefinition definition,
    @Nullable BlockPos markerPosition,
    @Nullable UUID existingZoneId,
    boolean linkToMarker
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "save_zone")
    );
    public static final Type<SaveZonePayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveZonePayload> STREAM_CODEC =
        StreamCodec.of(SaveZonePayload::encode, SaveZonePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, SaveZonePayload payload) {
        // Definition (required)
        ZoneDefinition.STREAM_CODEC.encode(Objects.requireNonNull(buf), Objects.requireNonNull(payload.definition));

        // Marker position (optional)
        buf.writeBoolean(payload.markerPosition != null);
        if (payload.markerPosition != null) {
            buf.writeBlockPos(payload.markerPosition);
        }

        // Existing zone ID (optional)
        buf.writeBoolean(payload.existingZoneId != null);
        if (payload.existingZoneId != null) {
            buf.writeUUID(payload.existingZoneId);
        }

        // Link to marker flag
        buf.writeBoolean(payload.linkToMarker);
    }

    private static SaveZonePayload decode(RegistryFriendlyByteBuf buf) {
        ZoneDefinition definition = ZoneDefinition.STREAM_CODEC.decode(Objects.requireNonNull(buf));

        BlockPos markerPosition = buf.readBoolean()
            ? buf.readBlockPos()
            : null;

        UUID existingZoneId = buf.readBoolean()
            ? buf.readUUID()
            : null;

        boolean linkToMarker = buf.readBoolean();

        return new SaveZonePayload(definition, markerPosition, existingZoneId, linkToMarker);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 512; // Estimate for ZoneDefinition
        size += 3; // 3 booleans
        if (markerPosition != null) {
            size += 8;
        }
        if (existingZoneId != null) {
            size += 16;
        }
        return size;
    }

    /**
     * Returns true if this is for creating a new zone (no existing ID).
     */
    public boolean isNewZone() {
        return existingZoneId == null;
    }

    /**
     * Returns true if this is for updating an existing zone.
     */
    public boolean isUpdatingZone() {
        return existingZoneId != null;
    }
}
