package com.devmod.client.testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;

import com.devmod.client.overlay.ImpactData;
import com.devmod.client.overlay.ImpactHudOverlay;
import com.devmod.client.rendering.DebugRenderer;
import com.devmod.client.rendering.LightLevelOverlay;
import com.devmod.client.rendering.LineOfSightVisualizer;
import com.devmod.client.rendering.PathfindingDebugger;
import com.devmod.client.rendering.RoomBoundsVisualizer;
import com.devmod.client.rendering.SafeSpotVisualizer;
import com.devmod.testing.DynamicTestGenerator;
import com.devmod.testing.ModDiscoveryService;
import com.devmod.testing.TestCase;
import com.devmod.util.ConfigPaths;

public class TestingSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestingSession.class);
    public static final TestingSession INSTANCE = new TestingSession();

    // Lazy initialization to avoid NPE during class loading (FMLPaths not ready yet)
    private static Path getSessionFile() {
        return ConfigPaths.getQASessionFile();
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Thread-safe collections for concurrent access (100-1000 users)
    private final List<TestCase> testCases = new CopyOnWriteArrayList<>();
    private final Map<String, List<TestCase>> categorizedTests = new ConcurrentHashMap<>();
    private volatile String testerName = "Anonymous";
    @Nullable
    private volatile Instant sessionStarted;
    private volatile String modVersion = "1.0.0"; // Updated from gradle.properties
    private final List<String> capturedLogs = new CopyOnWriteArrayList<>();
    private static final int maxLogLines = 500;
    private final AtomicBoolean sessionActive = new AtomicBoolean(false);
    private final AtomicInteger autoValidationTickCounter = new AtomicInteger(0);
    private static final int AUTO_VALIDATION_INTERVAL = 20; // Every second (20 ticks)

    // Dynamic test generation - thread-safe
    private final AtomicBoolean dynamicTestsLoaded = new AtomicBoolean(false);
    private final AtomicInteger dynamicTestCount = new AtomicInteger(0);

    // Read-Write lock for file operations
    private final ReentrantReadWriteLock fileLock = new ReentrantReadWriteLock();

    // Rate limiting for saves (prevent excessive I/O with many concurrent users)
    private volatile long lastSaveTime = 0;
    private static final long SAVE_COOLDOWN_MS = 500; // Max 2 saves per second

    // Async executor for file I/O operations - prevents main thread blocking
    // NOT final - recreated after shutdown when player re-enters world
    private volatile ExecutorService saveExecutor;
    private final AtomicBoolean saveInProgress = new AtomicBoolean(false);
    private final Object executorLock = new Object();

    private TestingSession() {
        ensureExecutor(); // Initialize executor first
        initializeTestCases();
        loadSession(); // Load saved session on startup
    }

    /**
     * Ensures the save executor is available and running.
     * Creates a new executor if null or shutdown.
     * Thread-safe with double-checked locking.
     */
    private void ensureExecutor() {
        if (saveExecutor == null || saveExecutor.isShutdown()) {
            synchronized (executorLock) {
                if (saveExecutor == null || saveExecutor.isShutdown()) {
                    saveExecutor = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "DevMod-QASession-Save");
                        t.setDaemon(true);
                        return t;
                    });
                    LOGGER.debug("[DevMod] QA session save executor created/recreated");
                }
            }
        }
    }

    /**
     * Load dynamically generated tests for all mods in the modpack.
     * Call this after world load when registries are available.
     */
    public void loadDynamicTests() {
        // Thread-safe check-then-act using compareAndSet
        if (!dynamicTestsLoaded.compareAndSet(false, true)) return;

        LOGGER.info("Loading dynamic tests for modpack...");

        try {
            // Scan mods and generate tests
            ModDiscoveryService.INSTANCE.scanMods();
            DynamicTestGenerator.INSTANCE.generateAllTests();

            // Add generated tests to our list
            List<TestCase> generatedTests = DynamicTestGenerator.INSTANCE.getAllTestsFlat();
            for (TestCase test : generatedTests) {
                // Check if test already exists (from saved session)
                if (getTest(test.getId()) == null) {
                    addTest(test);
                }
            }

            // Reorganize categories - thread-safe with ConcurrentHashMap
            categorizedTests.clear();
            for (TestCase test : testCases) {
                categorizedTests.computeIfAbsent(test.getCategory(), k -> new CopyOnWriteArrayList<>()).add(test);
            }

            dynamicTestCount.set(generatedTests.size());

            LOGGER.info("Loaded {} dynamic tests from {} mods",
                dynamicTestCount.get(), DynamicTestGenerator.INSTANCE.getModsWithTestsCount());

            // Save session with new tests
            saveSession();

        } catch (Exception e) {
            LOGGER.error("Failed to load dynamic tests: {}", e.getMessage());
            dynamicTestsLoaded.set(false); // Reset on failure
        }
    }

    /**
     * Get count of dynamically generated tests.
     */
    public int getDynamicTestCount() {
        return dynamicTestCount.get();
    }

    /**
     * Check if dynamic tests have been loaded.
     */
    public boolean areDynamicTestsLoaded() {
        return dynamicTestsLoaded.get();
    }

    /**
     * Force reload of dynamic tests.
     */
    public void reloadDynamicTests() {
        // Remove old dynamic tests
        testCases.removeIf(t -> t.getId().contains("_weapon_") ||
                               t.getId().contains("_mob_") ||
                               t.getId().contains("_effect_") ||
                               t.getId().contains("_armor_") ||
                               t.getId().contains("_integration") ||
                               t.getId().contains("_performance"));

        dynamicTestsLoaded.set(false);
        dynamicTestCount.set(0);

        // Rescan and regenerate
        ModDiscoveryService.INSTANCE.rescan();
        DynamicTestGenerator.INSTANCE.regenerate();
        loadDynamicTests();
    }

    /**
     * Initializes all test cases organized by category.
     * Each test represents a feature that needs manual verification.
     */
    private void initializeTestCases() {
        // === DAMAGE SYSTEM TESTS ===
        addTest(new TestCase(
            "dmg_melee_basic", "Damage System", "Basic Melee Damage",
            "Verify melee attacks display body part hit location",
            "1. Get a sword\n2. Attack a zombie\n3. Check action bar shows hit location (HEAD/BODY/ARMS/LEGS)\n4. Verify damage number appears",
            TestCase.TestPriority.CRITICAL,
            (Supplier<Boolean>) () -> ImpactData.get() != null
        ));

        addTest(new TestCase(
            "dmg_ranged_arrow", "Damage System", "Arrow Damage Detection",
            "Verify arrow hits are tracked correctly",
            "1. Get a bow and arrows\n2. Shoot a mob from distance\n3. Check that hit location is detected\n4. Verify Impact HUD shows arrow damage",
            TestCase.TestPriority.CRITICAL
        ));

        addTest(new TestCase(
            "dmg_mace_smash", "Damage System", "Mace Smash Attack",
            "Verify mace fall-damage attack is detected",
            "1. Get a Mace\n2. Build a 10-block tower\n3. Jump off and hit a mob while falling\n4. Verify 'MACE SMASH!' appears in HUD",
            TestCase.TestPriority.HIGH
        ));

        addTest(new TestCase(
            "dmg_body_parts", "Damage System", "Body Part Multipliers",
            "Verify different body parts have different damage",
            "1. Configure weapon with different multipliers (HEAD=2x, BODY=1x)\n2. Hit a mob in the head (aim high)\n3. Hit same mob in body\n4. Compare damage values",
            TestCase.TestPriority.HIGH
        ));

        addTest(new TestCase(
            "dmg_enderman_evade", "Damage System", "Enderman Evasion",
            "Verify Enderman teleport evasion is tracked",
            "1. Spawn an Enderman\n2. Try to hit it (it may teleport)\n3. Verify 'EVADED' panel appears if it dodged\n4. Check panel shows at original position",
            TestCase.TestPriority.MEDIUM
        ));

        addTest(new TestCase(
            "dmg_environmental", "Damage System", "Environmental Damage",
            "Verify fire, fall, lava damage is tracked",
            "1. Take fall damage\n2. Stand in fire\n3. Touch lava\n4. Verify each type shows in Impact HUD",
            TestCase.TestPriority.MEDIUM
        ));

        // === WEAPON PROPERTIES (Editor + Runtime) ===
        addTest(new TestCase(
            "wp_component_roundtrip", "Weapon Properties", "Component round-trip",
            "Verify weapon_stats component persists and applies modifiers",
            "1. Open Weapon Editor on a sword\n2. Set attack damage 12.5, crit chance 25%, vs-undead 100%\n3. Apply and re-equip\n4. Confirm modifiers/tooltips show edited values (no duplicate modifiers)",
            TestCase.TestPriority.HIGH
        ));

        addTest(new TestCase(
            "wp_clear_tool_rules", "Weapon Properties", "Clear Tool Rules",
            "Verify clear tool rules removes tool component and restores default mining",
            "1. Edit a pickaxe: toggle 'Clear Tool Rules' ON and apply\n2. Check NBT: TOOL component absent\n3. Mine a block: speed is default\n4. Toggle OFF, add a tag+speed rule, apply, verify component present and speed applies",
            TestCase.TestPriority.HIGH
        ));

        addTest(new TestCase(
            "wp_damage_bonuses", "Weapon Properties", "Damage type bonuses",
            "Verify vs-undead/true damage/armor shred apply correctly",
            "1. Set vs-undead +100%, true damage 20%, armor shred 10 on a weapon\n2. Hit an undead mob: damage higher than baseline; part of armor bypassed\n3. Hit a player: vs-undead not applied",
            TestCase.TestPriority.CRITICAL
        ));

        addTest(new TestCase(
            "wp_datapack_export_import", "Weapon Properties", "Datapack export/import",
            "Verify datapack export/import preserves advanced fields without stacking",
            "1. Export overrides to datapack via editor\n2. Remove local overrides, import datapack\n3. Confirm values (sweep, armor_shred, vs-*, true_damage, clear_tool_rules) are restored, no duplicate modifiers",
            TestCase.TestPriority.MEDIUM
        ));

        addTest(new TestCase(
            "wp_clamp_override", "Weapon Properties", "Clamp out-of-range edits",
            "Verify server clamps out-of-range weapon edits and overwrites existing modifiers",
            "1. Attempt to set armor shred > 66 or true damage > 100%\n2. Apply and re-equip\n3. Confirm values are clamped to allowed max and modifiers are overwritten (no stacking with prior modifiers)",
            TestCase.TestPriority.CRITICAL
        ));

        // === OVERLAY TESTS ===
        addTest(new TestCase(
            "ovl_impact_hud", "Overlays", "Impact HUD Overlay",
            "Verify the main Impact HUD overlay works",
            "1. Press the Impact HUD toggle key\n2. Attack a mob\n3. Verify damage breakdown panel appears\n4. Check it shows: damage, multiplier, body part",
            TestCase.TestPriority.CRITICAL,
            (Supplier<Boolean>) () -> ImpactHudOverlay.isEnabled()
        ));

        addTest(new TestCase(
            "ovl_debug_renderer", "Overlays", "Debug Renderer",
            "Verify 3D debug shapes render correctly",
            "1. Press G to toggle Debug Renderer\n2. Attack a mob\n3. Verify 3D markers appear at hit location\n4. Check shapes fade out over time",
            TestCase.TestPriority.HIGH,
            (Supplier<Boolean>) () -> DebugRenderer.INSTANCE.isEnabled()
        ));

        addTest(new TestCase(
            "ovl_pathfinding", "Overlays", "Pathfinding Debugger",
            "Verify mob pathfinding visualization",
            "1. Press P to toggle Pathfinding\n2. Stand near mobs that are walking\n3. Verify green lines show their navigation path\n4. Red lines = unreachable destination",
            TestCase.TestPriority.HIGH,
            (Supplier<Boolean>) () -> PathfindingDebugger.INSTANCE.isEnabled()
        ));

        addTest(new TestCase(
            "ovl_light_level", "Overlays", "Light Level Overlay",
            "Verify light level display on ground",
            "1. Press L to toggle Light Level\n2. Walk around\n3. Verify numbers appear on blocks\n4. Red = mob spawn possible, Yellow = borderline, Green = safe",
            TestCase.TestPriority.MEDIUM,
            (Supplier<Boolean>) () -> LightLevelOverlay.INSTANCE.isEnabled()
        ));

        addTest(new TestCase(
            "ovl_room_bounds", "Overlays", "Room Bounds Visualizer",
            "Verify room boundary visualization",
            "1. Press R to toggle Room Bounds\n2. Check if configured rooms show wireframe boxes\n3. Verify colors match room types",
            TestCase.TestPriority.LOW,
            () -> RoomBoundsVisualizer.INSTANCE.isEnabled()
        ));

        addTest(new TestCase(
            "ovl_safe_spots", "Overlays", "Safe Spots Visualizer",
            "Verify safe spot markers",
            "1. Press C to toggle Safe Spots\n2. Look for marked safe areas\n3. Verify they render correctly",
            TestCase.TestPriority.LOW,
            () -> SafeSpotVisualizer.INSTANCE.isEnabled()
        ));

        addTest(new TestCase(
            "ovl_los", "Overlays", "Line of Sight Visualizer",
            "Verify line-of-sight debug",
            "1. Press V to toggle Line of Sight\n2. Look at mobs\n3. Verify lines show visibility checks",
            TestCase.TestPriority.LOW,
            () -> LineOfSightVisualizer.INSTANCE.isEnabled()
        ));

        // === UI TESTS ===
        addTest(new TestCase(
            "ui_telemetry_dash", "User Interface", "Telemetry Dashboard",
            "Verify the telemetry dashboard opens and functions",
            "1. Open the mod menu\n2. Navigate to Telemetry Dashboard\n3. Check all 4 tabs work (Overlays, Export, Stats, Visualizers)\n4. Verify buttons are clickable",
            TestCase.TestPriority.HIGH
        ));

        addTest(new TestCase(
            "ui_mob_config", "User Interface", "Mob Config Screen",
            "Verify mob configuration screen",
            "1. Right-click a mob while sneaking (with config key)\n2. Verify config screen opens\n3. Try adjusting health/damage values\n4. Confirm changes apply",
            TestCase.TestPriority.MEDIUM
        ));

        addTest(new TestCase(
            "ui_weapon_editor", "User Interface", "Weapon Editor Screen",
            "Verify weapon stats editor",
            "1. Hold a weapon\n2. Open weapon editor (keybind)\n3. Adjust damage multipliers\n4. Test the weapon and verify changes",
            TestCase.TestPriority.MEDIUM
        ));

        addTest(new TestCase(
            "ui_no_overlap", "User Interface", "No Text Overlapping",
            "Verify UI has no overlapping text",
            "1. Open each mod screen\n2. Check all text is readable\n3. No labels should overlap\n4. Buttons should be properly spaced",
            TestCase.TestPriority.MEDIUM
        ));

        // === PERFORMANCE TESTS ===
        addTest(new TestCase(
            "perf_fps_stable", "Performance", "FPS Stability",
            "Verify mod doesn't cause FPS drops",
            "1. Note FPS without overlays\n2. Enable ALL overlays (G, L, P, R, V, C)\n3. Walk around for 30 seconds\n4. FPS should not drop more than 20%",
            TestCase.TestPriority.CRITICAL
        ));

        addTest(new TestCase(
            "perf_no_freeze", "Performance", "No World Freeze",
            "Verify no freezing when exploring",
            "1. Explore the world for 2+ minutes\n2. Load new chunks\n3. Game should never freeze\n4. Report if any stuttering occurs",
            TestCase.TestPriority.CRITICAL
        ));

        addTest(new TestCase(
            "perf_memory", "Performance", "Memory Usage",
            "Verify no memory leaks",
            "1. Play for 10 minutes\n2. Check F3 memory usage\n3. Memory should stabilize (not keep growing)\n4. Report starting/ending memory",
            TestCase.TestPriority.HIGH
        ));

        // === KEYBIND TESTS ===
        addTest(new TestCase(
            "key_all_work", "Keybinds", "All Keybinds Functional",
            "Verify all keybinds work",
            "1. Test each overlay toggle key (G, L, P, R, V, C, etc.)\n2. Verify each one activates/deactivates\n3. Check no conflicts with vanilla keys\n4. Test modifier combinations",
            TestCase.TestPriority.HIGH
        ));

        // === NETWORK TESTS ===
        addTest(new TestCase(
            "net_sync", "Network", "Client-Server Sync",
            "Verify damage syncs between client/server",
            "1. In a world (singleplayer or server)\n2. Attack mobs\n3. Verify damage values match log output\n4. No desync errors in log",
            TestCase.TestPriority.HIGH
        ));

        // === TELEMETRY TESTS ===
        addTest(new TestCase(
            "tel_export", "Telemetry", "Heatmap Export",
            "Verify heatmap export functionality",
            "1. Open Telemetry Dashboard\n2. Go to Export tab\n3. Click each export button\n4. Check run/telemetry/ folder for files",
            TestCase.TestPriority.MEDIUM
        ));

        addTest(new TestCase(
            "tel_visualize", "Telemetry", "Heatmap Visualization",
            "Verify heatmap loading and display",
            "1. Export some heatmap data\n2. Open Visualizers tab\n3. Load a heatmap\n4. Verify colored markers appear in world",
            TestCase.TestPriority.MEDIUM
        ));

        // === Badge Popup Tests ===
        addTest(new TestCase(
            "badge_common", "Badge Popups", "Common Badge Popup",
            "Verify COMMON rarity badge popup displays correctly",
            "1. Press F8 to open test menu\n2. Click 'Test Common Badge'\n3. Verify popup slides in from top\n4. Check sound plays and popup fades out",
            TestCase.TestPriority.HIGH
        ));
        addTest(new TestCase(
            "badge_uncommon", "Badge Popups", "Uncommon Badge Popup",
            "Verify UNCOMMON rarity badge popup displays correctly",
            "1. Press F8 to open test menu\n2. Click 'Test Uncommon Badge'\n3. Verify green-tinted popup appears\n4. Check sound pitch is slightly different",
            TestCase.TestPriority.HIGH
        ));
        addTest(new TestCase(
            "badge_rare", "Badge Popups", "Rare Badge Popup",
            "Verify RARE rarity badge popup with glow effect",
            "1. Press F8 to open test menu\n2. Click 'Test Rare Badge'\n3. Verify blue popup with GLOW effect\n4. Check pulsing border animation",
            TestCase.TestPriority.HIGH
        ));
        addTest(new TestCase(
            "badge_epic", "Badge Popups", "Epic Badge Popup",
            "Verify EPIC rarity badge popup with particles",
            "1. Press F8 to open test menu\n2. Click 'Test Epic Badge'\n3. Verify purple popup with PARTICLES\n4. Particles should float around edges",
            TestCase.TestPriority.HIGH
        ));
        addTest(new TestCase(
            "badge_legendary", "Badge Popups", "Legendary Badge Popup",
            "Verify LEGENDARY rarity badge popup (full effects)",
            "1. Press F8 to open test menu\n2. Click 'Test Legendary Badge'\n3. Verify gold popup with ALL effects\n4. Check fanfare sound plays additionally",
            TestCase.TestPriority.CRITICAL
        ));
        addTest(new TestCase(
            "badge_queue", "Badge Popups", "Badge Queue System",
            "Verify multiple badges queue correctly",
            "1. Press F8 to open test menu\n2. Click 'Test All Badges' rapidly\n3. Verify popups appear ONE AT A TIME\n4. Each should complete before next starts",
            TestCase.TestPriority.MEDIUM
        ));
        addTest(new TestCase(
            "badge_config", "Badge Popups", "Badge Config Options",
            "Verify badge popup config works",
            "1. Open config (devmod-client.toml)\n2. Set badgePopup.enabled = false\n3. Test a badge popup - should NOT appear\n4. Re-enable and verify it works again",
            TestCase.TestPriority.MEDIUM
        ));

        // Organize by category
        for (TestCase test : testCases) {
            categorizedTests.computeIfAbsent(test.getCategory(), k -> new ArrayList<>()).add(test);
        }
    }

    private void addTest(TestCase test) {
        testCases.add(test);
    }

    // === Session Management ===

    public void startSession(String testerName) {
        // Only reset if starting fresh (not resuming)
        if (!sessionActive.get() || !this.testerName.equals(testerName)) {
            this.testerName = testerName;
            this.sessionStarted = Instant.now();
            capturedLogs.clear();
            for (TestCase test : testCases) {
                test.reset();
            }
        }
        sessionActive.set(true);
        saveSession();
    }

    /**
     * Resume session without resetting tests
     */
    public void resumeSession(String testerName) {
        this.testerName = testerName;
        if (this.sessionStarted == null) {
            this.sessionStarted = Instant.now();
        }
        sessionActive.set(true);
    }

    public boolean isSessionActive() {
        return sessionActive.get();
    }

    public boolean hasExistingSession() {
        return Files.exists(getSessionFile());
    }

    /**
     * Reset all tests and start fresh
     */
    public void resetSession() {
        this.sessionStarted = Instant.now();
        capturedLogs.clear();
        for (TestCase test : testCases) {
            test.reset();
        }
        saveSession();
    }

    public void captureLogs() {
        // Capture recent log entries
        try {
            Path logPath = ConfigPaths.getLatestLogFile();
            if (Files.exists(logPath)) {
                List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
                int start = Math.max(0, lines.size() - maxLogLines);
                capturedLogs.clear();
                capturedLogs.addAll(lines.subList(start, lines.size()));
            }
        } catch (IOException e) {
            capturedLogs.add("ERROR: Could not read log file: " + e.getMessage());
        }
    }

    // === Test Access ===

    public List<TestCase> getAllTests() {
        return Collections.unmodifiableList(testCases);
    }

    public Map<String, List<TestCase>> getCategorizedTests() {
        return Collections.unmodifiableMap(categorizedTests);
    }

    public List<String> getCategories() {
        return new ArrayList<>(categorizedTests.keySet());
    }

    @Nullable
    public TestCase getTest(String id) {
        return testCases.stream()
            .filter(t -> t.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    @Nullable
    public TestCase getNextPendingTest() {
        return testCases.stream()
            .filter(t -> t.getStatus() == TestCase.TestStatus.PENDING)
            .min(Comparator.comparingInt(t -> t.getPriority().getOrder()))
            .orElse(null);
    }

    // === Progress Tracking ===

    public int getTotalTests() {
        return testCases.size();
    }

    public int getCompletedTests() {
        return (int) testCases.stream()
            .filter(t -> t.getStatus() == TestCase.TestStatus.PASSED ||
                        t.getStatus() == TestCase.TestStatus.FAILED ||
                        t.getStatus() == TestCase.TestStatus.SKIPPED)
            .count();
    }

    public int getPassedTests() {
        return (int) testCases.stream()
            .filter(t -> t.getStatus() == TestCase.TestStatus.PASSED)
            .count();
    }

    public int getFailedTests() {
        return (int) testCases.stream()
            .filter(t -> t.getStatus() == TestCase.TestStatus.FAILED)
            .count();
    }

    public float getProgressPercent() {
        return (float) getCompletedTests() / getTotalTests() * 100f;
    }

    public boolean isSessionComplete() {
        return getCompletedTests() == getTotalTests();
    }

    // === Report Generation ===

    /**
     * Generates a complete test report in Markdown format.
     */
    @Nonnull
    public String generateReport() {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("# DevMod QA Test Report\n\n");
        sb.append("**Tester**: ").append(testerName).append("\n");
        sb.append("**Mod Version**: ").append(modVersion).append("\n");
        sb.append("**Session Started**: ").append(sessionStarted).append("\n");
        sb.append("**Report Generated**: ").append(Instant.now()).append("\n");
        sb.append("**Minecraft Version**: 1.21.1\n\n");

        // Summary
        sb.append("## Summary\n\n");
        sb.append("| Metric | Count |\n");
        sb.append("|--------|-------|\n");
        sb.append("| Total Tests | ").append(getTotalTests()).append(" |\n");
        sb.append("| Passed | ").append(getPassedTests()).append(" |\n");
        sb.append("| Failed | ").append(getFailedTests()).append(" |\n");
        sb.append("| Skipped | ").append((int) testCases.stream().filter(t -> t.getStatus() == TestCase.TestStatus.SKIPPED).count()).append(" |\n");
        sb.append("| Pending | ").append((int) testCases.stream().filter(t -> t.getStatus() == TestCase.TestStatus.PENDING).count()).append(" |\n");
        sb.append("| **Progress** | **").append(String.format("%.1f", getProgressPercent())).append("%** |\n\n");

        // Failed tests first (most important)
        List<TestCase> failed = testCases.stream()
            .filter(t -> t.getStatus() == TestCase.TestStatus.FAILED)
            .collect(Collectors.toList());

        if (!failed.isEmpty()) {
            sb.append("## FAILED TESTS (REQUIRES ATTENTION)\n\n");
            for (TestCase test : failed) {
                sb.append(test.toReportString());
            }
        }

        // All tests by category
        sb.append("## Detailed Results\n\n");
        for (Map.Entry<String, List<TestCase>> entry : categorizedTests.entrySet()) {
            sb.append("### ").append(entry.getKey()).append("\n\n");
            for (TestCase test : entry.getValue()) {
                sb.append(test.toReportString());
            }
        }

        // System info
        sb.append("## System Information\n\n");
        sb.append("```\n");
        sb.append("Java Version: ").append(System.getProperty("java.version")).append("\n");
        sb.append("OS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("\n");
        sb.append("Available Memory: ").append(Runtime.getRuntime().maxMemory() / 1024 / 1024).append(" MB\n");
        sb.append("```\n\n");

        // Captured logs (if any failures)
        if (!failed.isEmpty() && !capturedLogs.isEmpty()) {
            sb.append("## Captured Logs (last ").append(capturedLogs.size()).append(" lines)\n\n");
            sb.append("```\n");
            for (String line : capturedLogs) {
                sb.append(line).append("\n");
            }
            sb.append("```\n");
        }

        return Objects.requireNonNull(sb.toString());
    }

    /**
     * Saves the report to a file.
     * @return Path to saved file
     */
    public String saveReport() throws IOException {
        String timestamp = LocalDateTime.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String filename = "devmod_qa_report_" + timestamp + ".md";
        Path reportDir = ConfigPaths.getQAReportsDir();
        Files.createDirectories(reportDir);
        Path reportPath = reportDir.resolve(filename);

        Files.writeString(reportPath, generateReport(), StandardCharsets.UTF_8);

        return reportPath.toAbsolutePath().toString();
    }

    /**
     * Copies report to clipboard for easy sharing.
     */
    public void copyReportToClipboard() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(generateReport());
        }
    }

    /**
     * Returns the session duration in milliseconds.
     */
    public long getSessionDuration() {
        if (sessionStarted == null) return 0;
        return System.currentTimeMillis() - sessionStarted.toEpochMilli();
    }

    /**
     * Generates a short summary for quick sharing.
     */
    public String getQuickSummary() {
        return String.format(
            "DevMod QA Report - %s\n" +
            "Tester: %s | Version: %s\n" +
            "Progress: %d/%d (%.1f%%)\n" +
            "Passed: %d | Failed: %d | Skipped: %d\n" +
            "---\n" +
            "Use /devmod qa report to see full details",
            LocalDateTime.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            testerName,
            modVersion,
            getCompletedTests(),
            getTotalTests(),
            getProgressPercent(),
            getPassedTests(),
            getFailedTests(),
            (int) testCases.stream().filter(t -> t.getStatus() == TestCase.TestStatus.SKIPPED).count()
        );
    }

    // === Persistence ===

    /**
     * Save session state to file for persistence across game restarts.
     * ASYNC: Runs on background thread to prevent main thread blocking/freezing.
     * Thread-safe with rate limiting for 100-1000 concurrent users.
     */
    public void saveSession() {
        // Rate limiting: prevent excessive saves with many concurrent users
        long now = System.currentTimeMillis();
        if (now - lastSaveTime < SAVE_COOLDOWN_MS) {
            return; // Skip this save, too soon
        }

        // Skip if save is already in progress
        if (!saveInProgress.compareAndSet(false, true)) {
            return;
        }

        lastSaveTime = now;

        // Capture current state snapshot for async save (avoid race conditions)
        final String snapshotTesterName = testerName;
        final String snapshotSessionStarted = sessionStarted != null ? sessionStarted.toString() : null;
        final String snapshotModVersion = modVersion;
        final boolean snapshotSessionActive = sessionActive.get();

        // Snapshot test states
        final List<TestSnapshot> testSnapshots = new ArrayList<>();
        for (TestCase test : testCases) {
            testSnapshots.add(new TestSnapshot(
                test.getId(),
                test.getStatus().name(),
                test.getTesterComment(),
                test.getCompletedAt() != null ? test.getCompletedAt().toString() : null,
                test.getErrorLog(),
                test.wasAutoValidated()
            ));
        }

        // Submit async save task - does NOT block main thread
        // Ensure executor is available (may have been shut down during logout, recreate if needed)
        ensureExecutor();

        saveExecutor.execute(() -> {
            fileLock.writeLock().lock();
            try {
                Files.createDirectories(getSessionFile().getParent());

                JsonObject root = new JsonObject();
                root.addProperty("testerName", snapshotTesterName);
                root.addProperty("sessionStarted", snapshotSessionStarted);
                root.addProperty("modVersion", snapshotModVersion);
                root.addProperty("sessionActive", snapshotSessionActive);

                JsonArray testsArray = new JsonArray();
                for (TestSnapshot snapshot : testSnapshots) {
                    JsonObject testObj = new JsonObject();
                    testObj.addProperty("id", snapshot.id);
                    testObj.addProperty("status", snapshot.status);
                    testObj.addProperty("comment", snapshot.comment);
                    testObj.addProperty("completedAt", snapshot.completedAt);
                    testObj.addProperty("errorLog", snapshot.errorLog);
                    testObj.addProperty("autoValidated", snapshot.autoValidated);
                    testsArray.add(testObj);
                }
                root.add("tests", testsArray);

                // Atomic write using temp file + rename for crash safety
                Path tempFile = getSessionFile().resolveSibling(getSessionFile().getFileName() + ".tmp");
                Files.writeString(tempFile, GSON.toJson(root), StandardCharsets.UTF_8);
                Files.move(tempFile, getSessionFile(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            } catch (IOException e) {
                LOGGER.error("[DevMod] Failed to save QA session: {}", e.getMessage());
            } finally {
                fileLock.writeLock().unlock();
                saveInProgress.set(false);
            }
        });
    }

    // Immutable snapshot for async save
    private record TestSnapshot(
        String id,
        String status,
        String comment,
        @Nullable String completedAt,
        @Nullable String errorLog,
        boolean autoValidated
    ) {}

    /**
     * Load session state from file.
     * Thread-safe with read lock.
     */
    public void loadSession() {
        try {
            // Safety check - getSessionFile() might be null in edge cases
            if (getSessionFile() == null || !Files.exists(getSessionFile())) {
                return;
            }
        } catch (Exception e) {
            // Files.exists() can throw on certain file systems or thread contexts
            LOGGER.debug("Could not check session file existence: {}", e.getMessage());
            return;
        }

        fileLock.readLock().lock();
        try {
            String content = Files.readString(getSessionFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();

            this.testerName = root.has("testerName") ? root.get("testerName").getAsString() : "Anonymous";
            sessionActive.set(root.has("sessionActive") && root.get("sessionActive").getAsBoolean());

            if (root.has("sessionStarted") && !root.get("sessionStarted").isJsonNull()) {
                this.sessionStarted = Instant.parse(root.get("sessionStarted").getAsString());
            }

            if (root.has("tests")) {
                JsonArray testsArray = root.getAsJsonArray("tests");
                for (JsonElement elem : testsArray) {
                    JsonObject testObj = elem.getAsJsonObject();
                    String id = testObj.get("id").getAsString();
                    TestCase test = getTest(id);
                    if (test != null) {
                        TestCase.TestStatus status = TestCase.TestStatus.valueOf(testObj.get("status").getAsString());
                        String comment = testObj.has("comment") && !testObj.get("comment").isJsonNull()
                                ? testObj.get("comment").getAsString() : "";
                        Instant completed = testObj.has("completedAt") && !testObj.get("completedAt").isJsonNull()
                                ? Instant.parse(testObj.get("completedAt").getAsString()) : null;
                        String error = testObj.has("errorLog") && !testObj.get("errorLog").isJsonNull()
                                ? testObj.get("errorLog").getAsString() : null;
                        boolean autoValidated = testObj.has("autoValidated") && testObj.get("autoValidated").getAsBoolean();

                        test.restoreState(status, comment, completed, error, autoValidated);
                    }
                }
            }

            LOGGER.info("QA session loaded: {}/{} tests passed", getPassedTests(), getTotalTests());
        } catch (Exception e) {
            LOGGER.error("Failed to load QA session: {}", e.getMessage());
        } finally {
            fileLock.readLock().unlock();
        }
    }

    // === Auto-Validation Tick ===

    /**
     * Called every client tick to check auto-validators in background.
     * This allows tests to auto-complete when conditions are met.
     * Thread-safe using atomic operations.
     */
    public void clientTick() {
        if (!sessionActive.get()) return;

        // Atomic increment and check
        if (autoValidationTickCounter.incrementAndGet() < AUTO_VALIDATION_INTERVAL) {
            return;
        }
        autoValidationTickCounter.set(0);

        boolean anyChanged = false;

        // CopyOnWriteArrayList allows safe iteration without explicit synchronization
        for (TestCase test : testCases) {
            // Only check pending or in-progress tests with auto-validators
            if (test.getStatus() != TestCase.TestStatus.PENDING &&
                test.getStatus() != TestCase.TestStatus.IN_PROGRESS) {
                continue;
            }

            if (!test.hasAutoValidator()) {
                continue;
            }

            // Check if auto-validator passes
            if (test.checkAutoValidator()) {
                // Auto-mark as passed if not already validated
                if (!test.wasAutoValidated() || test.getStatus() == TestCase.TestStatus.PENDING) {
                    test.markPassed("Auto-validated");
                    anyChanged = true;
                }
            }
        }

        // Save if any tests changed (rate-limited internally)
        if (anyChanged) {
            saveSession();
        }
    }

    /**
     * Manually trigger save (e.g., when test status changes in UI)
     * Rate-limited internally.
     */
    public void markDirty() {
        saveSession();
    }

    /**
     * Shutdown the save executor gracefully.
     * Called on player logout to ensure pending saves complete.
     */
    public void shutdown() {
        LOGGER.info("[DevMod] Shutting down QA session save executor...");
        // Force a final save bypassing rate limiting
        lastSaveTime = 0;
        saveSession();

        // Wait for pending saves (max 2 seconds)
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                LOGGER.warn("[DevMod] QA session save did not complete in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // === Getters ===

    public String getTesterName() { return testerName; }
    @Nullable
    public Instant getSessionStarted() { return sessionStarted; }
    public List<String> getCapturedLogs() { return Collections.unmodifiableList(capturedLogs); }
}
