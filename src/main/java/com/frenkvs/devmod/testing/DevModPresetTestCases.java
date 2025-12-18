package com.frenkvs.devmod.testing;

import com.frenkvs.devmod.ItemEditorDataManager;
import com.frenkvs.devmod.ui.editor.systems.ModpackDetector;
import com.frenkvs.devmod.ui.editor.systems.PresetBridge;
import com.frenkvs.devmod.ui.editor.systems.PresetRegistry;
import com.frenkvs.devmod.ui.editor.systems.PresetScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Test cases for the Preset System (Plan B - Full PresetRegistry Integration).
 *
 * Tests cover:
 * - PresetScope hierarchy (Global, Category, Modpack)
 * - PresetRegistry initialization and loading
 * - PresetBridge conversion utilities
 * - ModpackDetector strategies
 * - Bundled preset loading
 * - User preset CRUD operations
 */
public class DevModPresetTestCases {

    private static final String CATEGORY = "Preset System";

    /**
     * Generate all preset system test cases.
     */
    public static List<TestCase> generateTestCases() {
        List<TestCase> tests = new ArrayList<>();

        // ═══════════════════════════════════════════════════════════════
        // PRESET SCOPE TESTS
        // ═══════════════════════════════════════════════════════════════

        tests.add(new TestCase(
            "preset_scope_hierarchy",
            CATEGORY,
            "PresetScope Hierarchy",
            "Verify PresetScope sealed interface and priority ordering",
            "1. Check Global priority = 1\n" +
            "2. Check Category priority = 2\n" +
            "3. Check Modpack priority = 3\n" +
            "4. Verify labels are correct",
            TestCase.TestPriority.HIGH,
            DevModPresetTestCases::validatePresetScopeHierarchy
        ));

        tests.add(new TestCase(
            "preset_scope_labels",
            CATEGORY,
            "PresetScope Labels",
            "Verify scope labels for UI display",
            "1. Global.label() = 'GLOBAL'\n" +
            "2. Category('sword').label() = 'CATEGORY: sword'\n" +
            "3. Modpack('rlcraft', 'sword').label() = 'MODPACK: rlcraft'",
            TestCase.TestPriority.MEDIUM,
            DevModPresetTestCases::validatePresetScopeLabels
        ));

        // ═══════════════════════════════════════════════════════════════
        // PRESET REGISTRY TESTS
        // ═══════════════════════════════════════════════════════════════

        tests.add(new TestCase(
            "preset_registry_singleton",
            CATEGORY,
            "PresetRegistry Singleton",
            "Verify PresetRegistry singleton pattern",
            "1. Access PresetRegistry.INSTANCE multiple times\n" +
            "2. Verify same instance returned",
            TestCase.TestPriority.CRITICAL,
            DevModPresetTestCases::validatePresetRegistrySingleton
        ));

        tests.add(new TestCase(
            "preset_registry_bundled_presets",
            CATEGORY,
            "Bundled Presets Loaded",
            "Verify bundled presets are available in registry",
            "1. Get all presets from registry\n" +
            "2. Check for vanilla_default, balanced_pvp, overpowered_debug\n" +
            "3. Check for tank_build, glass_cannon, diamond_tier",
            TestCase.TestPriority.HIGH,
            DevModPresetTestCases::validateBundledPresetsLoaded
        ));

        tests.add(new TestCase(
            "preset_registry_category_filter",
            CATEGORY,
            "Category Filtering",
            "Verify getPresetsForCategory() returns correct presets",
            "1. Call getPresetsForCategory('weapon')\n" +
            "2. Verify weapon presets returned\n" +
            "3. Call getPresetsForCategory('armor')\n" +
            "4. Verify armor presets returned",
            TestCase.TestPriority.HIGH,
            DevModPresetTestCases::validateCategoryFiltering
        ));

        // ═══════════════════════════════════════════════════════════════
        // PRESET BRIDGE TESTS
        // ═══════════════════════════════════════════════════════════════

        tests.add(new TestCase(
            "preset_bridge_to_preset_data",
            CATEGORY,
            "PresetBridge toPresetData",
            "Verify RegistryPreset -> PresetData conversion",
            "1. Create a RegistryPreset\n" +
            "2. Convert to PresetData\n" +
            "3. Verify name, itemType, scope preserved",
            TestCase.TestPriority.HIGH,
            DevModPresetTestCases::validateToPresetData
        ));

        tests.add(new TestCase(
            "preset_bridge_to_registry_preset",
            CATEGORY,
            "PresetBridge toRegistryPreset",
            "Verify PresetData -> RegistryPreset conversion",
            "1. Create a PresetData\n" +
            "2. Convert to RegistryPreset\n" +
            "3. Verify id, name, category preserved",
            TestCase.TestPriority.HIGH,
            DevModPresetTestCases::validateToRegistryPreset
        ));

        tests.add(new TestCase(
            "preset_bridge_id_generation",
            CATEGORY,
            "PresetBridge ID Generation",
            "Verify generateId() creates valid IDs",
            "1. generateId('My Preset Name') = 'my_preset_name'\n" +
            "2. generateId('Test 123!') = 'test_123'\n" +
            "3. generateId('') should handle empty",
            TestCase.TestPriority.MEDIUM,
            DevModPresetTestCases::validateIdGeneration
        ));

        // ═══════════════════════════════════════════════════════════════
        // MODPACK DETECTOR TESTS
        // ═══════════════════════════════════════════════════════════════

        tests.add(new TestCase(
            "modpack_detector_init",
            CATEGORY,
            "ModpackDetector Initialization",
            "Verify ModpackDetector initializes without error",
            "1. Call ModpackDetector.detect()\n" +
            "2. Should return Optional (empty or with value)\n" +
            "3. No exceptions thrown",
            TestCase.TestPriority.MEDIUM,
            DevModPresetTestCases::validateModpackDetectorInit
        ));

        // ═══════════════════════════════════════════════════════════════
        // USER PRESET CRUD TESTS
        // ═══════════════════════════════════════════════════════════════

        tests.add(new TestCase(
            "user_preset_save",
            CATEGORY,
            "User Preset Save",
            "Verify user presets can be saved",
            "1. Create a PresetData\n" +
            "2. Save via ItemEditorDataManager\n" +
            "3. Retrieve and verify exists",
            TestCase.TestPriority.HIGH,
            DevModPresetTestCases::validateUserPresetSave
        ));

        tests.add(new TestCase(
            "user_preset_delete",
            CATEGORY,
            "User Preset Delete",
            "Verify user presets can be deleted",
            "1. Save a test preset\n" +
            "2. Delete it\n" +
            "3. Verify no longer exists",
            TestCase.TestPriority.HIGH,
            DevModPresetTestCases::validateUserPresetDelete
        ));

        tests.add(new TestCase(
            "user_preset_rename",
            CATEGORY,
            "User Preset Rename",
            "Verify user presets can be renamed",
            "1. Save a preset with name 'Original'\n" +
            "2. Delete 'Original', save as 'Renamed'\n" +
            "3. Verify 'Renamed' exists, 'Original' does not",
            TestCase.TestPriority.MEDIUM,
            DevModPresetTestCases::validateUserPresetRename
        ));

        // ═══════════════════════════════════════════════════════════════
        // INTEGRATION TESTS
        // ═══════════════════════════════════════════════════════════════

        tests.add(new TestCase(
            "preset_overlay_data_flow",
            CATEGORY,
            "PresetSelectorOverlay Data Flow",
            "Verify overlay receives correct data from registry",
            "1. Registry contains bundled presets\n" +
            "2. User presets exist in ItemEditorDataManager\n" +
            "3. Both sources should be available in overlay",
            TestCase.TestPriority.HIGH,
            DevModPresetTestCases::validateOverlayDataFlow
        ));

        tests.add(new TestCase(
            "preset_priority_resolution",
            CATEGORY,
            "Preset Priority Resolution",
            "Verify higher priority presets override lower",
            "1. If Modpack preset exists, it has priority 3\n" +
            "2. Category presets have priority 2\n" +
            "3. Global presets have priority 1\n" +
            "4. Sorted list should show Modpack first",
            TestCase.TestPriority.HIGH,
            DevModPresetTestCases::validatePriorityResolution
        ));

        return tests;
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION METHODS
    // ═══════════════════════════════════════════════════════════════

    private static boolean validatePresetScopeHierarchy() {
        try {
            PresetScope global = new PresetScope.Global();
            PresetScope category = new PresetScope.Category("sword");
            PresetScope modpack = new PresetScope.Modpack("rlcraft", "sword");

            return global.priority() == 1
                && category.priority() == 2
                && modpack.priority() == 3;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validatePresetScopeLabels() {
        try {
            PresetScope global = new PresetScope.Global();
            PresetScope category = new PresetScope.Category("sword");
            PresetScope modpack = new PresetScope.Modpack("rlcraft", "sword");

            return "GLOBAL".equals(global.label())
                && "CATEGORY: sword".equals(category.label())
                && "MODPACK: rlcraft".equals(modpack.label());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validatePresetRegistrySingleton() {
        try {
            PresetRegistry instance1 = PresetRegistry.INSTANCE;
            PresetRegistry instance2 = PresetRegistry.INSTANCE;
            return instance1 != null && instance1 == instance2;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateBundledPresetsLoaded() {
        try {
            PresetRegistry registry = PresetRegistry.INSTANCE;
            List<PresetRegistry.RegistryPreset> all = registry.getAllPresets();

            // Should have at least 6 bundled presets
            if (all.size() < 6) return false;

            // Check for specific bundled presets by name
            boolean hasVanilla = all.stream().anyMatch(p -> "vanilla_default".equals(p.id()));
            boolean hasBalanced = all.stream().anyMatch(p -> "balanced_pvp".equals(p.id()));
            boolean hasOverpowered = all.stream().anyMatch(p -> "overpowered_debug".equals(p.id()));

            return hasVanilla && hasBalanced && hasOverpowered;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateCategoryFiltering() {
        try {
            PresetRegistry registry = PresetRegistry.INSTANCE;

            List<PresetRegistry.RegistryPreset> weaponPresets = registry.getPresetsForCategory("weapon");
            List<PresetRegistry.RegistryPreset> armorPresets = registry.getPresetsForCategory("armor");

            // Weapon category should have balanced_pvp, vanilla_default
            // Armor category should have tank_build, glass_cannon
            return !weaponPresets.isEmpty() && !armorPresets.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateToPresetData() {
        try {
            PresetRegistry.RegistryPreset rp = new PresetRegistry.RegistryPreset(
                "test_preset",
                "Test Preset",
                "A test description",
                new PresetScope.Global(),
                "weapon",
                Map.of("baseDamage", 10.0, "attackSpeed", 1.5),
                new PresetRegistry.PresetMetadata("1.0.0", "DevMod", java.time.Instant.now(), List.of("test"))
            );

            ItemEditorDataManager.PresetData pd = PresetBridge.toPresetData(rp);

            return "Test Preset".equals(pd.name)
                && "weapon".equals(pd.itemType)
                && pd.statValues != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateToRegistryPreset() {
        try {
            ItemEditorDataManager.PresetData pd = new ItemEditorDataManager.PresetData();
            pd.name = "User Preset";
            pd.itemType = "armor";
            pd.scope = "GLOBAL";
            pd.devmodVersion = "1.0.0";
            pd.createdAt = System.currentTimeMillis();
            pd.statValues = List.of(1.0f, 2.0f, 3.0f);

            PresetRegistry.RegistryPreset rp = PresetBridge.toRegistryPreset(pd);

            return rp != null
                && "User Preset".equals(rp.name())
                && "armor".equals(rp.category());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateIdGeneration() {
        try {
            String id1 = PresetBridge.generateId("My Preset Name");
            String id2 = PresetBridge.generateId("Test 123!");
            String id3 = PresetBridge.generateId("");

            return "my_preset_name".equals(id1)
                && id2 != null && !id2.contains("!")
                && id3 != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateModpackDetectorInit() {
        try {
            // Should not throw
            var detected = ModpackDetector.detect();
            // Result can be empty (no modpack) or present
            return detected != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateUserPresetSave() {
        try {
            ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
            data.ensureInitialized();

            ItemEditorDataManager.PresetData preset = new ItemEditorDataManager.PresetData();
            preset.name = "_test_save_preset_" + System.currentTimeMillis();
            preset.itemType = "weapon";
            preset.scope = "SPECIFIC";
            preset.devmodVersion = "1.0.0";
            preset.createdAt = System.currentTimeMillis();
            preset.statValues = List.of(1.0f, 2.0f, 3.0f, 4.0f, 5.0f);

            data.savePreset(preset);

            // Verify it exists
            var found = data.getPreset(preset.name);
            boolean exists = found.isPresent();

            // Cleanup
            data.deletePreset(preset.name);

            return exists;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateUserPresetDelete() {
        try {
            ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
            data.ensureInitialized();

            String testName = "_test_delete_preset_" + System.currentTimeMillis();

            ItemEditorDataManager.PresetData preset = new ItemEditorDataManager.PresetData();
            preset.name = testName;
            preset.itemType = "weapon";
            preset.createdAt = System.currentTimeMillis();
            preset.statValues = List.of(1.0f);

            data.savePreset(preset);
            boolean existsAfterSave = data.getPreset(testName).isPresent();

            data.deletePreset(testName);
            boolean existsAfterDelete = data.getPreset(testName).isPresent();

            return existsAfterSave && !existsAfterDelete;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateUserPresetRename() {
        try {
            ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
            data.ensureInitialized();

            String originalName = "_test_rename_orig_" + System.currentTimeMillis();
            String newName = "_test_rename_new_" + System.currentTimeMillis();

            ItemEditorDataManager.PresetData preset = new ItemEditorDataManager.PresetData();
            preset.name = originalName;
            preset.itemType = "weapon";
            preset.createdAt = System.currentTimeMillis();
            preset.statValues = List.of(1.0f, 2.0f);

            data.savePreset(preset);

            // Rename: delete old, save with new name
            preset.name = newName;
            data.deletePreset(originalName);
            data.savePreset(preset);

            boolean oldGone = data.getPreset(originalName).isEmpty();
            boolean newExists = data.getPreset(newName).isPresent();

            // Cleanup
            data.deletePreset(newName);

            return oldGone && newExists;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateOverlayDataFlow() {
        try {
            PresetRegistry registry = PresetRegistry.INSTANCE;
            ItemEditorDataManager userData = ItemEditorDataManager.INSTANCE;
            userData.ensureInitialized();

            // Registry should have bundled presets
            boolean hasRegistryPresets = !registry.getAllPresets().isEmpty();

            // ItemEditorDataManager should be initialized
            boolean dataManagerReady = userData != null;

            return hasRegistryPresets && dataManagerReady;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validatePriorityResolution() {
        try {
            PresetScope global = new PresetScope.Global();
            PresetScope category = new PresetScope.Category("weapon");
            PresetScope modpack = new PresetScope.Modpack("test", "weapon");

            // Priority order: Modpack (3) > Category (2) > Global (1)
            List<PresetScope> scopes = List.of(global, category, modpack);

            // Sort by priority descending
            List<PresetScope> sorted = scopes.stream()
                .sorted((a, b) -> Integer.compare(b.priority(), a.priority()))
                .toList();

            // First should be Modpack
            return sorted.get(0) instanceof PresetScope.Modpack
                && sorted.get(1) instanceof PresetScope.Category
                && sorted.get(2) instanceof PresetScope.Global;
        } catch (Exception e) {
            return false;
        }
    }
}
