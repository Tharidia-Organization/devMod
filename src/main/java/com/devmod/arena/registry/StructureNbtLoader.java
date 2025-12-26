package com.devmod.arena.registry;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;

/**
 * Secure structure loader/validator for structure NBT (spec v2.23).
 *
 * Path and manifest validation follow TODO_ARENA_TEMPLATE. Actual world
 * placement is out of scope; this class only validates and parses NBT data.
 */
public class StructureNbtLoader {

    private static final HexFormat HEX = HexFormat.of();

    /**
     * Validate and parse structure NBT loaded from the provided data supplier.
     * Performs path validation, manifest checks, checksum verification, and
     * content limits (block/entity counts) based on the NBT payload.
     */
    public LoadResult load(String path, Supplier<byte[]> dataSupplier, StructureManifest manifest) {
        byte[] data = dataSupplier.get();

        // First pass: manifest + checksum checks
        LoadResult pre = validate(path, data, manifest);
        if (!pre.ok()) {
            return pre;
        }

        // Second pass: parse NBT and enforce block/entity limits against actual data
        CompoundTag tag;
        try {
            tag = readTag(data);
        } catch (IOException e) {
            return LoadResult.error("NBT_PARSE_ERROR", "Failed to parse NBT: " + e.getMessage());
        }

        int blockCount = countBlocks(tag);
        int entityCount = countEntities(tag);

        if (blockCount > manifest.maxBlockCount()) {
            return LoadResult.error("TOO_MANY_BLOCKS", "Block limit exceeded: " + blockCount);
        }
        if (entityCount > manifest.maxEntityCount()) {
            return LoadResult.error("TOO_MANY_ENTITIES", "Entity limit exceeded: " + entityCount);
        }

        return LoadResult.success(tag, blockCount, entityCount);
    }

    public LoadResult validate(String path, byte[] data, StructureManifest manifest) {
        // 1. Path validation (ResourceLocation-style)
        if (!isValidPath(path)) {
            return LoadResult.error("INVALID_PATH", "Invalid path: " + path);
        }

        // 2. Namespace whitelist
        String namespace = path.split(":", 2)[0];
        if (!manifest.allowedNamespaces().contains(namespace)) {
            return LoadResult.error("UNAUTHORIZED_NAMESPACE", "Namespace not allowed: " + namespace);
        }

        // 3. Manifest entry exists
        StructureManifest.Entry entry = manifest.structures().get(path);
        if (entry == null) {
            return LoadResult.error("NOT_IN_MANIFEST", "Not in manifest: " + path);
        }

        // 4. Size pre-check
        if (entry.sizeBytes() > manifest.maxFileSizeBytes()) {
            return LoadResult.error("FILE_TOO_LARGE", "File too large: " + entry.sizeBytes());
        }
        if (data.length != entry.sizeBytes()) {
            return LoadResult.error("SIZE_MISMATCH", "Declared size does not match data length");
        }

        // 5. Checksum verification
        String computedHash = sha256(data);
        if (!computedHash.equalsIgnoreCase(entry.sha256())) {
            return LoadResult.error("CHECKSUM_MISMATCH", "Hash mismatch");
        }

        // 6. Content limits (block/entity counts from manifest only here)
        if (entry.blockCount() > manifest.maxBlockCount()) {
            return LoadResult.error("TOO_MANY_BLOCKS", "Block limit exceeded");
        }
        if (entry.entityCount() > manifest.maxEntityCount()) {
            return LoadResult.error("TOO_MANY_ENTITIES", "Entity limit exceeded");
        }

        // Pre-validation complete - NBT parsing happens in load() method
        return LoadResult.success(null, entry.blockCount(), entry.entityCount());
    }

    /**
     * Fallback validation when no manifest is available.
     * Performs path/namespace checks and enforces size/block/entity limits.
     */
    public LoadResult loadWithLimits(String path, Supplier<byte[]> dataSupplier, FallbackLimits limits) {
        byte[] data = dataSupplier.get();
        if (!isValidPath(path)) {
            return LoadResult.error("INVALID_PATH", "Invalid path: " + path);
        }
        if (limits != null && limits.allowedNamespaces() != null) {
            String namespace = path.split(":", 2)[0];
            if (!limits.allowedNamespaces().contains(namespace)) {
                return LoadResult.error("UNAUTHORIZED_NAMESPACE", "Namespace not allowed: " + namespace);
            }
        }
        if (limits != null && limits.maxFileSizeBytes() > 0 && data != null && data.length > limits.maxFileSizeBytes()) {
            return LoadResult.error("FILE_TOO_LARGE", "File too large: " + data.length);
        }

        CompoundTag tag;
        try {
            tag = readTag(data);
        } catch (IOException e) {
            return LoadResult.error("NBT_PARSE_ERROR", "Failed to parse NBT: " + e.getMessage());
        }

        int blockCount = countBlocks(tag);
        int entityCount = countEntities(tag);

        if (limits != null && limits.maxBlockCount() > 0 && blockCount > limits.maxBlockCount()) {
            return LoadResult.error("TOO_MANY_BLOCKS", "Block limit exceeded: " + blockCount);
        }
        if (limits != null && limits.maxEntityCount() > 0 && entityCount > limits.maxEntityCount()) {
            return LoadResult.error("TOO_MANY_ENTITIES", "Entity limit exceeded: " + entityCount);
        }

        return LoadResult.success(tag, blockCount, entityCount);
    }

    public boolean isValidPath(String path) {
        // Prefer ResourceLocation parsing for correctness
        ResourceLocation rl = ResourceLocation.tryParse(Objects.requireNonNull(path));
        if (rl == null) return false;

        String pathStr = rl.getPath();
        return !pathStr.contains("..") && !pathStr.contains("./") && !pathStr.contains("\\")
            && !pathStr.startsWith("/") && !pathStr.contains("://");
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HEX.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private CompoundTag readTag(byte[] data) throws IOException {
        // Structure NBT files are typically compressed; fallback to raw if needed.
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            return NbtIo.readCompressed(in, Objects.requireNonNull(net.minecraft.nbt.NbtAccounter.unlimitedHeap()));
        } catch (IOException compressedErr) {
            try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
                DataInputStream dataIn = new DataInputStream(in);
                return NbtIo.read(dataIn, Objects.requireNonNull(net.minecraft.nbt.NbtAccounter.unlimitedHeap()));
            } catch (IOException rawErr) {
                compressedErr.addSuppressed(rawErr);
                throw compressedErr;
            }
        }
    }

    private int countBlocks(CompoundTag tag) {
        if (tag == null || !tag.contains("blocks")) return 0;
        ListTag blocks = tag.getList("blocks", 10); // 10 = CompoundTag id
        return blocks.size();
    }

    private int countEntities(CompoundTag tag) {
        if (tag == null || !tag.contains("entities")) return 0;
        ListTag entities = tag.getList("entities", 10);
        return entities.size();
    }

    public record LoadResult(boolean ok, String errorCode, String message, CompoundTag tag, int blockCount, int entityCount) {
        public static LoadResult success(CompoundTag tag, int blockCount, int entityCount) {
            return new LoadResult(true, null, null, tag, blockCount, entityCount);
        }

        public static LoadResult error(String code, String message) {
            return new LoadResult(false, code, message, null, 0, 0);
        }
    }

    public record FallbackLimits(
        Set<String> allowedNamespaces,
        long maxFileSizeBytes,
        int maxBlockCount,
        int maxEntityCount
    ) {}
}
