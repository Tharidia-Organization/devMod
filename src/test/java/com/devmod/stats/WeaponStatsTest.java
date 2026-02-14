package com.devmod.stats;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WeaponStats")
class WeaponStatsTest {

    private WeaponStats stats;

    @BeforeEach
    void setUp() {
        stats = new WeaponStats();
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
        @DisplayName("default crit damage is 1.5")
        void defaultCritDamage() {
            assertEquals(1.5f, stats.getCritDamage(), 0.001f);
        }

        @Test
        @DisplayName("default tool mining speed is 1.0")
        void defaultToolMiningSpeed() {
            assertEquals(1.0f, stats.getToolDefaultMiningSpeed(), 0.001f);
        }

        @Test
        @DisplayName("default tool damage per block is 1")
        void defaultToolDamagePerBlock() {
            assertEquals(1, stats.getToolDamagePerBlock());
        }

        @Test
        @DisplayName("all combat stats default to zero")
        void combatStatsDefaultToZero() {
            assertEquals(0.0f, stats.getArmorPenetration(), 0.001f);
            assertEquals(0.0f, stats.getBaseDamageBonus(), 0.001f);
            assertEquals(0.0f, stats.getAttackDamage(), 0.001f);
            assertEquals(0.0f, stats.getAttackSpeed(), 0.001f);
            assertEquals(0.0f, stats.getAttackReach(), 0.001f);
            assertEquals(0.0f, stats.getAttackKnockback(), 0.001f);
            assertEquals(0.0f, stats.getDamageBonus(), 0.001f);
            assertEquals(0.0f, stats.getSweepingRatio(), 0.001f);
            assertEquals(0.0f, stats.getCritChance(), 0.001f);
            assertEquals(0.0f, stats.getArmorShred(), 0.001f);
        }

        @Test
        @DisplayName("all damage type bonuses default to zero")
        void damageTypeBonusesDefaultToZero() {
            assertEquals(0.0f, stats.getFireDamageBonus(), 0.001f);
            assertEquals(0.0f, stats.getMagicDamageBonus(), 0.001f);
            assertEquals(0.0f, stats.getLifesteal(), 0.001f);
            assertEquals(0.0f, stats.getDamageVsUndead(), 0.001f);
            assertEquals(0.0f, stats.getDamageVsArthropods(), 0.001f);
            assertEquals(0.0f, stats.getDamageVsPlayers(), 0.001f);
            assertEquals(0.0f, stats.getTrueDamagePercent(), 0.001f);
        }

        @Test
        @DisplayName("durability fields default correctly")
        void durabilityDefaults() {
            assertEquals(0, stats.getMaxDurability());
            assertEquals(0, stats.getCurrentDamage());
            assertEquals(0, stats.getRepairCost());
            assertFalse(stats.isUnbreakable());
            assertFalse(stats.isClearToolRules());
        }

        @Test
        @DisplayName("custom attribute modifiers list starts empty")
        void customAttributeModifiersEmpty() {
            assertNotNull(stats.getCustomAttributeModifiers());
            assertTrue(stats.getCustomAttributeModifiers().isEmpty());
        }

        @Test
        @DisplayName("tool rules list starts empty")
        void toolRulesEmpty() {
            assertNotNull(stats.toolRules);
            assertTrue(stats.toolRules.isEmpty());
        }
    }

    // ================================================================
    // isDefault changes when any stat is set
    // ================================================================

    @Nested
    @DisplayName("isDefault becomes false")
    class IsDefaultChanges {

        @Test void attackDamageBreaksDefault() {
            stats.setAttackDamage(5.0f);
            assertFalse(stats.isDefault());
        }

        @Test void critChanceBreaksDefault() {
            stats.setCritChance(0.1f);
            assertFalse(stats.isDefault());
        }

        @Test void critDamageNonDefaultBreaksDefault() {
            stats.setCritDamage(2.0f);
            assertFalse(stats.isDefault());
        }

        @Test void unbreakableBreaksDefault() {
            stats.setUnbreakable(true);
            assertFalse(stats.isDefault());
        }

        @Test void maxDurabilityBreaksDefault() {
            stats.setMaxDurability(100);
            assertFalse(stats.isDefault());
        }

