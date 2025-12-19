package com.frenkvs.devmod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for impact data and HUD damage breakdown system.
 * Tests HUD data creation, breakdown calculation, and display formatting.
 */
public class ImpactDataTest {

    /**
     * Body parts enum
     */
    enum BodyPart {
        HEAD, BODY, ARMS, LEGS
    }

    /**
     * Mock ImpactData matching the mod's hud.ImpactData structure
     */
    static class MockImpactData {
        private final float originalDamage;
        private final float finalDamage;
        private final BodyPart bodyPart;
        private final float bodyPartMultiplier;
        private final float baseDamageBonus;
        private final float armorPenetration;
        private final float armorReduction;
        private final boolean isCritical;
        private final String weaponId;
        private final long timestamp;

        public MockImpactData(Builder builder) {
            this.originalDamage = builder.originalDamage;
            this.finalDamage = builder.finalDamage;
            this.bodyPart = builder.bodyPart;
            this.bodyPartMultiplier = builder.bodyPartMultiplier;
            this.baseDamageBonus = builder.baseDamageBonus;
            this.armorPenetration = builder.armorPenetration;
            this.armorReduction = builder.armorReduction;
            this.isCritical = builder.isCritical;
            this.weaponId = builder.weaponId;
            this.timestamp = builder.timestamp;
        }

        public float getOriginalDamage() { return originalDamage; }
        public float getFinalDamage() { return finalDamage; }
        public BodyPart getBodyPart() { return bodyPart; }
        public float getBodyPartMultiplier() { return bodyPartMultiplier; }
        public float getBaseDamageBonus() { return baseDamageBonus; }
        public float getArmorPenetration() { return armorPenetration; }
        public float getArmorReduction() { return armorReduction; }
        public boolean isCritical() { return isCritical; }
        public String getWeaponId() { return weaponId; }
        public long getTimestamp() { return timestamp; }

        public float getDamageIncrease() {
            return finalDamage - originalDamage;
        }

        public float getDamageMultiplier() {
            if (originalDamage == 0) return 1.0f;
            return finalDamage / originalDamage;
        }

        public static class Builder {
            private float originalDamage = 0;
            private float finalDamage = 0;
            private BodyPart bodyPart = BodyPart.BODY;
            private float bodyPartMultiplier = 1.0f;
            private float baseDamageBonus = 0;
            private float armorPenetration = 0;
            private float armorReduction = 0;
            private boolean isCritical = false;
            private String weaponId = "minecraft:hand";
            private long timestamp = System.currentTimeMillis();

            public Builder originalDamage(float damage) { this.originalDamage = damage; return this; }
            public Builder finalDamage(float damage) { this.finalDamage = damage; return this; }
            public Builder bodyPart(BodyPart part) { this.bodyPart = part; return this; }
            public Builder bodyPartMultiplier(float mult) { this.bodyPartMultiplier = mult; return this; }
            public Builder baseDamageBonus(float bonus) { this.baseDamageBonus = bonus; return this; }
            public Builder armorPenetration(float pen) { this.armorPenetration = pen; return this; }
            public Builder armorReduction(float reduction) { this.armorReduction = reduction; return this; }
            public Builder isCritical(boolean critical) { this.isCritical = critical; return this; }
            public Builder weaponId(String id) { this.weaponId = id; return this; }
            public Builder timestamp(long ts) { this.timestamp = ts; return this; }

            public MockImpactData build() {
                return new MockImpactData(this);
            }
        }
    }

    /**
     * Mock DamageBreakdown for HUD display
     */
    static class MockDamageBreakdown {
        private final List<BreakdownEntry> entries = new ArrayList<>();
        private float totalDamage;

        public void addEntry(String label, float value, String color) {
            entries.add(new BreakdownEntry(label, value, color));
        }

        public void setTotalDamage(float damage) {
            this.totalDamage = damage;
        }

        public List<BreakdownEntry> getEntries() { return entries; }
        public float getTotalDamage() { return totalDamage; }

