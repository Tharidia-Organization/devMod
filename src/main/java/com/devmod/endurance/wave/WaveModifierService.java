package com.devmod.endurance.wave;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * P2 Architecture: Service for wave modifier logic.
 * Handles roguelike wave modifiers that add variety to waves.
 */
public final class WaveModifierService {

    public static final WaveModifierService INSTANCE = new WaveModifierService();

    private final Random random = new Random();

    // Modifier configuration constants
    private static final int MODIFIER_START_WAVE = 3;
    private static final int MAX_MODIFIERS_EARLY = 1;
    private static final int MAX_MODIFIERS_MID = 2;
    private static final int MAX_MODIFIERS_LATE = 3;
    private static final float MODIFIER_CHANCE_TIER_1 = 0.10f;
    private static final float MODIFIER_CHANCE_TIER_2 = 0.20f;
    private static final float MODIFIER_CHANCE_TIER_3 = 0.35f;

    private WaveModifierService() {}

    /**
     * Roguelike wave modifiers that add variety.
     */
    public enum WaveModifier {
        SPEED_BOOST("Swift", "Mobs move 25% faster"),
        DAMAGE_BOOST("Empowered", "Mobs deal 25% more damage"),
        HEALTH_BOOST("Fortified", "Mobs have 50% more health"),
        ARMOR_BOOST("Armored", "Mobs have increased armor"),
        FIRE_ASPECT("Blazing", "Mobs inflict fire on hit"),
        INVISIBILITY("Phantom", "Mobs are partially invisible"),
        REGEN("Regenerating", "Mobs slowly regenerate health"),
        DOUBLE_SPAWN("Horde", "Double the number of mobs");

        private final String displayName;
        private final String description;