        @Test void toolRulesBreaksDefault() {
            stats.toolRules.add(new WeaponStats.ToolRuleData("minecraft:stone", 2.0f, true));
            assertFalse(stats.isDefault());
        }

        @Test void customAttributeModifiersBreaksDefault() {
            List<WeaponStats.AttributeModifierData> mods = new ArrayList<>();
            mods.add(new WeaponStats.AttributeModifierData("minecraft:generic.max_health", 4.0, 0));
            stats.setCustomAttributeModifiers(mods);
            assertFalse(stats.isDefault());
        }

        @Test void toolDefaultMiningSpeedBreaksDefault() {
            stats.setToolDefaultMiningSpeed(2.0f);
            assertFalse(stats.isDefault());
        }

        @Test void toolDamagePerBlockBreaksDefault() {
            stats.setToolDamagePerBlock(3);
            assertFalse(stats.isDefault());
        }

        @Test void clearToolRulesBreaksDefault() {
            stats.setClearToolRules(true);
            assertFalse(stats.isDefault());
        }
    }

    // ================================================================
    // NBT save/load roundtrip
    // ================================================================

    @Nested
    @DisplayName("NBT Serialization")
    class NbtSerialization {

        @Test
        @DisplayName("save to empty tag for default stats only writes hit location multipliers")
        void defaultStatsSaveMinimal() {
            CompoundTag tag = new CompoundTag();
            stats.save(tag);
            // Hit-location multipliers are always written
            assertTrue(tag.contains("HeadMult"));
            assertTrue(tag.contains("BodyMult"));
            assertTrue(tag.contains("ArmsMult"));
            assertTrue(tag.contains("LegsMult"));
            // Optional fields should be absent for defaults
            assertFalse(tag.contains("ArmorPen"));
            assertFalse(tag.contains("AtkDmg"));
            assertFalse(tag.contains("CritCh"));
            assertFalse(tag.contains("MaxDur"));
            assertFalse(tag.contains("Unbreakable"));
            assertFalse(tag.contains("AttrMods"));
            assertFalse(tag.contains("ToolRules"));
        }

