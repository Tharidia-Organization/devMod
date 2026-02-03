package com.devmod.area.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.area.data.AreaDefinition;
import com.devmod.network.PayloadValidation;
import com.devmod.network.PayloadSizeUtil;

/**
 * Client -> Server payload to save an area configuration.
 * Used when creating a new area or updating an existing one.
 */
public record SaveAreaPayload(
    AreaDefinition definition,
    @Nullable BlockPos editorPosition,
    @Nullable UUID existingAreaId,
    int expectedRevision
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "save_area")
    );
    public static final Type<SaveAreaPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveAreaPayload> STREAM_CODEC =
        StreamCodec.of(SaveAreaPayload::encode, SaveAreaPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, SaveAreaPayload payload) {
        // Definition (required)
        AreaDefinition.STREAM_CODEC.encode(Objects.requireNonNull(buf), Objects.requireNonNull(payload.definition));

        // Editor position (optional)
        buf.writeBoolean(payload.editorPosition != null);
        if (payload.editorPosition != null) {
            buf.writeBlockPos(payload.editorPosition);
        }

        // Existing area ID (optional)
        buf.writeBoolean(payload.existingAreaId != null);
        if (payload.existingAreaId != null) {
            buf.writeUUID(payload.existingAreaId);
        }

        // Expected revision for optimistic locking
        buf.writeVarInt(payload.expectedRevision);
    }

    private static SaveAreaPayload decode(RegistryFriendlyByteBuf buf) {
        AreaDefinition definition = AreaDefinition.STREAM_CODEC.decode(Objects.requireNonNull(buf));

        BlockPos editorPosition = buf.readBoolean()
            ? buf.readBlockPos()
            : null;

        UUID existingAreaId = buf.readBoolean()
            ? buf.readUUID()
            : null;

        int expectedRevision = buf.readVarInt();

        return new SaveAreaPayload(definition, editorPosition, existingAreaId, expectedRevision);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 1024; // Estimate for AreaDefinition
        size += 2; // 2 booleans
        if (editorPosition != null) {
            size += 8;
        }
        if (existingAreaId != null) {
            size += 16;
        }
        size += PayloadSizeUtil.varIntSize(expectedRevision);
        return size;
    }

    /**
     * Returns true if this is for creating a new area (no existing ID).
     */
    public boolean isNewArea() {
        return existingAreaId == null;
    }

    /**
     * Returns true if this is for updating an existing area.
     */
    public boolean isUpdatingArea() {
        return existingAreaId != null;
    }
}
