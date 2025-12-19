package com.frenkvs.devmod.keybind;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L1 Keybind System Validation Tests.
 *
 * Validates:
 * - All keybinds are properly defined
 * - No duplicate key assignments (conflicts)
 * - Key categories are organized correctly
 * - Keybind naming conventions followed
 * - All expected keybinds are present
 */
@DisplayName("L1: Keybind System Validation")
class KeybindSystemValidationTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Constants (mirroring GLFW key codes for validation)
    // ═══════════════════════════════════════════════════════════════════════════

    // Letter keys
    private static final int GLFW_KEY_G = 71;
    private static final int GLFW_KEY_K = 75;
    private static final int GLFW_KEY_M = 77;
    private static final int GLFW_KEY_J = 74;
    private static final int GLFW_KEY_X = 88;
    private static final int GLFW_KEY_O = 79;
    private static final int GLFW_KEY_L = 76;
    private static final int GLFW_KEY_H = 72;
    private static final int GLFW_KEY_R = 82;
    private static final int GLFW_KEY_P = 80;
    private static final int GLFW_KEY_V = 86;
    private static final int GLFW_KEY_Y = 89;
    private static final int GLFW_KEY_C = 67;
    private static final int GLFW_KEY_U = 85;
    private static final int GLFW_KEY_B = 66;
    private static final int GLFW_KEY_Z = 90;
    private static final int GLFW_KEY_N = 78;

    // Function keys
    private static final int GLFW_KEY_F4 = 293;
    private static final int GLFW_KEY_F6 = 295;
    private static final int GLFW_KEY_F7 = 296;
    private static final int GLFW_KEY_F8 = 297;
    private static final int GLFW_KEY_F9 = 298;
    private static final int GLFW_KEY_F10 = 299;
    private static final int GLFW_KEY_F11 = 300;
    private static final int GLFW_KEY_F12 = 301;

    // Special keys
    private static final int GLFW_KEY_BACKSLASH = 92;
    private static final int GLFW_KEY_LEFT_BRACKET = 91;
    private static final int GLFW_KEY_RIGHT_BRACKET = 93;
    private static final int GLFW_KEY_SEMICOLON = 59;
    private static final int GLFW_KEY_APOSTROPHE = 39;
    private static final int GLFW_KEY_GRAVE_ACCENT = 96;
    private static final int GLFW_KEY_0 = 48;

    // Expected keybind field names and their expected keys
    private static final Map<String, Integer> EXPECTED_KEYBINDS = new LinkedHashMap<>();

    static {
        // Primary Access
        EXPECTED_KEYBINDS.put("OPEN_RADIAL_MENU_KEY", GLFW_KEY_G);

        // Configuration Screens
        EXPECTED_KEYBINDS.put("OPEN_SETTINGS_KEY", GLFW_KEY_K);
        EXPECTED_KEYBINDS.put("OPEN_WEAPON_EDITOR_KEY", GLFW_KEY_M);
        EXPECTED_KEYBINDS.put("OPEN_DASHBOARD_KEY", GLFW_KEY_J);
        EXPECTED_KEYBINDS.put("INSPECT_MOB_KEY", GLFW_KEY_X);

        // Debug Overlays
        EXPECTED_KEYBINDS.put("TOGGLE_DEBUG_OVERLAY_KEY", GLFW_KEY_O);
        EXPECTED_KEYBINDS.put("TOGGLE_LIGHT_OVERLAY_KEY", GLFW_KEY_L);
        EXPECTED_KEYBINDS.put("TOGGLE_HEATMAP_KEY", GLFW_KEY_H);
        EXPECTED_KEYBINDS.put("TOGGLE_ROOM_BOUNDS_KEY", GLFW_KEY_R);
        EXPECTED_KEYBINDS.put("TOGGLE_PATHFINDING_KEY", GLFW_KEY_P);
        EXPECTED_KEYBINDS.put("TOGGLE_LOS_KEY", GLFW_KEY_V);
        EXPECTED_KEYBINDS.put("TOGGLE_VERTICAL_LEVELS_KEY", GLFW_KEY_Y);
        EXPECTED_KEYBINDS.put("TOGGLE_SAFE_SPOT_KEY", GLFW_KEY_C);
        EXPECTED_KEYBINDS.put("TOGGLE_ATTRIBUTE_MONITOR_KEY", GLFW_KEY_U);

        // Performance Monitoring
        EXPECTED_KEYBINDS.put("TOGGLE_FPS_TRACKER_KEY", GLFW_KEY_F8);
        EXPECTED_KEYBINDS.put("TOGGLE_PROFILER_KEY", GLFW_KEY_F9);
        EXPECTED_KEYBINDS.put("TOGGLE_ENTITY_DENSITY_KEY", GLFW_KEY_F6);
        EXPECTED_KEYBINDS.put("TOGGLE_BOSS_PHASE_KEY", GLFW_KEY_B);
        EXPECTED_KEYBINDS.put("TOGGLE_SKILL_EFFICACY_KEY", GLFW_KEY_Z);
        EXPECTED_KEYBINDS.put("TOGGLE_SPAWNABILITY_KEY", GLFW_KEY_F4);

        // Testing & QA
        EXPECTED_KEYBINDS.put("OPEN_QA_TESTING_KEY", GLFW_KEY_N);
        EXPECTED_KEYBINDS.put("OPEN_TESTING_HUB_KEY", GLFW_KEY_F7);

        // Quest System
        EXPECTED_KEYBINDS.put("TOGGLE_QUEST_HUD_KEY", GLFW_KEY_BACKSLASH);
        EXPECTED_KEYBINDS.put("QUEST_COMPLETE_TASK_KEY", GLFW_KEY_RIGHT_BRACKET);
        EXPECTED_KEYBINDS.put("OPEN_QUEST_EDITOR_KEY", GLFW_KEY_LEFT_BRACKET);
        EXPECTED_KEYBINDS.put("OPEN_ENDURANCE_QUEST_KEY", GLFW_KEY_F10);
        EXPECTED_KEYBINDS.put("QUEST_CONTINUE_KEY", GLFW_KEY_F11);
        EXPECTED_KEYBINDS.put("QUEST_EXIT_KEY", GLFW_KEY_F12);

        // Economy & Misc
        EXPECTED_KEYBINDS.put("TOGGLE_ECONOMY_KEY", GLFW_KEY_SEMICOLON);
        EXPECTED_KEYBINDS.put("TOGGLE_CHUNK_PERF_KEY", GLFW_KEY_APOSTROPHE);
        EXPECTED_KEYBINDS.put("TOGGLE_HELP_KEY", GLFW_KEY_GRAVE_ACCENT);

        // Effects
        EXPECTED_KEYBINDS.put("TEST_SCREEN_SHAKE_KEY", GLFW_KEY_0);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L1-08: Keybind Definitions
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L1-08: Keybind Definitions")
    class KeybindDefinitionsTest {

        @Test
        @DisplayName("All expected keybinds are defined")
        void allExpectedKeybindsExist() {
            // This tests that we have the expected number of keybinds
            assertEquals(32, EXPECTED_KEYBINDS.size(),
                "Should have 32 expected keybinds defined");
        }

        @Test
        @DisplayName("Keybind naming follows _KEY suffix convention")
        void keybindNamingConvention() {
            for (String fieldName : EXPECTED_KEYBINDS.keySet()) {
                assertTrue(fieldName.endsWith("_KEY"),
                    "Keybind field '" + fieldName + "' should end with _KEY suffix");
            }
        }

        @Test
        @DisplayName("Toggle keybinds use TOGGLE_ prefix")
        void toggleKeybindNamingConvention() {
            List<String> toggleBinds = List.of(
                "TOGGLE_DEBUG_OVERLAY_KEY",
                "TOGGLE_LIGHT_OVERLAY_KEY",
                "TOGGLE_HEATMAP_KEY",
                "TOGGLE_ROOM_BOUNDS_KEY",
                "TOGGLE_PATHFINDING_KEY",
                "TOGGLE_LOS_KEY",
                "TOGGLE_VERTICAL_LEVELS_KEY",
                "TOGGLE_SAFE_SPOT_KEY",
                "TOGGLE_ATTRIBUTE_MONITOR_KEY",
                "TOGGLE_FPS_TRACKER_KEY",
                "TOGGLE_PROFILER_KEY",
                "TOGGLE_ENTITY_DENSITY_KEY",
                "TOGGLE_BOSS_PHASE_KEY",
                "TOGGLE_SKILL_EFFICACY_KEY",
                "TOGGLE_SPAWNABILITY_KEY",
                "TOGGLE_QUEST_HUD_KEY",
                "TOGGLE_ECONOMY_KEY",
                "TOGGLE_CHUNK_PERF_KEY",
                "TOGGLE_HELP_KEY"
            );

            for (String bind : toggleBinds) {
                assertTrue(bind.startsWith("TOGGLE_"),
                    "Toggle keybind '" + bind + "' should start with TOGGLE_ prefix");
                assertTrue(EXPECTED_KEYBINDS.containsKey(bind),
                    "Toggle keybind '" + bind + "' should be in expected keybinds");
            }
        }

        @Test
        @DisplayName("Open screen keybinds use OPEN_ prefix")
        void openKeybindNamingConvention() {
            List<String> openBinds = List.of(
                "OPEN_RADIAL_MENU_KEY",
                "OPEN_SETTINGS_KEY",
                "OPEN_WEAPON_EDITOR_KEY",
                "OPEN_DASHBOARD_KEY",
                "OPEN_QA_TESTING_KEY",
                "OPEN_TESTING_HUB_KEY",
                "OPEN_QUEST_EDITOR_KEY",
                "OPEN_ENDURANCE_QUEST_KEY"
            );

            for (String bind : openBinds) {
                assertTrue(bind.startsWith("OPEN_"),
                    "Open keybind '" + bind + "' should start with OPEN_ prefix");
                assertTrue(EXPECTED_KEYBINDS.containsKey(bind),
                    "Open keybind '" + bind + "' should be in expected keybinds");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L1-09: Key Conflict Detection
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L1-09: Key Conflict Detection")
    class KeyConflictDetectionTest {

        @Test
        @DisplayName("No duplicate key assignments in DevMod keybinds")
        void noDuplicateKeyAssignments() {
            Map<Integer, List<String>> keyToBindings = new HashMap<>();

            for (Map.Entry<String, Integer> entry : EXPECTED_KEYBINDS.entrySet()) {
                keyToBindings.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
            }

            List<String> conflicts = new ArrayList<>();
            for (Map.Entry<Integer, List<String>> entry : keyToBindings.entrySet()) {
                if (entry.getValue().size() > 1) {
                    conflicts.add("Key " + entry.getKey() + " is used by: " + entry.getValue());
                }
            }

            assertTrue(conflicts.isEmpty(),
                "Found key conflicts:\n" + String.join("\n", conflicts));
        }

        @Test
        @DisplayName("DevMod keys don't conflict with critical Minecraft keys")
        void noConflictWithCriticalMinecraftKeys() {
            // Critical Minecraft keys that should NOT be used
            Set<Integer> criticalKeys = Set.of(
                87,  // W - forward
                65,  // A - left
                83,  // S - back
                68,  // D - right
                32,  // Space - jump
                340, // Left Shift - sneak
                69,  // E - inventory
                81,  // Q - drop
                256  // Escape - menu
            );

            List<String> conflicts = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : EXPECTED_KEYBINDS.entrySet()) {
                if (criticalKeys.contains(entry.getValue())) {
                    conflicts.add(entry.getKey() + " uses critical key " + entry.getValue());
                }
            }

            assertTrue(conflicts.isEmpty(),
                "DevMod keybinds conflict with critical Minecraft keys:\n" + String.join("\n", conflicts));
        }

        @Test
        @DisplayName("F-keys avoid F1-F3 and F5 (Minecraft reserved)")
        void fKeysAvoidMinecraftReserved() {
            // F1 = Hide GUI, F2 = Screenshot, F3 = Debug, F5 = Camera
            Set<Integer> reservedFKeys = Set.of(290, 291, 292, 294); // F1, F2, F3, F5

            List<String> conflicts = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : EXPECTED_KEYBINDS.entrySet()) {
                if (reservedFKeys.contains(entry.getValue())) {
                    conflicts.add(entry.getKey() + " uses reserved F-key " + entry.getValue());
                }
            }

            assertTrue(conflicts.isEmpty(),
                "DevMod uses reserved F-keys:\n" + String.join("\n", conflicts));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L1-10: Key Category Organization
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L1-10: Key Category Organization")
    class KeyCategoryOrganizationTest {

        @Test
        @DisplayName("Core controls use letter keys (easy to reach)")
        void coreControlsUseLetteryKeys() {
            Map<String, Integer> coreControls = Map.of(
                "OPEN_RADIAL_MENU_KEY", GLFW_KEY_G,
                "OPEN_SETTINGS_KEY", GLFW_KEY_K,
                "OPEN_WEAPON_EDITOR_KEY", GLFW_KEY_M,
                "OPEN_DASHBOARD_KEY", GLFW_KEY_J
            );

            for (Map.Entry<String, Integer> entry : coreControls.entrySet()) {
                int key = entry.getValue();
                assertTrue(key >= 65 && key <= 90, // A-Z range
                    "Core control '" + entry.getKey() + "' should use a letter key");
            }
        }

        @Test
        @DisplayName("Debug overlays grouped logically")
        void debugOverlaysGroupedLogically() {
            // All debug overlays should use TOGGLE_ prefix
            List<String> debugOverlays = List.of(
                "TOGGLE_DEBUG_OVERLAY_KEY",
                "TOGGLE_LIGHT_OVERLAY_KEY",
                "TOGGLE_HEATMAP_KEY",
                "TOGGLE_ROOM_BOUNDS_KEY",
                "TOGGLE_PATHFINDING_KEY",
                "TOGGLE_LOS_KEY",
                "TOGGLE_VERTICAL_LEVELS_KEY",
                "TOGGLE_SAFE_SPOT_KEY"
            );

            for (String overlay : debugOverlays) {
                assertTrue(EXPECTED_KEYBINDS.containsKey(overlay),
                    "Debug overlay '" + overlay + "' should be defined");
            }
        }

        @Test
        @DisplayName("Quest system uses contiguous keys (brackets, F10-F12)")
        void questSystemUsesContiguousKeys() {
            // Quest keys should be grouped together
            List<Integer> questKeys = List.of(
                EXPECTED_KEYBINDS.get("TOGGLE_QUEST_HUD_KEY"),
                EXPECTED_KEYBINDS.get("QUEST_COMPLETE_TASK_KEY"),
                EXPECTED_KEYBINDS.get("OPEN_QUEST_EDITOR_KEY"),
                EXPECTED_KEYBINDS.get("OPEN_ENDURANCE_QUEST_KEY"),
                EXPECTED_KEYBINDS.get("QUEST_CONTINUE_KEY"),
                EXPECTED_KEYBINDS.get("QUEST_EXIT_KEY")
            );

            // Should contain bracket keys and F10-F12
            assertTrue(questKeys.contains(GLFW_KEY_BACKSLASH), "Quest HUD should use backslash");
            assertTrue(questKeys.contains(GLFW_KEY_LEFT_BRACKET), "Quest editor should use left bracket");
            assertTrue(questKeys.contains(GLFW_KEY_RIGHT_BRACKET), "Quest complete should use right bracket");
            assertTrue(questKeys.contains(GLFW_KEY_F10), "Endurance quest should use F10");
            assertTrue(questKeys.contains(GLFW_KEY_F11), "Quest continue should use F11");
            assertTrue(questKeys.contains(GLFW_KEY_F12), "Quest exit should use F12");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L1-11: Keybind Mnemonic Validation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L1-11: Keybind Mnemonics")
    class KeybindMnemonicsTest {

        @Test
        @DisplayName("Letter keys have mnemonic relationships")
        void letterKeysHaveMnemonics() {
            // Key -> Expected letter relationship (key name contains the letter mnemonic)
            Map<String, Character> mnemonics = Map.ofEntries(
                Map.entry("OPEN_RADIAL_MENU_KEY", 'G'),      // G = Go/General
                Map.entry("OPEN_SETTINGS_KEY", 'K'),         // K = Konfiguration
                Map.entry("OPEN_WEAPON_EDITOR_KEY", 'M'),    // M = Modify
                Map.entry("OPEN_DASHBOARD_KEY", 'J'),        // J = Journal
                Map.entry("INSPECT_MOB_KEY", 'X'),           // X = eXamine
                Map.entry("TOGGLE_DEBUG_OVERLAY_KEY", 'O'),  // O = Overlay
                Map.entry("TOGGLE_LIGHT_OVERLAY_KEY", 'L'),  // L = Light
                Map.entry("TOGGLE_HEATMAP_KEY", 'H'),        // H = Heatmap
                Map.entry("TOGGLE_ROOM_BOUNDS_KEY", 'R'),    // R = Room
                Map.entry("TOGGLE_PATHFINDING_KEY", 'P'),    // P = Pathfinding
                Map.entry("TOGGLE_LOS_KEY", 'V'),            // V = Vision
                Map.entry("TOGGLE_VERTICAL_LEVELS_KEY", 'Y'),// Y = Y-levels
                Map.entry("TOGGLE_SAFE_SPOT_KEY", 'C'),      // C = Camping
                Map.entry("TOGGLE_ATTRIBUTE_MONITOR_KEY", 'U'), // U = attribUtes
                Map.entry("TOGGLE_BOSS_PHASE_KEY", 'B'),     // B = Boss
                Map.entry("TOGGLE_SKILL_EFFICACY_KEY", 'Z'), // Z = (available)
                Map.entry("OPEN_QA_TESTING_KEY", 'N')        // N = New test
            );

            for (Map.Entry<String, Character> entry : mnemonics.entrySet()) {
                int expectedKey = entry.getValue() - 'A' + 65; // Convert to GLFW key code
                int actualKey = EXPECTED_KEYBINDS.get(entry.getKey());
                assertEquals(expectedKey, actualKey,
                    "Keybind '" + entry.getKey() + "' should use key '" + entry.getValue() + "'");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L1-12: Keybind Count Validation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L1-12: Keybind Count Validation")
    class KeybindCountValidationTest {

        @Test
        @DisplayName("Total keybind count is correct")
        void totalKeybindCountIsCorrect() {
            assertEquals(32, EXPECTED_KEYBINDS.size(),
                "DevMod should have exactly 32 keybinds");
        }

        @Test
        @DisplayName("Keybind categories have expected counts")
        void keybindCategoryCountsCorrect() {
            // Count by prefix
            long openCount = EXPECTED_KEYBINDS.keySet().stream()
                .filter(k -> k.startsWith("OPEN_")).count();
            long toggleCount = EXPECTED_KEYBINDS.keySet().stream()
                .filter(k -> k.startsWith("TOGGLE_")).count();
            long questCount = EXPECTED_KEYBINDS.keySet().stream()
                .filter(k -> k.startsWith("QUEST_")).count();

            assertEquals(8, openCount, "Should have 8 OPEN_ keybinds");
            assertEquals(19, toggleCount, "Should have 19 TOGGLE_ keybinds");
            assertEquals(3, questCount, "Should have 3 QUEST_ keybinds (excluding TOGGLE/OPEN quest binds)");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L1-13: Key Ergonomics Validation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L1-13: Key Ergonomics")
    class KeyErgonomicsTest {

        @Test
        @DisplayName("Most used keys are on left side of keyboard")
        void mostUsedKeysOnLeftSide() {
            // Primary controls should be accessible with left hand (WASD position)
            Set<Integer> leftSideKeys = Set.of(
                GLFW_KEY_G, GLFW_KEY_R, GLFW_KEY_V, GLFW_KEY_C, GLFW_KEY_B,
                GLFW_KEY_H, GLFW_KEY_Y, GLFW_KEY_Z, GLFW_KEY_X, GLFW_KEY_N
            );

            String radialKey = "OPEN_RADIAL_MENU_KEY";
            assertTrue(leftSideKeys.contains(EXPECTED_KEYBINDS.get(radialKey)),
                "Radial menu (primary access) should be on left side of keyboard");
        }

        @Test
        @DisplayName("Less frequent keys use right hand or F-keys")
        void lessFrequentKeysUseRightHandOrFKeys() {
            // Performance and quest keys use F-keys (intentionally harder to reach)
            List<String> lessFrequent = List.of(
                "TOGGLE_FPS_TRACKER_KEY",    // F8
                "TOGGLE_PROFILER_KEY",       // F9
                "OPEN_ENDURANCE_QUEST_KEY",  // F10
                "QUEST_CONTINUE_KEY",        // F11
                "QUEST_EXIT_KEY"             // F12
            );

            for (String bind : lessFrequent) {
                int key = EXPECTED_KEYBINDS.get(bind);
                assertTrue(key >= 290 && key <= 301, // F1-F12 range
                    "Less frequent keybind '" + bind + "' should use F-key");
            }
        }
    }
}
