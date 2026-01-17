package com.devmod.area.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Client -> Server payload to request opening the Area Builder screen.
 * Used by the Nexus Editor Central screen to open a specific area or create a new one.
 */
public record RequestOpenAreaBuilderPayload(
    @Nullable UUID areaId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "request_open_area_builder")
    );
    public static final Type<RequestOpenAreaBuilderPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, RequestOpenAreaBuilderPayload> STREAM_CODEC =
        StreamCodec.of(RequestOpenAreaBuilderPayload::encode, RequestOpenAreaBuilderPayload::decode);

    private static void encode(FriendlyByteBuf buf, RequestOpenAreaBuilderPayload payload) {
        buf.writeBoolean(payload.areaId != null);
        if (payload.areaId != null) {
            buf.writeUUID(payload.areaId);
        }
    }

    private static RequestOpenAreaBuilderPayload decode(FriendlyByteBuf buf) {
        UUID areaId = buf.readBoolean() ? buf.readUUID() : null;
        return new RequestOpenAreaBuilderPayload(areaId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 1 + (areaId != null ? 16 : 0);
    }
}
