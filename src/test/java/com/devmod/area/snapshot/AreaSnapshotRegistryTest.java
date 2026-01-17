package com.devmod.area.snapshot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import com.devmod.TestBootstrap;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaShape;

/**
 * Unit tests for AreaSnapshotRegistry.
 * Tests registration, pruning, queries, deletion, and persistence.
 */
@DisplayName("AreaSnapshotRegistry")
class AreaSnapshotRegistryTest {

    @TempDir
    Path tempDir;

    private AreaSnapshotRegistry registry;

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @BeforeEach
    void setUp() {
        registry = new AreaSnapshotRegistry();
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private AreaSnapshot createSnapshot(UUID areaId, String description) {
        return createSnapshot(Objects.requireNonNull(UUID.randomUUID()), areaId, description, System.currentTimeMillis());
    }

    private AreaSnapshot createSnapshot(UUID areaId, String description, long createdAt) {
        return createSnapshot(Objects.requireNonNull(UUID.randomUUID()), areaId, description, createdAt);
    }

    private AreaSnapshot createSnapshot(UUID id, UUID areaId, String description, long createdAt) {
        return Objects.requireNonNull(new AreaSnapshot(
            id,
            areaId,
            "Test Area",
            description,
            null,  // creatorId
            null,  // creatorName
            createdAt,
            100,   // blockCount
            1024,  // fileSizeBytes
            AreaDimensions.DEFAULT,
            BlockPos.ZERO,
            AreaShape.RECTANGULAR,
            Level.OVERWORLD,
            null   // customShapeNbt
        ));
    }

    private void createSnapshotFile(AreaSnapshot snapshot) throws IOException {
        Path snapshotPath = tempDir.resolve(snapshot.getFilePath());
        Files.createDirectories(snapshotPath.getParent());
        Files.write(snapshotPath, new byte[]{1, 2, 3, 4});  // Dummy content
    }

    // ========================================================================
    // Registration Tests
    // ========================================================================

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("registerSnapshot stores snapshot")
        void registerSnapshot_storesSnapshot() {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            AreaSnapshot snapshot = createSnapshot(areaId, "Test snapshot");

            registry.registerSnapshot(Objects.requireNonNull(snapshot));

            assertTrue(registry.getSnapshot(Objects.requireNonNull(snapshot.id())).isPresent());
            assertEquals(1, registry.getSnapshotCount());
        }

        @Test
        @DisplayName("registerSnapshot increments count")
        void registerSnapshot_incrementsCount() {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            assertEquals(0, registry.getSnapshotCount());

            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(areaId, "Snapshot 1")));
            assertEquals(1, registry.getSnapshotCount());

            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(areaId, "Snapshot 2")));
            assertEquals(2, registry.getSnapshotCount());
        }

        @Test
        @DisplayName("registerSnapshot updates area index")
        void registerSnapshot_updatesAreaIndex() {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            AreaSnapshot snapshot = createSnapshot(areaId, "Test snapshot");

            registry.registerSnapshot(Objects.requireNonNull(snapshot));

            assertEquals(1, registry.getSnapshotCountForArea(Objects.requireNonNull(areaId)));
            List<AreaSnapshot> areaSnapshots = registry.getSnapshotsForArea(Objects.requireNonNull(areaId));
            assertEquals(1, areaSnapshots.size());
            assertEquals(snapshot.id(), areaSnapshots.get(0).id());
        }
    }

    // ========================================================================
    // Query Tests
    // ========================================================================

    @Nested
    @DisplayName("Query Methods")
    class QueryTests {

        @Test
        @DisplayName("getSnapshot returns empty for non-existent ID")
        void getSnapshot_returnsEmptyForNonExistent() {
            Optional<AreaSnapshot> result = registry.getSnapshot(Objects.requireNonNull(UUID.randomUUID()));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("getSnapshot returns snapshot when exists")
        void getSnapshot_returnsWhenExists() {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            AreaSnapshot snapshot = createSnapshot(areaId, "Test snapshot");
            registry.registerSnapshot(Objects.requireNonNull(snapshot));

            Optional<AreaSnapshot> result = registry.getSnapshot(Objects.requireNonNull(snapshot.id()));

            assertTrue(result.isPresent());
            assertEquals(snapshot.id(), result.get().id());
            assertEquals("Test snapshot", result.get().description());
        }

        @Test
        @DisplayName("getSnapshotsForArea returns empty for non-existent area")
        void getSnapshotsForArea_returnsEmptyForNonExistent() {
            List<AreaSnapshot> result = registry.getSnapshotsForArea(Objects.requireNonNull(UUID.randomUUID()));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("getSnapshotsForArea returns snapshots sorted newest first")
        void getSnapshotsForArea_sortedNewestFirst() {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            long now = System.currentTimeMillis();

            AreaSnapshot oldest = createSnapshot(areaId, "Oldest", now - 2000);
            AreaSnapshot middle = createSnapshot(areaId, "Middle", now - 1000);
            AreaSnapshot newest = createSnapshot(areaId, "Newest", now);

            // Register in random order
            registry.registerSnapshot(Objects.requireNonNull(middle));
            registry.registerSnapshot(Objects.requireNonNull(oldest));
            registry.registerSnapshot(Objects.requireNonNull(newest));

            List<AreaSnapshot> result = registry.getSnapshotsForArea(Objects.requireNonNull(areaId));

            assertEquals(3, result.size());
            assertEquals("Newest", result.get(0).description());
            assertEquals("Middle", result.get(1).description());
            assertEquals("Oldest", result.get(2).description());
        }

        @Test
        @DisplayName("getLatestSnapshot returns most recent")
        void getLatestSnapshot_returnsMostRecent() {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            long now = System.currentTimeMillis();

            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(areaId, "Oldest", now - 2000)));
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(areaId, "Newest", now)));
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(areaId, "Middle", now - 1000)));

            Optional<AreaSnapshot> result = registry.getLatestSnapshot(Objects.requireNonNull(areaId));

            assertTrue(result.isPresent());
            assertEquals("Newest", result.get().description());
        }

        @Test
        @DisplayName("getLatestSnapshot returns empty for non-existent area")
        void getLatestSnapshot_returnsEmptyForNonExistent() {
            Optional<AreaSnapshot> result = registry.getLatestSnapshot(Objects.requireNonNull(UUID.randomUUID()));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("getSnapshotCount returns correct count")
        void getSnapshotCount_returnsCorrectCount() {
            UUID area1 = Objects.requireNonNull(UUID.randomUUID());
            UUID area2 = Objects.requireNonNull(UUID.randomUUID());

            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(area1, "A1-S1")));
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(area1, "A1-S2")));
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(area2, "A2-S1")));

            assertEquals(3, registry.getSnapshotCount());
        }

        @Test
        @DisplayName("getSnapshotCountForArea returns correct count")
        void getSnapshotCountForArea_returnsCorrectCount() {
            UUID area1 = Objects.requireNonNull(UUID.randomUUID());
            UUID area2 = Objects.requireNonNull(UUID.randomUUID());

            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(area1, "A1-S1")));
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(area1, "A1-S2")));
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(area2, "A2-S1")));

            assertEquals(2, registry.getSnapshotCountForArea(Objects.requireNonNull(area1)));
            assertEquals(1, registry.getSnapshotCountForArea(Objects.requireNonNull(area2)));
        }

        @Test
        @DisplayName("isAtLimit returns false when under limit")
        void isAtLimit_returnsFalseUnderLimit() {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(areaId, "Snapshot 1")));

            assertFalse(registry.isAtLimit(Objects.requireNonNull(areaId)));
        }
    }

    // ========================================================================
    // Pruning Tests
    // ========================================================================

    @Nested
    @DisplayName("Pruning")
    class PruningTests {

        @Test
        @DisplayName("registerSnapshot prunes oldest when per-area limit exceeded")
        void registerSnapshot_prunesWhenPerAreaLimitExceeded() {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            long now = System.currentTimeMillis();

            // Register MAX_SNAPSHOTS_PER_AREA snapshots
            for (int i = 0; i < AreaSnapshotRegistry.MAX_SNAPSHOTS_PER_AREA; i++) {
                registry.registerSnapshot(Objects.requireNonNull(createSnapshot(areaId, "Snapshot " + i, now + i * 1000)));
            }

            assertEquals(AreaSnapshotRegistry.MAX_SNAPSHOTS_PER_AREA, registry.getSnapshotCountForArea(Objects.requireNonNull(areaId)));

            // Register one more - should prune oldest
            AreaSnapshot newest = createSnapshot(areaId, "Newest", now + 100000);
            registry.registerSnapshot(Objects.requireNonNull(newest));

            // Should still be at limit
            assertEquals(AreaSnapshotRegistry.MAX_SNAPSHOTS_PER_AREA, registry.getSnapshotCountForArea(Objects.requireNonNull(areaId)));

            // Newest should be present, oldest ("Snapshot 0") should be gone
            List<AreaSnapshot> snapshots = registry.getSnapshotsForArea(Objects.requireNonNull(areaId));
            assertEquals("Newest", snapshots.get(0).description());
            assertTrue(snapshots.stream().noneMatch(s -> "Snapshot 0".equals(s.description())));
        }

        @Test
        @DisplayName("per-area pruning does not affect other areas")
        void pruning_doesNotAffectOtherAreas() {
            UUID area1 = Objects.requireNonNull(UUID.randomUUID());
            UUID area2 = Objects.requireNonNull(UUID.randomUUID());
            long now = System.currentTimeMillis();

            // Fill area1 to limit
            for (int i = 0; i < AreaSnapshotRegistry.MAX_SNAPSHOTS_PER_AREA; i++) {
                registry.registerSnapshot(Objects.requireNonNull(createSnapshot(area1, "A1-" + i, now + i * 1000)));
            }

            // Add one snapshot to area2
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(area2, "A2-Only", now)));

            // Trigger pruning on area1
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(area1, "A1-New", now + 100000)));

            // Area2 should be unaffected
            assertEquals(1, registry.getSnapshotCountForArea(Objects.requireNonNull(area2)));
        }
    }

    // ========================================================================
    // Deletion Tests
    // ========================================================================

    @Nested
    @DisplayName("Deletion")
    class DeletionTests {

        @Test
        @DisplayName("deleteSnapshot returns false for non-existent")
        void deleteSnapshot_returnsFalseForNonExistent() {
            boolean result = registry.deleteSnapshot(Objects.requireNonNull(UUID.randomUUID()), tempDir);

            assertFalse(result);
        }

        @Test
        @DisplayName("deleteSnapshot removes from registry")
        void deleteSnapshot_removesFromRegistry() {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            AreaSnapshot snapshot = createSnapshot(areaId, "Test snapshot");
            registry.registerSnapshot(Objects.requireNonNull(snapshot));
            assertTrue(registry.getSnapshot(Objects.requireNonNull(snapshot.id())).isPresent());

            boolean result = registry.deleteSnapshot(Objects.requireNonNull(snapshot.id()), null);

            assertTrue(result);
            assertTrue(registry.getSnapshot(Objects.requireNonNull(snapshot.id())).isEmpty());
            assertEquals(0, registry.getSnapshotCount());
        }

        @Test
        @DisplayName("deleteSnapshot updates area index")
        void deleteSnapshot_updatesAreaIndex() {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            AreaSnapshot snapshot = createSnapshot(areaId, "Test snapshot");
            registry.registerSnapshot(Objects.requireNonNull(snapshot));

            registry.deleteSnapshot(Objects.requireNonNull(snapshot.id()), null);

            assertEquals(0, registry.getSnapshotCountForArea(Objects.requireNonNull(areaId)));
            assertTrue(registry.getSnapshotsForArea(Objects.requireNonNull(areaId)).isEmpty());
        }

        @Test
        @DisplayName("deleteSnapshot deletes file when exists")
        void deleteSnapshot_deletesFile() throws IOException {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            AreaSnapshot snapshot = createSnapshot(areaId, "Test snapshot");
            createSnapshotFile(Objects.requireNonNull(snapshot));
            registry.registerSnapshot(Objects.requireNonNull(snapshot));

            Path filePath = tempDir.resolve(snapshot.getFilePath());
            assertTrue(Files.exists(filePath));

            registry.deleteSnapshot(Objects.requireNonNull(snapshot.id()), tempDir);

            assertFalse(Files.exists(filePath));
        }

        @Test
        @DisplayName("deleteSnapshotsForArea deletes all for area")
        void deleteSnapshotsForArea_deletesAll() {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(areaId, "Snapshot 1")));
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(areaId, "Snapshot 2")));
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(areaId, "Snapshot 3")));
            assertEquals(3, registry.getSnapshotCountForArea(Objects.requireNonNull(areaId)));

            int deleted = registry.deleteSnapshotsForArea(Objects.requireNonNull(areaId), null);

            assertEquals(3, deleted);
            assertEquals(0, registry.getSnapshotCountForArea(Objects.requireNonNull(areaId)));
        }

        @Test
        @DisplayName("deleteSnapshotsForArea does not affect other areas")
        void deleteSnapshotsForArea_doesNotAffectOther() {
            UUID area1 = Objects.requireNonNull(UUID.randomUUID());
            UUID area2 = Objects.requireNonNull(UUID.randomUUID());
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(area1, "A1-Snapshot")));
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(area2, "A2-Snapshot")));

            registry.deleteSnapshotsForArea(Objects.requireNonNull(area1), null);

            assertEquals(0, registry.getSnapshotCountForArea(Objects.requireNonNull(area1)));
            assertEquals(1, registry.getSnapshotCountForArea(Objects.requireNonNull(area2)));
        }

        @Test
        @DisplayName("deleteSnapshotsForArea returns 0 for non-existent area")
        void deleteSnapshotsForArea_returnsZeroForNonExistent() {
            int deleted = registry.deleteSnapshotsForArea(Objects.requireNonNull(UUID.randomUUID()), null);

            assertEquals(0, deleted);
        }
    }

    // ========================================================================
    // Persistence Tests
    // ========================================================================

    @Nested
    @DisplayName("Persistence")
    class PersistenceTests {

        @Test
        @DisplayName("save and load preserves snapshots")
        void saveLoad_preservesSnapshots() {
            UUID area1 = Objects.requireNonNull(UUID.randomUUID());
            UUID area2 = Objects.requireNonNull(UUID.randomUUID());
            AreaSnapshot snapshot1 = createSnapshot(area1, "Snapshot 1");
            AreaSnapshot snapshot2 = createSnapshot(area2, "Snapshot 2");
            registry.registerSnapshot(Objects.requireNonNull(snapshot1));
            registry.registerSnapshot(Objects.requireNonNull(snapshot2));

            // Save
            CompoundTag tag = new CompoundTag();
            HolderLookup.Provider provider = Objects.requireNonNull(mock(HolderLookup.Provider.class));
            registry.save(tag, provider);

            // Load into new registry
            AreaSnapshotRegistry loaded = AreaSnapshotRegistry.load(tag, provider);

            assertEquals(2, loaded.getSnapshotCount());
            assertTrue(loaded.getSnapshot(Objects.requireNonNull(snapshot1.id())).isPresent());
            assertTrue(loaded.getSnapshot(Objects.requireNonNull(snapshot2.id())).isPresent());
            assertEquals("Snapshot 1", loaded.getSnapshot(Objects.requireNonNull(snapshot1.id())).get().description());
            assertEquals("Snapshot 2", loaded.getSnapshot(Objects.requireNonNull(snapshot2.id())).get().description());
        }

        @Test
        @DisplayName("save and load preserves area index")
        void saveLoad_preservesAreaIndex() {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(areaId, "Snapshot 1")));
            registry.registerSnapshot(Objects.requireNonNull(createSnapshot(areaId, "Snapshot 2")));

            // Save
            CompoundTag tag = new CompoundTag();
            HolderLookup.Provider provider = Objects.requireNonNull(mock(HolderLookup.Provider.class));
            registry.save(tag, provider);

            // Load into new registry
            AreaSnapshotRegistry loaded = AreaSnapshotRegistry.load(tag, provider);

            assertEquals(2, loaded.getSnapshotCountForArea(Objects.requireNonNull(areaId)));
            List<AreaSnapshot> snapshots = loaded.getSnapshotsForArea(Objects.requireNonNull(areaId));
            assertEquals(2, snapshots.size());
        }

        @Test
        @DisplayName("load handles empty tag gracefully")
        void load_handlesEmptyTag() {
            CompoundTag emptyTag = new CompoundTag();
            HolderLookup.Provider provider = Objects.requireNonNull(mock(HolderLookup.Provider.class));

            AreaSnapshotRegistry loaded = AreaSnapshotRegistry.load(emptyTag, provider);

            assertNotNull(loaded);
            assertEquals(0, loaded.getSnapshotCount());
        }

        @Test
        @DisplayName("save preserves all snapshot fields")
        void save_preservesAllFields() {
            UUID snapshotId = Objects.requireNonNull(UUID.randomUUID());
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            UUID creatorId = Objects.requireNonNull(UUID.randomUUID());
            long createdAt = System.currentTimeMillis();

            AreaSnapshot original = Objects.requireNonNull(new AreaSnapshot(
                snapshotId,
                areaId,
                "My Area",
                "Test Description",
                creatorId,
                "PlayerName",
                createdAt,
                500,
                2048,
                new AreaDimensions(64, 64, 32, 100),
                new BlockPos(100, 100, 100),
                AreaShape.CIRCULAR,
                Level.OVERWORLD,
                null
            ));
            registry.registerSnapshot(Objects.requireNonNull(original));

            // Save
            CompoundTag tag = new CompoundTag();
            HolderLookup.Provider provider = Objects.requireNonNull(mock(HolderLookup.Provider.class));
            registry.save(tag, provider);

            // Load
            AreaSnapshotRegistry loaded = AreaSnapshotRegistry.load(tag, provider);
            AreaSnapshot restored = loaded.getSnapshot(Objects.requireNonNull(snapshotId)).orElseThrow();

            assertEquals(snapshotId, restored.id());
            assertEquals(areaId, restored.areaId());
            assertEquals("My Area", restored.areaName());
            assertEquals("Test Description", restored.description());
            assertEquals(creatorId, restored.creatorId());
            assertEquals("PlayerName", restored.creatorName());
            assertEquals(createdAt, restored.createdAt());
            assertEquals(500, restored.blockCount());
            assertEquals(2048, restored.fileSizeBytes());
            assertEquals(64, restored.dimensions().width());
            assertEquals(100, restored.center().getX());
            assertEquals(AreaShape.CIRCULAR, restored.shape());
            assertEquals(Level.OVERWORLD, restored.dimensionKey());
        }
    }

    // ========================================================================
    // AreaSnapshot Record Tests
    // ========================================================================

    @Nested
    @DisplayName("AreaSnapshot Record")
    class AreaSnapshotRecordTests {

        @Test
        @DisplayName("getFilePath returns correct format")
        void getFilePath_returnsCorrectFormat() {
            UUID id = Objects.requireNonNull(UUID.randomUUID());
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            AreaSnapshot snapshot = createSnapshot(id, areaId, "Test", System.currentTimeMillis());

            String expected = "devmod/snapshots/" + areaId + "/" + id + ".nbt";
            assertEquals(expected, snapshot.getFilePath());
        }

        @Test
        @DisplayName("getDirectoryPath returns correct format")
        void getDirectoryPath_returnsCorrectFormat() {
            UUID areaId = Objects.requireNonNull(UUID.randomUUID());
            AreaSnapshot snapshot = createSnapshot(areaId, "Test");

            String expected = "devmod/snapshots/" + areaId;
            assertEquals(expected, snapshot.getDirectoryPath());
        }

        @Test
        @DisplayName("truncates long description")
        void constructor_truncatesLongDescription() {
            String longDesc = "A".repeat(200);  // Longer than MAX_DESCRIPTION_LENGTH
            AreaSnapshot snapshot = Objects.requireNonNull(new AreaSnapshot(
                Objects.requireNonNull(UUID.randomUUID()),
                Objects.requireNonNull(UUID.randomUUID()),
                "Area",
                longDesc,
                null, null,
                System.currentTimeMillis(),
                100, 1024,
                AreaDimensions.DEFAULT,
                BlockPos.ZERO,
                AreaShape.RECTANGULAR,
                Level.OVERWORLD,
                null
            ));

            assertEquals(AreaSnapshot.MAX_DESCRIPTION_LENGTH, snapshot.description().length());
        }

        @Test
        @DisplayName("truncates long creator name")
        void constructor_truncatesLongCreatorName() {
            String longName = "A".repeat(100);  // Longer than MAX_CREATOR_NAME_LENGTH
            AreaSnapshot snapshot = Objects.requireNonNull(new AreaSnapshot(
                Objects.requireNonNull(UUID.randomUUID()),
                Objects.requireNonNull(UUID.randomUUID()),
                "Area",
                "Desc",
                null, longName,
                System.currentTimeMillis(),
                100, 1024,
                AreaDimensions.DEFAULT,
                BlockPos.ZERO,
                AreaShape.RECTANGULAR,
                Level.OVERWORLD,
                null
            ));

            assertEquals(AreaSnapshot.MAX_CREATOR_NAME_LENGTH, Objects.requireNonNull(snapshot.creatorName()).length());
        }

        @Test
        @DisplayName("normalizes negative blockCount to zero")
        void constructor_normalizesNegativeBlockCount() {
            AreaSnapshot snapshot = Objects.requireNonNull(new AreaSnapshot(
                Objects.requireNonNull(UUID.randomUUID()),
                Objects.requireNonNull(UUID.randomUUID()),
                "Area",
                "Desc",
                null, null,
                System.currentTimeMillis(),
                -100,  // Negative
                1024,
                AreaDimensions.DEFAULT,
                BlockPos.ZERO,
                AreaShape.RECTANGULAR,
                Level.OVERWORLD,
                null
            ));

            assertEquals(0, snapshot.blockCount());
        }

        @Test
        @DisplayName("normalizes negative fileSizeBytes to zero")
        void constructor_normalizesNegativeFileSize() {
            AreaSnapshot snapshot = Objects.requireNonNull(new AreaSnapshot(
                Objects.requireNonNull(UUID.randomUUID()),
                Objects.requireNonNull(UUID.randomUUID()),
                "Area",
                "Desc",
                null, null,
                System.currentTimeMillis(),
                100,
                -5000,  // Negative
                AreaDimensions.DEFAULT,
                BlockPos.ZERO,
                AreaShape.RECTANGULAR,
                Level.OVERWORLD,
                null
            ));

            assertEquals(0, snapshot.fileSizeBytes());
        }

        @Test
        @DisplayName("withFileSize creates copy with new size")
        void withFileSize_createsCopy() {
            AreaSnapshot original = createSnapshot(Objects.requireNonNull(UUID.randomUUID()), "Test");

            AreaSnapshot updated = Objects.requireNonNull(original.withFileSize(9999));

            assertEquals(original.id(), updated.id());
            assertEquals(original.description(), updated.description());
            assertEquals(9999, updated.fileSizeBytes());
            assertEquals(1024, original.fileSizeBytes());  // Original unchanged
        }

        @Test
        @DisplayName("withBlockCount creates copy with new count")
        void withBlockCount_createsCopy() {
            AreaSnapshot original = createSnapshot(Objects.requireNonNull(UUID.randomUUID()), "Test");

            AreaSnapshot updated = Objects.requireNonNull(original.withBlockCount(250));

            assertEquals(original.id(), updated.id());
            assertEquals(250, updated.blockCount());
            assertEquals(100, original.blockCount());  // Original unchanged
        }

        @Test
        @DisplayName("getFormattedFileSize formats bytes correctly")
        void getFormattedFileSize_formatsBytes() {
            AreaSnapshot small = createSnapshot(Objects.requireNonNull(UUID.randomUUID()), "Test");
            AreaSnapshot withSize = Objects.requireNonNull(small.withFileSize(512));
            assertEquals("512 B", withSize.getFormattedFileSize());
        }

        @Test
        @DisplayName("getFormattedFileSize formats KB correctly")
        void getFormattedFileSize_formatsKB() {
            AreaSnapshot snapshot = Objects.requireNonNull(createSnapshot(Objects.requireNonNull(UUID.randomUUID()), "Test").withFileSize(1536));
            String result = snapshot.getFormattedFileSize();
            // Handle locale differences (1.5 vs 1,5)
            assertTrue(result.equals("1.5 KB") || result.equals("1,5 KB"),
                "Expected '1.5 KB' or '1,5 KB' but got: " + result);
        }

        @Test
        @DisplayName("getFormattedFileSize formats MB correctly")
        void getFormattedFileSize_formatsMB() {
            AreaSnapshot snapshot = Objects.requireNonNull(createSnapshot(Objects.requireNonNull(UUID.randomUUID()), "Test")
                .withFileSize((long)(1.5 * 1024 * 1024)));
            String result = snapshot.getFormattedFileSize();
            // Handle locale differences (1.5 vs 1,5)
            assertTrue(result.equals("1.5 MB") || result.equals("1,5 MB"),
                "Expected '1.5 MB' or '1,5 MB' but got: " + result);
        }

        @Test
        @DisplayName("null description becomes empty string")
        void constructor_nullDescriptionBecomesEmpty() {
            AreaSnapshot snapshot = Objects.requireNonNull(new AreaSnapshot(
                Objects.requireNonNull(UUID.randomUUID()),
                Objects.requireNonNull(UUID.randomUUID()),
                "Area",
                null,
                null, null,
                System.currentTimeMillis(),
                100, 1024,
                AreaDimensions.DEFAULT,
                BlockPos.ZERO,
                AreaShape.RECTANGULAR,
                Level.OVERWORLD,
                null
            ));

            assertEquals("", snapshot.description());
        }
    }
}