        @Test
        @DisplayName("full roundtrip preserves all stats")
        void fullRoundtrip() {
            stats.setHeadMult(2.5f);
            stats.setBodyMult(1.2f);
            stats.setArmsMult(0.9f);
            stats.setLegsMult(0.6f);
            stats.setArmorPenetration(0.4f);
            stats.setBaseDamageBonus(3.0f);
            stats.setAttackDamage(8.0f);
            stats.setAttackSpeed(1.6f);
            stats.setAttackReach(3.5f);
            stats.setAttackKnockback(0.5f);
            stats.setDamageBonus(0.1f);
            stats.setSweepingRatio(0.5f);
            stats.setCritChance(0.25f);
            stats.setCritDamage(2.0f);
            stats.setArmorShred(10.0f);
            stats.setFireDamageBonus(2.0f);
            stats.setMagicDamageBonus(1.5f);
            stats.setLifesteal(0.1f);
            stats.setDamageVsUndead(0.5f);
            stats.setDamageVsArthropods(0.3f);
            stats.setDamageVsPlayers(0.2f);
            stats.setTrueDamagePercent(0.15f);
            stats.setMaxDurability(500);
            stats.setCurrentDamage(50);
            stats.setRepairCost(3);
            stats.setUnbreakable(true);
            stats.setClearToolRules(true);
            stats.setToolDefaultMiningSpeed(1.5f);
            stats.setToolDamagePerBlock(2);

            stats.toolRules.add(new WeaponStats.ToolRuleData("#minecraft:mineable/pickaxe", 6.0f, true));
            List<WeaponStats.AttributeModifierData> mods = new ArrayList<>();
            mods.add(new WeaponStats.AttributeModifierData("minecraft:generic.max_health", 4.0, 0));
            stats.setCustomAttributeModifiers(mods);

            CompoundTag tag = new CompoundTag();
            stats.save(tag);

            WeaponStats loaded = WeaponStats.fromTag(tag);

            assertEquals(2.5f, loaded.getHeadMult(), 0.001f);
            assertEquals(1.2f, loaded.getBodyMult(), 0.001f);
            assertEquals(0.9f, loaded.getArmsMult(), 0.001f);
            assertEquals(0.6f, loaded.getLegsMult(), 0.001f);
            assertEquals(0.4f, loaded.getArmorPenetration(), 0.001f);
            assertEquals(3.0f, loaded.getBaseDamageBonus(), 0.001f);
            assertEquals(8.0f, loaded.getAttackDamage(), 0.001f);
            assertEquals(1.6f, loaded.getAttackSpeed(), 0.001f);
            assertEquals(3.5f, loaded.getAttackReach(), 0.001f);
            assertEquals(0.5f, loaded.getAttackKnockback(), 0.001f);
            assertEquals(0.1f, loaded.getDamageBonus(), 0.001f);
            assertEquals(0.5f, loaded.getSweepingRatio(), 0.001f);
            assertEquals(0.25f, loaded.getCritChance(), 0.001f);
            assertEquals(2.0f, loaded.getCritDamage(), 0.001f);
            assertEquals(10.0f, loaded.getArmorShred(), 0.001f);
            assertEquals(2.0f, loaded.getFireDamageBonus(), 0.001f);
            assertEquals(1.5f, loaded.getMagicDamageBonus(), 0.001f);
            assertEquals(0.1f, loaded.getLifesteal(), 0.001f);
            assertEquals(0.5f, loaded.getDamageVsUndead(), 0.001f);
            assertEquals(0.3f, loaded.getDamageVsArthropods(), 0.001f);
            assertEquals(0.2f, loaded.getDamageVsPlayers(), 0.001f);
            assertEquals(0.15f, loaded.getTrueDamagePercent(), 0.001f);
            assertEquals(500, loaded.getMaxDurability());
            assertEquals(50, loaded.getCurrentDamage());
            assertEquals(3, loaded.getRepairCost());
            assertTrue(loaded.isUnbreakable());
            assertTrue(loaded.isClearToolRules());
            assertEquals(1.5f, loaded.getToolDefaultMiningSpeed(), 0.001f);
            assertEquals(2, loaded.getToolDamagePerBlock());

            assertEquals(1, loaded.toolRules.size());
            assertEquals("#minecraft:mineable/pickaxe", loaded.toolRules.get(0).blockTag);
            assertEquals(6.0f, loaded.toolRules.get(0).speed, 0.001f);
            assertEquals(Boolean.TRUE, loaded.toolRules.get(0).correctForDrops);

            assertEquals(1, loaded.getCustomAttributeModifiers().size());
            assertEquals("minecraft:generic.max_health", loaded.getCustomAttributeModifiers().get(0).attributeId);
            assertEquals(4.0, loaded.getCustomAttributeModifiers().get(0).amount, 0.001);
            assertEquals(0, loaded.getCustomAttributeModifiers().get(0).operation);
        }

        @Test
        @DisplayName("loading from empty tag keeps defaults")
        void loadFromEmptyTag() {
            CompoundTag tag = new CompoundTag();
            WeaponStats loaded = WeaponStats.fromTag(tag);
            assertEquals(0.0f, loaded.getArmorPenetration(), 0.001f);
            assertEquals(0.0f, loaded.getAttackDamage(), 0.001f);
            assertEquals(1.5f, loaded.getCritDamage(), 0.001f);
            assertEquals(0, loaded.getMaxDurability());
            assertFalse(loaded.isUnbreakable());
            assertTrue(loaded.toolRules.isEmpty());
        }

        @Test
        @DisplayName("tool rules with isTag flag roundtrips correctly")
        void toolRulesWithIsTagFlag() {
            WeaponStats.ToolRuleData rule = new WeaponStats.ToolRuleData("minecraft:stone", 4.0f, null);
            rule.isTag = true;
            stats.toolRules.add(rule);

            CompoundTag tag = new CompoundTag();
            stats.save(tag);
            WeaponStats loaded = WeaponStats.fromTag(tag);

            assertEquals(1, loaded.toolRules.size());
            assertTrue(loaded.toolRules.get(0).isTag);
            assertNull(loaded.toolRules.get(0).correctForDrops);
        }

