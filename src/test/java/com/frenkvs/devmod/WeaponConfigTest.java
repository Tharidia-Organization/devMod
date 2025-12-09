package com.frenkvs.devmod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for weapon configuration system.
 * Tests load/save JSON, default stats, and persistence.
 */
public class WeaponConfigTest {

    /**
     * Mock WeaponStats matching the mod's WeaponStats class
     */
    static class MockWeaponStats {
        public float baseDamageBonus = 0f;
        public float headMult = 2.0f;
        public float bodyMult = 1.0f;
        public float armsMult = 0.75f;
        public float legsMult = 0.5f;
        public float armorPenetration = 0f;
        public float attackSpeed = 1.0f;
        public float range = 3.0f;

        public static MockWeaponStats defaultStats() {
            return new MockWeaponStats();
        }

        public static MockWeaponStats copy(MockWeaponStats other) {
            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = other.baseDamageBonus;
            stats.headMult = other.headMult;
            stats.bodyMult = other.bodyMult;
            stats.armsMult = other.armsMult;
            stats.legsMult = other.legsMult;
            stats.armorPenetration = other.armorPenetration;
            stats.attackSpeed = other.attackSpeed;
            stats.range = other.range;
            return stats;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MockWeaponStats other)) return false;
            return Float.compare(baseDamageBonus, other.baseDamageBonus) == 0 &&
                   Float.compare(headMult, other.headMult) == 0 &&
                   Float.compare(bodyMult, other.bodyMult) == 0 &&
                   Float.compare(armsMult, other.armsMult) == 0 &&
                   Float.compare(legsMult, other.legsMult) == 0 &&
                   Float.compare(armorPenetration, other.armorPenetration) == 0 &&
                   Float.compare(attackSpeed, other.attackSpeed) == 0 &&
                   Float.compare(range, other.range) == 0;
        }

        @Override
        public int hashCode() {
            return Float.hashCode(baseDamageBonus) ^ Float.hashCode(headMult);
        }
    }

    /**
     * Mock WeaponConfigManager simulating the mod's config system
     */
    static class MockWeaponConfigManager {
        private final Map<String, MockWeaponStats> weaponStats = new ConcurrentHashMap<>();
        private Path configPath;

        public void setConfigPath(Path path) {
            this.configPath = path;
        }

        public MockWeaponStats getStatsFor(String weaponId) {
            return weaponStats.computeIfAbsent(weaponId, k -> MockWeaponStats.defaultStats());
        }

        public void setStatsFor(String weaponId, MockWeaponStats stats) {
            weaponStats.put(weaponId, MockWeaponStats.copy(stats));
        }

        public boolean hasStats(String weaponId) {
            return weaponStats.containsKey(weaponId);
        }

        public void clearStats(String weaponId) {
            weaponStats.remove(weaponId);
        }

        public void clearAll() {
            weaponStats.clear();
        }

        public int getConfigCount() {
            return weaponStats.size();
        }

        /**
         * Serialize to simple JSON format
         */
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            boolean first = true;
            for (Map.Entry<String, MockWeaponStats> entry : weaponStats.entrySet()) {
                if (!first) sb.append(",\n");
                first = false;
                MockWeaponStats s = entry.getValue();
                sb.append("  \"").append(entry.getKey()).append("\": {\n");
                sb.append("    \"baseDamageBonus\": ").append(s.baseDamageBonus).append(",\n");
                sb.append("    \"headMult\": ").append(s.headMult).append(",\n");
                sb.append("    \"bodyMult\": ").append(s.bodyMult).append(",\n");
                sb.append("    \"armsMult\": ").append(s.armsMult).append(",\n");
                sb.append("    \"legsMult\": ").append(s.legsMult).append(",\n");
                sb.append("    \"armorPenetration\": ").append(s.armorPenetration).append(",\n");
                sb.append("    \"attackSpeed\": ").append(s.attackSpeed).append(",\n");
                sb.append("    \"range\": ").append(s.range).append("\n");
                sb.append("  }");
            }
            sb.append("\n}");
            return sb.toString();
        }

        /**
         * Parse simple JSON format
         */
        public void fromJson(String json) {
            weaponStats.clear();
            // Simple parser for our format
            String[] lines = json.split("\n");
            String currentWeapon = null;
            MockWeaponStats currentStats = null;

            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("\"") && line.contains("\": {")) {
                    // Weapon ID line
                    currentWeapon = line.substring(1, line.indexOf("\": {"));
                    currentStats = new MockWeaponStats();
                } else if (line.startsWith("}") && currentWeapon != null && currentStats != null) {
                    weaponStats.put(currentWeapon, currentStats);
                    if (line.equals("}")) {
                        currentWeapon = null;
                        currentStats = null;
                    }
                } else if (currentStats != null && line.contains(":")) {
                    String[] parts = line.split(":");
                    String key = parts[0].trim().replace("\"", "");
                    String value = parts[1].trim().replace(",", "");

                    switch (key) {
                        case "baseDamageBonus" -> currentStats.baseDamageBonus = Float.parseFloat(value);
                        case "headMult" -> currentStats.headMult = Float.parseFloat(value);
                        case "bodyMult" -> currentStats.bodyMult = Float.parseFloat(value);
                        case "armsMult" -> currentStats.armsMult = Float.parseFloat(value);
                        case "legsMult" -> currentStats.legsMult = Float.parseFloat(value);
                        case "armorPenetration" -> currentStats.armorPenetration = Float.parseFloat(value);
                        case "attackSpeed" -> currentStats.attackSpeed = Float.parseFloat(value);
                        case "range" -> currentStats.range = Float.parseFloat(value);
                    }
                }
            }
        }

        public void saveToFile() throws IOException {
            if (configPath != null) {
                Files.writeString(configPath, toJson());
            }
        }

        public void loadFromFile() throws IOException {
            if (configPath != null && Files.exists(configPath)) {
                String json = Files.readString(configPath);
                fromJson(json);
            }
        }
    }

    @TempDir
    Path tempDir;

    private MockWeaponConfigManager configManager;

    @BeforeEach
    void setUp() {
        configManager = new MockWeaponConfigManager();
    }

    // ============================================
    // Default Stats Tests
    // ============================================

    @Nested
    @DisplayName("Default Stats Tests")
    class DefaultStatsTests {

        @Test
        @DisplayName("New weapon should have default stats")
        void testNewWeaponDefaultStats() {
            MockWeaponStats stats = configManager.getStatsFor("minecraft:diamond_sword");

            assertEquals(0f, stats.baseDamageBonus);
            assertEquals(2.0f, stats.headMult);
            assertEquals(1.0f, stats.bodyMult);
            assertEquals(0.75f, stats.armsMult);
            assertEquals(0.5f, stats.legsMult);
            assertEquals(0f, stats.armorPenetration);
        }

        @Test
        @DisplayName("Default stats should be independent per weapon")
        void testIndependentDefaultStats() {
            MockWeaponStats sword = configManager.getStatsFor("minecraft:diamond_sword");
            MockWeaponStats axe = configManager.getStatsFor("minecraft:diamond_axe");

            // Modify sword
            sword.baseDamageBonus = 5f;

            // Axe should still have default
            MockWeaponStats axeCheck = configManager.getStatsFor("minecraft:diamond_axe");
            assertEquals(0f, axeCheck.baseDamageBonus);
        }

        @Test
        @DisplayName("Getting same weapon returns same stats object")
        void testSameWeaponSameStats() {
            MockWeaponStats first = configManager.getStatsFor("minecraft:diamond_sword");
            MockWeaponStats second = configManager.getStatsFor("minecraft:diamond_sword");

            assertSame(first, second);
        }
    }

    // ============================================
    // Stats Modification Tests
    // ============================================

    @Nested
    @DisplayName("Stats Modification Tests")
    class StatsModificationTests {

        @Test
        @DisplayName("Set stats should persist")
        void testSetStatsPersists() {
            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = 10f;
            stats.headMult = 3.0f;
            stats.armorPenetration = 0.5f;

            configManager.setStatsFor("minecraft:netherite_sword", stats);

            MockWeaponStats retrieved = configManager.getStatsFor("minecraft:netherite_sword");
            assertEquals(10f, retrieved.baseDamageBonus);
            assertEquals(3.0f, retrieved.headMult);
            assertEquals(0.5f, retrieved.armorPenetration);
        }

        @Test
        @DisplayName("Set stats should be copied not referenced")
        void testSetStatsCopied() {
            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = 10f;

            configManager.setStatsFor("minecraft:diamond_sword", stats);

            // Modify original
            stats.baseDamageBonus = 20f;

            // Should still be 10
            MockWeaponStats retrieved = configManager.getStatsFor("minecraft:diamond_sword");
            assertEquals(10f, retrieved.baseDamageBonus);
        }

        @Test
        @DisplayName("Clear stats should remove weapon config")
        void testClearStats() {
            configManager.getStatsFor("minecraft:diamond_sword"); // Create entry
            assertTrue(configManager.hasStats("minecraft:diamond_sword"));

            configManager.clearStats("minecraft:diamond_sword");
            assertFalse(configManager.hasStats("minecraft:diamond_sword"));
        }

        @Test
        @DisplayName("Clear all should remove all configs")
        void testClearAll() {
            configManager.getStatsFor("minecraft:diamond_sword");
            configManager.getStatsFor("minecraft:diamond_axe");
            configManager.getStatsFor("minecraft:iron_sword");

            assertEquals(3, configManager.getConfigCount());

            configManager.clearAll();

            assertEquals(0, configManager.getConfigCount());
        }
    }

    // ============================================
    // JSON Serialization Tests
    // ============================================

    @Nested
    @DisplayName("JSON Serialization Tests")
    class JsonSerializationTests {

        @Test
        @DisplayName("Empty config should serialize to empty object")
        void testEmptyConfigSerialization() {
            String json = configManager.toJson();
            // Trim whitespace and check for empty JSON object
            String trimmed = json.replaceAll("\\s+", "");
            assertEquals("{}", trimmed);
        }

        @Test
        @DisplayName("Single weapon should serialize correctly")
        void testSingleWeaponSerialization() {
            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = 5f;
            configManager.setStatsFor("minecraft:diamond_sword", stats);

            String json = configManager.toJson();

            assertTrue(json.contains("\"minecraft:diamond_sword\""));
            assertTrue(json.contains("\"baseDamageBonus\": 5.0"));
            assertTrue(json.contains("\"headMult\": 2.0"));
        }

        @Test
        @DisplayName("Multiple weapons should serialize correctly")
        void testMultipleWeaponsSerialization() {
            MockWeaponStats sword = new MockWeaponStats();
            sword.baseDamageBonus = 5f;
            configManager.setStatsFor("minecraft:diamond_sword", sword);

            MockWeaponStats axe = new MockWeaponStats();
            axe.baseDamageBonus = 8f;
            axe.headMult = 2.5f;
            configManager.setStatsFor("minecraft:diamond_axe", axe);

            String json = configManager.toJson();

            assertTrue(json.contains("\"minecraft:diamond_sword\""));
            assertTrue(json.contains("\"minecraft:diamond_axe\""));
        }

        @Test
        @DisplayName("Deserialization should restore stats")
        void testDeserialization() {
            String json = """
                {
                  "minecraft:diamond_sword": {
                    "baseDamageBonus": 5.0,
                    "headMult": 3.0,
                    "bodyMult": 1.5,
                    "armsMult": 1.0,
                    "legsMult": 0.75,
                    "armorPenetration": 0.25,
                    "attackSpeed": 1.2,
                    "range": 4.0
                  }
                }
                """;

            configManager.fromJson(json);

            MockWeaponStats stats = configManager.getStatsFor("minecraft:diamond_sword");
            assertEquals(5.0f, stats.baseDamageBonus, 0.01f);
            assertEquals(3.0f, stats.headMult, 0.01f);
            assertEquals(1.5f, stats.bodyMult, 0.01f);
            assertEquals(0.25f, stats.armorPenetration, 0.01f);
        }

        @Test
        @DisplayName("Round-trip serialization should preserve data")
        void testRoundTrip() {
            MockWeaponStats original = new MockWeaponStats();
            original.baseDamageBonus = 7.5f;
            original.headMult = 2.5f;
            original.armorPenetration = 0.33f;
            original.attackSpeed = 1.4f;
            original.range = 5.0f;

            configManager.setStatsFor("test:weapon", original);

            String json = configManager.toJson();
            configManager.clearAll();
            configManager.fromJson(json);

            MockWeaponStats restored = configManager.getStatsFor("test:weapon");
            assertEquals(original.baseDamageBonus, restored.baseDamageBonus, 0.01f);
            assertEquals(original.headMult, restored.headMult, 0.01f);
            assertEquals(original.armorPenetration, restored.armorPenetration, 0.01f);
        }
    }

    // ============================================
    // File Persistence Tests
    // ============================================

    @Nested
    @DisplayName("File Persistence Tests")
    class FilePersistenceTests {

        @Test
        @DisplayName("Save to file should create file")
        void testSaveCreatesFile() throws IOException {
            Path configFile = tempDir.resolve("weapons.json");
            configManager.setConfigPath(configFile);

            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = 10f;
            configManager.setStatsFor("minecraft:diamond_sword", stats);

            configManager.saveToFile();

            assertTrue(Files.exists(configFile));
        }

        @Test
        @DisplayName("Load from file should restore stats")
        void testLoadFromFile() throws IOException {
            Path configFile = tempDir.resolve("weapons.json");

            String json = """
                {
                  "minecraft:iron_sword": {
                    "baseDamageBonus": 3.0,
                    "headMult": 2.0,
                    "bodyMult": 1.0,
                    "armsMult": 0.75,
                    "legsMult": 0.5,
                    "armorPenetration": 0.1,
                    "attackSpeed": 1.0,
                    "range": 3.0
                  }
                }
                """;
            Files.writeString(configFile, json);

            configManager.setConfigPath(configFile);
            configManager.loadFromFile();

            assertTrue(configManager.hasStats("minecraft:iron_sword"));
            MockWeaponStats stats = configManager.getStatsFor("minecraft:iron_sword");
            assertEquals(3.0f, stats.baseDamageBonus, 0.01f);
        }

        @Test
        @DisplayName("Save and load round-trip")
        void testSaveLoadRoundTrip() throws IOException {
            Path configFile = tempDir.resolve("weapons.json");
            configManager.setConfigPath(configFile);

            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = 15f;
            stats.headMult = 3.5f;
            stats.armorPenetration = 0.6f;
            configManager.setStatsFor("modded:epic_sword", stats);

            configManager.saveToFile();

            // New manager, same file
            MockWeaponConfigManager newManager = new MockWeaponConfigManager();
            newManager.setConfigPath(configFile);
            newManager.loadFromFile();

            MockWeaponStats loaded = newManager.getStatsFor("modded:epic_sword");
            assertEquals(15f, loaded.baseDamageBonus, 0.01f);
            assertEquals(3.5f, loaded.headMult, 0.01f);
            assertEquals(0.6f, loaded.armorPenetration, 0.01f);
        }

        @Test
        @DisplayName("Load from non-existent file should not fail")
        void testLoadNonExistentFile() throws IOException {
            Path configFile = tempDir.resolve("nonexistent.json");
            configManager.setConfigPath(configFile);

            assertDoesNotThrow(() -> configManager.loadFromFile());
            assertEquals(0, configManager.getConfigCount());
        }

        @Test
        @DisplayName("Multiple saves should overwrite")
        void testMultipleSaves() throws IOException {
            Path configFile = tempDir.resolve("weapons.json");
            configManager.setConfigPath(configFile);

            // First save
            MockWeaponStats stats1 = new MockWeaponStats();
            stats1.baseDamageBonus = 5f;
            configManager.setStatsFor("minecraft:diamond_sword", stats1);
            configManager.saveToFile();

            // Second save with different value
            MockWeaponStats stats2 = new MockWeaponStats();
            stats2.baseDamageBonus = 20f;
            configManager.setStatsFor("minecraft:diamond_sword", stats2);
            configManager.saveToFile();

            // Load and verify latest
            MockWeaponConfigManager newManager = new MockWeaponConfigManager();
            newManager.setConfigPath(configFile);
            newManager.loadFromFile();

            MockWeaponStats loaded = newManager.getStatsFor("minecraft:diamond_sword");
            assertEquals(20f, loaded.baseDamageBonus, 0.01f);
        }
    }

    // ============================================
    // Weapon ID Tests
    // ============================================

    @Nested
    @DisplayName("Weapon ID Tests")
    class WeaponIdTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "minecraft:diamond_sword",
            "minecraft:netherite_sword",
            "modded:custom_weapon",
            "some_mod:super_sword_v2",
            "namespace:item_with_underscores"
        })
        @DisplayName("Various weapon IDs should work")
        void testVariousWeaponIds(String weaponId) {
            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = 10f;
            configManager.setStatsFor(weaponId, stats);

            assertTrue(configManager.hasStats(weaponId));
            assertEquals(10f, configManager.getStatsFor(weaponId).baseDamageBonus);
        }

        @Test
        @DisplayName("Case-sensitive weapon IDs")
        void testCaseSensitiveIds() {
            MockWeaponStats lower = new MockWeaponStats();
            lower.baseDamageBonus = 5f;
            configManager.setStatsFor("minecraft:sword", lower);

            MockWeaponStats upper = new MockWeaponStats();
            upper.baseDamageBonus = 10f;
            configManager.setStatsFor("MINECRAFT:SWORD", upper);

            assertEquals(5f, configManager.getStatsFor("minecraft:sword").baseDamageBonus);
            assertEquals(10f, configManager.getStatsFor("MINECRAFT:SWORD").baseDamageBonus);
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Extreme stat values")
        void testExtremeValues() {
            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = 1000f;
            stats.headMult = 100f;
            stats.armorPenetration = 1.0f;

            configManager.setStatsFor("op:weapon", stats);

            String json = configManager.toJson();
            configManager.clearAll();
            configManager.fromJson(json);

            MockWeaponStats loaded = configManager.getStatsFor("op:weapon");
            assertEquals(1000f, loaded.baseDamageBonus, 0.01f);
            assertEquals(100f, loaded.headMult, 0.01f);
        }

        @Test
        @DisplayName("Negative stat values")
        void testNegativeValues() {
            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = -5f;
            stats.headMult = -1f;

            configManager.setStatsFor("cursed:weapon", stats);

            MockWeaponStats loaded = configManager.getStatsFor("cursed:weapon");
            assertEquals(-5f, loaded.baseDamageBonus, 0.01f);
            assertEquals(-1f, loaded.headMult, 0.01f);
        }

        @Test
        @DisplayName("Zero multipliers")
        void testZeroMultipliers() {
            MockWeaponStats stats = new MockWeaponStats();
            stats.headMult = 0f;
            stats.bodyMult = 0f;

            configManager.setStatsFor("weak:weapon", stats);

            MockWeaponStats loaded = configManager.getStatsFor("weak:weapon");
            assertEquals(0f, loaded.headMult, 0.01f);
            assertEquals(0f, loaded.bodyMult, 0.01f);
        }

        @Test
        @DisplayName("Floating point precision")
        void testFloatingPointPrecision() {
            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = 0.123456789f;
            stats.headMult = 2.987654321f;

            configManager.setStatsFor("precise:weapon", stats);

            String json = configManager.toJson();
            configManager.clearAll();
            configManager.fromJson(json);

            MockWeaponStats loaded = configManager.getStatsFor("precise:weapon");
            assertEquals(0.123456789f, loaded.baseDamageBonus, 0.0001f);
            assertEquals(2.987654321f, loaded.headMult, 0.0001f);
        }

        @Test
        @DisplayName("Many weapons performance")
        void testManyWeapons() {
            // Add 100 weapons
            for (int i = 0; i < 100; i++) {
                MockWeaponStats stats = new MockWeaponStats();
                stats.baseDamageBonus = i;
                configManager.setStatsFor("test:weapon_" + i, stats);
            }

            assertEquals(100, configManager.getConfigCount());

            // Verify random access
            MockWeaponStats stats50 = configManager.getStatsFor("test:weapon_50");
            assertEquals(50f, stats50.baseDamageBonus, 0.01f);

            // Serialize and deserialize
            String json = configManager.toJson();
            configManager.clearAll();
            configManager.fromJson(json);

            assertEquals(100, configManager.getConfigCount());
        }
    }
}
