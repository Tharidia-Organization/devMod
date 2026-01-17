package com.devmod.area.network;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Client -> Server payload to request building an area.
 * Triggers the AreaBuilder to construct the area in the world.
 */
public record BuildAreaPayload(
    UUID areaId,
    boolean clearFirst,
    boolean useMultiTick
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "build_area")
    );
    public static final Type<BuildAreaPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, BuildAreaPayload> STREAM_CODEC =
        StreamCodec.of(BuildAreaPayload::encode, BuildAreaPayload::decode);

    private static void encode(FriendlyByteBuf buf, BuildAreaPayload payload) {
        buf.writeUUID(Objects.requireNonNull(payload.areaId));
        buf.writeBoolean(payload.clearFirst);
        buf.writeBoolean(payload.useMultiTick);
    }

    private static BuildAreaPayload decode(FriendlyByteBuf buf) {
        return new BuildAreaPayload(
            buf.readUUID(),
            buf.readBoolean(),
            buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 16 + 2; // UUID + 2 booleans
    }

    /**
     * Creates a payload for immediate building (no clear, single-tick).
     */
    public static BuildAreaPayload immediate(UUID areaId) {
        return new BuildAreaPayload(areaId, false, false);
    }

    /**
     * Creates a payload for building with clear (clears existing blocks first).
     */
    public static BuildAreaPayload withClear(UUID areaId) {
        return new BuildAreaPayload(areaId, true, false);
    }

    /**
     * Creates a payload for multi-tick building (for large areas).
     */
    public static BuildAreaPayload multiTick(UUID areaId, boolean clearFirst) {
        return new BuildAreaPayload(areaId, clearFirst, true);
    }
}
