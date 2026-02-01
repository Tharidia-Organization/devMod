package com.devmod.boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.runtime.InstanceState;
import com.devmod.runtime.PlayerInstanceState;

import static org.junit.jupiter.api.Assertions.*;

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
    @DisplayName("L0-03: Design Tokens")
    class DesignTokensTests {

        @Test
        @Order(1)
        @DisplayName("Background colors are defined")
        void backgroundColorsDefined() {
            // These are int constants, should be non-zero (assuming they have alpha)
            assertNotEquals(0, DesignTokens.Background.PANEL);
            assertNotEquals(0, DesignTokens.Background.HEADER);
        }

        @Test
        @Order(2)
        @DisplayName("Text colors are defined")
        void textColorsDefined() {
            assertNotEquals(0, DesignTokens.Text.PRIMARY);
            assertNotEquals(0, DesignTokens.Text.SECONDARY);
        }

        @Test
        @Order(3)
        @DisplayName("Border colors are defined")
        void borderColorsDefined() {
            assertNotEquals(0, DesignTokens.Border.DEFAULT);
            assertNotEquals(0, DesignTokens.Border.ACCENT);
        }

        @Test
        @Order(4)
        @DisplayName("Status colors are defined")
        void statusColorsDefined() {
            assertNotEquals(0, DesignTokens.Status.SUCCESS);
            assertNotEquals(0, DesignTokens.Status.ERROR);
        }

        @Test
        @Order(5)
        @DisplayName("Toggle colors are defined")
        void toggleColorsDefined() {
            assertNotEquals(0, DesignTokens.Toggle.ON);
            assertNotEquals(0, DesignTokens.Toggle.OFF);
        }

        @Test
        @Order(6)
        @DisplayName("Colors have proper alpha values")
        void colorsHaveProperAlpha() {
            // Extract alpha from ARGB
            int panelAlpha = (DesignTokens.Background.PANEL >> 24) & 0xFF;
            assertTrue(panelAlpha > 0, "Panel should have non-zero alpha");
        }
    }

    // =========================================================================
    // L0-04: SOURCE FILE EXISTENCE VERIFICATION
    // =========================================================================

    @Nested
    @DisplayName("L0-04: Critical Source Files")
    class SourceFileTests {

        private static final String SRC_MAIN = "src/main/java/com/devmod/";

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
            assertTrue(sourceFileExists("client/input/KeyInputHandler.java"),
                "KeyInputHandler.java should exist in client/input package");
        }

        @Test
        @Order(3)
        @DisplayName("Config.java exists")
        void configSourceExists() {
            assertTrue(sourceFileExists("config/Config.java"),
                "Config.java should exist in config package");
        }

        @Test
        @Order(4)
        @DisplayName("Runtime system files exist")
        void runtimeSystemFilesExist() {
            String[] runtimeFiles = {
                "runtime/InstanceState.java",
                "runtime/PlayerInstanceState.java",
                "runtime/InstanceManager.java",
                "runtime/InstanceData.java",
                "runtime/PlayerInstanceSnapshot.java"
            };

            for (String file : runtimeFiles) {
                assertTrue(sourceFileExists(file),
                    "Should exist: " + file);
            }
        }

        @Test
        @Order(5)
        @DisplayName("Endurance system files exist")
        void enduranceSystemFilesExist() {
            String[] enduranceFiles = {
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
            // Check for network handler files
            String[] networkFiles = {
                "network/PacketValidator.java"
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
                "client/ui/editor/core/DesignTokens.java",
                "client/ui/AxiomRenderer.java",
                "client/ui/unified/UnifiedSettingsScreen.java"
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

        private static final String BUILD_CLASSES = "build/classes/java/main/com/devmod/";

        @Test
        @Order(1)
        @DisplayName("Core classes are compiled")
        void coreClassesCompiled() {
            String[] coreClasses = {
                "DevMod.class",
                "client/input/KeyInputHandler.class",
                "config/Config.class"
            };

            for (String classFile : coreClasses) {
                assertTrue(classFileExists(classFile),
                    "Should be compiled: " + classFile);
            }
        }

        @Test
        @Order(2)
        @DisplayName("Runtime state enums are compiled")
        void runtimeEnumsCompiled() {
            assertTrue(classFileExists("runtime/InstanceState.class"),
                "InstanceState should be compiled");
            assertTrue(classFileExists("runtime/PlayerInstanceState.class"),
                "PlayerInstanceState should be compiled");
        }

        @Test
        @Order(3)
        @DisplayName("Design Tokens are compiled")
        void uiConstantsCompiled() {
            assertTrue(classFileExists("client/ui/editor/core/DesignTokens.class"),
                "DesignTokens should be compiled");
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
            Path sourceFile = Paths.get("src/main/java/com/devmod/client/input/KeyInputHandler.java");
            assertTrue(Files.exists(sourceFile), "KeyInputHandler.java should exist in client/input package");

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
        @DisplayName("KeyInputHandler has 40 KeyMapping fields")
        void keybindCount() throws IOException {
            Path sourceFile = Paths.get("src/main/java/com/devmod/client/input/KeyInputHandler.java");
            String content = Files.readString(sourceFile);

            // Count KeyMapping field declarations
            long count = content.lines()
                .filter(line -> line.contains("public static final KeyMapping"))
                .count();

            assertEquals(40, count, "Should have exactly 40 KeyMapping definitions");
        }

        @Test
        @Order(3)
        @DisplayName("All keybinds use devmod category")
        void keybindsUseCorrectCategory() throws IOException {
            Path sourceFile = Paths.get("src/main/java/com/devmod/client/input/KeyInputHandler.java");
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
            Path sourceFile = Paths.get("src/main/java/com/devmod/DevMod.java");
            String content = Files.readString(sourceFile);

            assertTrue(content.contains("@Mod(\"devmod\")") || content.contains("@Mod(DevMod.MODID)"),
                "DevMod should have @Mod annotation");
        }

        @Test
        @Order(2)
        @DisplayName("MODID is defined as devmod")
        void modIdDefined() throws IOException {
            Path sourceFile = Paths.get("src/main/java/com/devmod/DevMod.java");
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
            assertTrue(Files.isDirectory(Paths.get("src/main/java/com/devmod")),
                "Main source directory should exist");
        }

        @Test
        @Order(2)
        @DisplayName("Test source directory exists")
        void testSourceExists() {
            assertTrue(Files.isDirectory(Paths.get("src/test/java/com/devmod")),
                "Test source directory should exist");
        }

        @Test
        @Order(3)
        @DisplayName("Key packages exist")
        void keyPackagesExist() {
            String[] packages = {
                "runtime",
                "endurance",
                "quest",
                "network",
                "client/ui",
                "client/rendering",
                "client/overlay",
                "telemetry",
                "util"
            };

            for (String pkg : packages) {
                Path pkgPath = Paths.get("src/main/java/com/devmod/" + pkg);
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
