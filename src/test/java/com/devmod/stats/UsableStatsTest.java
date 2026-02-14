package com.devmod.stats;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UsableStats")
class UsableStatsTest {

    private UsableStats stats;

    @BeforeEach
    void setUp() {
        stats = new UsableStats();
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
        @DisplayName("default use duration is zero")
        void defaultUseDuration() {
            assertEquals(0, stats.getUseDuration());
        }

        @Test
        @DisplayName("default cooldown is zero")
        void defaultCooldown() {
            assertEquals(0, stats.getCooldownDuration());
        }

        @Test
        @DisplayName("default animation is NONE")
        void defaultAnimation() {
            assertEquals("NONE", stats.getUseAnimation());
        }

        @Test
        @DisplayName("not throwable by default")
        void notThrowableByDefault() {
            assertFalse(stats.isThrowable());
        }

        @Test
        @DisplayName("default projectile values")
        void defaultProjectileValues() {
            assertEquals(1.5f, stats.getProjectileSpeed(), 0.001f);
            assertEquals(0.03f, stats.getProjectileGravity(), 0.001f);
            assertEquals(1.0f, stats.getProjectileInaccuracy(), 0.001f);
            assertEquals(0, stats.getProjectileDamage());
        }

        @Test
        @DisplayName("consume on use by default")
        void consumeOnUseByDefault() {
            assertTrue(stats.isConsumeOnUse());
        }

        @Test
        @DisplayName("default remainder item is empty")
        void defaultRemainderEmpty() {
            assertEquals("", stats.getRemainderItem());
        }
    }

    // ================================================================
    // isDefault changes
    // ================================================================

    @Nested
    @DisplayName("isDefault becomes false")
    class IsDefaultChanges {

        @Test void useDurationBreaksDefault() {
            stats.setUseDuration(20);
            assertFalse(stats.isDefault());
        }

        @Test void cooldownBreaksDefault() {
            stats.setCooldownDuration(40);
            assertFalse(stats.isDefault());
        }

        @Test void animationBreaksDefault() {
            stats.setUseAnimation("EAT");
            assertFalse(stats.isDefault());
        }

        @Test void throwableBreaksDefault() {
            stats.setThrowable(true);
            assertFalse(stats.isDefault());
        }

        @Test void consumeOnUseFalseBreaksDefault() {
            stats.setConsumeOnUse(false);
            assertFalse(stats.isDefault());
        }

        @Test void remainderItemBreaksDefault() {
            stats.setRemainderItem("minecraft:bucket");
            assertFalse(stats.isDefault());
        }
    }

    // ================================================================
    // Null safety for string setters
    // ================================================================

    @Nested
    @DisplayName("Null Safety")
    class NullSafety {

        @Test
        @DisplayName("setUseAnimation null becomes NONE")
        void nullAnimationBecomesNone() {
            stats.setUseAnimation(null);
            assertEquals("NONE", stats.getUseAnimation());
        }

        @Test
        @DisplayName("setRemainderItem null becomes empty string")
        void nullRemainderBecomesEmpty() {
            stats.setRemainderItem(null);
            assertEquals("", stats.getRemainderItem());
        }

        @Test
        @DisplayName("setAmmoFilter null becomes empty string (RangedWeaponStats)")
        void nullAmmoFilterBecomesEmpty() {
            RangedWeaponStats ranged = new RangedWeaponStats();
            ranged.setAmmoFilter(null);
            assertEquals("", ranged.getAmmoFilter());
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
            assertFalse(tag.contains("UseDur"));
            assertFalse(tag.contains("Cooldown"));
            assertFalse(tag.contains("UseAnim"));
            assertFalse(tag.contains("Throwable"));
            assertFalse(tag.contains("NoConsume"));
            assertFalse(tag.contains("Remainder"));
        }

