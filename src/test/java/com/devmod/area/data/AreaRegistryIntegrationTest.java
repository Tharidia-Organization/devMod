package com.devmod.area.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import com.devmod.TestBootstrap;
import com.devmod.area.data.AreaRegistry.EditorKey;

/**
 * Integration tests for AreaRegistry.
 * Tests CRUD operations, optimistic locking, spatial queries, and persistence.
 */
@DisplayName("AreaRegistry Integration")
class AreaRegistryIntegrationTest {

    private AreaRegistry registry;

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @BeforeEach
    void setUp() {
        registry = new AreaRegistry();
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private AreaDefinition createTestArea(String name) {
        return AreaDefinition.builder()
            .name(Objects.requireNonNull(name))
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(32, 32, 16, 64))
            .center(new BlockPos(0, 64, 0))
            .dimension(Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld")))
            .palette(Objects.requireNonNull(AreaPalette.empty("default")))
            .options(Objects.requireNonNull(AreaOptions.DEFAULT))
            .build();
    }

    private AreaDefinition createTestAreaAt(String name, BlockPos center) {
        return AreaDefinition.builder()
            .name(Objects.requireNonNull(name))
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(32, 32, 16, center.getY()))
            .center(center)
            .dimension(Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld")))
            .palette(Objects.requireNonNull(AreaPalette.empty("default")))
            .options(Objects.requireNonNull(AreaOptions.DEFAULT))
            .build();
    }

    private AreaDefinition createTestAreaWithDimension(String name, ResourceLocation dimension) {
        return AreaDefinition.builder()
            .name(Objects.requireNonNull(name))
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(32, 32, 16, 64))
            .center(new BlockPos(0, 64, 0))
            .dimension(Objects.requireNonNull(dimension))
            .palette(Objects.requireNonNull(AreaPalette.empty("default")))
            .options(Objects.requireNonNull(AreaOptions.DEFAULT))
            .build();
    }

    // ========================================================================
    // CRUD Operations
    // ========================================================================

    @Nested
    @DisplayName("CRUD Operations")
    class CRUDOperations {

