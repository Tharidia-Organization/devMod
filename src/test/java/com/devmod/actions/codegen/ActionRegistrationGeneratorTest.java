package com.devmod.actions.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the code generation system: generator, validator, and snapshot manager.
 */
@DisplayName("Action Registration Generator")
class ActionRegistrationGeneratorTest {

    private static final Set<ActionOrigin> TRUSTED_ORIGINS = EnumSet.of(
        ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
        ActionOrigin.UI, ActionOrigin.EVENT);

    private static ActionSpec sampleSpec(String id, ActionCategory category) {
        return ActionSpec.builder(id)
            .category(category)
            .ui("test.label." + id, "test.desc." + id)
            .build();
    }

    private static ActionSpec commandSpec(String id, String command) {
        return ActionSpec.builder(id)
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .channel(ActionSpec.ActionChannel.SERVER)
            .permissionLevel(2)
            .ui("test.label." + id, "test.desc." + id,
                "minecraft:command_block", "Root/Tools/Commands", false)
            .binding(null, command, null)
            .build();
    }

    private static ActionSpec toggleSpec(String id) {
        return ActionSpec.builder(id)
            .category(ActionCategory.DEBUG)
            .actionType(ActionType.TOGGLE_SETTING)
            .ui("test.label." + id, "test.desc." + id,
                "minecraft:redstone_torch", "Root/Debug", true)
            .build();
    }

    // =========================================================================
    // Deterministic Output
    // =========================================================================

    @Nested
    @DisplayName("Deterministic Output")
    class DeterministicOutput {

        @Test
        @DisplayName("Same input produces identical output")
        void sameInputSameOutput() {
            List<ActionSpec> specs = List.of(
                sampleSpec("devmod.test.alpha", ActionCategory.UI),
                sampleSpec("devmod.test.beta", ActionCategory.DEBUG),
                sampleSpec("devmod.test.gamma", ActionCategory.TOOLS)
            );

            RegistrationOutput first = ActionRegistrationGenerator.generate(
                specs, "com.devmod.generated", "TestActions");
            RegistrationOutput second = ActionRegistrationGenerator.generate(
                specs, "com.devmod.generated", "TestActions");

            assertEquals(first.generatedSource(), second.generatedSource(),
                "Generated source should be identical for same input");
            assertEquals(first.actionIds(), second.actionIds(),
                "Action IDs should be identical for same input");
        }

        @Test
        @DisplayName("Different input order produces same output (sorted by ID)")
        void sortedOutputRegardlessOfInputOrder() {
            ActionSpec alpha = sampleSpec("devmod.test.alpha", ActionCategory.UI);
            ActionSpec beta = sampleSpec("devmod.test.beta", ActionCategory.DEBUG);
            ActionSpec gamma = sampleSpec("devmod.test.gamma", ActionCategory.TOOLS);

            RegistrationOutput forward = ActionRegistrationGenerator.generate(
                List.of(alpha, beta, gamma), "com.devmod.gen", "Test");
            RegistrationOutput reverse = ActionRegistrationGenerator.generate(
                List.of(gamma, beta, alpha), "com.devmod.gen", "Test");

            assertEquals(forward.generatedSource(), reverse.generatedSource(),
                "Output should be the same regardless of input order");
        }

        @Test
        @DisplayName("Action IDs in output are sorted")
        void actionIdsAreSorted() {
            List<ActionSpec> specs = List.of(
                sampleSpec("devmod.test.zebra", ActionCategory.UI),
                sampleSpec("devmod.test.apple", ActionCategory.UI),
                sampleSpec("devmod.test.mango", ActionCategory.UI)
            );

            RegistrationOutput output = ActionRegistrationGenerator.generate(
                specs, "com.devmod.gen", "Test");

            assertEquals(
                List.of("devmod.test.apple", "devmod.test.mango", "devmod.test.zebra"),
                output.actionIds());
        }
    }

    // =========================================================================
    // RadialAction Builder Generation
    // =========================================================================

    @Nested
    @DisplayName("RadialAction Builder Generation")
    class BuilderGeneration {

