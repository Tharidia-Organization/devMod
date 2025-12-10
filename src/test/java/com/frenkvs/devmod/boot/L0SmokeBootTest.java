package com.frenkvs.devmod.boot;

import com.frenkvs.devmod.instance.InstanceState;
import com.frenkvs.devmod.instance.PlayerInstanceState;
import com.frenkvs.devmod.ui.UIConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L0 - Smoke/Boot Tests
 *
 * PURPOSE: Validate that the mod's core pure-Java components are correctly defined
 * and can initialize without errors. These tests run WITHOUT Minecraft/NeoForge runtime.
 *
 * GATING CRITERIA (Must pass 100% before advancing to L1):
 * - Core enums are properly defined
 * - UIConstants are present and correct
 * - Source files exist for critical classes
 * - No compilation errors in pure-Java components
 *
 * NOTE: Classes that depend on Minecraft/NeoForge (KeyMapping, BlockPos, etc.) cannot
 * be class-loaded in unit tests. Their existence is verified via source file checks.
 *
 * PASS CONDITION: ALL tests GREEN
 */
@DisplayName("L0: Smoke/Boot Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.CONCURRENT)
public class L0SmokeBootTest {

    // =========================================================================
    // L0-01: INSTANCE STATE ENUM VERIFICATION (Pure Java)
    // =========================================================================

    @Nested
    @DisplayName("L0-01: InstanceState Enum")
    class InstanceStateTests {

        @Test
        @Order(1)
        @DisplayName("InstanceState has all required states")
        void instanceStateHasAllStates() {
            InstanceState[] states = InstanceState.values();
            assertTrue(states.length >= 5, "Should have at least 5 instance states");
        }

        @Test
        @Order(2)
        @DisplayName("Required states exist")
        void requiredStatesExist() {
            // These should not throw
            assertNotNull(InstanceState.CREATING);
            assertNotNull(InstanceState.READY);
            assertNotNull(InstanceState.ACTIVE);
            assertNotNull(InstanceState.COMPLETING);
            assertNotNull(InstanceState.DESTROYED);
        }

        @Test
        @Order(3)
        @DisplayName("DESTROYED is terminal")
        void destroyedIsTerminal() {
            assertTrue(InstanceState.DESTROYED.isTerminal(),
                "DESTROYED should be terminal state");
        }

        @Test
        @Order(4)
        @DisplayName("ACTIVE is alive")
        void activeIsAlive() {
            assertTrue(InstanceState.ACTIVE.isAlive(),
                "ACTIVE should be an alive state");
        }

        @Test
        @Order(5)
        @DisplayName("State transitions are validated")
        void stateTransitionsValidated() {
            // CREATING -> READY should be valid
            assertTrue(InstanceState.CREATING.canTransitionTo(InstanceState.READY));

            // DESTROYED -> anything should be invalid
            assertFalse(InstanceState.DESTROYED.canTransitionTo(InstanceState.CREATING));
            assertFalse(InstanceState.DESTROYED.canTransitionTo(InstanceState.ACTIVE));
        }
    }

    // =========================================================================
    // L0-02: PLAYER INSTANCE STATE ENUM VERIFICATION (Pure Java)
    // =========================================================================

    @Nested
    @DisplayName("L0-02: PlayerInstanceState Enum")
    class PlayerInstanceStateTests {

        @Test
        @Order(1)
        @DisplayName("PlayerInstanceState enum loads")
        void playerInstanceStateLoads() {
            PlayerInstanceState[] states = PlayerInstanceState.values();
            assertNotNull(states);
            assertTrue(states.length >= 3, "Should have at least 3 player states");
        }

        @Test
        @Order(2)
        @DisplayName("Key states exist")
        void keyStatesExist() {
            // Should have IN_OVERWORLD (or equivalent starting state)
            // Should have IN_INSTANCE
            // Should have IN_TRANSIT or similar
            Set<String> stateNames = Arrays.stream(PlayerInstanceState.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

            assertTrue(stateNames.contains("IN_OVERWORLD") || stateNames.contains("NORMAL"),
                "Should have a normal/overworld state");
            assertTrue(stateNames.contains("IN_INSTANCE"),
                "Should have IN_INSTANCE state");
        }
    }

    // =========================================================================
    // L0-03: UI CONSTANTS VERIFICATION (Pure Java)
    // =========================================================================

    @Nested
    @DisplayName("L0-03: UI Constants")
    class UIConstantsTests {

        @Test
        @Order(1)
        @DisplayName("Background colors are defined")
        void backgroundColorsDefined() {
            // These are int constants, should be non-zero (assuming they have alpha)
            assertNotEquals(0, UIConstants.Background.PANEL);
            assertNotEquals(0, UIConstants.Background.HEADER);
        }

        @Test
        @Order(2)
        @DisplayName("Text colors are defined")
        void textColorsDefined() {
            assertNotEquals(0, UIConstants.Text.PRIMARY);
            assertNotEquals(0, UIConstants.Text.SECONDARY);
        }

        @Test
        @Order(3)
        @DisplayName("Border colors are defined")
        void borderColorsDefined() {
            assertNotEquals(0, UIConstants.Border.DEFAULT);
            assertNotEquals(0, UIConstants.Border.ACCENT);
        }

        @Test
        @Order(4)
        @DisplayName("Status colors are defined")
        void statusColorsDefined() {
            assertNotEquals(0, UIConstants.Status.SUCCESS);
            assertNotEquals(0, UIConstants.Status.ERROR);
        }

        @Test
        @Order(5)
        @DisplayName("Toggle colors are defined")
        void toggleColorsDefined() {
            assertNotEquals(0, UIConstants.Toggle.ON);
            assertNotEquals(0, UIConstants.Toggle.OFF);
        }

        @Test
        @Order(6)
        @DisplayName("Colors have proper alpha values")
        void colorsHaveProperAlpha() {
            // Extract alpha from ARGB
            int panelAlpha = (UIConstants.Background.PANEL >> 24) & 0xFF;
            assertTrue(panelAlpha > 0, "Panel should have non-zero alpha");
        }
    }

    // =========================================================================
    // L0-04: SOURCE FILE EXISTENCE VERIFICATION
    // =========================================================================

    @Nested
    @DisplayName("L0-04: Critical Source Files")
    class SourceFileTests {

        private static final String SRC_MAIN = "src/main/java/com/frenkvs/devmod/";

        @Test
        @Order(1)
        @DisplayName("DevMod.java exists")
        void devModSourceExists() {
            assertTrue(sourceFileExists("DevMod.java"),
                "DevMod.java should exist");
        }

        @Test
        @Order(2)
        @DisplayName("KeyInputHandler.java exists")
        void keyInputHandlerSourceExists() {
            assertTrue(sourceFileExists("KeyInputHandler.java"),
                "KeyInputHandler.java should exist");
        }

        @Test
        @Order(3)
        @DisplayName("Config.java exists")
        void configSourceExists() {
            assertTrue(sourceFileExists("Config.java"),
                "Config.java should exist");
        }

        @Test
        @Order(4)
        @DisplayName("Instance system files exist")
        void instanceSystemFilesExist() {
            String[] instanceFiles = {
                "instance/InstanceState.java",
                "instance/PlayerInstanceState.java",
                "instance/InstanceManager.java",
                "instance/InstanceData.java",
                "instance/PlayerInstanceSnapshot.java"  // Correct filename
            };

            for (String file : instanceFiles) {
                assertTrue(sourceFileExists(file),
                    "Should exist: " + file);
            }
        }

        @Test
        @Order(5)
        @DisplayName("Endurance system files exist")
        void enduranceSystemFilesExist() {
            String[] enduranceFiles = {
                "endurance/ArenaManager.java",
                "endurance/WaveManager.java",
                "endurance/EnduranceQuestManager.java"
            };

            for (String file : enduranceFiles) {
                assertTrue(sourceFileExists(file),
                    "Should exist: " + file);
            }
        }

        @Test
        @Order(6)
        @DisplayName("Network files exist")
        void networkFilesExist() {
            // Check for actual network files in the codebase
            String[] networkFiles = {
                "network/PacketSecurityService.java"
            };

            for (String file : networkFiles) {
                assertTrue(sourceFileExists(file),
                    "Should exist: " + file);
            }
        }

        @Test
        @Order(7)
        @DisplayName("Utility files exist")
        void utilityFilesExist() {
            // PathSanitizer is in util/ package
            assertTrue(sourceFileExists("util/PathSanitizer.java"),
                "PathSanitizer.java should exist in util/");
        }

        @Test
        @Order(8)
        @DisplayName("UI system files exist")
        void uiSystemFilesExist() {
            String[] uiFiles = {
                "ui/UIConstants.java",
                "ui/AxiomRenderer.java",
                "ui/unified/UnifiedSettingsScreen.java"
            };

            for (String file : uiFiles) {
                assertTrue(sourceFileExists(file),
                    "Should exist: " + file);
            }
        }

        private boolean sourceFileExists(String relativePath) {
            Path path = Paths.get(SRC_MAIN + relativePath);
            return Files.exists(path);
        }
    }

    // =========================================================================
    // L0-05: CLASS FILE COMPILATION VERIFICATION
    // =========================================================================

    @Nested
    @DisplayName("L0-05: Compiled Classes")
    class CompiledClassTests {

        private static final String BUILD_CLASSES = "build/classes/java/main/com/frenkvs/devmod/";

        @Test
        @Order(1)
        @DisplayName("Core classes are compiled")
        void coreClassesCompiled() {
            String[] coreClasses = {
                "DevMod.class",
                "KeyInputHandler.class",
                "Config.class"
            };

            for (String classFile : coreClasses) {
                assertTrue(classFileExists(classFile),
                    "Should be compiled: " + classFile);
            }
        }

        @Test
        @Order(2)
        @DisplayName("Instance state enums are compiled")
        void instanceEnumsCompiled() {
            assertTrue(classFileExists("instance/InstanceState.class"),
                "InstanceState should be compiled");
            assertTrue(classFileExists("instance/PlayerInstanceState.class"),
                "PlayerInstanceState should be compiled");
        }

        @Test
        @Order(3)
        @DisplayName("UI Constants are compiled")
        void uiConstantsCompiled() {
            assertTrue(classFileExists("ui/UIConstants.class"),
                "UIConstants should be compiled");
        }

        private boolean classFileExists(String relativePath) {
            Path path = Paths.get(BUILD_CLASSES + relativePath);
            return Files.exists(path);
        }
    }

    // =========================================================================
    // L0-06: KEYBIND DEFINITION VERIFICATION (via source parsing)
    // =========================================================================

    @Nested
    @DisplayName("L0-06: Keybind Definitions")
    class KeybindDefinitionTests {

        @Test
        @Order(1)
        @DisplayName("KeyInputHandler defines expected keybinds")
        void keybindFieldsDefined() throws IOException {
            Path sourceFile = Paths.get("src/main/java/com/frenkvs/devmod/KeyInputHandler.java");
            assertTrue(Files.exists(sourceFile), "KeyInputHandler.java should exist");

            String content = Files.readString(sourceFile);

            // Check for key keybind definitions
            String[] expectedKeybinds = {
                "OPEN_SETTINGS_KEY",
                "OPEN_RADIAL_MENU_KEY",
                "OPEN_WEAPON_EDITOR_KEY",
                "TOGGLE_DEBUG_OVERLAY_KEY",
                "TOGGLE_QUEST_HUD_KEY",
                "OPEN_ENDURANCE_QUEST_KEY"
            };

            for (String keybind : expectedKeybinds) {
                assertTrue(content.contains(keybind),
                    "KeyInputHandler should define: " + keybind);
            }
        }

        @Test
        @Order(2)
        @DisplayName("KeyInputHandler has 32 KeyMapping fields")
        void keybindCount() throws IOException {
            Path sourceFile = Paths.get("src/main/java/com/frenkvs/devmod/KeyInputHandler.java");
            String content = Files.readString(sourceFile);

            // Count KeyMapping field declarations
            long count = content.lines()
                .filter(line -> line.contains("public static final KeyMapping"))
                .count();

            assertEquals(32, count, "Should have exactly 32 KeyMapping definitions");
        }

        @Test
        @Order(3)
        @DisplayName("All keybinds use devmod category")
        void keybindsUseCorrectCategory() throws IOException {
            Path sourceFile = Paths.get("src/main/java/com/frenkvs/devmod/KeyInputHandler.java");
            String content = Files.readString(sourceFile);

            // Count category references
            long categoryCount = content.lines()
                .filter(line -> line.contains("key.categories.devmod"))
                .count();

            // Should match number of keybinds
            assertTrue(categoryCount >= 32,
                "All keybinds should use key.categories.devmod");
        }
    }

    // =========================================================================
    // L0-07: MOD METADATA VERIFICATION
    // =========================================================================

    @Nested
    @DisplayName("L0-07: Mod Metadata")
    class ModMetadataTests {

        @Test
        @Order(1)
        @DisplayName("DevMod.java contains @Mod annotation")
        void modAnnotationPresent() throws IOException {
            Path sourceFile = Paths.get("src/main/java/com/frenkvs/devmod/DevMod.java");
            String content = Files.readString(sourceFile);

            assertTrue(content.contains("@Mod(\"devmod\")") || content.contains("@Mod(DevMod.MODID)"),
                "DevMod should have @Mod annotation");
        }

        @Test
        @Order(2)
        @DisplayName("MODID is defined as devmod")
        void modIdDefined() throws IOException {
            Path sourceFile = Paths.get("src/main/java/com/frenkvs/devmod/DevMod.java");
            String content = Files.readString(sourceFile);

            assertTrue(content.contains("MODID = \"devmod\""),
                "MODID should be defined as 'devmod'");
        }

        @Test
        @Order(3)
        @DisplayName("neoforge.mods.toml template exists")
        void modsTomlExists() {
            // NeoForge uses templates that get processed during build
            Path tomlPath = Paths.get("src/main/templates/META-INF/neoforge.mods.toml");
            assertTrue(Files.exists(tomlPath),
                "neoforge.mods.toml template should exist");
        }

        @Test
        @Order(4)
        @DisplayName("neoforge.mods.toml contains modId reference")
        void modsTomlContainsModId() throws IOException {
            Path tomlPath = Paths.get("src/main/templates/META-INF/neoforge.mods.toml");
            if (Files.exists(tomlPath)) {
                String content = Files.readString(tomlPath);
                // Template uses ${mod_id} placeholder
                assertTrue(content.contains("modId") || content.contains("mod_id"),
                    "neoforge.mods.toml should reference modId");
            }
        }
    }

    // =========================================================================
    // L0-08: DIRECTORY STRUCTURE VERIFICATION
    // =========================================================================

    @Nested
    @DisplayName("L0-08: Directory Structure")
    class DirectoryStructureTests {

        @Test
        @Order(1)
        @DisplayName("Main source directory exists")
        void mainSourceExists() {
            assertTrue(Files.isDirectory(Paths.get("src/main/java/com/frenkvs/devmod")),
                "Main source directory should exist");
        }

        @Test
        @Order(2)
        @DisplayName("Test source directory exists")
        void testSourceExists() {
            assertTrue(Files.isDirectory(Paths.get("src/test/java/com/frenkvs/devmod")),
                "Test source directory should exist");
        }

        @Test
        @Order(3)
        @DisplayName("Key packages exist")
        void keyPackagesExist() {
            String[] packages = {
                "instance",
                "endurance",
                "quest",
                "network",
                "ui",
                "rendering",
                "telemetry",
                "util"  // Changed from 'security' - PathSanitizer is in util/
            };

            for (String pkg : packages) {
                Path pkgPath = Paths.get("src/main/java/com/frenkvs/devmod/" + pkg);
                assertTrue(Files.isDirectory(pkgPath),
                    "Package should exist: " + pkg);
            }
        }

        @Test
        @Order(4)
        @DisplayName("Resources directory exists")
        void resourcesExists() {
            assertTrue(Files.isDirectory(Paths.get("src/main/resources")),
                "Resources directory should exist");
        }
    }

    // =========================================================================
    // L0-09: BUILD CONFIGURATION VERIFICATION
    // =========================================================================

    @Nested
    @DisplayName("L0-09: Build Configuration")
    class BuildConfigTests {

        @Test
        @Order(1)
        @DisplayName("build.gradle exists")
        void buildGradleExists() {
            assertTrue(Files.exists(Paths.get("build.gradle")),
                "build.gradle should exist");
        }

        @Test
        @Order(2)
        @DisplayName("settings.gradle exists")
        void settingsGradleExists() {
            assertTrue(Files.exists(Paths.get("settings.gradle")),
                "settings.gradle should exist");
        }

        @Test
        @Order(3)
        @DisplayName("gradlew is executable")
        void gradlewExecutable() {
            Path gradlew = Paths.get("gradlew");
            assertTrue(Files.exists(gradlew), "gradlew should exist");
            assertTrue(Files.isExecutable(gradlew), "gradlew should be executable");
        }
    }

    // =========================================================================
    // L0-10: ENUM VALUE COUNTS
    // =========================================================================

    @Nested
    @DisplayName("L0-10: Enum Completeness")
    class EnumCompletenessTests {

        @Test
        @Order(1)
        @DisplayName("InstanceState has 6 values")
        void instanceStateCount() {
            assertEquals(6, InstanceState.values().length,
                "InstanceState should have exactly 6 values");
        }

        @Test
        @Order(2)
        @DisplayName("PlayerInstanceState has correct count")
        void playerInstanceStateCount() {
            int count = PlayerInstanceState.values().length;
            assertTrue(count >= 4 && count <= 8,
                "PlayerInstanceState should have 4-8 values, has: " + count);
        }
    }
}
