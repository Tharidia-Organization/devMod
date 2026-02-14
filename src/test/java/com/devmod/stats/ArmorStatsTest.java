package com.devmod.stats;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ArmorStats")
class ArmorStatsTest {

    private ArmorStats stats;

    @BeforeEach
    void setUp() {
        stats = new ArmorStats();
    }

    // ================================================================
    // Default state
    // ================================================================

    @Nested
    @DisplayName("Default State")
    class DefaultState {

        @Test
        @DisplayName("new instance reports isDefault true")
        void newInstanceIsDefault() {
            assertTrue(stats.isDefault());
        }

        @Test
        @DisplayName("all reductions default to zero")
        void reductionsDefaultToZero() {
            assertEquals(0.0f, stats.getPhysicalReduction(), 0.001f);
            assertEquals(0.0f, stats.getFireReduction(), 0.001f);
            assertEquals(0.0f, stats.getMagicReduction(), 0.001f);
            assertEquals(0.0f, stats.getExplosionReduction(), 0.001f);
            assertEquals(0.0f, stats.getProjectileReduction(), 0.001f);
        }

        @Test
        @DisplayName("vanilla modifiers default to zero")
        void vanillaModifiersDefaultToZero() {
            assertEquals(0.0f, stats.getArmorBonus(), 0.001f);
            assertEquals(0.0f, stats.getToughnessBonus(), 0.001f);
            assertEquals(0.0f, stats.getKnockbackResistance(), 0.001f);
        }

        @Test
        @DisplayName("shield defaults are correct")
        void shieldDefaults() {
            assertEquals(0.5f, stats.getShieldBlockStrength(), 0.001f);
            assertEquals(1.0f, stats.getShieldRecoverySpeed(), 0.001f);
            assertFalse(stats.isShieldReflectProjectiles());
        }

        @Test
        @DisplayName("shield visual defaults are correct")
        void shieldVisualDefaults() {
            assertEquals(0.6f, stats.getShieldOpacity(), 0.001f);
            assertTrue(stats.isShieldGlowEnabled());
            assertEquals(1.0f, stats.getShieldGlowIntensity(), 0.001f);
            assertEquals(0.15f, stats.getShieldNoiseIntensity(), 0.001f);
            assertEquals(1.0f, stats.getShieldPulseSpeed(), 0.001f);
        }

        @Test
        @DisplayName("shield deflection defaults are correct")
        void shieldDeflectionDefaults() {
            assertEquals(0.15f, stats.getShieldDeflectionSpread(), 0.001f);
            assertFalse(stats.isShieldDeflectToOwner());
            assertEquals(0.8f, stats.getShieldDeflectSpeedMult(), 0.001f);
        }

        @Test
        @DisplayName("shield shatter defaults are correct")
        void shieldShatterDefaults() {
            assertEquals(10.0f, stats.getShieldShatterThreshold(), 0.001f);
            assertTrue(stats.isShieldAutoRegenerate());
            assertEquals(3.0f, stats.getShieldRegenDelay(), 0.001f);
        }

        @Test
        @DisplayName("thorns defaults are correct")
        void thornsDefaults() {
            assertFalse(stats.isThornsReflect());
            assertEquals(0.0f, stats.getThornsPercent(), 0.001f);
        }
    }

    // ================================================================
    // isDefault changes
    // ================================================================

    @Nested
    @DisplayName("isDefault becomes false")
    class IsDefaultChanges {

        @Test void physicalReductionBreaksDefault() {
            stats.setPhysicalReduction(0.1f);
            assertFalse(stats.isDefault());
        }

        @Test void thornsReflectBreaksDefault() {
            stats.setThornsReflect(true);
            assertFalse(stats.isDefault());
        }

        @Test void shieldBlockStrengthBreaksDefault() {
            stats.setShieldBlockStrength(0.8f);
            assertFalse(stats.isDefault());
        }

        @Test void shieldGlowDisabledBreaksDefault() {
            stats.setShieldGlowEnabled(false);
            assertFalse(stats.isDefault());
        }

        @Test void shieldAutoRegenDisabledBreaksDefault() {
            stats.setShieldAutoRegenerate(false);
            assertFalse(stats.isDefault());
        }

        @Test void shieldDeflectToOwnerBreaksDefault() {
            stats.setShieldDeflectToOwner(true);
            assertFalse(stats.isDefault());
        }
    }

    // ================================================================
    // getReductionFor
    // ================================================================

    @Nested
    @DisplayName("getReductionFor")
    class GetReductionFor {

