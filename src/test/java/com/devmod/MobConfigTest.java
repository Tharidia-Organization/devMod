package com.devmod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for mob configuration system.
 * Tests mob stats load/save, attribute modifiers, and persistence.
 */
public class MobConfigTest {

    /**
     * Mock MobStats matching the mod's mob configuration
     */
    static class MockMobStats {
        public float maxHealth = 20.0f;
        public float attackDamage = 2.0f;
        public float armor = 0.0f;
        public float armorToughness = 0.0f;
        public float movementSpeed = 0.3f;
        public float knockbackResistance = 0.0f;
        public float followRange = 16.0f;
        public float attackKnockback = 0.0f;

        public static MockMobStats defaultStats() {
            return new MockMobStats();
        }

        public static MockMobStats copy(MockMobStats other) {
            MockMobStats stats = new MockMobStats();
            stats.maxHealth = other.maxHealth;
            stats.attackDamage = other.attackDamage;
            stats.armor = other.armor;
            stats.armorToughness = other.armorToughness;
            stats.movementSpeed = other.movementSpeed;
            stats.knockbackResistance = other.knockbackResistance;
            stats.followRange = other.followRange;
            stats.attackKnockback = other.attackKnockback;
            return stats;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MockMobStats other)) return false;
            return Float.compare(maxHealth, other.maxHealth) == 0 &&
                   Float.compare(attackDamage, other.attackDamage) == 0 &&
                   Float.compare(armor, other.armor) == 0 &&
                   Float.compare(armorToughness, other.armorToughness) == 0 &&
                   Float.compare(movementSpeed, other.movementSpeed) == 0 &&
                   Float.compare(knockbackResistance, other.knockbackResistance) == 0 &&
                   Float.compare(followRange, other.followRange) == 0 &&
                   Float.compare(attackKnockback, other.attackKnockback) == 0;
        }

        @Override
        public int hashCode() {
            int result = Float.hashCode(maxHealth);
            result = 31 * result + Float.hashCode(attackDamage);
            result = 31 * result + Float.hashCode(armor);
            result = 31 * result + Float.hashCode(armorToughness);
            result = 31 * result + Float.hashCode(movementSpeed);
            result = 31 * result + Float.hashCode(knockbackResistance);
            result = 31 * result + Float.hashCode(followRange);
            result = 31 * result + Float.hashCode(attackKnockback);
            return result;
        }
    }

    /**
     * Mock attribute modifier
     */
    static class MockAttributeModifier {
        public final String attributeName;
        public final float value;
        public final ModifierOperation operation;

        public enum ModifierOperation {
            ADD, MULTIPLY_BASE, MULTIPLY_TOTAL
        }

        public MockAttributeModifier(String attributeName, float value, ModifierOperation operation) {
            this.attributeName = attributeName;
            this.value = value;
            this.operation = operation;
        }

        public float apply(float base) {
            return switch (operation) {
                case ADD -> base + value;
                case MULTIPLY_BASE -> base * value;
                case MULTIPLY_TOTAL -> base * (1 + value);
            };
        }
    }

    /**
     * Mock MobConfigManager
     */
    static class MockMobConfigManager {
        private final Map<String, MockMobStats> mobStats = new ConcurrentHashMap<>();
        private Path configPath;

        public void setConfigPath(Path path) {
            this.configPath = path;
        }

        public MockMobStats getStatsFor(String mobId) {
            return mobStats.computeIfAbsent(mobId, k -> MockMobStats.defaultStats());
        }

        public void setStatsFor(String mobId, MockMobStats stats) {
            mobStats.put(mobId, MockMobStats.copy(stats));
        }

        public boolean hasStats(String mobId) {
            return mobStats.containsKey(mobId);
        }

        public void clearStats(String mobId) {
            mobStats.remove(mobId);
        }

        public void clearAll() {
            mobStats.clear();
        }

        public int getConfigCount() {
            return mobStats.size();
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            boolean first = true;
            for (Map.Entry<String, MockMobStats> entry : mobStats.entrySet()) {
                if (!first) sb.append(",\n");
                first = false;
                MockMobStats s = entry.getValue();
                sb.append("  \"").append(entry.getKey()).append("\": {\n");
                sb.append("    \"maxHealth\": ").append(s.maxHealth).append(",\n");
                sb.append("    \"attackDamage\": ").append(s.attackDamage).append(",\n");
                sb.append("    \"armor\": ").append(s.armor).append(",\n");
                sb.append("    \"armorToughness\": ").append(s.armorToughness).append(",\n");
                sb.append("    \"movementSpeed\": ").append(s.movementSpeed).append(",\n");
                sb.append("    \"knockbackResistance\": ").append(s.knockbackResistance).append(",\n");
                sb.append("    \"followRange\": ").append(s.followRange).append(",\n");
                sb.append("    \"attackKnockback\": ").append(s.attackKnockback).append("\n");
                sb.append("  }");
            }
            sb.append("\n}");
            return sb.toString();
        }

        public void fromJson(String json) {
            mobStats.clear();
            String[] lines = json.split("\n");
            String currentMob = null;
            MockMobStats currentStats = null;

            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("\"") && line.contains("\": {")) {
                    currentMob = line.substring(1, line.indexOf("\": {"));
                    currentStats = new MockMobStats();
                } else if (line.startsWith("}") && currentMob != null && currentStats != null) {
                    mobStats.put(currentMob, currentStats);
                    if (line.equals("}")) {
                        currentMob = null;
                        currentStats = null;
                    }
                } else if (currentStats != null && line.contains(":")) {
                    String[] parts = line.split(":");
                    String key = parts[0].trim().replace("\"", "");
                    String value = parts[1].trim().replace(",", "");

                    switch (key) {
                        case "maxHealth" -> currentStats.maxHealth = Float.parseFloat(value);
                        case "attackDamage" -> currentStats.attackDamage = Float.parseFloat(value);
                        case "armor" -> currentStats.armor = Float.parseFloat(value);
                        case "armorToughness" -> currentStats.armorToughness = Float.parseFloat(value);
                        case "movementSpeed" -> currentStats.movementSpeed = Float.parseFloat(value);
                        case "knockbackResistance" -> currentStats.knockbackResistance = Float.parseFloat(value);
                        case "followRange" -> currentStats.followRange = Float.parseFloat(value);
                        case "attackKnockback" -> currentStats.attackKnockback = Float.parseFloat(value);
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

    private MockMobConfigManager configManager;

    @BeforeEach
    void setUp() {
        configManager = new MockMobConfigManager();
    }

    // ============================================
    // Default Stats Tests
    // ============================================

    @Nested
    @DisplayName("Default Stats Tests")
    class DefaultStatsTests {

        @Test
        @DisplayName("New mob should have default stats")
        void testNewMobDefaultStats() {
            MockMobStats stats = configManager.getStatsFor("minecraft:zombie");

            assertEquals(20.0f, stats.maxHealth);
            assertEquals(2.0f, stats.attackDamage);
            assertEquals(0.0f, stats.armor);
            assertEquals(0.3f, stats.movementSpeed);
        }

        @Test
        @DisplayName("Default stats are independent per mob")
        void testIndependentDefaultStats() {
            MockMobStats zombie = configManager.getStatsFor("minecraft:zombie");
            zombie.maxHealth = 100;

            MockMobStats skeleton = configManager.getStatsFor("minecraft:skeleton");
            assertEquals(20.0f, skeleton.maxHealth);
        }

        @Test
        @DisplayName("Getting same mob returns same stats object")
        void testSameMobSameStats() {
            MockMobStats first = configManager.getStatsFor("minecraft:zombie");
            MockMobStats second = configManager.getStatsFor("minecraft:zombie");
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
            MockMobStats stats = new MockMobStats();
            stats.maxHealth = 100;
            stats.attackDamage = 20;
            stats.armor = 10;

            configManager.setStatsFor("minecraft:wither", stats);

            MockMobStats retrieved = configManager.getStatsFor("minecraft:wither");
            assertEquals(100, retrieved.maxHealth);
            assertEquals(20, retrieved.attackDamage);
            assertEquals(10, retrieved.armor);
        }

        @Test
        @DisplayName("Set stats should be copied not referenced")
        void testSetStatsCopied() {
            MockMobStats stats = new MockMobStats();
            stats.maxHealth = 100;

            configManager.setStatsFor("minecraft:zombie", stats);

            stats.maxHealth = 200;

            MockMobStats retrieved = configManager.getStatsFor("minecraft:zombie");
            assertEquals(100, retrieved.maxHealth);
        }

        @Test
        @DisplayName("Clear stats should remove mob config")
        void testClearStats() {
            configManager.getStatsFor("minecraft:zombie");
            assertTrue(configManager.hasStats("minecraft:zombie"));

            configManager.clearStats("minecraft:zombie");
            assertFalse(configManager.hasStats("minecraft:zombie"));
        }

        @Test
        @DisplayName("Clear all should remove all configs")
        void testClearAll() {
            configManager.getStatsFor("minecraft:zombie");
            configManager.getStatsFor("minecraft:skeleton");
            configManager.getStatsFor("minecraft:creeper");

            assertEquals(3, configManager.getConfigCount());

            configManager.clearAll();
            assertEquals(0, configManager.getConfigCount());
        }
    }

    // ============================================
    // Attribute Modifier Tests
    // ============================================

    @Nested
    @DisplayName("Attribute Modifier Tests")
    class AttributeModifierTests {

        @Test
        @DisplayName("ADD operation adds value")
        void testAddOperation() {
            MockAttributeModifier mod = new MockAttributeModifier(
                "maxHealth", 10, MockAttributeModifier.ModifierOperation.ADD
            );
            assertEquals(30, mod.apply(20), 0.01f);
        }

        @Test
        @DisplayName("MULTIPLY_BASE multiplies directly")
        void testMultiplyBaseOperation() {
            MockAttributeModifier mod = new MockAttributeModifier(
                "attackDamage", 2.0f, MockAttributeModifier.ModifierOperation.MULTIPLY_BASE
            );
            assertEquals(20, mod.apply(10), 0.01f);
        }

        @Test
        @DisplayName("MULTIPLY_TOTAL adds percentage")
        void testMultiplyTotalOperation() {
            MockAttributeModifier mod = new MockAttributeModifier(
                "armor", 0.5f, MockAttributeModifier.ModifierOperation.MULTIPLY_TOTAL
            );
            // 10 * (1 + 0.5) = 15
            assertEquals(15, mod.apply(10), 0.01f);
        }

        @ParameterizedTest
        @CsvSource({
            "20, 10, ADD, 30",
            "20, -5, ADD, 15",
            "10, 2, MULTIPLY_BASE, 20",
            "10, 0.5, MULTIPLY_TOTAL, 15",
            "100, -0.2, MULTIPLY_TOTAL, 80"
        })
        @DisplayName("Various modifier calculations")
        void testVariousModifiers(float base, float value,
                                   MockAttributeModifier.ModifierOperation op, float expected) {
            MockAttributeModifier mod = new MockAttributeModifier("test", value, op);
            assertEquals(expected, mod.apply(base), 0.01f);
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
            String trimmed = json.replaceAll("\\s+", "");
            assertEquals("{}", trimmed);
        }

        @Test
        @DisplayName("Single mob should serialize correctly")
        void testSingleMobSerialization() {
            MockMobStats stats = new MockMobStats();
            stats.maxHealth = 100;
            configManager.setStatsFor("minecraft:iron_golem", stats);

            String json = configManager.toJson();
            assertTrue(json.contains("\"minecraft:iron_golem\""));
            assertTrue(json.contains("\"maxHealth\": 100.0"));
        }

        @Test
        @DisplayName("Deserialization should restore stats")
        void testDeserialization() {
            String json = """
                {
                  "minecraft:zombie": {
                    "maxHealth": 40.0,
                    "attackDamage": 5.0,
                    "armor": 2.0,
                    "armorToughness": 0.0,
                    "movementSpeed": 0.23,
                    "knockbackResistance": 0.0,
                    "followRange": 35.0,
                    "attackKnockback": 1.0
                  }
                }
                """;

            configManager.fromJson(json);

            MockMobStats stats = configManager.getStatsFor("minecraft:zombie");
            assertEquals(40.0f, stats.maxHealth, 0.01f);
            assertEquals(5.0f, stats.attackDamage, 0.01f);
            assertEquals(35.0f, stats.followRange, 0.01f);
        }

        @Test
        @DisplayName("Round-trip serialization preserves data")
        void testRoundTrip() {
            MockMobStats original = new MockMobStats();
            original.maxHealth = 300;
            original.attackDamage = 25;
            original.armor = 15;
            original.movementSpeed = 0.4f;

            configManager.setStatsFor("minecraft:warden", original);

            String json = configManager.toJson();
            configManager.clearAll();
            configManager.fromJson(json);

            MockMobStats restored = configManager.getStatsFor("minecraft:warden");
            assertEquals(original.maxHealth, restored.maxHealth, 0.01f);
            assertEquals(original.attackDamage, restored.attackDamage, 0.01f);
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
            Path configFile = tempDir.resolve("mobs.json");
            configManager.setConfigPath(configFile);

            MockMobStats stats = new MockMobStats();
            stats.maxHealth = 50;
            configManager.setStatsFor("minecraft:spider", stats);

            configManager.saveToFile();

            assertTrue(Files.exists(configFile));
        }

        @Test
        @DisplayName("Load from file should restore stats")
        void testLoadFromFile() throws IOException {
            Path configFile = tempDir.resolve("mobs.json");

            String json = """
                {
                  "minecraft:enderman": {
                    "maxHealth": 40.0,
                    "attackDamage": 7.0,
                    "armor": 0.0,
                    "armorToughness": 0.0,
                    "movementSpeed": 0.3,
                    "knockbackResistance": 0.0,
                    "followRange": 64.0,
                    "attackKnockback": 0.0
                  }
                }
                """;
            Files.writeString(configFile, json);

            configManager.setConfigPath(configFile);
            configManager.loadFromFile();

            assertTrue(configManager.hasStats("minecraft:enderman"));
            assertEquals(40.0f, configManager.getStatsFor("minecraft:enderman").maxHealth, 0.01f);
        }

        @Test
        @DisplayName("Save and load round-trip")
        void testSaveLoadRoundTrip() throws IOException {
            Path configFile = tempDir.resolve("mobs.json");
            configManager.setConfigPath(configFile);

            MockMobStats stats = new MockMobStats();
            stats.maxHealth = 500;
            stats.attackDamage = 50;
            stats.knockbackResistance = 1.0f;
            configManager.setStatsFor("minecraft:ender_dragon", stats);

            configManager.saveToFile();

            MockMobConfigManager newManager = new MockMobConfigManager();
            newManager.setConfigPath(configFile);
            newManager.loadFromFile();

            MockMobStats loaded = newManager.getStatsFor("minecraft:ender_dragon");
            assertEquals(500, loaded.maxHealth, 0.01f);
            assertEquals(50, loaded.attackDamage, 0.01f);
            assertEquals(1.0f, loaded.knockbackResistance, 0.01f);
        }

        @Test
        @DisplayName("Load from non-existent file should not fail")
        void testLoadNonExistentFile() throws IOException {
            Path configFile = tempDir.resolve("nonexistent.json");
            configManager.setConfigPath(configFile);

            assertDoesNotThrow(() -> configManager.loadFromFile());
            assertEquals(0, configManager.getConfigCount());
        }
    }

    // ============================================
    // Mob ID Tests
    // ============================================

    @Nested
    @DisplayName("Mob ID Tests")
    class MobIdTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "minecraft:zombie",
            "minecraft:skeleton",
            "minecraft:creeper",
            "modded:custom_mob",
            "some_mod:boss_entity"
        })
        @DisplayName("Various mob IDs should work")
        void testVariousMobIds(String mobId) {
            MockMobStats stats = new MockMobStats();
            stats.maxHealth = 100;
            configManager.setStatsFor(mobId, stats);

            assertTrue(configManager.hasStats(mobId));
            assertEquals(100, configManager.getStatsFor(mobId).maxHealth);
        }

        @Test
        @DisplayName("Case-sensitive mob IDs")
        void testCaseSensitiveIds() {
            MockMobStats lower = new MockMobStats();
            lower.maxHealth = 50;
            configManager.setStatsFor("minecraft:zombie", lower);

            MockMobStats upper = new MockMobStats();
            upper.maxHealth = 100;
            configManager.setStatsFor("MINECRAFT:ZOMBIE", upper);

            assertEquals(50, configManager.getStatsFor("minecraft:zombie").maxHealth);
            assertEquals(100, configManager.getStatsFor("MINECRAFT:ZOMBIE").maxHealth);
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
            MockMobStats stats = new MockMobStats();
            stats.maxHealth = 10000;
            stats.attackDamage = 1000;
            stats.knockbackResistance = 1.0f;

            configManager.setStatsFor("op:boss", stats);

            String json = configManager.toJson();
            configManager.clearAll();
            configManager.fromJson(json);

            MockMobStats loaded = configManager.getStatsFor("op:boss");
            assertEquals(10000, loaded.maxHealth, 0.01f);
        }

        @Test
        @DisplayName("Zero stat values")
        void testZeroValues() {
            MockMobStats stats = new MockMobStats();
            stats.maxHealth = 0;
            stats.attackDamage = 0;
            stats.movementSpeed = 0;

            configManager.setStatsFor("weak:mob", stats);

            MockMobStats loaded = configManager.getStatsFor("weak:mob");
            assertEquals(0, loaded.maxHealth, 0.01f);
            assertEquals(0, loaded.attackDamage, 0.01f);
        }

        @Test
        @DisplayName("Negative stat values")
        void testNegativeValues() {
            MockMobStats stats = new MockMobStats();
            stats.attackDamage = -5; // Healing attack?

            configManager.setStatsFor("healer:mob", stats);

            MockMobStats loaded = configManager.getStatsFor("healer:mob");
            assertEquals(-5, loaded.attackDamage, 0.01f);
        }

        @Test
        @DisplayName("Floating point precision")
        void testFloatingPointPrecision() {
            MockMobStats stats = new MockMobStats();
            stats.movementSpeed = 0.23456789f;
            stats.knockbackResistance = 0.12345678f;

            configManager.setStatsFor("precise:mob", stats);

            String json = configManager.toJson();
            configManager.clearAll();
            configManager.fromJson(json);

            MockMobStats loaded = configManager.getStatsFor("precise:mob");
            assertEquals(0.23456789f, loaded.movementSpeed, 0.0001f);
        }

        @Test
        @DisplayName("Many mobs performance")
        void testManyMobs() {
            for (int i = 0; i < 100; i++) {
                MockMobStats stats = new MockMobStats();
                stats.maxHealth = i * 10;
                configManager.setStatsFor("test:mob_" + i, stats);
            }

            assertEquals(100, configManager.getConfigCount());

            MockMobStats stats50 = configManager.getStatsFor("test:mob_50");
            assertEquals(500, stats50.maxHealth, 0.01f);
        }

        @Test
        @DisplayName("All vanilla attributes")
        void testAllAttributes() {
            MockMobStats stats = new MockMobStats();
            stats.maxHealth = 100;
            stats.attackDamage = 10;
            stats.armor = 5;
            stats.armorToughness = 2;
            stats.movementSpeed = 0.35f;
            stats.knockbackResistance = 0.5f;
            stats.followRange = 48;
            stats.attackKnockback = 1.5f;

            configManager.setStatsFor("test:full_mob", stats);

            MockMobStats loaded = configManager.getStatsFor("test:full_mob");
            assertEquals(100, loaded.maxHealth, 0.01f);
            assertEquals(10, loaded.attackDamage, 0.01f);
            assertEquals(5, loaded.armor, 0.01f);
            assertEquals(2, loaded.armorToughness, 0.01f);
            assertEquals(0.35f, loaded.movementSpeed, 0.01f);
            assertEquals(0.5f, loaded.knockbackResistance, 0.01f);
            assertEquals(48, loaded.followRange, 0.01f);
            assertEquals(1.5f, loaded.attackKnockback, 0.01f);
        }
    }
}
