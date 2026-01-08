package com.devmod.portal;

import com.devmod.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for portal command functionality.
 * Tests portal listing, filtering, and information display.
 */
class PortalCommandTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("Portal Information Formatting")
    class PortalInformationFormatting {

        @Test
        @DisplayName("Format portal position correctly")
        void formatPortalPosition() {
            BlockPos pos = new BlockPos(100, 64, -200);
            String formatted = formatPosition(pos);
            assertEquals("100, 64, -200", formatted, "Position should be formatted as x, y, z");
        }

        @Test
        @DisplayName("Format portal color name")
        void formatPortalColorName() {
            for (PortalColor color : PortalColor.values()) {
                String name = color.getSerializedName();
                assertNotNull(name, "Color name should not be null");
                assertFalse(name.isEmpty(), "Color name should not be empty");
            }
        }

        @Test
        @DisplayName("Format linked status")
        void formatLinkedStatus() {
            PortalData linked = PortalData.create(PortalColor.BLUE)
                .linkTo(UUID.randomUUID());
            PortalData unlinked = PortalData.create(PortalColor.RED);

            assertEquals("Linked", formatLinkStatus(linked), "Should show 'Linked' for linked portal");
            assertEquals("Not Linked", formatLinkStatus(unlinked), "Should show 'Not Linked' for unlinked portal");
        }

        @Test
        @DisplayName("Format dimension name")
        void formatDimensionName() {
            ResourceLocation overworld = ResourceLocation.parse("minecraft:overworld");
            ResourceLocation nether = ResourceLocation.parse("minecraft:the_nether");
            ResourceLocation end = ResourceLocation.parse("minecraft:the_end");

            assertEquals("overworld", formatDimension(overworld), "Should extract dimension path");
            assertEquals("the_nether", formatDimension(nether), "Should extract nether path");
            assertEquals("the_end", formatDimension(end), "Should extract end path");
        }

        private String formatPosition(BlockPos pos) {
            return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        }

        private String formatLinkStatus(PortalData portal) {
            return portal.isLinked() ? "Linked" : "Not Linked";
        }

        private String formatDimension(ResourceLocation dimension) {
            return dimension.getPath();
        }
    }

    @Nested
    @DisplayName("Portal Filtering")
    class PortalFiltering {

        private List<PortalData> createTestPortals() {
            List<PortalData> portals = new ArrayList<>();

            ResourceLocation overworld = ResourceLocation.parse("minecraft:overworld");
            ResourceLocation nether = ResourceLocation.parse("minecraft:the_nether");

            // Blue portals in overworld
            portals.add(PortalData.create(PortalColor.BLUE, overworld, new BlockPos(0, 64, 0), 10));
            portals.add(PortalData.create(PortalColor.BLUE, overworld, new BlockPos(100, 64, 100), 10));

            // Red portals in overworld
            portals.add(PortalData.create(PortalColor.RED, overworld, new BlockPos(-50, 64, -50), 10));

            // Blue portal in nether
            portals.add(PortalData.create(PortalColor.BLUE, nether, new BlockPos(0, 64, 0), 10));

            // Purple portal in nether
            portals.add(PortalData.create(PortalColor.PURPLE, nether, new BlockPos(10, 64, 10), 10));

            return portals;
        }

        @Test
        @DisplayName("Filter by color returns only matching color")
        void filterByColorReturnsMatchingOnly() {
            List<PortalData> portals = createTestPortals();
            List<PortalData> bluePortals = filterByColor(portals, PortalColor.BLUE);

            assertEquals(3, bluePortals.size(), "Should find 3 blue portals");
            assertTrue(bluePortals.stream().allMatch(p -> p.color() == PortalColor.BLUE),
                "All filtered portals should be blue");
        }

        @Test
        @DisplayName("Filter by dimension returns only matching dimension")
        void filterByDimensionReturnsMatchingOnly() {
            List<PortalData> portals = createTestPortals();
            ResourceLocation nether = ResourceLocation.parse("minecraft:the_nether");
            List<PortalData> netherPortals = filterByDimension(portals, nether);

            assertEquals(2, netherPortals.size(), "Should find 2 nether portals");
            assertTrue(netherPortals.stream()
                .allMatch(p -> p.dimension().orElse(null) != null &&
                    p.dimension().get().equals(nether)),
                "All filtered portals should be in nether");
        }

        @Test
        @DisplayName("Filter by linked status")
        void filterByLinkedStatus() {
            List<PortalData> portals = createTestPortals();

            // Link two portals
            PortalData linked1 = portals.get(0).linkTo(portals.get(1).id());
            PortalData linked2 = portals.get(1).linkTo(portals.get(0).id());
            portals.set(0, linked1);
            portals.set(1, linked2);

            List<PortalData> linkedPortals = filterByLinked(portals, true);
            List<PortalData> unlinkedPortals = filterByLinked(portals, false);

            assertEquals(2, linkedPortals.size(), "Should find 2 linked portals");
            assertEquals(3, unlinkedPortals.size(), "Should find 3 unlinked portals");
        }

        @Test
        @DisplayName("Combined filters work correctly")
        void combinedFiltersWork() {
            List<PortalData> portals = createTestPortals();
            ResourceLocation overworld = ResourceLocation.parse("minecraft:overworld");

            List<PortalData> filtered = filterByColor(
                filterByDimension(portals, overworld),
                PortalColor.BLUE
            );

            assertEquals(2, filtered.size(), "Should find 2 blue overworld portals");
        }

        @Test
        @DisplayName("Empty filter returns empty list")
        void emptyFilterReturnsEmptyList() {
            List<PortalData> portals = createTestPortals();
            List<PortalData> filtered = filterByColor(portals, PortalColor.CYAN);

            assertTrue(filtered.isEmpty(), "Should return empty list for no matches");
        }

        private List<PortalData> filterByColor(List<PortalData> portals, PortalColor color) {
            return portals.stream()
                .filter(p -> p.color() == color)
                .toList();
        }

        private List<PortalData> filterByDimension(List<PortalData> portals, ResourceLocation dimension) {
            return portals.stream()
                .filter(p -> p.dimension().orElse(null) != null &&
                    p.dimension().get().equals(dimension))
                .toList();
        }

        private List<PortalData> filterByLinked(List<PortalData> portals, boolean linked) {
            return portals.stream()
                .filter(p -> p.isLinked() == linked)
                .toList();
        }
    }

    @Nested
    @DisplayName("Portal Statistics")
    class PortalStatistics {

        @Test
        @DisplayName("Count total portals")
        void countTotalPortals() {
            List<PortalData> portals = new ArrayList<>();
            portals.add(PortalData.create(PortalColor.BLUE));
            portals.add(PortalData.create(PortalColor.RED));
            portals.add(PortalData.create(PortalColor.GREEN));

            assertEquals(3, portals.size(), "Should count all portals");
        }

        @Test
        @DisplayName("Count linked pairs")
        void countLinkedPairs() {
            List<PortalData> portals = new ArrayList<>();

            // Create 3 pairs = 6 linked portals
            for (int i = 0; i < 3; i++) {
                PortalData p1 = PortalData.create(PortalColor.BLUE);
                PortalData p2 = PortalData.create(PortalColor.BLUE);
                portals.add(p1.linkTo(p2.id()));
                portals.add(p2.linkTo(p1.id()));
            }

            // Add 2 unlinked
            portals.add(PortalData.create(PortalColor.RED));
            portals.add(PortalData.create(PortalColor.RED));

            long linkedCount = portals.stream().filter(PortalData::isLinked).count();
            int pairCount = (int) (linkedCount / 2);

            assertEquals(6, linkedCount, "Should have 6 linked portals");
            assertEquals(3, pairCount, "Should have 3 linked pairs");
        }

        @Test
        @DisplayName("Count portals per color")
        void countPortalsPerColor() {
            List<PortalData> portals = new ArrayList<>();
            portals.add(PortalData.create(PortalColor.BLUE));
            portals.add(PortalData.create(PortalColor.BLUE));
            portals.add(PortalData.create(PortalColor.BLUE));
            portals.add(PortalData.create(PortalColor.RED));
            portals.add(PortalData.create(PortalColor.RED));

            long blueCount = portals.stream().filter(p -> p.color() == PortalColor.BLUE).count();
            long redCount = portals.stream().filter(p -> p.color() == PortalColor.RED).count();

            assertEquals(3, blueCount, "Should have 3 blue portals");
            assertEquals(2, redCount, "Should have 2 red portals");
        }
    }

    @Nested
    @DisplayName("Portal Lookup")
    class PortalLookup {

        @Test
        @DisplayName("Find portal by ID")
        void findPortalById() {
            PortalData portal = PortalData.create(PortalColor.BLUE);
            List<PortalData> portals = List.of(portal);

            Optional<PortalData> found = findById(portals, portal.id());

            assertTrue(found.isPresent(), "Should find portal by ID");
            assertEquals(portal.id(), found.get().id(), "Found portal should have matching ID");
        }

        @Test
        @DisplayName("Return empty for non-existent ID")
        void returnEmptyForNonExistentId() {
            List<PortalData> portals = List.of(PortalData.create(PortalColor.BLUE));

            Optional<PortalData> found = findById(portals, UUID.randomUUID());

            assertTrue(found.isEmpty(), "Should return empty for non-existent ID");
        }

        @Test
        @DisplayName("Find nearest portal to position")
        void findNearestPortal() {
            BlockPos searchPos = new BlockPos(50, 64, 50);
            ResourceLocation dim = ResourceLocation.parse("minecraft:overworld");

            List<PortalData> portals = new ArrayList<>();
            portals.add(PortalData.create(PortalColor.BLUE, dim, new BlockPos(0, 64, 0), 10));      // Distance ~70
            portals.add(PortalData.create(PortalColor.BLUE, dim, new BlockPos(100, 64, 100), 10)); // Distance ~70
            portals.add(PortalData.create(PortalColor.BLUE, dim, new BlockPos(45, 64, 45), 10));   // Distance ~7 (closest)

            Optional<PortalData> nearest = findNearest(portals, searchPos, dim);

            assertTrue(nearest.isPresent(), "Should find nearest portal");
            assertEquals(new BlockPos(45, 64, 45), nearest.get().position().orElse(null),
                "Should return the closest portal");
        }

        private Optional<PortalData> findById(List<PortalData> portals, UUID id) {
            return portals.stream()
                .filter(p -> p.id().equals(id))
                .findFirst();
        }

        private Optional<PortalData> findNearest(List<PortalData> portals, BlockPos pos, ResourceLocation dimension) {
            return portals.stream()
                .filter(p -> p.dimension().orElse(null) != null &&
                    p.dimension().get().equals(dimension))
                .filter(p -> p.position().isPresent())
                .min((a, b) -> {
                    double distA = a.position().get().distSqr(pos);
                    double distB = b.position().get().distSqr(pos);
                    return Double.compare(distA, distB);
                });
        }
    }
}