        WaveModifier(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * Roll random modifiers for a wave based on wave number.
     * @param waveNumber Current wave number
     * @return Set of modifiers to apply to this wave
     */
    public Set<WaveModifier> rollWaveModifiers(int waveNumber) {
        if (waveNumber < MODIFIER_START_WAVE) {
            return Set.of();
        }

        float modifierChance = getModifierChance(waveNumber);
        int maxModifiers = waveNumber < 5 ? MAX_MODIFIERS_EARLY : (waveNumber < 8 ? MAX_MODIFIERS_MID : MAX_MODIFIERS_LATE);
        Set<WaveModifier> modifiers = new HashSet<>();

        for (WaveModifier modifier : WaveModifier.values()) {
            if (random.nextFloat() < modifierChance) {
                modifiers.add(modifier);
                if (modifiers.size() >= maxModifiers) break;
            }
        }
        return modifiers;
    }

    /**
     * Get modifier chance based on wave number.
     */
    public float getModifierChance(int waveNumber) {
        if (waveNumber < MODIFIER_START_WAVE) {
            return 0f;
        }
        if (waveNumber < 5) {
            return MODIFIER_CHANCE_TIER_1;
        }
        if (waveNumber < 8) {
            return MODIFIER_CHANCE_TIER_2;
        }
        return MODIFIER_CHANCE_TIER_3;
    }

    /**
     * Get elite spawn chance based on wave number and base chance.
     */
    public float getEliteChance(float baseChance, int waveNumber) {
        if (baseChance <= 0f || waveNumber < MODIFIER_START_WAVE) {
            return 0f;
        }
        float rampChance;
        if (waveNumber < 5) {
            rampChance = MODIFIER_CHANCE_TIER_1;
        } else if (waveNumber < 8) {
            rampChance = MODIFIER_CHANCE_TIER_2;
        } else {
            rampChance = MODIFIER_CHANCE_TIER_3;
        }
        return Math.min(baseChance, rampChance);
    }

    /**
     * Apply wave modifiers to a mob.
     * @param mob The mob to modify
     * @param modifiers Set of modifiers to apply
     */
    public void applyModifiers(Mob mob, Set<WaveModifier> modifiers) {
        for (WaveModifier modifier : modifiers) {
            switch (modifier) {
                case SPEED_BOOST -> {
                    var speedAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
                    if (speedAttr != null) {
                        speedAttr.setBaseValue(speedAttr.getBaseValue() * 1.25);
                    }
                }
                case DAMAGE_BOOST -> {
                    var attackAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
                    if (attackAttr != null) {
                        attackAttr.setBaseValue(attackAttr.getBaseValue() * 1.25);
                    }
                }
                case HEALTH_BOOST -> {
                    var healthAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
                    if (healthAttr != null) {
                        healthAttr.setBaseValue(healthAttr.getBaseValue() * 1.5);
                        mob.setHealth(mob.getMaxHealth());
                    }
                }
                case ARMOR_BOOST -> {
                    var armorAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ARMOR));
                    if (armorAttr != null) {
                        armorAttr.setBaseValue(armorAttr.getBaseValue() + 8);
                    }
                }
                case FIRE_ASPECT -> {
                    // Handled in damage events
                    mob.getPersistentData().putBoolean("endurance_fire_aspect", true);
                }
                case INVISIBILITY -> {
                    mob.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.INVISIBILITY), Integer.MAX_VALUE, 0, false, false));
                }
                case REGEN -> {
                    mob.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.REGENERATION), Integer.MAX_VALUE, 0, false, false));
                }
                case DOUBLE_SPAWN -> {
                    // Handled in wave setup, not per-mob
                }
            }
        }
    }

    /**
     * Apply elite buffs to a mob (special stronger variant).
     * @param mob The mob to buff
     * @param waveNumber Current wave number for scaling
     */
    public void applyEliteBuffs(Mob mob, int waveNumber) {
        // Mark as elite
        mob.getPersistentData().putBoolean("endurance_elite", true);

        // Scale stats based on wave
        float scaleFactor = 1.0f + (waveNumber * 0.1f);

        var healthAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
        if (healthAttr != null) {
            healthAttr.setBaseValue(healthAttr.getBaseValue() * scaleFactor * 1.5);
            mob.setHealth(mob.getMaxHealth());
        }

        var attackAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        if (attackAttr != null) {
            attackAttr.setBaseValue(attackAttr.getBaseValue() * scaleFactor);
        }

        // Give elites equipment
        if (random.nextBoolean()) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Objects.requireNonNull(Items.IRON_HELMET)));
        }
        if (random.nextBoolean()) {
            mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Objects.requireNonNull(Items.IRON_CHESTPLATE)));
        }

        // Visual indicator - glowing effect
        mob.setGlowingTag(true);

        // Effects
        mob.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.DAMAGE_RESISTANCE), Integer.MAX_VALUE, 0, false, false));
    }

    /**
     * Check if a modifier affects spawn count (like DOUBLE_SPAWN).
     */
    public boolean affectsSpawnCount(Set<WaveModifier> modifiers) {
        return modifiers.contains(WaveModifier.DOUBLE_SPAWN);
    }

    /**
     * Get spawn count multiplier from modifiers.
     */
    public int getSpawnCountMultiplier(Set<WaveModifier> modifiers) {
        return modifiers.contains(WaveModifier.DOUBLE_SPAWN) ? 2 : 1;
    }

    /**
     * Apply wave modifiers to a mob using WaveManager.WaveModifier set.
     * Bridge method for backward compatibility with WaveManager.
     * @param mob The mob to modify
     * @param modifiers Set of WaveManager.WaveModifier to apply
     */
    public void applyWaveManagerModifiers(Mob mob, Set<com.devmod.endurance.WaveManager.WaveModifier> modifiers) {
        for (com.devmod.endurance.WaveManager.WaveModifier modifier : modifiers) {
            switch (modifier) {
                case SPEED_BOOST -> {
                    var speedAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
                    if (speedAttr != null) {
                        speedAttr.setBaseValue(speedAttr.getBaseValue() * 1.25);
                    }
                }
                case DAMAGE_BOOST -> {
                    var attackAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
                    if (attackAttr != null) {
                        attackAttr.setBaseValue(attackAttr.getBaseValue() * 1.25);
                    }
                }
                case HEALTH_BOOST -> {
                    var healthAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
                    if (healthAttr != null) {
                        healthAttr.setBaseValue(healthAttr.getBaseValue() * 1.5);
                        mob.setHealth(mob.getMaxHealth());
                    }
                }
                case ARMOR_BOOST -> {
                    var armorAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ARMOR));
                    if (armorAttr != null) {
                        armorAttr.setBaseValue(armorAttr.getBaseValue() + 8);
                    }
                }
                case FIRE_ASPECT -> {
                    mob.getPersistentData().putBoolean("endurance_fire_aspect", true);
                }
                case INVISIBILITY -> {
                    mob.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.INVISIBILITY), Integer.MAX_VALUE, 0, false, false));
                }
                case REGEN -> {
                    mob.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.REGENERATION), Integer.MAX_VALUE, 0, false, false));
                }
                case DOUBLE_SPAWN -> {
                    // Handled in wave setup, not per-mob
                }
            }
        }
    }
}