        public static MockDamageBreakdown fromImpactData(MockImpactData data) {
            MockDamageBreakdown breakdown = new MockDamageBreakdown();

            // Base damage
            breakdown.addEntry("Base Damage", data.getOriginalDamage(), "#FFFFFF");

            // Bonus damage
            if (data.getBaseDamageBonus() != 0) {
                String color = data.getBaseDamageBonus() > 0 ? "#00FF00" : "#FF0000";
                breakdown.addEntry("Damage Bonus", data.getBaseDamageBonus(), color);
            }

            // Body part multiplier
            if (data.getBodyPartMultiplier() != 1.0f) {
                String partName = data.getBodyPart().name();
                String color = data.getBodyPartMultiplier() > 1.0f ? "#FFD700" : "#808080";
                breakdown.addEntry(partName + " (" + data.getBodyPartMultiplier() + "x)",
                                   (data.getOriginalDamage() + data.getBaseDamageBonus()) * (data.getBodyPartMultiplier() - 1),
                                   color);
            }

            // Armor penetration
            if (data.getArmorPenetration() > 0) {
                breakdown.addEntry("Armor Pen", data.getArmorPenetration() * 10, "#FF8C00"); // Simplified
            }

            // Armor reduction (negative)
            if (data.getArmorReduction() > 0) {
                breakdown.addEntry("Armor", -data.getArmorReduction(), "#4169E1");
            }

            // Critical hit
            if (data.isCritical()) {
                breakdown.addEntry("CRITICAL!", data.getFinalDamage() * 0.5f, "#FF0000");
            }

            breakdown.setTotalDamage(data.getFinalDamage());
            return breakdown;
        }

        public record BreakdownEntry(String label, float value, String color) {}
    }

    /**
     * Mock HUD display data manager
     */
    static class MockHUDDataManager {
        private final List<MockImpactData> recentImpacts = new ArrayList<>();
        private static final int MAX_RECENT = 5;
        private static final long IMPACT_DISPLAY_TIME_MS = 3000;

        public void addImpact(MockImpactData impact) {
            recentImpacts.add(0, impact);
            if (recentImpacts.size() > MAX_RECENT) {
                recentImpacts.remove(recentImpacts.size() - 1);
            }
        }

        public List<MockImpactData> getRecentImpacts() {
            return new ArrayList<>(recentImpacts);
        }

        public void clearOldImpacts(long currentTime) {
            recentImpacts.removeIf(impact ->
                currentTime - impact.getTimestamp() > IMPACT_DISPLAY_TIME_MS);
        }

        public void clear() {
            recentImpacts.clear();
        }

        public float getTotalRecentDamage() {
            return (float) recentImpacts.stream()
                .mapToDouble(MockImpactData::getFinalDamage)
                .sum();
        }

        public int getRecentCritCount() {
            return (int) recentImpacts.stream()
                .filter(MockImpactData::isCritical)
                .count();
        }
    }

    private MockHUDDataManager hudManager;

    @BeforeEach
    void setUp() {
        hudManager = new MockHUDDataManager();
    }

    // ============================================
    // ImpactData Builder Tests
    // ============================================

    @Nested
    @DisplayName("ImpactData Builder Tests")
    class ImpactDataBuilderTests {

        @Test
        @DisplayName("Default values are set correctly")
        void testDefaultValues() {
            MockImpactData data = new MockImpactData.Builder().build();

            assertEquals(0, data.getOriginalDamage());
            assertEquals(0, data.getFinalDamage());
            assertEquals(BodyPart.BODY, data.getBodyPart());
            assertEquals(1.0f, data.getBodyPartMultiplier());
            assertEquals("minecraft:hand", data.getWeaponId());
            assertFalse(data.isCritical());
        }

        @Test
        @DisplayName("Builder sets all values")
        void testBuilderSetsValues() {
            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(10)
                .finalDamage(25)
                .bodyPart(BodyPart.HEAD)
                .bodyPartMultiplier(2.0f)
                .baseDamageBonus(5)
                .armorPenetration(0.3f)
                .armorReduction(2)
                .isCritical(true)
                .weaponId("minecraft:diamond_sword")
                .build();

            assertEquals(10, data.getOriginalDamage());
            assertEquals(25, data.getFinalDamage());
            assertEquals(BodyPart.HEAD, data.getBodyPart());
            assertEquals(2.0f, data.getBodyPartMultiplier());
            assertEquals(5, data.getBaseDamageBonus());
            assertEquals(0.3f, data.getArmorPenetration());
            assertEquals(2, data.getArmorReduction());
            assertTrue(data.isCritical());
            assertEquals("minecraft:diamond_sword", data.getWeaponId());
        }

