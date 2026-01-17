package com.devmod.area.snapshot;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Handles reading/writing snapshot block data to files.
 * Uses efficient palette-based storage similar to Minecraft chunks:
 * - Unique block states are stored once in a palette
 * - Each block references a palette index instead of full state
 * - File is GZIP compressed via NbtIo.writeCompressed
 */
public final class AreaSnapshotData {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaSnapshotData.class);

    private static final int FILE_VERSION = 1;
    private static final String TAG_VERSION = "version";
    private static final String TAG_AREA_ID = "areaId";
    private static final String TAG_PALETTE = "palette";
    private static final String TAG_BLOCKS = "blocks";
    private static final String TAG_BLOCK_ENTITIES = "blockEntities";

    private AreaSnapshotData() {} // Utility class

    /**
     * HIGH-06 fix: Validates that a snapshot file path doesn't contain path traversal sequences.
     * Ensures the path stays within expected directory structure.
     *
     * @param file The path to validate
     * @throws IOException if the path contains traversal sequences or is invalid
     */
    private static void validateSnapshotPath(@Nonnull Path file) throws IOException {
        Path normalized = file.normalize();
        String pathStr = normalized.toString();

        // Check for path traversal sequences
        if (pathStr.contains("..") || pathStr.contains("//")) {
            throw new IOException("Invalid snapshot path - contains traversal sequences: " + file);
        }

        // Ensure path contains expected structure (devmod/snapshots/)
        if (!pathStr.contains("devmod") || !pathStr.contains("snapshots")) {
            throw new IOException("Invalid snapshot path - not in expected directory: " + file);
        }

        // Ensure file has expected extension
        if (!pathStr.endsWith(".nbt")) {
            throw new IOException("Invalid snapshot path - unexpected extension: " + file);
        }
    }

    /**
     * Writes snapshot data to a file with compression.
     * Uses palette-based storage for efficiency.
     *
     * @param file          The file path to write to
     * @param areaId        The area UUID
     * @param blocks        Map of positions to block states
     * @param blockEntities Map of positions to block entity NBT (may be null)
     * @param provider      HolderLookup for registry access
     * @throws IOException if file writing fails
     */
    public static void write(
        @Nonnull Path file,
        @Nonnull UUID areaId,
        @Nonnull Map<BlockPos, BlockState> blocks,
        @Nullable Map<BlockPos, CompoundTag> blockEntities,
        @Nonnull HolderLookup.Provider provider
    ) throws IOException {
        Objects.requireNonNull(file);
        Objects.requireNonNull(areaId);
        Objects.requireNonNull(blocks);
        Objects.requireNonNull(provider);

        // HIGH-06 fix: Validate path to prevent directory traversal attacks
        validateSnapshotPath(file);

        CompoundTag root = new CompoundTag();
        root.putInt(TAG_VERSION, FILE_VERSION);
        root.putUUID(TAG_AREA_ID, areaId);

        // Build palette of unique block states
        Map<String, Integer> paletteMap = new HashMap<>();
        List<String> paletteList = new ArrayList<>();

        for (BlockState state : blocks.values()) {
            String stateStr = serializeBlockState(Objects.requireNonNull(state));
            if (!paletteMap.containsKey(stateStr)) {
                paletteMap.put(stateStr, paletteList.size());
                paletteList.add(stateStr);
            }
        }

        // Write palette
        ListTag paletteTag = new ListTag();
        for (String stateStr : paletteList) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("s", Objects.requireNonNull(stateStr));
            paletteTag.add(entryTag);
        }
        root.put(TAG_PALETTE, paletteTag);

        // Write blocks as position + palette index
        ListTag blocksTag = new ListTag();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            String stateStr = serializeBlockState(Objects.requireNonNull(entry.getValue()));
            Integer paletteIndex = paletteMap.get(stateStr);
            if (paletteIndex == null) {
                throw new IllegalStateException("Missing palette entry for block state: " + stateStr);
            }

            CompoundTag blockTag = new CompoundTag();
            blockTag.putInt("x", pos.getX());
            blockTag.putInt("y", pos.getY());
            blockTag.putInt("z", pos.getZ());
            blockTag.putInt("p", paletteIndex);
            blocksTag.add(blockTag);
        }
        root.put(TAG_BLOCKS, blocksTag);

        // Write block entities if present
        if (blockEntities != null && !blockEntities.isEmpty()) {
            ListTag beTag = new ListTag();
            for (Map.Entry<BlockPos, CompoundTag> entry : blockEntities.entrySet()) {
                CompoundTag beEntry = entry.getValue().copy();
                BlockPos pos = entry.getKey();
                beEntry.putInt("x", pos.getX());
                beEntry.putInt("y", pos.getY());
                beEntry.putInt("z", pos.getZ());
                beTag.add(beEntry);
            }
            root.put(TAG_BLOCK_ENTITIES, beTag);
        }

        // Create parent directories if needed
        Files.createDirectories(file.getParent());

        // Write compressed (atomic via temp file)
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            NbtIo.writeCompressed(root, Objects.requireNonNull(tempFile));
            Files.move(tempFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempFile);
        }

        LOGGER.debug("[Snapshot] Wrote {} blocks, {} palette entries, {} block entities to {}",
            blocks.size(), paletteList.size(),
            blockEntities != null ? blockEntities.size() : 0, file);
    }

    /**
     * Reads snapshot data from a file.
     *
     * @param file     The file path to read from
     * @param provider HolderLookup for registry access
     * @return The read result containing blocks and block entities
     * @throws IOException if file reading fails
     */
    @Nonnull
    public static SnapshotReadResult read(
        @Nonnull Path file,
        @Nonnull HolderLookup.Provider provider
    ) throws IOException {
        Objects.requireNonNull(file);
        Objects.requireNonNull(provider);

        // HIGH-06 fix: Validate path to prevent directory traversal attacks
        validateSnapshotPath(file);

        CompoundTag root = NbtIo.readCompressed(file, Objects.requireNonNull(net.minecraft.nbt.NbtAccounter.unlimitedHeap()));

        // C-05 fix: Null check after readCompressed() to prevent NPE on corrupted files
        if (root == null) {
            throw new IOException("Failed to read snapshot file: corrupted or empty NBT data at " + file);
        }

        int version = root.getInt(TAG_VERSION);
        if (version > FILE_VERSION) {
            LOGGER.warn("[Snapshot] Reading file from newer version {} (current: {})", version, FILE_VERSION);
        }

        UUID areaId = root.getUUID(TAG_AREA_ID);

        // Read palette
        ListTag paletteTag = root.getList(TAG_PALETTE, Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entryTag = paletteTag.getCompound(i);
            String stateStr = entryTag.getString("s");
            BlockState state = deserializeBlockState(Objects.requireNonNull(stateStr), provider);
            palette.add(state);
        }

        // Read blocks
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        ListTag blocksTag = root.getList(TAG_BLOCKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);
            int x = blockTag.getInt("x");
            int y = blockTag.getInt("y");
            int z = blockTag.getInt("z");
            int paletteIndex = blockTag.getInt("p");

            if (paletteIndex >= 0 && paletteIndex < palette.size()) {
                blocks.put(new BlockPos(x, y, z), palette.get(paletteIndex));
            } else {
                LOGGER.warn("[Snapshot] Invalid palette index {} at ({}, {}, {})", paletteIndex, x, y, z);
            }
        }

        // Read block entities
        Map<BlockPos, CompoundTag> blockEntities = null;
        if (root.contains(TAG_BLOCK_ENTITIES, Tag.TAG_LIST)) {
            ListTag beTag = root.getList(TAG_BLOCK_ENTITIES, Tag.TAG_COMPOUND);
            if (!beTag.isEmpty()) {
                blockEntities = new HashMap<>();
                for (int i = 0; i < beTag.size(); i++) {
                    CompoundTag beEntry = beTag.getCompound(i);
                    int x = beEntry.getInt("x");
                    int y = beEntry.getInt("y");
                    int z = beEntry.getInt("z");

                    // Remove position tags to get clean BE data
                    CompoundTag beData = beEntry.copy();
                    beData.remove("x");
                    beData.remove("y");
                    beData.remove("z");

                    blockEntities.put(new BlockPos(x, y, z), beData);
                }
            }
        }

        LOGGER.debug("[Snapshot] Read {} blocks, {} block entities from {}",
            blocks.size(), blockEntities != null ? blockEntities.size() : 0, file);

        return new SnapshotReadResult(areaId, blocks, blockEntities);
    }

    /**
     * Serializes a BlockState to a string representation.
     * Format: "minecraft:stone" or "minecraft:oak_stairs[facing=north,half=bottom]"
     */
    @Nonnull
    private static String serializeBlockState(@Nonnull BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(state.getBlock()));
        StringBuilder sb = new StringBuilder(blockId.toString());

        // Add properties if any
        if (!state.getValues().isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(entry.getKey().getName());
                sb.append('=');
                sb.append(getPropertyValueName(entry.getKey(), entry.getValue()));
            }
            sb.append(']');
        }

        return Objects.requireNonNull(sb.toString());
    }

    /**
     * Gets the string name for a property value.
     * Uses Property.getValueClass().cast() for type-safe conversion.
     */
    @Nonnull
    private static <T extends Comparable<T>> String getPropertyValueName(Property<T> property, Comparable<?> value) {
        T typedValue = Objects.requireNonNull(property.getValueClass().cast(value));
        return Objects.requireNonNull(property.getName(typedValue));
    }

    /**
     * Deserializes a BlockState from a string representation.
     */
    @Nonnull
    private static BlockState deserializeBlockState(@Nonnull String stateStr, @Nonnull HolderLookup.Provider provider) {
        try {
            // Use Minecraft's BlockStateParser for robust parsing
            HolderLookup.RegistryLookup<Block> blockLookup = provider.lookupOrThrow(Objects.requireNonNull(Registries.BLOCK));
            BlockStateParser.BlockResult result = BlockStateParser.parseForBlock(Objects.requireNonNull(blockLookup), new StringReader(stateStr), false);
            return Objects.requireNonNull(result.blockState());
        } catch (CommandSyntaxException e) {
            LOGGER.warn("[Snapshot] Failed to parse block state '{}': {}", stateStr, e.getMessage());

            // Fallback: try just the block name without properties
            int bracketIndex = stateStr.indexOf('[');
            String blockIdStr = bracketIndex > 0 ? stateStr.substring(0, bracketIndex) : stateStr;
            ResourceLocation blockId = ResourceLocation.tryParse(Objects.requireNonNull(blockIdStr));
            if (blockId != null) {
                Block block = BuiltInRegistries.BLOCK.get(blockId);
                if (block != Blocks.AIR || "minecraft:air".equals(blockIdStr)) {
                    return Objects.requireNonNull(block.defaultBlockState());
                }
            }

            return Objects.requireNonNull(Blocks.AIR.defaultBlockState());
        }
    }

    /**
     * Gets the file size in bytes.
     * LOW-08 fix: Syncs file metadata before reading size to ensure accuracy
     * on file systems where metadata might be cached (e.g., NFS).
     */
    public static long getFileSize(@Nonnull Path file) throws IOException {
        // Force metadata sync by opening channel briefly
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            channel.force(true); // Sync metadata
        }
        return Files.size(file);
    }

    /**
     * Checks if a snapshot file exists and is readable.
     */
    public static boolean exists(@Nonnull Path file) {
        return Files.exists(file) && Files.isReadable(file);
    }

    /**
     * Result of reading a snapshot file.
     *
     * @param areaId        The area UUID from the file
     * @param blocks        Map of positions to block states
     * @param blockEntities Map of positions to block entity NBT (null if none)
     */
    public record SnapshotReadResult(
        @Nonnull UUID areaId,
        @Nonnull Map<BlockPos, BlockState> blocks,
        @Nullable Map<BlockPos, CompoundTag> blockEntities
    ) {
        public SnapshotReadResult {
            Objects.requireNonNull(areaId);
            Objects.requireNonNull(blocks);
        }

        /**
         * Gets the total number of blocks.
         */
        public int getBlockCount() {
            return blocks.size();
        }

        /**
         * Gets the number of block entities.
         */
        public int getBlockEntityCount() {
            return blockEntities != null ? blockEntities.size() : 0;
        }
    }
}