        @Test
        @DisplayName("empty tool rules are skipped during save")
        void emptyToolRulesSkipped() {
            stats.toolRules.add(new WeaponStats.ToolRuleData("", 1.0f, true));
            stats.toolRules.add(new WeaponStats.ToolRuleData(null, 1.0f, true));

            CompoundTag tag = new CompoundTag();
            stats.save(tag);
            // ToolRules tag should not be present since rules are empty
            // (toolRules is not empty but defaults for speed/damagePerBlock cause tag to be written,
            // however the Rules ListTag inside should have 0 entries)
            if (tag.contains("ToolRules")) {
                CompoundTag toolTag = tag.getCompound("ToolRules");
                ListTag rules = toolTag.getList("Rules", 10);
                assertEquals(0, rules.size());
            }
        }
    }

    // ================================================================
    // Copy
    // ================================================================

    @Nested
    @DisplayName("Copy")
    class CopyTests {

        @Test
        @DisplayName("copy produces equal values")
        void copyProducesEqualValues() {
            stats.setAttackDamage(10.0f);
            stats.setCritChance(0.3f);
            stats.setMaxDurability(250);
            stats.setUnbreakable(true);
            stats.toolRules.add(new WeaponStats.ToolRuleData("test", 5.0f, false));

            List<WeaponStats.AttributeModifierData> mods = new ArrayList<>();
            mods.add(new WeaponStats.AttributeModifierData("test_attr", 2.0, 1));
            stats.setCustomAttributeModifiers(mods);

            WeaponStats copy = stats.copy();

            assertEquals(stats.getAttackDamage(), copy.getAttackDamage(), 0.001f);
            assertEquals(stats.getCritChance(), copy.getCritChance(), 0.001f);
            assertEquals(stats.getMaxDurability(), copy.getMaxDurability());
            assertEquals(stats.isUnbreakable(), copy.isUnbreakable());
            assertEquals(1, copy.toolRules.size());
            assertEquals("test", copy.toolRules.get(0).blockTag);
            assertEquals(1, copy.getCustomAttributeModifiers().size());
        }

        @Test
        @DisplayName("copy is independent - modifying original does not affect copy")
        void copyIsIndependent() {
            stats.setAttackDamage(10.0f);
            stats.toolRules.add(new WeaponStats.ToolRuleData("block", 2.0f, true));

            WeaponStats copy = stats.copy();
            stats.setAttackDamage(99.0f);
            stats.toolRules.get(0).blockTag = "changed";

            assertEquals(10.0f, copy.getAttackDamage(), 0.001f);
            assertEquals("block", copy.toolRules.get(0).blockTag);
        }

        @Test
        @DisplayName("copy with empty attribute modifiers filters nulls")
        void copyFiltersEmptyModifiers() {
            List<WeaponStats.AttributeModifierData> mods = new ArrayList<>();
            mods.add(new WeaponStats.AttributeModifierData("valid", 1.0, 0));
            mods.add(new WeaponStats.AttributeModifierData("", 0.0, 0)); // empty
            stats.setCustomAttributeModifiers(mods);

            WeaponStats copy = stats.copy();
            assertEquals(1, copy.getCustomAttributeModifiers().size());
            assertEquals("valid", copy.getCustomAttributeModifiers().get(0).attributeId);
        }
    }

    // ================================================================
    // calculateDPS
    // ================================================================

    @Nested
    @DisplayName("calculateDPS")
    class CalculateDPSTests {

        @Test
        @DisplayName("uses override damage when set")
        void usesOverrideDamage() {
            stats.setAttackDamage(10.0f);
            stats.setAttackSpeed(2.0f);
            assertEquals(20.0f, stats.calculateDPS(5.0f), 0.001f);
        }

        @Test
        @DisplayName("uses base item damage when override is zero")
        void usesBaseItemDamage() {
            assertEquals(5.0f * 1.6f, stats.calculateDPS(5.0f), 0.001f);
        }

        @Test
        @DisplayName("uses default speed 1.6 when speed override is zero")
        void usesDefaultSpeed() {
            stats.setAttackDamage(10.0f);
            assertEquals(10.0f * 1.6f, stats.calculateDPS(0.0f), 0.001f);
        }