        @Test
        @DisplayName("physical only returns physical reduction")
        void physicalOnly() {
            stats.setPhysicalReduction(0.3f);
            assertEquals(0.3f, stats.getReductionFor(true, false, false, false, false), 0.001f);
        }

        @Test
        @DisplayName("fire only returns fire reduction")
        void fireOnly() {
            stats.setFireReduction(0.2f);
            assertEquals(0.2f, stats.getReductionFor(false, true, false, false, false), 0.001f);
        }

        @Test
        @DisplayName("all flags set sums all reductions")
        void allFlagsSum() {
            stats.setPhysicalReduction(0.1f);
            stats.setFireReduction(0.15f);
            stats.setMagicReduction(0.05f);
            stats.setExplosionReduction(0.2f);
            stats.setProjectileReduction(0.1f);

            float total = stats.getReductionFor(true, true, true, true, true);
            assertEquals(0.6f, total, 0.001f);
        }

        @Test
        @DisplayName("no flags set returns zero")
        void noFlagsReturnsZero() {
            stats.setPhysicalReduction(0.5f);
            assertEquals(0.0f, stats.getReductionFor(false, false, false, false, false), 0.001f);
        }

        @Test
        @DisplayName("projectile damage stacks physical and projectile")
        void projectileStacksPhysical() {
            stats.setPhysicalReduction(0.2f);
            stats.setProjectileReduction(0.15f);
            float total = stats.getReductionFor(true, false, false, false, true);
            assertEquals(0.35f, total, 0.001f);
        }

        @ParameterizedTest
        @CsvSource({
            "0.3, 0.0, 0.0, 0.0, 0.0, true, false, false, false, false, 0.3",
            "0.0, 0.4, 0.0, 0.0, 0.0, false, true, false, false, false, 0.4",
            "0.0, 0.0, 0.25, 0.0, 0.0, false, false, true, false, false, 0.25",
            "0.0, 0.0, 0.0, 0.5, 0.0, false, false, false, true, false, 0.5",
            "0.0, 0.0, 0.0, 0.0, 0.35, false, false, false, false, true, 0.35",
        })
        @DisplayName("individual damage type reductions")
        void parameterizedReductions(float phys, float fire, float magic, float expl, float proj,
                                      boolean isPhys, boolean isFire, boolean isMagic,
                                      boolean isExpl, boolean isProj, float expected) {
            stats.setPhysicalReduction(phys);
            stats.setFireReduction(fire);
            stats.setMagicReduction(magic);
            stats.setExplosionReduction(expl);
            stats.setProjectileReduction(proj);
            assertEquals(expected, stats.getReductionFor(isPhys, isFire, isMagic, isExpl, isProj), 0.001f);
        }
    }

    // ================================================================
    // NBT save/load roundtrip
    // ================================================================

    @Nested
    @DisplayName("NBT Serialization")
    class NbtSerialization {

        @Test
        @DisplayName("default stats produce minimal tag")
        void defaultStatsMinimalTag() {
            CompoundTag tag = new CompoundTag();
            stats.save(tag);
            assertFalse(tag.contains("PhysRed"));
            assertFalse(tag.contains("FireRed"));
            assertFalse(tag.contains("ArmorBon"));
            assertFalse(tag.contains("Thorns"));
            assertFalse(tag.contains("ShieldReflect"));
            assertFalse(tag.contains("ShieldColor"));
        }