        @Test
        @DisplayName("createArea returns UUID and stores area")
        void createArea_returnsUUIDAndStores() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));

            UUID id = registry.createArea(area);

            assertNotNull(id);
            assertEquals(area.id(), id);
            assertTrue(registry.getArea(id).isPresent());
            assertEquals("Test Area", registry.getArea(id).get().name());
        }

        @Test
        @DisplayName("createArea increments area count")
        void createArea_incrementsCount() {
            assertEquals(0, registry.getAreaCount());

            registry.createArea(Objects.requireNonNull(createTestArea("Area 1")));
            assertEquals(1, registry.getAreaCount());

            registry.createArea(Objects.requireNonNull(createTestArea("Area 2")));
            assertEquals(2, registry.getAreaCount());

            registry.createArea(Objects.requireNonNull(createTestArea("Area 3")));
            assertEquals(3, registry.getAreaCount());
        }

        @Test
        @DisplayName("getArea returns empty for non-existent ID")
        void getArea_returnsEmptyForNonExistent() {
            Optional<AreaDefinition> result = registry.getArea(Objects.requireNonNull(UUID.randomUUID()));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("getAllAreas returns all registered areas")
        void getAllAreas_returnsAll() {
            AreaDefinition area1 = Objects.requireNonNull(createTestArea("Area 1"));
            AreaDefinition area2 = Objects.requireNonNull(createTestArea("Area 2"));
            registry.createArea(area1);
            registry.createArea(area2);

            Collection<AreaDefinition> all = registry.getAllAreas();

            assertEquals(2, all.size());
            assertTrue(all.stream().anyMatch(a -> a.name().equals("Area 1")));
            assertTrue(all.stream().anyMatch(a -> a.name().equals("Area 2")));
        }

        @Test
        @DisplayName("getAllAreas returns unmodifiable collection")
        void getAllAreas_returnsUnmodifiable() {
            registry.createArea(Objects.requireNonNull(createTestArea("Test Area")));

            Collection<AreaDefinition> all = registry.getAllAreas();

            assertThrows(UnsupportedOperationException.class, all::clear);
        }

        @Test
        @DisplayName("deleteArea removes area from registry")
        void deleteArea_removesArea() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);
            assertTrue(registry.getArea(id).isPresent());

            registry.deleteArea(id);

            assertTrue(registry.getArea(id).isEmpty());
            assertEquals(0, registry.getAreaCount());
        }

        @Test
        @DisplayName("deleteArea handles non-existent ID gracefully")
        void deleteArea_handlesNonExistent() {
            assertDoesNotThrow(() -> registry.deleteArea(Objects.requireNonNull(UUID.randomUUID())));
        }

        @Test
        @DisplayName("deleteArea removes associated editor positions")
        void deleteArea_removesEditorPositions() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);
            ResourceLocation dim = Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld"));
            BlockPos pos = new BlockPos(100, 64, 100);
            registry.registerEditor(Objects.requireNonNull(dim), pos, id);
            assertTrue(registry.getAreaAtEditor(Objects.requireNonNull(dim), pos).isPresent());

            registry.deleteArea(id);

            assertTrue(registry.getAreaAtEditor(Objects.requireNonNull(dim), pos).isEmpty());
        }
    }

    // ========================================================================
    // Optimistic Locking
    // ========================================================================

    @Nested
    @DisplayName("Optimistic Locking")
    class OptimisticLocking {

        @Test
        @DisplayName("updateArea succeeds with correct revision")
        void updateArea_succeedsWithCorrectRevision() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Original Name"));
            UUID id = registry.createArea(area);
            AreaDefinition updated = area.withName("Updated Name");

            boolean success = registry.updateArea(id, updated, 0);

            assertTrue(success);
            assertEquals("Updated Name", registry.getArea(id).get().name());
        }

        @Test
        @DisplayName("updateArea fails with incorrect revision")
        void updateArea_failsWithIncorrectRevision() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Original Name"));
            UUID id = registry.createArea(area);
            AreaDefinition updated = area.withName("Updated Name");

            boolean success = registry.updateArea(id, updated, 999);

            assertFalse(success);
            assertEquals("Original Name", registry.getArea(id).get().name());
        }

        @Test
        @DisplayName("updateArea fails for non-existent area")
        void updateArea_failsForNonExistent() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));

            boolean success = registry.updateArea(Objects.requireNonNull(UUID.randomUUID()), Objects.requireNonNull(area), 0);

            assertFalse(success);
        }

        @Test
        @DisplayName("withUpdate increments revision")
        void withUpdate_incrementsRevision() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            registry.createArea(area);
            assertEquals(0, area.revision());

            AreaDefinition updated = area.withUpdate();

            assertEquals(1, updated.revision());
        }

        @Test
        @DisplayName("multiple updates require sequential revisions")
        void multipleUpdates_requireSequentialRevisions() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);

            // First update (rev 0 -> 1)
            AreaDefinition update1 = area.withName("Update 1");
            assertTrue(registry.updateArea(id, update1, 0));

            // Second update with wrong revision (still using 0) fails
            AreaDefinition update2 = area.withName("Update 2");
            assertFalse(registry.updateArea(id, update2, 0));

            // Second update with correct revision (1) succeeds
            AreaDefinition update2Fixed = update1.withName("Update 2");
            assertTrue(registry.updateArea(id, update2Fixed, 1));

            assertEquals("Update 2", registry.getArea(id).get().name());
        }
    }

    // ========================================================================
    // Main Hub
    // ========================================================================

    @Nested
    @DisplayName("Main Hub")
    class MainHubTests {

        @Test
        @DisplayName("hasMainHub returns false initially")
        void hasMainHub_returnsFalseInitially() {
            assertFalse(registry.hasMainHub());
        }

        @Test
        @DisplayName("getMainHub returns empty initially")
        void getMainHub_returnsEmptyInitially() {
            assertTrue(registry.getMainHub().isEmpty());
        }

        @Test
        @DisplayName("getMainHubId returns null initially")
        void getMainHubId_returnsNullInitially() {
            assertNull(registry.getMainHubId());
        }

        @Test
        @DisplayName("setMainHub sets hub when area exists")
        void setMainHub_setsWhenAreaExists() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Hub Area"));
            UUID id = registry.createArea(area);

            boolean success = registry.setMainHub(id);

            assertTrue(success);
            assertTrue(registry.hasMainHub());
            assertEquals(id, registry.getMainHubId());
            assertTrue(registry.getMainHub().isPresent());
        }

        @Test
        @DisplayName("setMainHub fails when area does not exist")
        void setMainHub_failsWhenAreaNotExists() {
            boolean success = registry.setMainHub(Objects.requireNonNull(UUID.randomUUID()));

            assertFalse(success);
            assertFalse(registry.hasMainHub());
        }

        @Test
        @DisplayName("createArea with isMainHub flag sets hub")
        void createArea_withMainHubFlag_setsHub() {
            AreaDefinition area = AreaDefinition.builder()
                .name("Auto Hub")
                .shape(AreaShape.RECTANGULAR)
                .dimensions(Objects.requireNonNull(AreaDimensions.DEFAULT))
                .center(Objects.requireNonNull(BlockPos.ZERO))
                .dimension(Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld")))
                .mainHub(true)
                .build();

            UUID id = registry.createArea(area);

            assertTrue(registry.hasMainHub());
            assertEquals(id, registry.getMainHubId());
        }

        @Test
        @DisplayName("deleteArea clears main hub if hub is deleted")
        void deleteArea_clearsMainHubIfDeleted() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Hub Area"));
            UUID id = registry.createArea(area);
            registry.setMainHub(id);
            assertTrue(registry.hasMainHub());

            registry.deleteArea(id);

            assertFalse(registry.hasMainHub());
            assertNull(registry.getMainHubId());
        }

        @Test
        @DisplayName("deleteArea does not affect hub if other area deleted")
        void deleteArea_doesNotAffectHubIfOtherDeleted() {
            AreaDefinition hub = Objects.requireNonNull(createTestArea("Hub Area"));
            AreaDefinition other = Objects.requireNonNull(createTestArea("Other Area"));
            UUID hubId = registry.createArea(hub);
            UUID otherId = registry.createArea(other);
            registry.setMainHub(hubId);

            registry.deleteArea(otherId);

            assertTrue(registry.hasMainHub());
            assertEquals(hubId, registry.getMainHubId());
        }

        @Test
        @DisplayName("updateArea can toggle main hub status")
        void updateArea_canToggleMainHubStatus() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);
            assertFalse(registry.hasMainHub());

            // Set as main hub via update
            AreaDefinition withHub = area.withMainHub(true);
            registry.updateArea(id, withHub, 0);

            assertTrue(registry.hasMainHub());
            assertEquals(id, registry.getMainHubId());

            // Remove main hub status via update
            AreaDefinition withoutHub = withHub.withMainHub(false);
            registry.updateArea(id, withoutHub, 1);

            assertFalse(registry.hasMainHub());
            assertNull(registry.getMainHubId());
        }
    }

    // ========================================================================
    // Editor Position Tracking
    // ========================================================================

    @Nested
    @DisplayName("Editor Position Tracking")
    class EditorPositionTracking {

        @Test
        @DisplayName("registerEditor stores position")
        void registerEditor_storesPosition() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);
            ResourceLocation dim = Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld"));
            BlockPos pos = new BlockPos(100, 64, 100);

            registry.registerEditor(Objects.requireNonNull(dim), pos, id);

            assertTrue(registry.getAreaAtEditor(Objects.requireNonNull(dim), pos).isPresent());
            assertEquals(id, registry.getAreaAtEditor(Objects.requireNonNull(dim), pos).get());
        }

        @Test
        @DisplayName("unregisterEditor removes position")
        void unregisterEditor_removesPosition() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);
            ResourceLocation dim = Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld"));
            BlockPos pos = new BlockPos(100, 64, 100);
            registry.registerEditor(Objects.requireNonNull(dim), pos, id);

            registry.unregisterEditor(Objects.requireNonNull(dim), pos);

            assertTrue(registry.getAreaAtEditor(Objects.requireNonNull(dim), pos).isEmpty());
        }

        @Test
        @DisplayName("getAreaDefinitionAtEditor returns area definition")
        void getAreaDefinitionAtEditor_returnsDefinition() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);
            ResourceLocation dim = Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld"));
            BlockPos pos = new BlockPos(100, 64, 100);
            registry.registerEditor(Objects.requireNonNull(dim), pos, id);

            Optional<AreaDefinition> result = registry.getAreaDefinitionAtEditor(Objects.requireNonNull(dim), pos);

            assertTrue(result.isPresent());
            assertEquals("Test Area", result.get().name());
        }

        @Test
        @DisplayName("getEditorPositions returns all positions")
        void getEditorPositions_returnsAll() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);
            ResourceLocation dim = Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld"));
            BlockPos pos1 = new BlockPos(100, 64, 100);
            BlockPos pos2 = new BlockPos(200, 64, 200);
            registry.registerEditor(dim, pos1, id);
            registry.registerEditor(dim, pos2, id);

            Set<EditorKey> positions = registry.getEditorPositions();

            assertEquals(2, positions.size());
            assertTrue(positions.contains(new EditorKey(Objects.requireNonNull(dim), pos1)));
            assertTrue(positions.contains(new EditorKey(Objects.requireNonNull(dim), pos2)));
        }

        @Test
        @DisplayName("editors in different dimensions are separate")
        void editorsInDifferentDimensions_areSeparate() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);
            ResourceLocation overworld = Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld"));
            ResourceLocation nether = Objects.requireNonNull(ResourceLocation.withDefaultNamespace("the_nether"));
            BlockPos pos = new BlockPos(100, 64, 100);

            registry.registerEditor(Objects.requireNonNull(overworld), pos, id);
            registry.registerEditor(Objects.requireNonNull(nether), pos, id);

            assertEquals(2, registry.getEditorPositions().size());
            assertTrue(registry.getAreaAtEditor(Objects.requireNonNull(overworld), pos).isPresent());
            assertTrue(registry.getAreaAtEditor(Objects.requireNonNull(nether), pos).isPresent());

            // Unregister only overworld
            registry.unregisterEditor(Objects.requireNonNull(overworld), pos);
            assertTrue(registry.getAreaAtEditor(Objects.requireNonNull(overworld), pos).isEmpty());
            assertTrue(registry.getAreaAtEditor(Objects.requireNonNull(nether), pos).isPresent());
        }
    }

    // ========================================================================
    // Spatial Query Methods
    // ========================================================================

    @Nested
    @DisplayName("Spatial Queries")
    class SpatialQueries {

        @Test
        @DisplayName("findAreasContaining returns matching areas")
        void findAreasContaining_returnsMatching() {
            // Area centered at (0, 64, 0) with 32x32x16 dimensions
            AreaDefinition area = Objects.requireNonNull(createTestAreaAt("Test Area", new BlockPos(0, 64, 0)));
            registry.createArea(area);

            // Position inside the area
            BlockPos inside = new BlockPos(5, 70, 5);
            List<AreaDefinition> result = registry.findAreasContaining(inside);

            assertEquals(1, result.size());
            assertEquals("Test Area", result.get(0).name());
        }

        @Test
        @DisplayName("findAreasContaining returns empty for positions outside")
        void findAreasContaining_returnsEmptyForOutside() {
            AreaDefinition area = Objects.requireNonNull(createTestAreaAt("Test Area", new BlockPos(0, 64, 0)));
            registry.createArea(area);

            // Position far outside the area
            BlockPos outside = new BlockPos(1000, 64, 1000);
            List<AreaDefinition> result = registry.findAreasContaining(outside);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("findAreasContaining can return multiple overlapping areas")
        void findAreasContaining_returnsMultipleOverlapping() {
            // Two overlapping areas
            AreaDefinition area1 = Objects.requireNonNull(createTestAreaAt("Area 1", new BlockPos(0, 64, 0)));
            AreaDefinition area2 = Objects.requireNonNull(createTestAreaAt("Area 2", new BlockPos(10, 64, 10)));
            registry.createArea(area1);
            registry.createArea(area2);

            // Position in overlap zone
            BlockPos overlap = new BlockPos(5, 70, 5);
            List<AreaDefinition> result = registry.findAreasContaining(overlap);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("isInsideArea correctly identifies inside positions")
        void isInsideArea_correctlyIdentifiesInside() {
            AreaDefinition area = Objects.requireNonNull(createTestAreaAt("Test Area", new BlockPos(0, 64, 0)));

            // Center should be inside
            assertTrue(registry.isInsideArea(new BlockPos(0, 70, 0), area));

            // Near edge should be inside
            assertTrue(registry.isInsideArea(new BlockPos(15, 70, 15), area));
        }

        @Test
        @DisplayName("isInsideArea correctly identifies outside positions")
        void isInsideArea_correctlyIdentifiesOutside() {
            AreaDefinition area = Objects.requireNonNull(createTestAreaAt("Test Area", new BlockPos(0, 64, 0)));

            // Far away should be outside
            assertFalse(registry.isInsideArea(new BlockPos(1000, 70, 1000), area));

            // Below floor should be outside
            assertFalse(registry.isInsideArea(new BlockPos(0, 50, 0), area));

            // Above ceiling should be outside
            assertFalse(registry.isInsideArea(new BlockPos(0, 100, 0), area));
        }

        @Test
        @DisplayName("wouldOverlap detects AABB collision")
        void wouldOverlap_detectsCollision() {
            AreaDefinition existing = Objects.requireNonNull(createTestAreaAt("Existing", new BlockPos(0, 64, 0)));
            registry.createArea(existing);

            // Overlapping area
            AreaDefinition overlapping = Objects.requireNonNull(createTestAreaAt("Overlapping", new BlockPos(10, 64, 10)));

            assertTrue(registry.wouldOverlap(overlapping));
        }

        @Test
        @DisplayName("wouldOverlap returns false for non-overlapping")
        void wouldOverlap_returnsFalseForNonOverlapping() {
            AreaDefinition existing = Objects.requireNonNull(createTestAreaAt("Existing", new BlockPos(0, 64, 0)));
            registry.createArea(existing);

            // Far away area
            AreaDefinition farAway = Objects.requireNonNull(createTestAreaAt("Far Away", new BlockPos(1000, 64, 1000)));

            assertFalse(registry.wouldOverlap(farAway));
        }

        @Test
        @DisplayName("wouldOverlap ignores self when updating")
        void wouldOverlap_ignoresSelf() {
            AreaDefinition area = Objects.requireNonNull(createTestAreaAt("Test Area", new BlockPos(0, 64, 0)));
            UUID id = registry.createArea(area);

            // Same area with slightly modified position should not overlap with itself
            AreaDefinition updated = AreaDefinition.builder()
                .id(id)  // Same ID
                .name("Updated")
                .shape(AreaShape.RECTANGULAR)
                .dimensions(new AreaDimensions(32, 32, 16, 64))
                .center(new BlockPos(5, 64, 5))
                .dimension(Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld")))
                .build();

            assertFalse(registry.wouldOverlap(updated));
        }

        @Test
        @DisplayName("wouldOverlap ignores areas in different dimensions")
        void wouldOverlap_ignoresDifferentDimensions() {
            AreaDefinition overworld = Objects.requireNonNull(createTestAreaWithDimension("Overworld Area",
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld"))));
            registry.createArea(overworld);

            // Same position but different dimension
            AreaDefinition nether = Objects.requireNonNull(createTestAreaWithDimension("Nether Area",
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("the_nether"))));

            assertFalse(registry.wouldOverlap(nether));
        }
    }

    // ========================================================================
    // Zone Linking
    // ========================================================================

    @Nested
    @DisplayName("Zone Linking")
    class ZoneLinking {

        @Test
        @DisplayName("getAreasByZone returns empty for non-existent zone")
        void getAreasByZone_returnsEmptyForNonExistent() {
            List<AreaDefinition> result = registry.getAreasByZone("non-existent-zone");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("getAreasByZone returns matching areas")
        void getAreasByZone_returnsMatching() {
            AreaDefinition area1 = AreaDefinition.builder()
                .name("Area 1")
                .linkedZone("zone-1")
                .shape(AreaShape.RECTANGULAR)
                .dimensions(Objects.requireNonNull(AreaDimensions.DEFAULT))
                .center(Objects.requireNonNull(BlockPos.ZERO))
                .dimension(Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld")))
                .build();
            AreaDefinition area2 = AreaDefinition.builder()
                .name("Area 2")
                .linkedZone("zone-1")
                .shape(AreaShape.RECTANGULAR)
                .dimensions(Objects.requireNonNull(AreaDimensions.DEFAULT))
                .center(new BlockPos(100, 64, 100))
                .dimension(Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld")))
                .build();
            AreaDefinition area3 = AreaDefinition.builder()
                .name("Area 3")
                .linkedZone("zone-2")
                .shape(AreaShape.RECTANGULAR)
                .dimensions(Objects.requireNonNull(AreaDimensions.DEFAULT))
                .center(new BlockPos(200, 64, 200))
                .dimension(Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld")))
                .build();

            registry.createArea(area1);
            registry.createArea(area2);
            registry.createArea(area3);

            List<AreaDefinition> zone1Areas = registry.getAreasByZone("zone-1");

            assertEquals(2, zone1Areas.size());
            assertTrue(zone1Areas.stream().allMatch(a -> "zone-1".equals(a.linkedZoneId())));
        }
    }

    // ========================================================================
    // Modification Version
    // ========================================================================

    @Nested
    @DisplayName("Modification Version")
    class ModificationVersion {

        @Test
        @DisplayName("initial version is 0")
        void initialVersion_isZero() {
            assertEquals(0, registry.getModificationVersion());
        }

        @Test
        @DisplayName("createArea increments version")
        void createArea_incrementsVersion() {
            long initial = registry.getModificationVersion();

            registry.createArea(Objects.requireNonNull(createTestArea("Test Area")));

            assertEquals(initial + 1, registry.getModificationVersion());
        }

        @Test
        @DisplayName("updateArea increments version on success")
        void updateArea_incrementsVersionOnSuccess() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);
            long afterCreate = registry.getModificationVersion();

            registry.updateArea(id, area.withName("Updated"), 0);

            assertEquals(afterCreate + 1, registry.getModificationVersion());
        }

        @Test
        @DisplayName("updateArea does not increment version on failure")
        void updateArea_doesNotIncrementOnFailure() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);
            long afterCreate = registry.getModificationVersion();

            registry.updateArea(id, area.withName("Updated"), 999);  // Wrong revision

            assertEquals(afterCreate, registry.getModificationVersion());
        }

        @Test
        @DisplayName("deleteArea increments version when area exists")
        void deleteArea_incrementsVersionWhenExists() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);
            long afterCreate = registry.getModificationVersion();

            registry.deleteArea(id);

            assertEquals(afterCreate + 1, registry.getModificationVersion());
        }

        @Test
        @DisplayName("deleteArea does not increment version when area not found")
        void deleteArea_doesNotIncrementWhenNotFound() {
            long initial = registry.getModificationVersion();

            registry.deleteArea(Objects.requireNonNull(UUID.randomUUID()));

            assertEquals(initial, registry.getModificationVersion());
        }
    }

    // ========================================================================
    // Persistence
    // ========================================================================

    @Nested
    @DisplayName("Persistence")
    class Persistence {

        @Test
        @DisplayName("save and load preserves areas")
        void saveLoad_preservesAreas() {
            AreaDefinition area1 = Objects.requireNonNull(createTestArea("Area 1"));
            AreaDefinition area2 = Objects.requireNonNull(createTestArea("Area 2"));
            registry.createArea(area1);
            registry.createArea(area2);

            // Save
            CompoundTag tag = new CompoundTag();
            HolderLookup.Provider provider = Objects.requireNonNull(mock(HolderLookup.Provider.class));
            registry.save(tag, provider);

            // Load into new registry
            AreaRegistry loaded = AreaRegistry.load(tag, provider);

            assertEquals(2, loaded.getAreaCount());
            assertTrue(loaded.getArea(Objects.requireNonNull(area1.id())).isPresent());
            assertTrue(loaded.getArea(Objects.requireNonNull(area2.id())).isPresent());
            assertEquals("Area 1", loaded.getArea(Objects.requireNonNull(area1.id())).get().name());
            assertEquals("Area 2", loaded.getArea(Objects.requireNonNull(area2.id())).get().name());
        }

        @Test
        @DisplayName("save and load preserves editor positions")
        void saveLoad_preservesEditorPositions() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);
            ResourceLocation dim = Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld"));
            BlockPos pos = new BlockPos(100, 64, 100);
            registry.registerEditor(Objects.requireNonNull(dim), pos, id);

            // Save
            CompoundTag tag = new CompoundTag();
            HolderLookup.Provider provider = Objects.requireNonNull(mock(HolderLookup.Provider.class));
            registry.save(tag, provider);

            // Load into new registry
            AreaRegistry loaded = AreaRegistry.load(tag, provider);

            assertTrue(loaded.getAreaAtEditor(Objects.requireNonNull(dim), pos).isPresent());
            assertEquals(id, loaded.getAreaAtEditor(Objects.requireNonNull(dim), pos).get());
        }

        @Test
        @DisplayName("save and load preserves main hub")
        void saveLoad_preservesMainHub() {
            AreaDefinition area = Objects.requireNonNull(createTestArea("Hub Area"));
            UUID id = registry.createArea(area);
            registry.setMainHub(id);

            // Save
            CompoundTag tag = new CompoundTag();
            HolderLookup.Provider provider = Objects.requireNonNull(mock(HolderLookup.Provider.class));
            registry.save(tag, provider);

            // Load into new registry
            AreaRegistry loaded = AreaRegistry.load(tag, provider);

            assertTrue(loaded.hasMainHub());
            assertEquals(id, loaded.getMainHubId());
        }

        @Test
        @DisplayName("load handles empty tag gracefully")
        void load_handlesEmptyTag() {
            CompoundTag emptyTag = new CompoundTag();
            HolderLookup.Provider provider = Objects.requireNonNull(mock(HolderLookup.Provider.class));

            AreaRegistry loaded = AreaRegistry.load(emptyTag, provider);

            assertNotNull(loaded);
            assertEquals(0, loaded.getAreaCount());
            assertFalse(loaded.hasMainHub());
        }

        @Test
        @DisplayName("load skips editor positions referencing non-existent areas")
        void load_skipsOrphanedEditorPositions() {
            // Create area and editor, save
            AreaDefinition area = Objects.requireNonNull(createTestArea("Test Area"));
            UUID id = registry.createArea(area);
            ResourceLocation dim = Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld"));
            BlockPos pos = new BlockPos(100, 64, 100);
            registry.registerEditor(Objects.requireNonNull(dim), pos, id);

            CompoundTag tag = new CompoundTag();
            HolderLookup.Provider provider = Objects.requireNonNull(mock(HolderLookup.Provider.class));
            registry.save(tag, provider);

            // Manually corrupt the tag by removing the area but keeping editor
            tag.getList("areas", 10).clear();

            // Load should skip the orphaned editor
            AreaRegistry loaded = AreaRegistry.load(tag, provider);

            assertEquals(0, loaded.getAreaCount());
            assertEquals(0, loaded.getEditorPositions().size());
        }

        @Test
        @DisplayName("load clears main hub if referenced area does not exist")
        void load_clearsOrphanedMainHub() {
            // Create area and set as hub, save
            AreaDefinition area = Objects.requireNonNull(createTestArea("Hub Area"));
            UUID id = registry.createArea(area);
            registry.setMainHub(id);

            CompoundTag tag = new CompoundTag();
            HolderLookup.Provider provider = Objects.requireNonNull(mock(HolderLookup.Provider.class));
            registry.save(tag, provider);

            // Manually corrupt the tag by removing the area
            tag.getList("areas", 10).clear();

            // Load should clear the orphaned main hub
            AreaRegistry loaded = AreaRegistry.load(tag, provider);

            assertFalse(loaded.hasMainHub());
            assertNull(loaded.getMainHubId());
        }
    }
}