        @ParameterizedTest
        @CsvSource({
            "10.0, 2.0, 5.0, 20.0",   // override damage 10 * speed 2.0
            "0.0, 2.0, 5.0, 10.0",    // base damage 5 * speed 2.0
            "0.0, 0.0, 5.0, 8.0",     // base damage 5 * default speed 1.6
            "8.0, 4.0, 3.0, 32.0",    // override damage 8 * speed 4.0
        })
        @DisplayName("DPS calculation parameterized")
        void dpsParameterized(float atkDmg, float atkSpd, float baseItemDmg, float expectedDps) {
            stats.setAttackDamage(atkDmg);
            stats.setAttackSpeed(atkSpd);
            assertEquals(expectedDps, stats.calculateDPS(baseItemDmg), 0.01f);
        }

        @Test
        @DisplayName("zero damage and zero speed yields zero DPS")
        void zeroDamageZeroSpeed() {
            assertEquals(0.0f, stats.calculateDPS(0.0f), 0.001f);
        }
    }

    // ================================================================
    // ToolRuleData
    // ================================================================

    @Nested
    @DisplayName("ToolRuleData")
    class ToolRuleDataTests {

        @Test
        @DisplayName("default constructor has empty blockTag")
        void defaultConstructorEmptyBlockTag() {
            WeaponStats.ToolRuleData rule = new WeaponStats.ToolRuleData();
            assertEquals("", rule.blockTag);
            assertEquals(1.0f, rule.speed, 0.001f);
            assertTrue(rule.isEmpty());
        }

        @Test
        @DisplayName("parameterized constructor stores values")
        void parameterizedConstructor() {
            WeaponStats.ToolRuleData rule = new WeaponStats.ToolRuleData("minecraft:stone", 3.0f, true);
            assertEquals("minecraft:stone", rule.blockTag);
            assertEquals(3.0f, rule.speed, 0.001f);
            assertEquals(Boolean.TRUE, rule.correctForDrops);
            assertFalse(rule.isEmpty());
        }

        @Test
        @DisplayName("null blockTag in constructor becomes empty string")
        void nullBlockTagBecomesEmpty() {
            WeaponStats.ToolRuleData rule = new WeaponStats.ToolRuleData(null, 1.0f, true);
            assertEquals("", rule.blockTag);
            assertTrue(rule.isEmpty());
        }

        @Test
        @DisplayName("copy preserves all fields including isTag")
        void copyPreservesAllFields() {
            WeaponStats.ToolRuleData rule = new WeaponStats.ToolRuleData("test", 2.5f, false);
            rule.isTag = true;
            WeaponStats.ToolRuleData copy = rule.copy();
            assertEquals("test", copy.blockTag);
            assertEquals(2.5f, copy.speed, 0.001f);
            assertEquals(Boolean.FALSE, copy.correctForDrops);
            assertTrue(copy.isTag);
        }

        @Test
        @DisplayName("isEmpty returns true for blank string")
        void isEmptyBlankString() {
            WeaponStats.ToolRuleData rule = new WeaponStats.ToolRuleData();
            rule.blockTag = "   ";
            assertTrue(rule.isEmpty());
        }
    }

    // ================================================================
    // AttributeModifierData
    // ================================================================

    @Nested
    @DisplayName("AttributeModifierData")
    class AttributeModifierDataTests {

        @Test
        @DisplayName("default constructor has empty attributeId")
        void defaultConstructorEmpty() {
            WeaponStats.AttributeModifierData mod = new WeaponStats.AttributeModifierData();
            assertEquals("", mod.attributeId);
            assertEquals(0.0, mod.amount, 0.001);
            assertEquals(0, mod.operation);
            assertTrue(mod.isEmpty());
        }

        @Test
        @DisplayName("parameterized constructor stores values")
        void parameterizedConstructor() {
            WeaponStats.AttributeModifierData mod = new WeaponStats.AttributeModifierData(
                "minecraft:generic.attack_damage", 5.0, 1
            );
            assertEquals("minecraft:generic.attack_damage", mod.attributeId);
            assertEquals(5.0, mod.amount, 0.001);
            assertEquals(1, mod.operation);
            assertFalse(mod.isEmpty());
        }