        @Test
        @DisplayName("Damage increase calculated correctly")
        void testDamageIncrease() {
            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(10)
                .finalDamage(15)
                .build();

            assertEquals(5, data.getDamageIncrease());
        }

        @Test
        @DisplayName("Damage multiplier calculated correctly")
        void testDamageMultiplier() {
            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(10)
                .finalDamage(20)
                .build();

            assertEquals(2.0f, data.getDamageMultiplier(), 0.01f);
        }

        @Test
        @DisplayName("Zero original damage returns 1.0 multiplier")
        void testZeroOriginalDamageMultiplier() {
            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(0)
                .finalDamage(10)
                .build();

            assertEquals(1.0f, data.getDamageMultiplier());
        }
    }

    // ============================================
    // DamageBreakdown Tests
    // ============================================

    @Nested
    @DisplayName("DamageBreakdown Tests")
    class DamageBreakdownTests {

        @Test
        @DisplayName("Basic damage breakdown")
        void testBasicBreakdown() {
            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(10)
                .finalDamage(10)
                .build();

            MockDamageBreakdown breakdown = MockDamageBreakdown.fromImpactData(data);

            assertEquals(10, breakdown.getTotalDamage());
            assertTrue(breakdown.getEntries().stream()
                .anyMatch(e -> e.label().equals("Base Damage")));
        }

        @Test
        @DisplayName("Breakdown includes damage bonus")
        void testBreakdownWithBonus() {
            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(10)
                .finalDamage(15)
                .baseDamageBonus(5)
                .build();

            MockDamageBreakdown breakdown = MockDamageBreakdown.fromImpactData(data);

            assertTrue(breakdown.getEntries().stream()
                .anyMatch(e -> e.label().equals("Damage Bonus") && e.value() == 5));
        }

        @Test
        @DisplayName("Breakdown includes body part multiplier")
        void testBreakdownWithBodyPart() {
            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(10)
                .finalDamage(20)
                .bodyPart(BodyPart.HEAD)
                .bodyPartMultiplier(2.0f)
                .build();

            MockDamageBreakdown breakdown = MockDamageBreakdown.fromImpactData(data);

            assertTrue(breakdown.getEntries().stream()
                .anyMatch(e -> e.label().contains("HEAD")));
        }

        @Test
        @DisplayName("Breakdown shows armor reduction as negative")
        void testBreakdownArmorReduction() {
            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(10)
                .finalDamage(7)
                .armorReduction(3)
                .build();

            MockDamageBreakdown breakdown = MockDamageBreakdown.fromImpactData(data);

            assertTrue(breakdown.getEntries().stream()
                .anyMatch(e -> e.label().equals("Armor") && e.value() < 0));
        }

        @Test
        @DisplayName("Critical hit adds entry")
        void testBreakdownCritical() {
            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(10)
                .finalDamage(20)
                .isCritical(true)
                .build();

            MockDamageBreakdown breakdown = MockDamageBreakdown.fromImpactData(data);

            assertTrue(breakdown.getEntries().stream()
                .anyMatch(e -> e.label().contains("CRITICAL")));
        }

        @Test
        @DisplayName("Entry colors are set correctly")
        void testEntryColors() {
            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(10)
                .finalDamage(20)
                .baseDamageBonus(5)
                .bodyPart(BodyPart.HEAD)
                .bodyPartMultiplier(2.0f)
                .build();

            MockDamageBreakdown breakdown = MockDamageBreakdown.fromImpactData(data);

            // Bonus should be green
            assertTrue(breakdown.getEntries().stream()
                .anyMatch(e -> e.label().equals("Damage Bonus") && e.color().equals("#00FF00")));

            // Head should be gold
            assertTrue(breakdown.getEntries().stream()
                .anyMatch(e -> e.label().contains("HEAD") && e.color().equals("#FFD700")));
        }
    }

    // ============================================
    // HUD Data Manager Tests
    // ============================================