        @Test
        @DisplayName("Generates builder with all basic fields")
        void generatesBasicBuilder() {
            ActionSpec spec = ActionSpec.builder("devmod.ui.test.screen")
                .category(ActionCategory.UI)
                .actionType(ActionType.NAVIGATE_SCREEN)
                .ui("test.label", "test.desc", "minecraft:compass",
                    "Root/UI/Test", false)
                .build();

            String source = ActionRegistrationGenerator.generateSingleRegistration(spec);

            assertTrue(source.contains("RadialAction.builder(\"devmod.ui.test.screen\")"),
                "Should contain builder call");
            assertTrue(source.contains(".labelKey(\"test.label\")"),
                "Should contain labelKey");
            assertTrue(source.contains(".descriptionKey(\"test.desc\")"),
                "Should contain descriptionKey");
            assertTrue(source.contains(".category(ActionCategory.UI)"),
                "Should contain category");
            assertTrue(source.contains(".actionType(ActionType.NAVIGATE_SCREEN)"),
                "Should contain actionType");
            assertTrue(source.contains(".menuPath(\"Root/UI/Test\")"),
                "Should contain menuPath");
            assertTrue(source.contains(".icon(Items.COMPASS)"),
                "Should contain icon");
        }

        @Test
        @DisplayName("Generates command handler from commandHint")
        void generatesCommandHandler() {
            ActionSpec spec = commandSpec("devmod.command.test.cmd", "gamemode creative");

            String source = ActionRegistrationGenerator.generateSingleRegistration(spec);

            assertTrue(source.contains(".handler(ctx -> ctx.executeCommand(\"gamemode creative\"))"),
                "Should generate executeCommand handler");
            assertTrue(source.contains(".commandHint(\"gamemode creative\")"),
                "Should contain commandHint");
            assertTrue(source.contains(".permissionLevel(2)"),
                "Should contain permissionLevel");
            assertTrue(source.contains(".uiFeedback(RadialAction.UIFeedback.CHAT)"),
                "Should set CHAT feedback for server commands");
        }

        @Test
        @DisplayName("Generates toggle with active predicate placeholder")
        void generatesToggle() {
            ActionSpec spec = toggleSpec("devmod.debug.test.toggle");

            String source = ActionRegistrationGenerator.generateSingleRegistration(spec);

            assertTrue(source.contains(".toggle(ctx -> false)"),
                "Should generate toggle with placeholder predicate");
        }

        @Test
        @DisplayName("Generates requiresConfirm for confirmed actions")
        void generatesRequiresConfirm() {
            ActionSpec spec = ActionSpec.builder("devmod.admin.test.action")
                .category(ActionCategory.ADMIN)
                .channel(ActionSpec.ActionChannel.SERVER)
                .permissionLevel(4)
                .ui("test.label", "test.desc")
                .policy(true, 0, null)
                .build();

            String source = ActionRegistrationGenerator.generateSingleRegistration(spec);

            assertTrue(source.contains(".requiresConfirm(true)"),
                "Should contain requiresConfirm");
        }

        @Test
        @DisplayName("Generates full class wrapper with imports")
        void generatesFullClass() {
            List<ActionSpec> specs = List.of(
                sampleSpec("devmod.test.one", ActionCategory.UI));

            RegistrationOutput output = ActionRegistrationGenerator.generate(
                specs, "com.devmod.generated", "GeneratedActions");

            String src = output.generatedSource();
            assertTrue(src.contains("package com.devmod.generated;"),
                "Should contain package declaration");
            assertTrue(src.contains("import com.devmod.actions.ActionRegistry;"),
                "Should import ActionRegistry");
            assertTrue(src.contains("public final class GeneratedActions"),
                "Should contain class declaration");
            assertTrue(src.contains("public static void registerAll()"),
                "Should contain registerAll method");
            assertTrue(src.contains("DO NOT EDIT"),
                "Should contain generated file warning");
        }
    }

    // =========================================================================
    // Keybind Registration
    // =========================================================================

    @Nested
    @DisplayName("Keybind Handling")
    class KeybindHandling {

