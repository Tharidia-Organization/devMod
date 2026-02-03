package com.devmod.area.network;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.area.builder.AreaShapeGenerator;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaShape;
import com.devmod.network.PayloadValidation;
import com.devmod.network.PayloadSizeUtil;

/**
 * Server -> Client payload containing preview data for area visualization.
 * Used to show a preview of the area before building.
 */
public record AreaPreviewPayload(
    BlockPos center,
    int width,
    int length,
    int height,
    String shape,
    List<PreviewBlock> outlineBlocks,
    boolean useBiome,
    String biomeId,
    String terrainStyle,
    long biomeSeed
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "area_preview")
    );
    public static final Type<AreaPreviewPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, AreaPreviewPayload> STREAM_CODEC =
        StreamCodec.of(AreaPreviewPayload::encode, AreaPreviewPayload::decode);

    /**
     * Represents a block in the preview outline.
     */
    public record PreviewBlock(
        BlockPos pos,
        PreviewBlockType type
    ) {
        public static final StreamCodec<FriendlyByteBuf, PreviewBlock> STREAM_CODEC =
            StreamCodec.of(PreviewBlock::encode, PreviewBlock::decode);

        private static void encode(FriendlyByteBuf buf, PreviewBlock block) {
            buf.writeBlockPos(Objects.requireNonNull(block.pos));
            buf.writeEnum(Objects.requireNonNull(block.type));
        }

        private static PreviewBlock decode(FriendlyByteBuf buf) {
            return new PreviewBlock(
                buf.readBlockPos(),
                buf.readEnum(PreviewBlockType.class)
            );
        }
    }

    /**
     * Type of preview block for different visualization.
     */
    public enum PreviewBlockType {
        CORNER,
        EDGE,
        FLOOR,
        WALL,
        CEILING,
        CENTER
    }

    /** M-07 fix: Maximum preview blocks to send in a single payload */
    public static final int MAX_PREVIEW_BLOCKS = 1000;

    private static void encode(FriendlyByteBuf buf, AreaPreviewPayload payload) {
        buf.writeBlockPos(Objects.requireNonNull(payload.center));
        buf.writeVarInt(payload.width);
        buf.writeVarInt(payload.length);
        buf.writeVarInt(payload.height);
        buf.writeUtf(Objects.requireNonNull(payload.shape), 32);

        // Outline blocks (limit to prevent huge payloads) - M-07 fix: use constant
        int blockCount = Math.min(payload.outlineBlocks.size(), MAX_PREVIEW_BLOCKS);
        buf.writeVarInt(blockCount);
        for (int i = 0; i < blockCount; i++) {
            PreviewBlock.STREAM_CODEC.encode(buf, Objects.requireNonNull(payload.outlineBlocks.get(i)));
        }

        // Biome data
        buf.writeBoolean(payload.useBiome);
        buf.writeUtf(Objects.requireNonNull(payload.biomeId), 128);
        buf.writeUtf(Objects.requireNonNull(payload.terrainStyle), 32);
        buf.writeLong(payload.biomeSeed);
    }

    private static AreaPreviewPayload decode(FriendlyByteBuf buf) {
        BlockPos center = buf.readBlockPos();
        int width = buf.readVarInt();
        int length = buf.readVarInt();
        int height = buf.readVarInt();
        String shape = buf.readUtf(32);

        int blockCount = buf.readVarInt();
        List<PreviewBlock> outlineBlocks = new java.util.ArrayList<>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            outlineBlocks.add(PreviewBlock.STREAM_CODEC.decode(buf));
        }

        // Biome data
        boolean useBiome = buf.readBoolean();
        String biomeId = buf.readUtf(128);
        String terrainStyle = buf.readUtf(32);
        long biomeSeed = buf.readLong();

        return new AreaPreviewPayload(center, width, length, height, shape, List.copyOf(outlineBlocks),
            useBiome, biomeId, terrainStyle, biomeSeed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int blockCount = outlineBlocks != null ? Math.min(outlineBlocks.size(), MAX_PREVIEW_BLOCKS) : 0;
        int size = 8; // BlockPos
        size += PayloadSizeUtil.varIntSize(width);
        size += PayloadSizeUtil.varIntSize(length);
        size += PayloadSizeUtil.varIntSize(height);
        size += PayloadSizeUtil.estimatedUtfSize(shape);
        size += PayloadSizeUtil.varIntSize(blockCount);
        for (int i = 0; i < blockCount; i++) {
            PreviewBlock block = outlineBlocks.get(i);
            int typeOrdinal = block != null && block.type() != null ? block.type().ordinal() : 0;
            size += 8; // BlockPos
            size += PayloadSizeUtil.varIntSize(typeOrdinal);
        }
        size += 1; // useBiome
        size += PayloadSizeUtil.estimatedUtfSize(biomeId);
        size += PayloadSizeUtil.estimatedUtfSize(terrainStyle);
        size += 8; // biomeSeed
        return size;
    }

    /**
     * Creates a simple bounding box preview without biome.
     */
    public static AreaPreviewPayload boundingBox(BlockPos center, int width, int length, int height, String shape) {
        return boundingBox(center, width, length, height, shape, false, "", "NATURAL", 0L);
    }

    /**
     * Creates a bounding box preview with optional biome configuration.
     */
    public static AreaPreviewPayload boundingBox(BlockPos center, int width, int length, int height, String shape,
                                                  boolean useBiome, String biomeId, String terrainStyle, long biomeSeed) {
        List<PreviewBlock> outline = new java.util.ArrayList<>();

        int minX = com.devmod.area.builder.AreaShapeGenerator.minOffset(width);
        int maxX = com.devmod.area.builder.AreaShapeGenerator.maxOffset(width);
        int minZ = com.devmod.area.builder.AreaShapeGenerator.minOffset(length);
        int maxZ = com.devmod.area.builder.AreaShapeGenerator.maxOffset(length);

        // H-09 fix: Validate positions before adding to prevent world bounds overflow
        // Add corner markers (only if within valid world bounds)
        addValidatedPreviewBlock(outline, center, minX, 0, minZ, PreviewBlockType.CORNER);
        addValidatedPreviewBlock(outline, center, maxX, 0, minZ, PreviewBlockType.CORNER);
        addValidatedPreviewBlock(outline, center, minX, 0, maxZ, PreviewBlockType.CORNER);
        addValidatedPreviewBlock(outline, center, maxX, 0, maxZ, PreviewBlockType.CORNER);

        // Add top corners
        addValidatedPreviewBlock(outline, center, minX, height, minZ, PreviewBlockType.CORNER);
        addValidatedPreviewBlock(outline, center, maxX, height, minZ, PreviewBlockType.CORNER);
        addValidatedPreviewBlock(outline, center, minX, height, maxZ, PreviewBlockType.CORNER);
        addValidatedPreviewBlock(outline, center, maxX, height, maxZ, PreviewBlockType.CORNER);

        // Add center marker
        addValidatedPreviewBlock(outline, center, 0, 0, 0, PreviewBlockType.CENTER);

        return new AreaPreviewPayload(center, width, length, height, shape, List.copyOf(outline),
            useBiome, biomeId, terrainStyle, biomeSeed);
    }

    /** H-09 fix: Minecraft world coordinate bounds */
    private static final int MIN_WORLD_COORD = -30_000_000;
    private static final int MAX_WORLD_COORD = 30_000_000;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    /**
     * H-09 fix: Adds a preview block only if it's within valid world bounds.
     */
    private static void addValidatedPreviewBlock(List<PreviewBlock> outline, BlockPos center,
                                                  int offsetX, int offsetY, int offsetZ, PreviewBlockType type) {
        int x = center.getX() + offsetX;
        int y = center.getY() + offsetY;
        int z = center.getZ() + offsetZ;

        // Validate coordinates are within Minecraft world bounds
        if (x >= MIN_WORLD_COORD && x < MAX_WORLD_COORD &&
            y >= MIN_Y && y <= MAX_Y &&
            z >= MIN_WORLD_COORD && z < MAX_WORLD_COORD) {
            outline.add(new PreviewBlock(new BlockPos(x, y, z), type));
        }
    }

    // ========================================================================
    // HIGH-01 fix: Shape-accurate preview generation
    // ========================================================================

    /**
     * Creates a preview that accurately represents the actual shape footprint.
     * Unlike boundingBox(), this generates outline blocks from the real shape
     * (CIRCULAR, HEX, OCT, CUSTOM_NBT, etc.).
     *
     * @param center        The center position
     * @param shape         The area shape
     * @param dims          The area dimensions
     * @param customNbt     Custom NBT for CUSTOM_NBT shapes (nullable)
     * @return A preview payload with accurate shape outline
     */
    @Nonnull
    public static AreaPreviewPayload fromShape(
            @Nonnull BlockPos center,
            @Nonnull AreaShape shape,
            @Nonnull AreaDimensions dims,
            @Nullable CompoundTag customNbt) {
        return fromShape(center, shape, dims, customNbt, false, "", "NATURAL", 0L);
    }

    /**
     * Creates a preview with accurate shape footprint and optional biome configuration.
     *
     * @param center        The center position
     * @param shape         The area shape
     * @param dims          The area dimensions
     * @param customNbt     Custom NBT for CUSTOM_NBT shapes (nullable)
     * @param useBiome      Whether biome terrain is used
     * @param biomeId       The biome ID
     * @param terrainStyle  The terrain style name
     * @param biomeSeed     The biome seed
     * @return A preview payload with accurate shape outline
     */
    @Nonnull
    public static AreaPreviewPayload fromShape(
            @Nonnull BlockPos center,
            @Nonnull AreaShape shape,
            @Nonnull AreaDimensions dims,
            @Nullable CompoundTag customNbt,
            boolean useBiome,
            @Nonnull String biomeId,
            @Nonnull String terrainStyle,
            long biomeSeed) {

        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(dims, "dims");

        // Generate actual floor positions for this shape
        Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(shape, center, dims, customNbt);

        // Build outline by finding edge blocks (blocks with at least one missing neighbor)
        List<PreviewBlock> outline = new java.util.ArrayList<>();
        Set<BlockPos> floorXZSet = new HashSet<>();
        for (BlockPos pos : floorPositions) {
            floorXZSet.add(new BlockPos(pos.getX(), 0, pos.getZ())); // Normalize Y for neighbor check
        }

        int height = dims.height();

        // Find perimeter blocks (edge blocks)
        for (BlockPos floorPos : floorPositions) {
            BlockPos normalized = new BlockPos(floorPos.getX(), 0, floorPos.getZ());
            boolean isEdge = !floorXZSet.contains(normalized.north()) ||
                             !floorXZSet.contains(normalized.south()) ||
                             !floorXZSet.contains(normalized.east()) ||
                             !floorXZSet.contains(normalized.west());

            if (isEdge) {
                // Add floor edge
                addValidatedPreviewBlockDirect(outline, floorPos, PreviewBlockType.EDGE);
                // Add ceiling edge
                addValidatedPreviewBlockDirect(outline, floorPos.above(height), PreviewBlockType.EDGE);
            }
        }

        // Add center marker
        addValidatedPreviewBlockDirect(outline, center, PreviewBlockType.CENTER);

        // Limit to MAX_PREVIEW_BLOCKS to prevent payload explosion
        if (outline.size() > MAX_PREVIEW_BLOCKS) {
            // Sample evenly from the outline
            List<PreviewBlock> sampled = new java.util.ArrayList<>(MAX_PREVIEW_BLOCKS);
            double step = (double) outline.size() / MAX_PREVIEW_BLOCKS;
            for (int i = 0; i < MAX_PREVIEW_BLOCKS; i++) {
                sampled.add(outline.get((int) (i * step)));
            }
            outline = sampled;
        }

        return new AreaPreviewPayload(
            center,
            dims.width(),
            dims.length(),
            height,
            shape.name(),
            List.copyOf(outline),
            useBiome,
            Objects.requireNonNull(biomeId),
            Objects.requireNonNull(terrainStyle),
            biomeSeed
        );
    }

    /**
     * Adds a preview block directly (not offset-based) if within world bounds.
     */
    private static void addValidatedPreviewBlockDirect(List<PreviewBlock> outline, BlockPos pos, PreviewBlockType type) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        if (x >= MIN_WORLD_COORD && x < MAX_WORLD_COORD &&
            y >= MIN_Y && y <= MAX_Y &&
            z >= MIN_WORLD_COORD && z < MAX_WORLD_COORD) {
            outline.add(new PreviewBlock(pos, type));
        }
    }
}