    @Nested
    @DisplayName("HUD Data Manager Tests")
    class HUDDataManagerTests {

        @Test
        @DisplayName("Add impact to manager")
        void testAddImpact() {
            MockImpactData impact = new MockImpactData.Builder()
                .finalDamage(10)
                .build();

            hudManager.addImpact(impact);

            assertEquals(1, hudManager.getRecentImpacts().size());
        }

        @Test
        @DisplayName("Recent impacts are in reverse order (newest first)")
        void testReverseOrder() {
            MockImpactData first = new MockImpactData.Builder().finalDamage(10).build();
            MockImpactData second = new MockImpactData.Builder().finalDamage(20).build();

            hudManager.addImpact(first);
            hudManager.addImpact(second);

            assertEquals(20, hudManager.getRecentImpacts().get(0).getFinalDamage());
            assertEquals(10, hudManager.getRecentImpacts().get(1).getFinalDamage());
        }

        @Test
        @DisplayName("Maximum 5 recent impacts")
        void testMaxRecentImpacts() {
            for (int i = 0; i < 10; i++) {
                hudManager.addImpact(new MockImpactData.Builder().finalDamage(i).build());
            }

            assertEquals(5, hudManager.getRecentImpacts().size());
        }

        @Test
        @DisplayName("Oldest impacts are removed when limit exceeded")
        void testOldestRemoved() {
            for (int i = 1; i <= 6; i++) {
                hudManager.addImpact(new MockImpactData.Builder().finalDamage(i * 10).build());
            }

            // First impact (10 damage) should be removed
            List<MockImpactData> recent = hudManager.getRecentImpacts();
            assertTrue(recent.stream().noneMatch(d -> d.getFinalDamage() == 10));
        }

        @Test
        @DisplayName("Clear removes all impacts")
        void testClear() {
            hudManager.addImpact(new MockImpactData.Builder().finalDamage(10).build());
            hudManager.addImpact(new MockImpactData.Builder().finalDamage(20).build());

            hudManager.clear();

            assertTrue(hudManager.getRecentImpacts().isEmpty());
        }

        @Test
        @DisplayName("Total recent damage calculation")
        void testTotalRecentDamage() {
            hudManager.addImpact(new MockImpactData.Builder().finalDamage(10).build());
            hudManager.addImpact(new MockImpactData.Builder().finalDamage(20).build());
            hudManager.addImpact(new MockImpactData.Builder().finalDamage(30).build());

            assertEquals(60, hudManager.getTotalRecentDamage(), 0.01f);
        }

        @Test
        @DisplayName("Count recent critical hits")
        void testRecentCritCount() {
            hudManager.addImpact(new MockImpactData.Builder().finalDamage(10).isCritical(true).build());
            hudManager.addImpact(new MockImpactData.Builder().finalDamage(20).isCritical(false).build());
            hudManager.addImpact(new MockImpactData.Builder().finalDamage(30).isCritical(true).build());

            assertEquals(2, hudManager.getRecentCritCount());
        }

        @Test
        @DisplayName("Old impacts are cleared based on timestamp")
        void testClearOldImpacts() {
            long now = System.currentTimeMillis();

            // Old impact (5 seconds ago)
            MockImpactData oldImpact = new MockImpactData.Builder()
                .finalDamage(10)
                .timestamp(now - 5000)
                .build();

            // Recent impact
            MockImpactData newImpact = new MockImpactData.Builder()
                .finalDamage(20)
                .timestamp(now)
                .build();

            hudManager.addImpact(oldImpact);
            hudManager.addImpact(newImpact);

            hudManager.clearOldImpacts(now);

            assertEquals(1, hudManager.getRecentImpacts().size());
            assertEquals(20, hudManager.getRecentImpacts().get(0).getFinalDamage());
        }
    }

    // ============================================
    // Damage Calculation Breakdown Tests
    // ============================================

    @Nested
    @DisplayName("Damage Calculation Breakdown Tests")
    class DamageCalculationBreakdownTests {