        @Test
        @DisplayName("full roundtrip preserves all fields")
        void fullRoundtrip() {
            stats.setUseDuration(32);
            stats.setCooldownDuration(60);
            stats.setUseAnimation("DRINK");
            stats.setThrowable(true);
            stats.setProjectileSpeed(2.5f);
            stats.setProjectileGravity(0.05f);
            stats.setProjectileInaccuracy(0.5f);
            stats.setProjectileDamage(4);
            stats.setConsumeOnUse(false);
            stats.setRemainderItem("minecraft:glass_bottle");

            CompoundTag tag = new CompoundTag();
            stats.save(tag);
            UsableStats loaded = UsableStats.fromTag(tag);

            assertEquals(32, loaded.getUseDuration());
            assertEquals(60, loaded.getCooldownDuration());
            assertEquals("DRINK", loaded.getUseAnimation());
            assertTrue(loaded.isThrowable());
            assertEquals(2.5f, loaded.getProjectileSpeed(), 0.001f);
            assertEquals(0.05f, loaded.getProjectileGravity(), 0.001f);
            assertEquals(0.5f, loaded.getProjectileInaccuracy(), 0.001f);
            assertEquals(4, loaded.getProjectileDamage());
            assertFalse(loaded.isConsumeOnUse());
            assertEquals("minecraft:glass_bottle", loaded.getRemainderItem());
        }

        @Test
        @DisplayName("loading from empty tag keeps defaults")
        void loadFromEmptyTag() {
            UsableStats loaded = UsableStats.fromTag(new CompoundTag());
            assertTrue(loaded.isDefault());
            assertEquals("NONE", loaded.getUseAnimation());
            assertFalse(loaded.isThrowable());
            assertTrue(loaded.isConsumeOnUse());
        }

        @Test
        @DisplayName("throwable properties only saved when throwable is true")
        void throwablePropertiesOnlySavedWhenThrowable() {
            stats.setProjectileSpeed(5.0f);
            stats.setProjectileDamage(10);
            // isThrowable = false, so these should not be saved
            CompoundTag tag = new CompoundTag();
            stats.save(tag);
            assertFalse(tag.contains("Throwable"));
            assertFalse(tag.contains("ProjSpeed"));
            assertFalse(tag.contains("ProjDmg"));
        }

        @Test
        @DisplayName("NoConsume only saved when consumeOnUse is false")
        void noConsumeOnlySavedWhenFalse() {
            CompoundTag tag = new CompoundTag();
            stats.save(tag);
            assertFalse(tag.contains("NoConsume"));

            stats.setConsumeOnUse(false);
            tag = new CompoundTag();
            stats.save(tag);
            assertTrue(tag.contains("NoConsume"));
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
            stats.setUseDuration(40);
            stats.setCooldownDuration(100);
            stats.setUseAnimation("BOW");
            stats.setThrowable(true);
            stats.setProjectileSpeed(3.0f);
            stats.setProjectileGravity(0.08f);
            stats.setProjectileInaccuracy(2.0f);
            stats.setProjectileDamage(6);
            stats.setConsumeOnUse(false);
            stats.setRemainderItem("minecraft:bowl");

            UsableStats copy = stats.copy();

            assertEquals(40, copy.getUseDuration());
            assertEquals(100, copy.getCooldownDuration());
            assertEquals("BOW", copy.getUseAnimation());
            assertTrue(copy.isThrowable());
            assertEquals(3.0f, copy.getProjectileSpeed(), 0.001f);
            assertEquals(0.08f, copy.getProjectileGravity(), 0.001f);
            assertEquals(2.0f, copy.getProjectileInaccuracy(), 0.001f);
            assertEquals(6, copy.getProjectileDamage());
            assertFalse(copy.isConsumeOnUse());
            assertEquals("minecraft:bowl", copy.getRemainderItem());
        }

        @Test
        @DisplayName("copy is independent from original")
        void copyIsIndependent() {
            stats.setUseDuration(30);
            stats.setRemainderItem("minecraft:stick");

            UsableStats copy = stats.copy();
            stats.setUseDuration(999);
            stats.setRemainderItem("changed");

            assertEquals(30, copy.getUseDuration());
            assertEquals("minecraft:stick", copy.getRemainderItem());
        }
    }

    // ================================================================
    // toString
    // ================================================================

    @Test
    @DisplayName("toString includes key fields")
    void toStringContainsKeyFields() {
        stats.setUseDuration(32);
        stats.setUseAnimation("EAT");
        String str = stats.toString();
        assertTrue(str.contains("UsableStats"));
        assertTrue(str.contains("useDur=32"));
        assertTrue(str.contains("anim=EAT"));
    }
}
