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

/**
 * Server -> Client payload to open the Area Builder screen.
 * Used when creating a new area or editing an existing one.
 */
public record OpenAreaBuilderPayload(
    @Nullable AreaDefinition existingArea,
    @Nullable BlockPos editorPosition,
    boolean isMainHub
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "open_area_builder")
    );
    public static final Type<OpenAreaBuilderPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAreaBuilderPayload> STREAM_CODEC =
        StreamCodec.of(OpenAreaBuilderPayload::encode, OpenAreaBuilderPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, OpenAreaBuilderPayload payload) {
        // Existing area (optional)
        buf.writeBoolean(payload.existingArea != null);
        if (payload.existingArea != null) {
            AreaDefinition.STREAM_CODEC.encode(buf, payload.existingArea);
        }

        // Editor position (optional)
        buf.writeBoolean(payload.editorPosition != null);
        if (payload.editorPosition != null) {
            buf.writeBlockPos(payload.editorPosition);
        }

        // Is main hub flag
        buf.writeBoolean(payload.isMainHub);
    }

    private static OpenAreaBuilderPayload decode(RegistryFriendlyByteBuf buf) {
        AreaDefinition existingArea = buf.readBoolean()
            ? AreaDefinition.STREAM_CODEC.decode(buf)
            : null;

        BlockPos editorPosition = buf.readBoolean()
            ? buf.readBlockPos()
            : null;

        boolean isMainHub = buf.readBoolean();

        return new OpenAreaBuilderPayload(existingArea, editorPosition, isMainHub);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 3; // 3 booleans
        if (existingArea != null) {
            size += 1024; // Estimate for AreaDefinition
        }
        if (editorPosition != null) {
            size += 8; // BlockPos
        }
        return size;
    }

    /**
     * Returns true if this is for creating a new area (no existing area).
     */
    public boolean isNewArea() {
        return existingArea == null;
    }

    /**
     * Returns true if this is for editing an existing area.
     */
    public boolean isEditingArea() {
        return existingArea != null;
    }

    /**
     * Gets the area ID if editing, null otherwise.
     */
    @Nullable
    public UUID getAreaId() {
        return existingArea != null ? existingArea.id() : null;
    }
}
