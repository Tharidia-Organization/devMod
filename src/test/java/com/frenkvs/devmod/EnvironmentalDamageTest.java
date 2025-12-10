package com.frenkvs.devmod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for environmental damage system.
 * Tests fire, fall, lava, void, and other environmental damage types.
 */
public class EnvironmentalDamageTest {

    /**
     * Environmental damage types matching DamageHandler categories
     */
    enum EnvironmentalDamageType {
        FIRE("fire", true),
        LAVA("lava", true),
        FALL("fall", false),
        VOID("void", false),
        DROWNING("drowning", false),
        FREEZING("freezing", false),
        LIGHTNING("lightning", false),
        CACTUS("cactus", false),
        CRAMMING("cramming", false),
        STARVING("starving", false),
        WITHER("wither", true),
        MAGIC("magic", true),
        DRAGON_BREATH("dragon_breath", true),
        HOT_FLOOR("hot_floor", true),
        FLY_INTO_WALL("fly_into_wall", false),
        IN_WALL("in_wall", false),
        FELL_OUT_OF_WORLD("fell_out_of_world", false);

        private final String id;
        private final boolean bypassesArmor;

        EnvironmentalDamageType(String id, boolean bypassesArmor) {
            this.id = id;
            this.bypassesArmor = bypassesArmor;
        }

        public String getId() { return id; }
        public boolean bypassesArmor() { return bypassesArmor; }
    }

    /**
     * Mock damage source for environmental damage
     */
    static class MockEnvironmentalDamageSource {
        private final EnvironmentalDamageType type;
        private final float baseDamage;

        public MockEnvironmentalDamageSource(EnvironmentalDamageType type, float baseDamage) {
            this.type = type;
            this.baseDamage = baseDamage;
        }

        public EnvironmentalDamageType getType() { return type; }
        public float getBaseDamage() { return baseDamage; }
        public boolean bypassesArmor() { return type.bypassesArmor(); }
    }

    /**
     * Mock entity with health and armor
     */
    static class MockEntity {
        private float health;
        private float maxHealth;
        private float armor;
        private float armorToughness;
        private boolean fireResistant;
        private boolean inWater;
        private boolean onGround;

        public MockEntity(float maxHealth, float armor) {
            this.maxHealth = maxHealth;
            this.health = maxHealth;
            this.armor = armor;
            this.armorToughness = 0;
            this.fireResistant = false;
            this.inWater = false;
            this.onGround = true;
        }

        public void setFireResistant(boolean resistant) { this.fireResistant = resistant; }
        public void setInWater(boolean inWater) { this.inWater = inWater; }
        public void setOnGround(boolean onGround) { this.onGround = onGround; }
        public void setArmorToughness(float toughness) { this.armorToughness = toughness; }

        public float getHealth() { return health; }
        public float getMaxHealth() { return maxHealth; }
        public float getArmor() { return armor; }
        public float getArmorToughness() { return armorToughness; }
        public boolean isFireResistant() { return fireResistant; }
        public boolean isInWater() { return inWater; }
        public boolean isOnGround() { return onGround; }
        public boolean isAlive() { return health > 0; }

        public void damage(float amount) {
            health = Math.max(0, health - amount);
        }

        public void heal(float amount) {
            health = Math.min(maxHealth, health + amount);
        }
    }

    /**
     * Environmental damage calculator simulating DamageHandler logic
     */
    static class EnvironmentalDamageCalculator {

        /**
         * Calculate armor reduction (Minecraft formula approximation)
         */
        public float calculateArmorReduction(float damage, float armor, float toughness) {
            // Simplified Minecraft armor formula
            float defensePoints = armor;
            float reduction = defensePoints / (defensePoints + 8.0f);
            return damage * (1 - reduction);
        }

        /**
         * Calculate fall damage based on fall distance
         */
        public float calculateFallDamage(float fallDistance) {
            // Fall damage = fallDistance - 3 (immune to first 3 blocks)
            if (fallDistance <= 3.0f) return 0;
            return fallDistance - 3.0f;
        }

        /**
         * Apply environmental damage to entity
         */
        public float applyEnvironmentalDamage(MockEntity entity, MockEnvironmentalDamageSource source) {
            float damage = source.getBaseDamage();

            // Check immunities
            if (source.getType() == EnvironmentalDamageType.FIRE ||
                source.getType() == EnvironmentalDamageType.LAVA ||
                source.getType() == EnvironmentalDamageType.HOT_FLOOR) {
                if (entity.isFireResistant()) {
                    return 0;
                }
            }

            // Fire is extinguished in water
            if (source.getType() == EnvironmentalDamageType.FIRE && entity.isInWater()) {
                return 0;
            }

            // Apply armor reduction if damage doesn't bypass armor
            if (!source.bypassesArmor()) {
                damage = calculateArmorReduction(damage, entity.getArmor(), entity.getArmorToughness());
            }

            // Void damage is always lethal
            if (source.getType() == EnvironmentalDamageType.FELL_OUT_OF_WORLD) {
                damage = Math.max(damage, entity.getHealth());
            }

            entity.damage(damage);
            return damage;
        }

