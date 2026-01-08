package com.devmod.portal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for portal linking service.
 * Tests bidirectional linking, unlinking, and validation.
 */
class PortalLinkerTest {

    private MockPortalRegistry registry;
    private PortalLinker linker;

    @BeforeEach
    void setUp() {
        registry = new MockPortalRegistry();
        linker = new PortalLinker(registry);
    }

    @Nested
    @DisplayName("Bidirectional Linking")
    class BidirectionalLinking {

        @Test
        @DisplayName("Link creates bidirectional connection")
        void linkCreatesBidirectionalConnection() {
            UUID portal1Id = registry.createPortal(PortalColor.BLUE);
            UUID portal2Id = registry.createPortal(PortalColor.BLUE);

            boolean success = linker.link(portal1Id, portal2Id);

            assertTrue(success);
            assertEquals(portal2Id, registry.getLinkedPortal(portal1Id).orElse(null));
            assertEquals(portal1Id, registry.getLinkedPortal(portal2Id).orElse(null));
        }

        @Test
        @DisplayName("Cannot link portal to itself")
        void cannotLinkToSelf() {
            UUID portalId = registry.createPortal(PortalColor.BLUE);

            boolean success = linker.link(portalId, portalId);

            assertFalse(success);
            assertFalse(registry.isLinked(portalId));
        }

        @Test
        @DisplayName("Cannot link already linked portals without unlinking first")
        void cannotLinkAlreadyLinked() {
            UUID portal1Id = registry.createPortal(PortalColor.BLUE);
            UUID portal2Id = registry.createPortal(PortalColor.BLUE);
            UUID portal3Id = registry.createPortal(PortalColor.BLUE);

            linker.link(portal1Id, portal2Id);
            boolean success = linker.link(portal1Id, portal3Id);

            assertFalse(success, "Should not link already-linked portal");
            assertEquals(portal2Id, registry.getLinkedPortal(portal1Id).orElse(null));
        }

        @Test
        @DisplayName("Cannot link to non-existent portal")
        void cannotLinkToNonExistent() {
            UUID portal1Id = registry.createPortal(PortalColor.BLUE);
            UUID fakeId = UUID.randomUUID();

            boolean success = linker.link(portal1Id, fakeId);

            assertFalse(success);
            assertFalse(registry.isLinked(portal1Id));
        }
    }

    @Nested
    @DisplayName("Unlinking")
    class Unlinking {

        @Test
        @DisplayName("Unlink removes bidirectional connection")
        void unlinkRemovesBidirectionalConnection() {
            UUID portal1Id = registry.createPortal(PortalColor.BLUE);
            UUID portal2Id = registry.createPortal(PortalColor.BLUE);

            linker.link(portal1Id, portal2Id);
            boolean success = linker.unlink(portal1Id);

            assertTrue(success);
            assertFalse(registry.isLinked(portal1Id));
            assertFalse(registry.isLinked(portal2Id));
        }

        @Test
        @DisplayName("Unlink non-linked portal returns false")
        void unlinkNonLinkedReturnsFalse() {
            UUID portalId = registry.createPortal(PortalColor.BLUE);

            boolean success = linker.unlink(portalId);

            assertFalse(success);
        }

        @Test
        @DisplayName("Unlink handles orphaned links gracefully")
        void unlinkHandlesOrphanedLinks() {
            UUID portal1Id = registry.createPortal(PortalColor.BLUE);
            UUID portal2Id = registry.createPortal(PortalColor.BLUE);

            linker.link(portal1Id, portal2Id);
            // Simulate portal2 being destroyed without proper cleanup
            registry.removePortal(portal2Id);

            boolean success = linker.unlink(portal1Id);

            assertTrue(success, "Should handle orphaned link gracefully");
            assertFalse(registry.isLinked(portal1Id));
        }
    }

    @Nested
    @DisplayName("Link Validation")
    class LinkValidation {

        @Test
        @DisplayName("Different colored portals cannot link by default")
        void differentColorsCannotLink() {
            UUID bluePortal = registry.createPortal(PortalColor.BLUE);
            UUID redPortal = registry.createPortal(PortalColor.RED);

            boolean success = linker.link(bluePortal, redPortal);

            assertFalse(success, "Different colors should not link");
        }

        @Test
        @DisplayName("Same colored portals can link")
        void sameColorsCanLink() {
            UUID blue1 = registry.createPortal(PortalColor.BLUE);
            UUID blue2 = registry.createPortal(PortalColor.BLUE);

            boolean success = linker.link(blue1, blue2);

            assertTrue(success);
        }
    }

    // ========================================================================
    // Test Support Classes
    // ========================================================================

    enum PortalColor {
        BLUE, RED, GREEN, ORANGE
    }

    /**
     * Mock portal registry for testing.
     */
    static class MockPortalRegistry {
        private final Map<UUID, PortalEntry> portals = new HashMap<>();

        UUID createPortal(PortalColor color) {
            UUID id = UUID.randomUUID();
            portals.put(id, new PortalEntry(id, color, null));
            return id;
        }

        void removePortal(UUID id) {
            portals.remove(id);
        }

        Optional<PortalColor> getColor(UUID id) {
            PortalEntry entry = portals.get(id);
            return entry != null ? Optional.of(entry.color) : Optional.empty();
        }

        boolean exists(UUID id) {
            return portals.containsKey(id);
        }

        boolean isLinked(UUID id) {
            PortalEntry entry = portals.get(id);
            return entry != null && entry.linkedTo != null;
        }

        Optional<UUID> getLinkedPortal(UUID id) {
            PortalEntry entry = portals.get(id);
            return entry != null ? Optional.ofNullable(entry.linkedTo) : Optional.empty();
        }

        void setLinked(UUID portalId, UUID linkedTo) {
            PortalEntry entry = portals.get(portalId);
            if (entry != null) {
                portals.put(portalId, new PortalEntry(portalId, entry.color, linkedTo));
            }
        }

        record PortalEntry(UUID id, PortalColor color, UUID linkedTo) {}
    }

    /**
     * Portal linker implementation for testing.
     */
    static class PortalLinker {
        private final MockPortalRegistry registry;

        PortalLinker(MockPortalRegistry registry) {
            this.registry = registry;
        }

        boolean link(UUID portal1, UUID portal2) {
            // Validation
            if (portal1.equals(portal2)) {
                return false;
            }
            if (!registry.exists(portal1) || !registry.exists(portal2)) {
                return false;
            }
            if (registry.isLinked(portal1) || registry.isLinked(portal2)) {
                return false;
            }

            // Color check
            Optional<PortalColor> color1 = registry.getColor(portal1);
            Optional<PortalColor> color2 = registry.getColor(portal2);
            if (color1.isEmpty() || color2.isEmpty() || !color1.get().equals(color2.get())) {
                return false;
            }

            // Create bidirectional link
            registry.setLinked(portal1, portal2);
            registry.setLinked(portal2, portal1);
            return true;
        }

        boolean unlink(UUID portalId) {
            if (!registry.isLinked(portalId)) {
                return false;
            }

            Optional<UUID> linkedOpt = registry.getLinkedPortal(portalId);
            registry.setLinked(portalId, null);

            // Unlink the other side if it exists
            linkedOpt.ifPresent(linkedId -> {
                if (registry.exists(linkedId)) {
                    registry.setLinked(linkedId, null);
                }
            });

            return true;
        }
    }
}
