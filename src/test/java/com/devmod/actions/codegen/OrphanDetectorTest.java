package com.devmod.actions.codegen;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.catalog.ActionSpec;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OrphanDetector: drift detection between catalog and runtime.
 */
@DisplayName("Orphan Detector")
class OrphanDetectorTest {

    private static final Set<ActionOrigin> TRUSTED_ORIGINS = EnumSet.of(
        ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
        ActionOrigin.UI, ActionOrigin.EVENT);

    private static ActionSpec spec(String id) {
        return ActionSpec.builder(id)
            .category(ActionCategory.UI)
            .ui("label." + id, "desc." + id)
            .build();
    }

    private static ActionSpec specWithHandler(String id, String handlerRef) {
        return ActionSpec.builder(id)
            .category(ActionCategory.UI)
            .handlerRef(handlerRef)
            .ui("label." + id, "desc." + id)
            .build();
    }

    private static ActionSpec specWithCommand(String id, String command) {
        return ActionSpec.builder(id)
            .category(ActionCategory.TOOLS)
            .channel(ActionSpec.ActionChannel.SERVER)
            .ui("label." + id, "desc." + id)
            .binding(null, command, null)
            .build();
    }

    // =========================================================================
    // Empty Catalog/Registry
    // =========================================================================

    @Nested
    @DisplayName("Empty Inputs")
    class EmptyInputs {

        @Test
        @DisplayName("Empty catalog and empty sources produce no orphans")
        void emptyCatalogEmptySources() {
            List<String> catalogOrphans = OrphanDetector.catalogOrphans(
                List.of(), List.of());
            List<String> registryOrphans = OrphanDetector.registryOrphans(
                List.of(), List.of());

            assertTrue(catalogOrphans.isEmpty(), "No catalog orphans expected");
            assertTrue(registryOrphans.isEmpty(), "No registry orphans expected");
        }

        @Test
        @DisplayName("Empty specs produce no handler orphans")
        void emptySpecsNoHandlerOrphans() {
            List<String> orphans = OrphanDetector.handlerOrphans(List.of());
            assertTrue(orphans.isEmpty());
        }

        @Test
        @DisplayName("Empty catalog produces no keybind orphans")
        void emptyKeybindSource() {
            List<String> orphans = OrphanDetector.keybindOrphans(
                List.of(), "");
            assertTrue(orphans.isEmpty());
        }
    }

    // =========================================================================
    // Matching Catalog and Registry
    // =========================================================================

    @Nested
    @DisplayName("Matching Catalog and Registry")
    class MatchingCatalogAndRegistry {

        @Test
        @DisplayName("All catalog IDs found in sources produces no orphans")
        void allIdsFoundNoOrphans() {
            Set<String> catalogIds = Set.of("devmod.test.alpha", "devmod.test.beta");
            List<String> sources = List.of(
                """
                ActionRegistry.register(RadialAction.builder("devmod.test.alpha")
                    .handler(ctx -> {}).build());
                ActionRegistry.register(RadialAction.builder("devmod.test.beta")
                    .handler(ctx -> {}).build());
                """
            );

            List<String> catalogOrphans = OrphanDetector.catalogOrphans(catalogIds, sources);
            List<String> registryOrphans = OrphanDetector.registryOrphans(catalogIds, sources);

            assertTrue(catalogOrphans.isEmpty(), "No catalog orphans when all match");
            assertTrue(registryOrphans.isEmpty(), "No registry orphans when all match");
        }
    }

    // =========================================================================
    // Catalog Orphans
    // =========================================================================

    @Nested
    @DisplayName("Catalog Orphans")
    class CatalogOrphans {

        @Test
        @DisplayName("Detects IDs in catalog but not in registration source")
        void detectsCatalogOrphans() {
            Set<String> catalogIds = Set.of(
                "devmod.test.registered",
                "devmod.test.orphan_one",
                "devmod.test.orphan_two");

            List<String> sources = List.of(
                "builder(\"devmod.test.registered\").build();"
            );

            List<String> orphans = OrphanDetector.catalogOrphans(catalogIds, sources);

            assertEquals(2, orphans.size(), "Should find 2 orphans");
            assertTrue(orphans.contains("devmod.test.orphan_one"));
            assertTrue(orphans.contains("devmod.test.orphan_two"));
        }

        @Test
        @DisplayName("Orphans are returned sorted")
        void orphansAreSorted() {
            Set<String> catalogIds = Set.of(
                "devmod.test.zebra",
                "devmod.test.apple");
            List<String> sources = List.of("// empty");

            List<String> orphans = OrphanDetector.catalogOrphans(catalogIds, sources);

            assertEquals(List.of("devmod.test.apple", "devmod.test.zebra"), orphans);
        }
    }

    // =========================================================================
    // Registry Orphans
    // =========================================================================

    @Nested
    @DisplayName("Registry Orphans")
    class RegistryOrphans {

        @Test
        @DisplayName("Detects IDs in registry but not in catalog")
        void detectsRegistryOrphans() {
            Set<String> catalogIds = Set.of("devmod.test.known");

            List<String> sources = List.of(
                """
                builder("devmod.test.known").build();
                builder("devmod.test.unknown_legacy").build();
                """
            );

            List<String> orphans = OrphanDetector.registryOrphans(catalogIds, sources);

            assertEquals(1, orphans.size());
            assertEquals("devmod.test.unknown_legacy", orphans.get(0));
        }

