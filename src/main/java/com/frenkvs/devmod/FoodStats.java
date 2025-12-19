package com.frenkvs.devmod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Data class holding all modifiable food item statistics.
 * Supports NBT serialization for persistence and network sync.
 */
public class FoodStats {

    // ═══════════════════════════════════════════════════════════════
    // NUTRITION PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    /** Hunger points restored (0-20, each unit = half drumstick) */
    public int nutrition = 0;

    /** Saturation modifier (0.0-2.0, multiplied by nutrition for actual saturation) */
    public float saturation = 0.0f;

    /** Time in ticks to consume the food (default 32 for food, 40 for drinks) */
    public int consumptionTime = 32;

    /** Whether the food can be eaten even when hunger is full */
    public boolean canAlwaysEat = false;

    // ═══════════════════════════════════════════════════════════════
    // FOOD PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    /** Whether wolves can eat this food */
    public boolean isMeat = false;

    /** Whether this is a fast food (faster consumption animation) */
    public boolean isFastFood = false;

    // ═══════════════════════════════════════════════════════════════
    // EFFECTS
    // ═══════════════════════════════════════════════════════════════

    /** List of effects applied when consuming this food */
    public List<FoodEffect> effects = new ArrayList<>();

    /**
     * Represents a single effect that can be applied by food.
     */
    public static class FoodEffect {
        /** Effect resource location (e.g., "minecraft:regeneration") */
        public String effectId = "";

        /** Duration in ticks (20 ticks = 1 second) */
        public int duration = 200;

        /** Amplifier (0 = Level I, 1 = Level II, etc.) */
        public int amplifier = 0;

        /** Probability of applying the effect (0.0-1.0) */
        public float probability = 1.0f;

        public FoodEffect() {}

        public FoodEffect(String effectId, int duration, int amplifier, float probability) {
            this.effectId = effectId;
            this.duration = duration;
            this.amplifier = amplifier;
            this.probability = probability;
        }

        public FoodEffect copy() {
            return new FoodEffect(effectId, duration, amplifier, probability);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("effectId", effectId);
            tag.putInt("duration", duration);
            tag.putInt("amplifier", amplifier);
            tag.putFloat("probability", probability);
            return tag;
        }

        public static FoodEffect load(CompoundTag tag) {
            FoodEffect effect = new FoodEffect();
            effect.effectId = tag.getString("effectId");
            effect.duration = tag.getInt("duration");
            effect.amplifier = tag.getInt("amplifier");
            effect.probability = tag.getFloat("probability");
            return effect;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FoodEffect that = (FoodEffect) o;
            return duration == that.duration &&
                   amplifier == that.amplifier &&
                   Float.compare(that.probability, probability) == 0 &&
                   Objects.equals(effectId, that.effectId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(effectId, duration, amplifier, probability);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════

    public FoodStats() {}

    public FoodStats copy() {
        FoodStats copy = new FoodStats();
        copy.nutrition = this.nutrition;
        copy.saturation = this.saturation;
        copy.consumptionTime = this.consumptionTime;
        copy.canAlwaysEat = this.canAlwaysEat;
        copy.isMeat = this.isMeat;
        copy.isFastFood = this.isFastFood;
        copy.effects = new ArrayList<>();
        for (FoodEffect effect : this.effects) {
            copy.effects.add(effect.copy());
        }
        return copy;
    }

    // ═══════════════════════════════════════════════════════════════
    // NBT SERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    public void save(CompoundTag tag) {
        tag.putInt("nutrition", nutrition);
        tag.putFloat("saturation", saturation);
        tag.putInt("consumptionTime", consumptionTime);
        tag.putBoolean("canAlwaysEat", canAlwaysEat);
        tag.putBoolean("isMeat", isMeat);
        tag.putBoolean("isFastFood", isFastFood);

        // Save effects
        ListTag effectsList = new ListTag();
        for (FoodEffect effect : effects) {
            effectsList.add(effect.save());
        }
        tag.put("effects", effectsList);
    }

    public void load(CompoundTag tag) {
        nutrition = tag.getInt("nutrition");
        saturation = tag.getFloat("saturation");
        consumptionTime = tag.getInt("consumptionTime");
        canAlwaysEat = tag.getBoolean("canAlwaysEat");
        isMeat = tag.getBoolean("isMeat");
        isFastFood = tag.getBoolean("isFastFood");

        // Load effects
        effects.clear();
        if (tag.contains("effects", Tag.TAG_LIST)) {
            ListTag effectsList = tag.getList("effects", Tag.TAG_COMPOUND);
            for (int i = 0; i < effectsList.size(); i++) {
                effects.add(FoodEffect.load(effectsList.getCompound(i)));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Calculate actual saturation value (nutrition * saturation modifier * 2).
     */
    public float getActualSaturation() {
        return nutrition * saturation * 2.0f;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FoodStats that = (FoodStats) o;
        return nutrition == that.nutrition &&
               Float.compare(that.saturation, saturation) == 0 &&
               consumptionTime == that.consumptionTime &&
               canAlwaysEat == that.canAlwaysEat &&
               isMeat == that.isMeat &&
               isFastFood == that.isFastFood &&
               Objects.equals(effects, that.effects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nutrition, saturation, consumptionTime, canAlwaysEat, isMeat, isFastFood, effects);
    }

    @Override
    public String toString() {
        return "FoodStats{" +
               "nutrition=" + nutrition +
               ", saturation=" + saturation +
               ", consumptionTime=" + consumptionTime +
               ", canAlwaysEat=" + canAlwaysEat +
               ", isMeat=" + isMeat +
               ", isFastFood=" + isFastFood +
               ", effects=" + effects.size() +
               '}';
    }
}
