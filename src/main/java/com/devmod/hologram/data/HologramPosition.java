package com.devmod.hologram.data;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Position data for a hologram, including block position, fine offset, dimension, and rotation.
 */
public record HologramPosition(
    BlockPos blockPos,
    Vec3 offset,
    ResourceLocation dimension,
    float yaw,
    float pitch
) {
    /**
     * Zero position in the overworld.
     */
    public static final HologramPosition ZERO = new HologramPosition(
        BlockPos.ZERO,
        Vec3.ZERO,
        Level.OVERWORLD.location(),
        0f,
        0f
    );

    public static final Codec<HologramPosition> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BlockPos.CODEC.fieldOf("blockPos").forGetter(HologramPosition::blockPos),
            Vec3.CODEC.fieldOf("offset").forGetter(HologramPosition::offset),
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(HologramPosition::dimension),
            Codec.FLOAT.fieldOf("yaw").forGetter(HologramPosition::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(HologramPosition::pitch)
        ).apply(instance, HologramPosition::new)
    );

    public static final StreamCodec<FriendlyByteBuf, HologramPosition> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HologramPosition decode(FriendlyByteBuf buf) {
            return new HologramPosition(
                buf.readBlockPos(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readResourceLocation(),
                buf.readFloat(),
                buf.readFloat()
            );
        }

        @Override
        public void encode(FriendlyByteBuf buf, HologramPosition pos) {
            buf.writeBlockPos(pos.blockPos);
            buf.writeDouble(pos.offset.x);
            buf.writeDouble(pos.offset.y);
            buf.writeDouble(pos.offset.z);
            buf.writeResourceLocation(pos.dimension);
            buf.writeFloat(pos.yaw);
            buf.writeFloat(pos.pitch);
        }
    };

    public HologramPosition {
        Objects.requireNonNull(blockPos, "blockPos");
        Objects.requireNonNull(offset, "offset");
        Objects.requireNonNull(dimension, "dimension");
    }

    /**
     * Calculates the exact world position by combining blockPos center with offset.
     */
    public Vec3 getExactPosition() {
        return Vec3.atCenterOf(blockPos).add(offset);
    }

    /**
     * Creates a position at the center of the given block.
     */
    public static HologramPosition atBlock(BlockPos pos, ResourceLocation dimension) {
        return new HologramPosition(pos, Vec3.ZERO, dimension, 0f, 0f);
    }

    /**
     * Creates a position above the given block (one block up).
     */
    public static HologramPosition aboveBlock(BlockPos pos, ResourceLocation dimension) {
        return new HologramPosition(pos.above(), Vec3.ZERO, dimension, 0f, 0f);
    }

    /**
     * Returns a new position with adjusted offset.
     */
    public HologramPosition withOffset(Vec3 newOffset) {
        return new HologramPosition(blockPos, newOffset, dimension, yaw, pitch);
    }

    /**
     * Returns a new position with adjusted rotation.
     */
    public HologramPosition withRotation(float newYaw, float newPitch) {
        return new HologramPosition(blockPos, offset, dimension, newYaw, newPitch);
    }

    /**
     * Returns the short string representation of block position.
     */
    public String toShortString() {
        return blockPos.toShortString();
    }
}