        @Test
        @DisplayName("Actions with keybindId are tracked in warnings when no handler")
        void keybindIdTracked() {
            ActionSpec spec = ActionSpec.builder("devmod.test.keybind")
                .category(ActionCategory.UI)
                .ui("test.label", "test.desc")
                .binding("KEY_RADIAL_OPEN", null, null)
                .build();

            RegistrationOutput output = ActionRegistrationGenerator.generate(
                List.of(spec), "com.devmod.gen", "Test");

            // Should warn about no handler
            assertFalse(output.warnings().isEmpty(),
                "Should produce warning for no handler/commandHint");
        }
    }

    // =========================================================================
    // Manual Override Detection
    // =========================================================================

    @Nested
    @DisplayName("Manual Override Detection")
    class ManualOverride {

        @Test
        @DisplayName("Uses handler class reference when known")
        void usesKnownHandlerClass() {
            ActionSpec spec = ActionSpec.builder("devmod.test.override")
                .category(ActionCategory.UI)
                .handlerRef("com.devmod.handlers.TestHandler")
                .ui("test.label", "test.desc")
                .build();

            RegistrationOutput output = ActionRegistrationGenerator.generate(
                List.of(spec), "com.devmod.gen", "Test",
                Set.of("com.devmod.handlers.TestHandler"));

            assertTrue(output.generatedSource().contains(
                ".handler(com.devmod.handlers.TestHandler::handle)"),
                "Should reference handler class");
            assertTrue(output.warnings().isEmpty(),
                "Known handler should not produce warnings");
        }

        @Test
        @DisplayName("Warns when handler class is not in known set")
        void warnsForUnknownHandlerClass() {
            ActionSpec spec = ActionSpec.builder("devmod.test.unknown_handler")
                .category(ActionCategory.UI)
                .handlerRef("com.devmod.handlers.MissingHandler")
                .ui("test.label", "test.desc")
                .build();

            RegistrationOutput output = ActionRegistrationGenerator.generate(
                List.of(spec), "com.devmod.gen", "Test", Set.of());

            assertTrue(output.warnings().stream()
                .anyMatch(w -> w.contains("MissingHandler")),
                "Should warn about missing handler class");
            assertTrue(output.generatedSource().contains("TODO: com.devmod.handlers.MissingHandler"),
                "Should generate stub with TODO comment");
        }
    }

    // =========================================================================
    // Duplicate Detection
    // =========================================================================

    @Nested
    @DisplayName("Duplicate Detection")
    class DuplicateDetection {

        @Test
        @DisplayName("Warns on duplicate action IDs in input")
        void warnsDuplicateIds() {
            ActionSpec spec1 = sampleSpec("devmod.test.dup", ActionCategory.UI);
            ActionSpec spec2 = sampleSpec("devmod.test.dup", ActionCategory.DEBUG);

            RegistrationOutput output = ActionRegistrationGenerator.generate(
                List.of(spec1, spec2), "com.devmod.gen", "Test");

            assertTrue(output.warnings().stream()
                .anyMatch(w -> w.contains("Duplicate")),
                "Should warn about duplicate IDs");
            assertEquals(1, output.actionIds().size(),
                "Should only include one copy");
        }
    }

    // =========================================================================
    // Snapshot Comparison
    // =========================================================================

    @Nested
    @DisplayName("Snapshot Comparison")
    class SnapshotComparison {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Identical outputs produce identical snapshot diff")
        void identicalOutputsNoDiff() {
            List<ActionSpec> specs = List.of(
                sampleSpec("devmod.test.snap", ActionCategory.UI));

            RegistrationOutput first = ActionRegistrationGenerator.generate(
                specs, "com.devmod.gen", "Test");
            RegistrationOutput second = ActionRegistrationGenerator.generate(
                specs, "com.devmod.gen", "Test");

            SnapshotManager.SnapshotDiff diff = SnapshotManager.compare(second, first);

            assertTrue(diff.isIdentical(), "Same outputs should have no diff");
        }

