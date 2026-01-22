package com.devmod.area.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Client -> Server: Request to clone an existing area to a new position.
 *
 * @param sourceAreaId   The UUID of the area to clone
 * @param newCenter      The center position for the cloned area
 * @param newName        The name for the cloned area
 * @param keepLinkedZone Whether to keep the same zone link (true) or clear it (false)
 */
public record CloneAreaPayload(
    UUID sourceAreaId,
    BlockPos newCenter,
    String newName,
    boolean keepLinkedZone
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /** Maximum name length for cloned area */
    public static final int MAX_NAME_LENGTH = 64;

    public static final Type<CloneAreaPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "clone_area")));

    public static final StreamCodec<FriendlyByteBuf, CloneAreaPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), CloneAreaPayload::sourceAreaId,
            Objects.requireNonNull(BlockPos.STREAM_CODEC), CloneAreaPayload::newCenter,
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8), CloneAreaPayload::newName,
            Objects.requireNonNull(ByteBufCodecs.BOOL), CloneAreaPayload::keepLinkedZone,
            CloneAreaPayload::create
        );

    /** Factory method to handle Boolean -> boolean conversion for StreamCodec */
    private static CloneAreaPayload create(UUID sourceAreaId, BlockPos newCenter, String newName, Boolean keepLinkedZone) {
        return new CloneAreaPayload(sourceAreaId, newCenter, newName,
            keepLinkedZone != null && keepLinkedZone.booleanValue());
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        long size = 16; // UUID
        size += PayloadSizeUtil.varLongSize(newCenter.asLong());
        size += PayloadSizeUtil.estimatedUtfSize(newName);
        size += 1; // keepLinkedZone
        return PayloadSizeUtil.clampToInt(size);
    }

    /**
     * Validates the payload data.
     *
     * @return True if payload is valid
     */
    public boolean isValid() {
        return sourceAreaId != null
            && newCenter != null
            && newName != null
            && !newName.isBlank()
            && newName.length() <= MAX_NAME_LENGTH;
    }
}
