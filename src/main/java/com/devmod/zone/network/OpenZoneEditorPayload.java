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
 * Server -> Client payload to open the Zone Editor screen.
 * Used when creating a new zone or editing an existing one via zone marker.
 */
public record OpenZoneEditorPayload(
    @Nullable ZoneDefinition existingZone,
    @Nullable BlockPos markerPosition,
    boolean canDelete
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "open_zone_editor")
    );
    public static final Type<OpenZoneEditorPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenZoneEditorPayload> STREAM_CODEC =
        StreamCodec.of(OpenZoneEditorPayload::encode, OpenZoneEditorPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, OpenZoneEditorPayload payload) {
        // Existing zone (optional)
        buf.writeBoolean(payload.existingZone != null);
        if (payload.existingZone != null) {
            ZoneDefinition.STREAM_CODEC.encode(buf, payload.existingZone);
        }

        // Marker position (optional)
        buf.writeBoolean(payload.markerPosition != null);
        if (payload.markerPosition != null) {
            buf.writeBlockPos(payload.markerPosition);
        }

        // Can delete flag
        buf.writeBoolean(payload.canDelete);
    }

    private static OpenZoneEditorPayload decode(RegistryFriendlyByteBuf buf) {
        ZoneDefinition existingZone = buf.readBoolean()
            ? ZoneDefinition.STREAM_CODEC.decode(buf)
            : null;

        BlockPos markerPosition = buf.readBoolean()
            ? buf.readBlockPos()
            : null;

        boolean canDelete = buf.readBoolean();

        return new OpenZoneEditorPayload(existingZone, markerPosition, canDelete);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 3; // 3 booleans
        if (existingZone != null) {
            size += 512; // Estimate for ZoneDefinition
        }
        if (markerPosition != null) {
            size += 8; // BlockPos
        }
        return size;
    }

    /**
     * Returns true if this is for creating a new zone (no existing zone).
     */
    public boolean isNewZone() {
        return existingZone == null;
    }

    /**
     * Returns true if this is for editing an existing zone.
     */
    public boolean isEditingZone() {
        return existingZone != null;
    }

    /**
     * Gets the zone ID if editing, null otherwise.
     */
    @Nullable
    public UUID getZoneUUID() {
        return existingZone != null ? existingZone.id() : null;
    }

    /**
     * Gets the zone string ID if editing, null otherwise.
     */
    @Nullable
    public String getZoneId() {
        return existingZone != null ? existingZone.zoneId() : null;
    }
}