        /**
         * Check if entity would survive a fall from given height
         */
        public boolean wouldSurviveFall(MockEntity entity, float fallDistance) {
            float damage = calculateFallDamage(fallDistance);
            if (!entity.isOnGround()) return true; // Still falling
            damage = calculateArmorReduction(damage, entity.getArmor(), entity.getArmorToughness());
            return entity.getHealth() > damage;
        }
    }

    private EnvironmentalDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new EnvironmentalDamageCalculator();
    }

    // ============================================
    // Fall Damage Tests
    // ============================================

    @Nested
    @DisplayName("Fall Damage Tests")
    class FallDamageTests {

        @Test
        @DisplayName("No damage for falls under 3 blocks")
        void testNoDamageUnder3Blocks() {
            assertEquals(0, calculator.calculateFallDamage(0));
            assertEquals(0, calculator.calculateFallDamage(1));
            assertEquals(0, calculator.calculateFallDamage(2));
            assertEquals(0, calculator.calculateFallDamage(3));
        }

        @Test
        @DisplayName("Damage starts at 4 blocks")
        void testDamageStartsAt4Blocks() {
            assertEquals(1, calculator.calculateFallDamage(4), 0.01f);
        }

        @ParameterizedTest
        @CsvSource({
            "4, 1",
            "5, 2",
            "10, 7",
            "20, 17",
            "50, 47"
        })
        @DisplayName("Fall damage scales with distance")
        void testFallDamageScaling(float distance, float expectedDamage) {
            assertEquals(expectedDamage, calculator.calculateFallDamage(distance), 0.01f);
        }

        @Test
        @DisplayName("Fall damage is reduced by armor")
        void testFallDamageArmorReduction() {
            MockEntity armoredEntity = new MockEntity(20, 10);
            MockEntity nakedEntity = new MockEntity(20, 0);

            float fallDamage = calculator.calculateFallDamage(10); // 7 base damage

            float armoredDamage = calculator.calculateArmorReduction(fallDamage, 10, 0);
            float nakedDamage = calculator.calculateArmorReduction(fallDamage, 0, 0);

            assertTrue(armoredDamage < nakedDamage);
            assertTrue(calculator.wouldSurviveFall(armoredEntity, 10));
            assertFalse(calculator.wouldSurviveFall(nakedEntity, 50));
        }

        @Test
        @DisplayName("Entity survives small fall")
        void testEntitySurvivesSmallFall() {
            MockEntity entity = new MockEntity(20, 5);
            assertTrue(calculator.wouldSurviveFall(entity, 10)); // 7 damage reduced by armor
        }

        @Test
        @DisplayName("Entity dies from large fall")
        void testEntityDiesFromLargeFall() {
            MockEntity entity = new MockEntity(20, 0);
            assertFalse(calculator.wouldSurviveFall(entity, 50)); // 47 damage > 20 health
        }
    }

    // ============================================
    // Fire Damage Tests
    // ============================================

    @Nested
    @DisplayName("Fire Damage Tests")
    class FireDamageTests {

        @Test
        @DisplayName("Fire damage bypasses armor")
        void testFireBypassesArmor() {
            MockEntity entity = new MockEntity(20, 20); // Full diamond armor

            MockEnvironmentalDamageSource fire = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.FIRE, 1.0f
            );

            float damage = calculator.applyEnvironmentalDamage(entity, fire);
            assertEquals(1.0f, damage, 0.01f); // Full damage despite armor
        }

        @Test
        @DisplayName("Fire resistant entities take no fire damage")
        void testFireResistanceImmunity() {
            MockEntity entity = new MockEntity(20, 0);
            entity.setFireResistant(true);

            MockEnvironmentalDamageSource fire = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.FIRE, 5.0f
            );

            float damage = calculator.applyEnvironmentalDamage(entity, fire);
            assertEquals(0, damage, 0.01f);
            assertEquals(20, entity.getHealth(), 0.01f);
        }

        @Test
        @DisplayName("Fire extinguished in water")
        void testFireExtinguishedInWater() {
            MockEntity entity = new MockEntity(20, 0);
            entity.setInWater(true);

            MockEnvironmentalDamageSource fire = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.FIRE, 5.0f
            );

            float damage = calculator.applyEnvironmentalDamage(entity, fire);
            assertEquals(0, damage, 0.01f);
        }
    }

    // ============================================
    // Lava Damage Tests
    // ============================================

    @Nested
    @DisplayName("Lava Damage Tests")
    class LavaDamageTests {

        @Test
        @DisplayName("Lava damage is higher than fire")
        void testLavaHigherThanFire() {
            // Lava typically does 4 damage per tick vs fire's 1
            MockEnvironmentalDamageSource lava = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.LAVA, 4.0f
            );
            MockEnvironmentalDamageSource fire = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.FIRE, 1.0f
            );

            assertTrue(lava.getBaseDamage() > fire.getBaseDamage());
        }

        @Test
        @DisplayName("Lava bypasses armor")
        void testLavaBypassesArmor() {
            MockEntity entity = new MockEntity(20, 20);

            MockEnvironmentalDamageSource lava = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.LAVA, 4.0f
            );

            float damage = calculator.applyEnvironmentalDamage(entity, lava);
            assertEquals(4.0f, damage, 0.01f);
        }

        @Test
        @DisplayName("Fire resistant entities immune to lava")
        void testFireResistanceBlocksLava() {
            MockEntity entity = new MockEntity(20, 0);
            entity.setFireResistant(true);

            MockEnvironmentalDamageSource lava = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.LAVA, 4.0f
            );

            float damage = calculator.applyEnvironmentalDamage(entity, lava);
            assertEquals(0, damage, 0.01f);
        }
    }

    // ============================================
    // Void Damage Tests
    // ============================================

    @Nested
    @DisplayName("Void Damage Tests")
    class VoidDamageTests {

        @Test
        @DisplayName("Void damage kills instantly")
        void testVoidDamageKills() {
            MockEntity entity = new MockEntity(1000, 100); // Even very tanky entity

            MockEnvironmentalDamageSource voidDamage = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.FELL_OUT_OF_WORLD, 4.0f
            );

            calculator.applyEnvironmentalDamage(entity, voidDamage);
            assertFalse(entity.isAlive());
        }

        @Test
        @DisplayName("Void damage ignores health amount")
        void testVoidIgnoresHealth() {
            MockEntity entity = new MockEntity(20, 0);
            entity.heal(-10); // 10 health

            MockEnvironmentalDamageSource voidDamage = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.FELL_OUT_OF_WORLD, 4.0f
            );

            calculator.applyEnvironmentalDamage(entity, voidDamage);
            assertEquals(0, entity.getHealth(), 0.01f);
        }
    }

    // ============================================
    // Drowning Damage Tests
    // ============================================

    @Nested
    @DisplayName("Drowning Damage Tests")
    class DrowningDamageTests {

        @Test
        @DisplayName("Drowning is reduced by armor")
        void testDrowningReducedByArmor() {
            MockEntity armoredEntity = new MockEntity(20, 10);
            MockEntity nakedEntity = new MockEntity(20, 0);

            MockEnvironmentalDamageSource drowning = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.DROWNING, 2.0f
            );

            float armoredDamage = calculator.applyEnvironmentalDamage(armoredEntity, drowning);

            // Reset
            nakedEntity = new MockEntity(20, 0);
            MockEnvironmentalDamageSource drowning2 = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.DROWNING, 2.0f
            );
            float nakedDamage = calculator.applyEnvironmentalDamage(nakedEntity, drowning2);

            assertTrue(armoredDamage < nakedDamage);
        }
    }

    // ============================================
    // Armor Reduction Tests
    // ============================================

    @Nested
    @DisplayName("Armor Reduction Tests")
    class ArmorReductionTests {

        @Test
        @DisplayName("No armor means full damage")
        void testNoArmorFullDamage() {
            float damage = calculator.calculateArmorReduction(10, 0, 0);
            assertEquals(10, damage, 0.01f);
        }

        @Test
        @DisplayName("Armor reduces damage")
        void testArmorReducesDamage() {
            float damage = calculator.calculateArmorReduction(10, 10, 0);
            assertTrue(damage < 10);
            assertTrue(damage > 0);
        }

        @ParameterizedTest
        @CsvSource({
            "10, 0, 10.0",
            "10, 5, 6.15",
            "10, 10, 4.44",
            "10, 20, 2.86"
        })
        @DisplayName("Armor reduction scales with armor value")
        void testArmorReductionScaling(float baseDamage, float armor, float expectedDamage) {
            float damage = calculator.calculateArmorReduction(baseDamage, armor, 0);
            assertEquals(expectedDamage, damage, 0.1f);
        }

        @Test
        @DisplayName("Armor can't reduce damage to zero")
        void testArmorCantReduceToZero() {
            float damage = calculator.calculateArmorReduction(10, 100, 0);
            assertTrue(damage > 0);
        }
    }

    // ============================================
    // Damage Type Properties Tests
    // ============================================

    @Nested
    @DisplayName("Damage Type Properties Tests")
    class DamageTypePropertiesTests {

        @ParameterizedTest
        @EnumSource(EnvironmentalDamageType.class)
        @DisplayName("All damage types have an ID")
        void testAllTypesHaveId(EnvironmentalDamageType type) {
            assertNotNull(type.getId());
            assertFalse(type.getId().isEmpty());
        }

        @Test
        @DisplayName("Fire-type damages bypass armor")
        void testFireTypesBypassArmor() {
            assertTrue(EnvironmentalDamageType.FIRE.bypassesArmor());
            assertTrue(EnvironmentalDamageType.LAVA.bypassesArmor());
            assertTrue(EnvironmentalDamageType.HOT_FLOOR.bypassesArmor());
        }

        @Test
        @DisplayName("Physical damages don't bypass armor")
        void testPhysicalDontBypassArmor() {
            assertFalse(EnvironmentalDamageType.FALL.bypassesArmor());
            assertFalse(EnvironmentalDamageType.CACTUS.bypassesArmor());
            assertFalse(EnvironmentalDamageType.CRAMMING.bypassesArmor());
        }

        @Test
        @DisplayName("Magic damage bypasses armor")
        void testMagicBypassesArmor() {
            assertTrue(EnvironmentalDamageType.MAGIC.bypassesArmor());
            assertTrue(EnvironmentalDamageType.WITHER.bypassesArmor());
            assertTrue(EnvironmentalDamageType.DRAGON_BREATH.bypassesArmor());
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Zero damage input")
        void testZeroDamage() {
            MockEntity entity = new MockEntity(20, 10);

            MockEnvironmentalDamageSource source = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.FIRE, 0f
            );

            float damage = calculator.applyEnvironmentalDamage(entity, source);
            assertEquals(0, damage, 0.01f);
            assertEquals(20, entity.getHealth(), 0.01f);
        }

        @Test
        @DisplayName("Very high damage")
        void testVeryHighDamage() {
            MockEntity entity = new MockEntity(20, 10);

            MockEnvironmentalDamageSource source = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.MAGIC, 1000f
            );

            calculator.applyEnvironmentalDamage(entity, source);
            assertEquals(0, entity.getHealth(), 0.01f);
            assertFalse(entity.isAlive());
        }

        @Test
        @DisplayName("Damage doesn't go negative")
        void testDamageNotNegative() {
            MockEntity entity = new MockEntity(5, 0);
            entity.damage(10); // 10 damage to 5 health entity

            assertEquals(0, entity.getHealth(), 0.01f);
            assertFalse(entity.isAlive());
        }

        @Test
        @DisplayName("Entity with 1 health survives 0.5 damage")
        void testSurvivesFractionalDamage() {
            MockEntity entity = new MockEntity(1, 0);
            entity.damage(0.5f);

            assertEquals(0.5f, entity.getHealth(), 0.01f);
            assertTrue(entity.isAlive());
        }

        @Test
        @DisplayName("Multiple damage applications")
        void testMultipleDamageApplications() {
            MockEntity entity = new MockEntity(20, 0);

            for (int i = 0; i < 5; i++) {
                MockEnvironmentalDamageSource fire = new MockEnvironmentalDamageSource(
                    EnvironmentalDamageType.FIRE, 2.0f
                );
                calculator.applyEnvironmentalDamage(entity, fire);
            }

            assertEquals(10, entity.getHealth(), 0.01f);
        }
    }

    // ============================================
    // Hot Floor Tests
    // ============================================

    @Nested
    @DisplayName("Hot Floor (Magma Block) Tests")
    class HotFloorTests {

        @Test
        @DisplayName("Hot floor damages normally")
        void testHotFloorDamage() {
            MockEntity entity = new MockEntity(20, 0);

            MockEnvironmentalDamageSource hotFloor = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.HOT_FLOOR, 1.0f
            );

            float damage = calculator.applyEnvironmentalDamage(entity, hotFloor);
            assertEquals(1.0f, damage, 0.01f);
        }

        @Test
        @DisplayName("Fire resistant entities immune to hot floor")
        void testFireResistanceBlocksHotFloor() {
            MockEntity entity = new MockEntity(20, 0);
            entity.setFireResistant(true);

            MockEnvironmentalDamageSource hotFloor = new MockEnvironmentalDamageSource(
                EnvironmentalDamageType.HOT_FLOOR, 1.0f
            );

            float damage = calculator.applyEnvironmentalDamage(entity, hotFloor);
            assertEquals(0, damage, 0.01f);
        }
    }
}
