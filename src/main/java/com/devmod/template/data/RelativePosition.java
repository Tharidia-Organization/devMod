package com.devmod.template.data;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Position relative to an anchor point in a room.
 */
public record RelativePosition(
    @Nonnull AnchorPoint anchor,
    int offsetX,
    int offsetY,
    int offsetZ
) {
    public static final Codec<RelativePosition> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("anchor").xmap(AnchorPoint::fromString, AnchorPoint::getSerializedName)
                .forGetter(RelativePosition::anchor),
            Codec.INT.fieldOf("offsetX").forGetter(RelativePosition::offsetX),
            Codec.INT.fieldOf("offsetY").forGetter(RelativePosition::offsetY),
            Codec.INT.fieldOf("offsetZ").forGetter(RelativePosition::offsetZ)
        ).apply(instance, RelativePosition::new)
    );

    public static final StreamCodec<FriendlyByteBuf, RelativePosition> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RelativePosition decode(FriendlyByteBuf buf) {
            AnchorPoint anchor = AnchorPoint.fromString(buf.readUtf());
            int offsetX = buf.readVarInt();
            int offsetY = buf.readVarInt();
            int offsetZ = buf.readVarInt();
            return new RelativePosition(anchor, offsetX, offsetY, offsetZ);
        }

        @Override
        public void encode(FriendlyByteBuf buf, RelativePosition pos) {
            buf.writeUtf(pos.anchor.getSerializedName());
            buf.writeVarInt(pos.offsetX);
            buf.writeVarInt(pos.offsetY);
            buf.writeVarInt(pos.offsetZ);
        }
    };

    public RelativePosition {
        Objects.requireNonNull(anchor, "anchor");
    }

    /**
     * Resolve this relative position to an absolute BlockPos.
     */
    public BlockPos resolve(int minX, int minZ, int maxX, int maxZ, int floorY) {
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int width = maxX - minX;
        int depth = maxZ - minZ;

        int baseX, baseZ;

        switch (anchor) {
            case CENTER -> {
                baseX = centerX;
                baseZ = centerZ;
            }
            case NORTH_WALL -> {
                baseX = centerX;
                baseZ = minZ;
            }
            case SOUTH_WALL -> {
                baseX = centerX;
                baseZ = maxZ;
            }
            case EAST_WALL -> {
                baseX = maxX;
                baseZ = centerZ;
            }
            case WEST_WALL -> {
                baseX = minX;
                baseZ = centerZ;
            }
            case NW_CORNER -> {
                baseX = minX;
                baseZ = minZ;
            }
            case NE_CORNER -> {
                baseX = maxX;
                baseZ = minZ;
            }
            case SW_CORNER -> {
                baseX = minX;
                baseZ = maxZ;
            }
            case SE_CORNER -> {
                baseX = maxX;
                baseZ = maxZ;
            }
            case NORTH_CENTER -> {
                baseX = centerX;
                baseZ = minZ + depth / 4;
            }
            case SOUTH_CENTER -> {
                baseX = centerX;
                baseZ = maxZ - depth / 4;
            }
            case EAST_CENTER -> {
                baseX = maxX - width / 4;
                baseZ = centerZ;
            }
            case WEST_CENTER -> {
                baseX = minX + width / 4;
                baseZ = centerZ;
            }
            default -> {
                baseX = centerX;
                baseZ = centerZ;
            }
        }

        return new BlockPos(baseX + offsetX, floorY + offsetY, baseZ + offsetZ);
    }

    // Factory methods for common positions
    public static RelativePosition center() {
        return new RelativePosition(AnchorPoint.CENTER, 0, 0, 0);
    }

    public static RelativePosition center(int offsetX, int offsetZ) {
        return new RelativePosition(AnchorPoint.CENTER, offsetX, 0, offsetZ);
    }

    public static RelativePosition northWall(int offset) {
        return new RelativePosition(AnchorPoint.NORTH_WALL, 0, 0, offset);
    }

    public static RelativePosition southWall(int offset) {
        return new RelativePosition(AnchorPoint.SOUTH_WALL, 0, 0, -offset);
    }

    public static RelativePosition eastWall(int offset) {
        return new RelativePosition(AnchorPoint.EAST_WALL, -offset, 0, 0);
    }

    public static RelativePosition westWall(int offset) {
        return new RelativePosition(AnchorPoint.WEST_WALL, offset, 0, 0);
    }
}