        @Test
        @DisplayName("Detects added actions")
        void detectsAddedActions() {
            RegistrationOutput baseline = ActionRegistrationGenerator.generate(
                List.of(sampleSpec("devmod.test.one", ActionCategory.UI)),
                "com.devmod.gen", "Test");

            RegistrationOutput current = ActionRegistrationGenerator.generate(
                List.of(
                    sampleSpec("devmod.test.one", ActionCategory.UI),
                    sampleSpec("devmod.test.two", ActionCategory.UI)),
                "com.devmod.gen", "Test");

            SnapshotManager.SnapshotDiff diff = SnapshotManager.compare(current, baseline);

            assertEquals(List.of("devmod.test.two"), diff.added());
            assertTrue(diff.removed().isEmpty());
            assertTrue(diff.sourceChanged());
        }

        @Test
        @DisplayName("Detects removed actions")
        void detectsRemovedActions() {
            RegistrationOutput baseline = ActionRegistrationGenerator.generate(
                List.of(
                    sampleSpec("devmod.test.one", ActionCategory.UI),
                    sampleSpec("devmod.test.two", ActionCategory.UI)),
                "com.devmod.gen", "Test");

            RegistrationOutput current = ActionRegistrationGenerator.generate(
                List.of(sampleSpec("devmod.test.one", ActionCategory.UI)),
                "com.devmod.gen", "Test");

            SnapshotManager.SnapshotDiff diff = SnapshotManager.compare(current, baseline);

            assertTrue(diff.added().isEmpty());
            assertEquals(List.of("devmod.test.two"), diff.removed());
        }

        @Test
        @DisplayName("Snapshot round-trip through disk")
        void snapshotRoundTrip() throws IOException {
            List<ActionSpec> specs = List.of(
                sampleSpec("devmod.test.snap_a", ActionCategory.UI),
                sampleSpec("devmod.test.snap_b", ActionCategory.DEBUG));

            RegistrationOutput output = ActionRegistrationGenerator.generate(
                specs, "com.devmod.gen", "Test");

            Path snapshotFile = tempDir.resolve("test-snapshot.txt");
            SnapshotManager.takeSnapshot(output, snapshotFile);

            assertTrue(Files.exists(snapshotFile), "Snapshot file should exist");

            // Compare same output against stored snapshot
            SnapshotManager.SnapshotDiff diff =
                SnapshotManager.compareWithSnapshot(output, snapshotFile);

            assertTrue(diff.isIdentical(),
                "Same output compared with its own snapshot should be identical");
        }

        @Test
        @DisplayName("No previous snapshot treats all as added")
        void noPreviousSnapshotAllAdded() throws IOException {
            RegistrationOutput output = ActionRegistrationGenerator.generate(
                List.of(sampleSpec("devmod.test.new_action", ActionCategory.UI)),
                "com.devmod.gen", "Test");

            Path missing = tempDir.resolve("does-not-exist.txt");

            SnapshotManager.SnapshotDiff diff =
                SnapshotManager.compareWithSnapshot(output, missing);

            assertEquals(List.of("devmod.test.new_action"), diff.added());
            assertTrue(diff.removed().isEmpty());
            assertTrue(diff.sourceChanged());
        }
    }

    // =========================================================================
    // Validation
    // =========================================================================

    @Nested
    @DisplayName("Codegen Validation")
    class Validation {

        @Test
        @DisplayName("Valid specs pass validation")
        void validSpecsPass() {
            List<ActionSpec> specs = List.of(
                sampleSpec("devmod.test.valid", ActionCategory.UI));

            CodegenValidator.ValidationResult result =
                CodegenValidator.validate(specs, Set.of());

            assertTrue(result.isValid(), "Valid spec should pass: " + result.errors());
        }