        @Test
        @DisplayName("Ignores non-action-ID strings in source")
        void ignoresNonActionIdStrings() {
            Set<String> catalogIds = Set.of("devmod.test.only");

            List<String> sources = List.of(
                """
                String label = "some.translation.key";
                builder("devmod.test.only").build();
                """
            );

            // "some.translation.key" doesn't match devmod.* pattern
            List<String> orphans = OrphanDetector.registryOrphans(catalogIds, sources);
            assertTrue(orphans.isEmpty(),
                "Non-devmod strings should be ignored");
        }
    }

    // =========================================================================
    // Handler Orphans
    // =========================================================================

    @Nested
    @DisplayName("Handler Orphans")
    class HandlerOrphans {

        @Test
        @DisplayName("Specs with handlerRef are not orphans")
        void handlerRefNotOrphan() {
            List<ActionSpec> specs = List.of(
                specWithHandler("devmod.test.has_handler", "com.devmod.Handler"));

            List<String> orphans = OrphanDetector.handlerOrphans(specs);
            assertTrue(orphans.isEmpty(), "Spec with handlerRef should not be orphan");
        }

        @Test
        @DisplayName("Specs with commandHint are not orphans")
        void commandHintNotOrphan() {
            List<ActionSpec> specs = List.of(
                specWithCommand("devmod.test.has_command", "gamemode creative"));

            List<String> orphans = OrphanDetector.handlerOrphans(specs);
            assertTrue(orphans.isEmpty(), "Spec with commandHint should not be orphan");
        }

        @Test
        @DisplayName("Specs with neither handlerRef nor commandHint are orphans")
        void noHandlerNoCommandIsOrphan() {
            List<ActionSpec> specs = List.of(
                spec("devmod.test.no_handler"),
                specWithHandler("devmod.test.with_handler", "Handler"),
                spec("devmod.test.also_no_handler"));

            List<String> orphans = OrphanDetector.handlerOrphans(specs);

            assertEquals(2, orphans.size());
            assertTrue(orphans.contains("devmod.test.no_handler"));
            assertTrue(orphans.contains("devmod.test.also_no_handler"));
        }

        @Test
        @DisplayName("Handler orphans are sorted")
        void handlerOrphansSorted() {
            List<ActionSpec> specs = List.of(
                spec("devmod.test.zebra"),
                spec("devmod.test.apple"));

            List<String> orphans = OrphanDetector.handlerOrphans(specs);

            assertEquals(List.of("devmod.test.apple", "devmod.test.zebra"), orphans);
        }
    }

    // =========================================================================
    // Keybind Orphans
    // =========================================================================

    @Nested
    @DisplayName("Keybind Orphans")
    class KeybindOrphans {

        @Test
        @DisplayName("Keybinds referencing known IDs are not orphans")
        void knownKeybindsNotOrphans() {
            Set<String> catalogIds = Set.of("devmod.ui.radial.open");
            String keybindSource = """
                register("devmod.ui.radial.open", KeyInputHandler.radialOpen);
                """;

            List<String> orphans = OrphanDetector.keybindOrphans(catalogIds, keybindSource);
            assertTrue(orphans.isEmpty());
        }

        @Test
        @DisplayName("Keybinds referencing unknown IDs are orphans")
        void unknownKeybindsAreOrphans() {
            Set<String> catalogIds = Set.of("devmod.ui.radial.open");
            String keybindSource = """
                register("devmod.ui.radial.open", KeyInputHandler.radialOpen);
                register("devmod.ui.deleted.feature", KeyInputHandler.deleted);
                """;

            List<String> orphans = OrphanDetector.keybindOrphans(catalogIds, keybindSource);

            assertEquals(1, orphans.size());
            assertEquals("devmod.ui.deleted.feature", orphans.get(0));
        }

        @Test
        @DisplayName("Empty keybind source produces no orphans")
        void emptyKeybindSourceNoOrphans() {
            Set<String> catalogIds = Set.of("devmod.test.something");

            List<String> orphans = OrphanDetector.keybindOrphans(catalogIds, "");
            assertTrue(orphans.isEmpty());
        }
    }

    // =========================================================================
    // OrphanReport
    // =========================================================================

    @Nested
    @DisplayName("OrphanReport")
    class OrphanReportTests {

        @Test
        @DisplayName("Clean report when all empty")
        void cleanReport() {
            var report = new OrphanDetector.OrphanReport(
                List.of(), List.of(), List.of(), List.of());

            assertTrue(report.isClean());
            assertEquals(0, report.totalOrphans());
        }

        @Test
        @DisplayName("Not clean when any orphans exist")
        void notCleanWithOrphans() {
            var report = new OrphanDetector.OrphanReport(
                List.of("devmod.test.orphan"), List.of(), List.of(), List.of());

            assertFalse(report.isClean());
            assertEquals(1, report.totalOrphans());
        }

        @Test
        @DisplayName("Total orphans sums all categories")
        void totalOrphansSums() {
            var report = new OrphanDetector.OrphanReport(
                List.of("a.b.c"),
                List.of("d.e.f", "g.h.i"),
                List.of("j.k.l"),
                List.of("m.n.o", "p.q.r", "s.t.u"));

            assertEquals(7, report.totalOrphans());
        }
    }
}