        @Test
        @DisplayName("null attributeId becomes empty string")
        void nullAttributeIdBecomesEmpty() {
            WeaponStats.AttributeModifierData mod = new WeaponStats.AttributeModifierData(null, 1.0, 0);
            assertEquals("", mod.attributeId);
            assertTrue(mod.isEmpty());
        }

        @Test
        @DisplayName("copy is independent")
        void copyIsIndependent() {
            WeaponStats.AttributeModifierData mod = new WeaponStats.AttributeModifierData("attr", 3.0, 2);
            WeaponStats.AttributeModifierData copy = mod.copy();
            assertEquals("attr", copy.attributeId);
            assertEquals(3.0, copy.amount, 0.001);
            assertEquals(2, copy.operation);

            mod.amount = 99.0;
            assertEquals(3.0, copy.amount, 0.001);
        }
    }

    // ================================================================
    // writeAttributeModifiers / readAttributeModifiers static helpers
    // ================================================================

    @Nested
    @DisplayName("Attribute Modifier NBT Helpers")
    class AttributeModifierNbtHelpers {

        @Test
        @DisplayName("writeAttributeModifiers with null returns empty list")
        void writeNullReturnsEmpty() {
            ListTag result = WeaponStats.writeAttributeModifiers(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("writeAttributeModifiers with empty list returns empty")
        void writeEmptyListReturnsEmpty() {
            ListTag result = WeaponStats.writeAttributeModifiers(new ArrayList<>());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("writeAttributeModifiers skips null and empty entries")
        void writeSkipsNullAndEmpty() {
            List<WeaponStats.AttributeModifierData> list = new ArrayList<>();
            list.add(null);
            list.add(new WeaponStats.AttributeModifierData("", 0.0, 0));
            list.add(new WeaponStats.AttributeModifierData("valid", 1.0, 0));

            ListTag result = WeaponStats.writeAttributeModifiers(list);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("readAttributeModifiers roundtrips correctly")
        void readWriteRoundtrip() {
            List<WeaponStats.AttributeModifierData> original = new ArrayList<>();
            original.add(new WeaponStats.AttributeModifierData("attr1", 1.5, 0));
            original.add(new WeaponStats.AttributeModifierData("attr2", -3.0, 2));

            ListTag nbt = WeaponStats.writeAttributeModifiers(original);
            List<WeaponStats.AttributeModifierData> loaded = WeaponStats.readAttributeModifiers(nbt);

            assertEquals(2, loaded.size());
            assertEquals("attr1", loaded.get(0).attributeId);
            assertEquals(1.5, loaded.get(0).amount, 0.001);
            assertEquals(0, loaded.get(0).operation);
            assertEquals("attr2", loaded.get(1).attributeId);
            assertEquals(-3.0, loaded.get(1).amount, 0.001);
            assertEquals(2, loaded.get(1).operation);
        }

        @Test
        @DisplayName("readAttributeModifiers with null returns empty")
        void readNullReturnsEmpty() {
            List<WeaponStats.AttributeModifierData> result = WeaponStats.readAttributeModifiers(null);
            assertTrue(result.isEmpty());
        }
    }

    // ================================================================
    // setCustomAttributeModifiers
    // ================================================================

    @Nested
    @DisplayName("setCustomAttributeModifiers")
    class SetCustomAttributeModifiersTests {

        @Test
        @DisplayName("null input clears list")
        void nullInputClearsList() {
            List<WeaponStats.AttributeModifierData> mods = new ArrayList<>();
            mods.add(new WeaponStats.AttributeModifierData("x", 1.0, 0));
            stats.setCustomAttributeModifiers(mods);
            assertEquals(1, stats.getCustomAttributeModifiers().size());

            stats.setCustomAttributeModifiers(null);
            assertTrue(stats.getCustomAttributeModifiers().isEmpty());
        }
    }

    // ================================================================
    // toString
    // ================================================================

    @Test
    @DisplayName("toString contains key fields")
    void toStringContainsKeyFields() {
        stats.setAttackDamage(7.0f);
        stats.setCritChance(0.2f);
        String str = stats.toString();
        assertTrue(str.contains("WeaponStats"));
        assertTrue(str.contains("atkDmg=7.0"));
        assertTrue(str.contains("crit=0.2"));
    }
}
