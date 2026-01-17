package com.devmod.area.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Server -> Client: Confirms an area save and returns the authoritative ID/revision.
 *
 * @param requestId The client-provided ID from the save request (for correlation)
 * @param areaId    The authoritative area ID assigned by the server
 * @param revision  The new revision after save
 * @param isNewArea Whether the save created a new area
 */
public record SaveAreaResultPayload(
    UUID requestId,
    UUID areaId,
    int revision,
    boolean isNewArea
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "save_area_result")
    );
    public static final Type<SaveAreaResultPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, SaveAreaResultPayload> STREAM_CODEC =
        StreamCodec.of(SaveAreaResultPayload::encode, SaveAreaResultPayload::decode);

    private static void encode(FriendlyByteBuf buf, SaveAreaResultPayload payload) {
        FriendlyByteBuf b = Objects.requireNonNull(buf);
        UUIDUtil.STREAM_CODEC.encode(b, Objects.requireNonNull(payload.requestId));
        UUIDUtil.STREAM_CODEC.encode(b, Objects.requireNonNull(payload.areaId));
        ByteBufCodecs.VAR_INT.encode(b, payload.revision);
        ByteBufCodecs.BOOL.encode(b, payload.isNewArea);
    }

    private static SaveAreaResultPayload decode(FriendlyByteBuf buf) {
        FriendlyByteBuf b = Objects.requireNonNull(buf);
        return new SaveAreaResultPayload(
            UUIDUtil.STREAM_CODEC.decode(b),
            UUIDUtil.STREAM_CODEC.decode(b),
            ByteBufCodecs.VAR_INT.decode(b),
            ByteBufCodecs.BOOL.decode(b)
        );
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        return 16 + 16 + 5 + 1; // 2 UUIDs + VarInt + boolean
    }
}