        @Test
        @DisplayName("full roundtrip preserves all fields")
        void fullRoundtrip() {
            stats.setPhysicalReduction(0.3f);
            stats.setFireReduction(0.2f);
            stats.setMagicReduction(0.1f);
            stats.setExplosionReduction(0.15f);
            stats.setProjectileReduction(0.25f);
            stats.setArmorBonus(5.0f);
            stats.setToughnessBonus(3.0f);
            stats.setKnockbackResistance(0.4f);
            stats.setThornsReflect(true);
            stats.setThornsPercent(0.25f);
            stats.setShieldReflectProjectiles(true);
            stats.setShieldBlockStrength(0.8f);
            stats.setShieldRecoverySpeed(1.5f);
            stats.setShieldColor(0xFF00FF);
            stats.setShieldOpacity(0.9f);
            stats.setShieldGlowEnabled(false);
            stats.setShieldGlowIntensity(1.5f);
            stats.setShieldNoiseIntensity(0.3f);
            stats.setShieldPulseSpeed(1.5f);
            stats.setShieldDeflectionSpread(0.3f);
            stats.setShieldDeflectToOwner(true);
            stats.setShieldDeflectSpeedMult(1.2f);
            stats.setShieldShatterThreshold(15.0f);
            stats.setShieldAutoRegenerate(false);
            stats.setShieldRegenDelay(5.0f);

            CompoundTag tag = new CompoundTag();
            stats.save(tag);
            ArmorStats loaded = ArmorStats.fromTag(tag);

            assertEquals(0.3f, loaded.getPhysicalReduction(), 0.001f);
            assertEquals(0.2f, loaded.getFireReduction(), 0.001f);
            assertEquals(0.1f, loaded.getMagicReduction(), 0.001f);
            assertEquals(0.15f, loaded.getExplosionReduction(), 0.001f);
            assertEquals(0.25f, loaded.getProjectileReduction(), 0.001f);
            assertEquals(5.0f, loaded.getArmorBonus(), 0.001f);
            assertEquals(3.0f, loaded.getToughnessBonus(), 0.001f);
            assertEquals(0.4f, loaded.getKnockbackResistance(), 0.001f);
            assertTrue(loaded.isThornsReflect());
            assertEquals(0.25f, loaded.getThornsPercent(), 0.001f);
            assertTrue(loaded.isShieldReflectProjectiles());
            assertEquals(0.8f, loaded.getShieldBlockStrength(), 0.001f);
            assertEquals(1.5f, loaded.getShieldRecoverySpeed(), 0.001f);
            assertEquals(0xFF00FF, loaded.getShieldColor());
            assertEquals(0.9f, loaded.getShieldOpacity(), 0.001f);
            assertFalse(loaded.isShieldGlowEnabled());
            assertEquals(1.5f, loaded.getShieldGlowIntensity(), 0.001f);
            assertEquals(0.3f, loaded.getShieldNoiseIntensity(), 0.001f);
            assertEquals(1.5f, loaded.getShieldPulseSpeed(), 0.001f);
            assertEquals(0.3f, loaded.getShieldDeflectionSpread(), 0.001f);
            assertTrue(loaded.isShieldDeflectToOwner());
            assertEquals(1.2f, loaded.getShieldDeflectSpeedMult(), 0.001f);
            assertEquals(15.0f, loaded.getShieldShatterThreshold(), 0.001f);
            assertFalse(loaded.isShieldAutoRegenerate());
            assertEquals(5.0f, loaded.getShieldRegenDelay(), 0.001f);
        }

        @Test
        @DisplayName("loading from empty tag restores defaults")
        void loadFromEmptyTag() {
            ArmorStats loaded = ArmorStats.fromTag(new CompoundTag());
            assertTrue(loaded.isDefault());
            assertEquals(0.5f, loaded.getShieldBlockStrength(), 0.001f);
            assertEquals(1.0f, loaded.getShieldRecoverySpeed(), 0.001f);
            assertTrue(loaded.isShieldGlowEnabled());
            assertTrue(loaded.isShieldAutoRegenerate());
        }
    }

    // ================================================================
    // Copy
    // ================================================================

    @Nested
    @DisplayName("Copy")
    class CopyTests {

        @Test
        @DisplayName("copy preserves all fields")
        void copyPreservesAllFields() {
            stats.setPhysicalReduction(0.4f);
            stats.setThornsReflect(true);
            stats.setThornsPercent(0.3f);
            stats.setShieldColor(0xABCDEF);
            stats.setShieldAutoRegenerate(false);

            ArmorStats copy = stats.copy();

            assertEquals(0.4f, copy.getPhysicalReduction(), 0.001f);
            assertTrue(copy.isThornsReflect());
            assertEquals(0.3f, copy.getThornsPercent(), 0.001f);
            assertEquals(0xABCDEF, copy.getShieldColor());
            assertFalse(copy.isShieldAutoRegenerate());
        }

        @Test
        @DisplayName("copy is independent from original")
        void copyIsIndependent() {
            stats.setPhysicalReduction(0.5f);
            ArmorStats copy = stats.copy();
            stats.setPhysicalReduction(0.9f);
            assertEquals(0.5f, copy.getPhysicalReduction(), 0.001f);
        }
    }

    // ================================================================
    // toString
    // ================================================================

    @Test
    @DisplayName("toString includes key fields")
    void toStringContainsKeyFields() {
        stats.setPhysicalReduction(0.3f);
        stats.setArmorBonus(5.0f);
        String str = stats.toString();
        assertTrue(str.contains("ArmorStats"));
        assertTrue(str.contains("phys=0.3"));
        assertTrue(str.contains("armor=5.0"));
    }
}
