package com.devmod.arena.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.IntArrayTag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StructureNbtLoaderTest {

    private static final HexFormat HEX = HexFormat.of();

    @Test
    void validStructurePassesAllChecks() throws Exception {
        CompoundTag tag = sampleStructure(2, 1);
        byte[] data = toCompressedBytes(tag);

        StructureManifest manifest = manifestFor("devmod:structures/test", data, 2, 1,
            Set.of("devmod"), 512_000, 10, 10);

        StructureNbtLoader loader = new StructureNbtLoader();
        StructureNbtLoader.LoadResult result = loader.load("devmod:structures/test", () -> data, manifest);

        assertTrue(result.ok());
        assertEquals(2, result.blockCount());
        assertEquals(1, result.entityCount());
        assertNotNull(result.tag());
    }

    @Test
    void invalidPathIsRejected() throws Exception {
        CompoundTag tag = sampleStructure(1, 0);
        byte[] data = toCompressedBytes(tag);
        StructureManifest manifest = manifestFor("devmod:structures/test", data, 1, 0,
            Set.of("devmod"), 512_000, 10, 10);

        StructureNbtLoader loader = new StructureNbtLoader();
        StructureNbtLoader.LoadResult result = loader.load("devmod:../evil", () -> data, manifest);

        assertFalse(result.ok());
        assertEquals("INVALID_PATH", result.errorCode());
    }

    @Test
    void unauthorizedNamespaceRejected() throws Exception {
        CompoundTag tag = sampleStructure(1, 0);
        byte[] data = toCompressedBytes(tag);
        StructureManifest manifest = manifestFor("devmod:structures/test", data, 1, 0,
            Set.of("devmod"), 512_000, 10, 10);

        StructureNbtLoader loader = new StructureNbtLoader();
        StructureNbtLoader.LoadResult result = loader.load("other:structures/test", () -> data, manifest);

        assertFalse(result.ok());
        assertEquals("UNAUTHORIZED_NAMESPACE", result.errorCode());
    }

    @Test
    void checksumMismatchRejected() throws Exception {
        CompoundTag tag = sampleStructure(1, 0);
        byte[] data = toCompressedBytes(tag);
        // Tamper with hash
        StructureManifest manifest = new StructureManifest(
            Map.of("devmod:structures/test", new StructureManifest.Entry("deadbeef", data.length, 1, 0)),
            Set.of("devmod"),
            512_000,
            10,
            10
        );

        StructureNbtLoader loader = new StructureNbtLoader();
        StructureNbtLoader.LoadResult result = loader.load("devmod:structures/test", () -> data, manifest);

        assertFalse(result.ok());
        assertEquals("CHECKSUM_MISMATCH", result.errorCode());
    }

    @Test
    void blockLimitExceededRejected() throws Exception {
        CompoundTag tag = sampleStructure(5, 0);
        byte[] data = toCompressedBytes(tag);
        StructureManifest manifest = new StructureManifest(
            Map.of("devmod:structures/test", new StructureManifest.Entry(hash(data), data.length, 5, 0)),
            Set.of("devmod"),
            512_000,
            3, // limit lower than blocks
            10
        );

        StructureNbtLoader loader = new StructureNbtLoader();
        StructureNbtLoader.LoadResult result = loader.load("devmod:structures/test", () -> data, manifest);

        assertFalse(result.ok());
        assertEquals("TOO_MANY_BLOCKS", result.errorCode());
    }

    @Test
    void entityLimitExceededRejected() throws Exception {
        CompoundTag tag = sampleStructure(1, 4);
        byte[] data = toCompressedBytes(tag);
        StructureManifest manifest = new StructureManifest(
            Map.of("devmod:structures/test", new StructureManifest.Entry(hash(data), data.length, 1, 4)),
            Set.of("devmod"),
            512_000,
            10,
            2 // limit lower than entities
        );

        StructureNbtLoader loader = new StructureNbtLoader();
        StructureNbtLoader.LoadResult result = loader.load("devmod:structures/test", () -> data, manifest);

        assertFalse(result.ok());
        assertEquals("TOO_MANY_ENTITIES", result.errorCode());
    }

    private CompoundTag sampleStructure(int blocks, int entities) {
        CompoundTag tag = new CompoundTag();
        ListTag blockList = new ListTag();
        for (int i = 0; i < blocks; i++) {
            CompoundTag b = new CompoundTag();
            b.put("pos", new IntArrayTag(new int[]{i, 0, 0}));
            blockList.add(b);
        }
        tag.put("blocks", blockList);

        ListTag entityList = new ListTag();
        for (int i = 0; i < entities; i++) {
            CompoundTag e = new CompoundTag();
            e.put("pos", new ListTag());
            entityList.add(e);
        }
        tag.put("entities", entityList);
        return tag;
    }

    private byte[] toCompressedBytes(CompoundTag tag) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, out);
        return out.toByteArray();
    }

    private StructureManifest manifestFor(String path, byte[] data, int blocks, int entities,
                                          Set<String> namespaces, long maxSize, int maxBlocks, int maxEntities) throws Exception {
        return new StructureManifest(
            Map.of(path, new StructureManifest.Entry(hash(data), data.length, blocks, entities)),
            namespaces,
            maxSize,
            maxBlocks,
            maxEntities
        );
    }

    private String hash(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HEX.formatHex(digest.digest(data));
    }
}
