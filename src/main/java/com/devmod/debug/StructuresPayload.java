package com.devmod.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Server → Client: structure bounding boxes near a player.
 * <p>
 * Structure starts live only in the server chunk map and are never synced, so the client
 * renderer has no way to read them itself.
 */
public record StructuresPayload(List<StructureBox> boxes) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /** Maximum boxes per payload to prevent DoS via unbounded allocation */
    private static final int MAX_BOXES = 128;

    public static final CustomPacketPayload.Type<StructuresPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_structures")));

    public static final StreamCodec<FriendlyByteBuf, StructuresPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public StructuresPayload decode(@Nonnull FriendlyByteBuf buf) {
            int count = Math.min(buf.readVarInt(), MAX_BOXES);
            if (count < 0) count = 0;
            List<StructureBox> boxes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                boxes.add(new StructureBox(
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), // min x, y, z
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()  // max x, y, z
                ));
            }
            return new StructuresPayload(boxes);
        }

        @Override
        public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull StructuresPayload payload) {
            buf.writeVarInt(payload.boxes.size());
            for (StructureBox box : payload.boxes) {
                buf.writeVarInt(box.minX);
                buf.writeVarInt(box.minY);
                buf.writeVarInt(box.minZ);
                buf.writeVarInt(box.maxX);
                buf.writeVarInt(box.maxY);
                buf.writeVarInt(box.maxZ);
            }
        }
    };

    /** Caps the sender so a dense chunk batch cannot exceed what the codec will decode. */
    public static int maxBoxes() {
        return MAX_BOXES;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.varIntSize(boxes.size());
        for (StructureBox box : boxes) {
            size += PayloadSizeUtil.varIntSize(box.minX);
            size += PayloadSizeUtil.varIntSize(box.minY);
            size += PayloadSizeUtil.varIntSize(box.minZ);
            size += PayloadSizeUtil.varIntSize(box.maxX);
            size += PayloadSizeUtil.varIntSize(box.maxY);
            size += PayloadSizeUtil.varIntSize(box.maxZ);
        }
        return PayloadSizeUtil.clampToInt(size);
    }

    public record StructureBox(
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ
    ) {}
}