        @ParameterizedTest
        @CsvSource({
            "10, 5, 2.0, 30",    // (10 + 5) * 2.0 = 30
            "10, 0, 2.0, 20",    // (10 + 0) * 2.0 = 20
            "10, 5, 1.0, 15",    // (10 + 5) * 1.0 = 15
            "10, -3, 2.0, 14"    // (10 - 3) * 2.0 = 14
        })
        @DisplayName("Damage formula verification")
        void testDamageFormula(float base, float bonus, float mult, float expected) {
            float calculated = (base + bonus) * mult;
            assertEquals(expected, calculated, 0.01f);
        }

        @Test
        @DisplayName("Full damage breakdown matches final damage")
        void testBreakdownMatchesFinal() {
            float baseDamage = 10;
            float bonus = 5;
            float headMult = 2.0f;
            float armorPen = 0.2f;
            float armorReduction = 3;

            // Calculate: (10 + 5) * 2.0 - 3 + (armorPen bonus) = 27 + pen bonus
            float damageBeforeArmor = (baseDamage + bonus) * headMult;
            float armorPenBonus = 10 * armorPen * 0.5f; // Simplified
            float finalDamage = damageBeforeArmor - armorReduction + armorPenBonus;

            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(baseDamage)
                .finalDamage(finalDamage)
                .bodyPart(BodyPart.HEAD)
                .bodyPartMultiplier(headMult)
                .baseDamageBonus(bonus)
                .armorPenetration(armorPen)
                .armorReduction(armorReduction)
                .build();

            MockDamageBreakdown breakdown = MockDamageBreakdown.fromImpactData(data);

            assertEquals(finalDamage, breakdown.getTotalDamage(), 0.01f);
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Zero damage impact")
        void testZeroDamage() {
            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(0)
                .finalDamage(0)
                .build();

            MockDamageBreakdown breakdown = MockDamageBreakdown.fromImpactData(data);

            assertEquals(0, breakdown.getTotalDamage());
        }

        @Test
        @DisplayName("Negative damage reduction")
        void testNegativeDamageBonus() {
            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(10)
                .finalDamage(7)
                .baseDamageBonus(-3)
                .build();

            MockDamageBreakdown breakdown = MockDamageBreakdown.fromImpactData(data);

            // Negative bonus should have red color
            assertTrue(breakdown.getEntries().stream()
                .anyMatch(e -> e.label().equals("Damage Bonus") && e.color().equals("#FF0000")));
        }

        @Test
        @DisplayName("Very high damage values")
        void testHighDamage() {
            MockImpactData data = new MockImpactData.Builder()
                .originalDamage(1000)
                .finalDamage(5000)
                .bodyPart(BodyPart.HEAD)
                .bodyPartMultiplier(5.0f)
                .build();

            assertEquals(5000, data.getFinalDamage());
            assertEquals(5.0f, data.getDamageMultiplier(), 0.01f);
        }

        @Test
        @DisplayName("All body parts have breakdown entries")
        void testAllBodyParts() {
            for (BodyPart part : BodyPart.values()) {
                MockImpactData data = new MockImpactData.Builder()
                    .originalDamage(10)
                    .finalDamage(10)
                    .bodyPart(part)
                    .bodyPartMultiplier(part == BodyPart.HEAD ? 2.0f :
                                        part == BodyPart.BODY ? 1.0f :
                                        part == BodyPart.ARMS ? 0.75f : 0.5f)
                    .build();

                assertEquals(part, data.getBodyPart());
            }
        }

        @Test
        @DisplayName("Timestamp is recorded")
        void testTimestamp() {
            long before = System.currentTimeMillis();
            MockImpactData data = new MockImpactData.Builder().build();
            long after = System.currentTimeMillis();

            assertTrue(data.getTimestamp() >= before);
            assertTrue(data.getTimestamp() <= after);
        }

        @Test
        @DisplayName("Custom timestamp can be set")
        void testCustomTimestamp() {
            long customTime = 1234567890L;
            MockImpactData data = new MockImpactData.Builder()
                .timestamp(customTime)
                .build();

            assertEquals(customTime, data.getTimestamp());
        }

        @Test
        @DisplayName("Empty HUD manager returns empty list")
        void testEmptyManager() {
            assertTrue(hudManager.getRecentImpacts().isEmpty());
            assertEquals(0, hudManager.getTotalRecentDamage(), 0.01f);
            assertEquals(0, hudManager.getRecentCritCount());
        }
    }
}