        @Test
        @DisplayName("Duplicate IDs produce errors")
        void duplicateIdsError() {
            List<ActionSpec> specs = List.of(
                sampleSpec("devmod.test.dup", ActionCategory.UI),
                sampleSpec("devmod.test.dup", ActionCategory.DEBUG));

            CodegenValidator.ValidationResult result =
                CodegenValidator.validate(specs, Set.of());

            assertFalse(result.isValid(), "Duplicate IDs should fail validation");
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.contains("Duplicate")));
        }

        @Test
        @DisplayName("Empty labelKey produces error")
        void emptyLabelKeyError() {
            ActionSpec spec = ActionSpec.builder("devmod.test.empty_label")
                .category(ActionCategory.UI)
                .ui("", "test.desc")
                .build();

            CodegenValidator.ValidationResult result =
                CodegenValidator.validateSingle(spec);

            assertFalse(result.isValid(), "Empty labelKey should fail");
        }

        @Test
        @DisplayName("SERVER command with CLIENT channel warns")
        void serverCommandClientChannelWarns() {
            ActionSpec spec = ActionSpec.builder("devmod.test.bad_channel")
                .category(ActionCategory.TOOLS)
                .actionType(ActionType.RUN_SERVER_COMMAND)
                .channel(ActionSpec.ActionChannel.CLIENT)
                .ui("test.label", "test.desc")
                .build();

            CodegenValidator.ValidationResult result =
                CodegenValidator.validateSingle(spec);

            assertTrue(result.warnings().stream()
                .anyMatch(w -> w.contains("RUN_SERVER_COMMAND") && w.contains("CLIENT")),
                "Should warn about channel mismatch");
        }

        @Test
        @DisplayName("High permission without confirm warns")
        void highPermNoConfirmWarns() {
            ActionSpec spec = ActionSpec.builder("devmod.admin.test.dangerous")
                .category(ActionCategory.ADMIN)
                .channel(ActionSpec.ActionChannel.SERVER)
                .permissionLevel(4)
                .ui("test.label", "test.desc")
                .build();

            CodegenValidator.ValidationResult result =
                CodegenValidator.validateSingle(spec);

            assertTrue(result.warnings().stream()
                .anyMatch(w -> w.contains("Permission level 4") && w.contains("requiresConfirm")),
                "Should warn about high perm without confirm");
        }

        @Test
        @DisplayName("Unknown handler class produces warning")
        void unknownHandlerWarns() {
            ActionSpec spec = ActionSpec.builder("devmod.test.unknown")
                .category(ActionCategory.UI)
                .handlerRef("com.devmod.NoSuchClass")
                .ui("test.label", "test.desc")
                .build();

            CodegenValidator.ValidationResult result =
                CodegenValidator.validate(List.of(spec), Set.of("com.devmod.RealClass"));

            assertTrue(result.warnings().stream()
                .anyMatch(w -> w.contains("NoSuchClass")),
                "Should warn about unknown handler");
        }
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethods {

        @Test
        @DisplayName("toItemsField strips minecraft: prefix")
        void toItemsFieldStripsPrefix() {
            assertEquals("COMPASS",
                ActionRegistrationGenerator.toItemsField("minecraft:compass"));
            assertEquals("IRON_SWORD",
                ActionRegistrationGenerator.toItemsField("minecraft:iron_sword"));
        }

        @Test
        @DisplayName("toItemsField handles bare names")
        void toItemsFieldBareName() {
            assertEquals("COMPASS",
                ActionRegistrationGenerator.toItemsField("compass"));
        }

        @Test
        @DisplayName("toItemsField returns null for null input")
        void toItemsFieldNull() {
            assertNull(ActionRegistrationGenerator.toItemsField(null));
        }

        @Test
        @DisplayName("RegistrationOutput has correct action count")
        void registrationOutputCount() {
            List<ActionSpec> specs = List.of(
                sampleSpec("devmod.test.a", ActionCategory.UI),
                sampleSpec("devmod.test.b", ActionCategory.UI),
                sampleSpec("devmod.test.c", ActionCategory.UI));

            RegistrationOutput output = ActionRegistrationGenerator.generate(
                specs, "com.devmod.gen", "Test");

            assertEquals(3, output.actionCount());
            assertFalse(output.hasWarnings() && output.warnings().stream()
                .anyMatch(w -> w.contains("Duplicate")),
                "Should have no duplicate warnings");
        }
    }
}
