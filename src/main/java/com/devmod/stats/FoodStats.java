package com.devmod.stats;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import com.devmod.config.stats.IItemStats;
import com.devmod.endurance.nutrition.NutritionCategory;

public class FoodStats implements IItemStats {

    // ═══════════════════════════════════════════════════════════════
    // NUTRITION PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    /** Hunger points restored (0-20, each unit = half drumstick) */
    private int nutrition = 0;

    /** Saturation modifier (0.0-2.0, multiplied by nutrition for actual saturation) */
    private float saturation = 0.0f;

    /** Time in ticks to consume the food (default 32 for food, 40 for drinks) */
    private int consumptionTime = 32;

    /** Whether the food can be eaten even when hunger is full */
    private boolean canAlwaysEat = false;

    // ═══════════════════════════════════════════════════════════════
    // FOOD PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    /** Whether wolves can eat this food */
    private boolean isMeat = false;

    /** Whether this is a fast food (faster consumption animation) */
    private boolean isFastFood = false;

    // ═══════════════════════════════════════════════════════════════
    // EFFECTS
    // ═══════════════════════════════════════════════════════════════

    /** List of effects applied when consuming this food */
    private List<FoodEffect> effects = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════
    // EASY-DIET INTEGRATION (optional)
    // ═══════════════════════════════════════════════════════════════

    /** Nutrition profile for Easy-Diet mod (category -> value 0.0-1.0) */
    private Map<NutritionCategory, Float> nutritionProfile = new EnumMap<>(NutritionCategory.class);

    /** Primary nutrition category for this food (used for display/sorting) */
    @Nullable
    private NutritionCategory primaryCategory = null;

    public int getNutrition() {
        return nutrition;
    }

    public void setNutrition(int value) {
        nutrition = value;
    }

    public float getSaturation() {
        return saturation;
    }

    public void setSaturation(float value) {
        saturation = value;
    }

    public int getConsumptionTime() {
        return consumptionTime;
    }

    public void setConsumptionTime(int value) {
        consumptionTime = value;
    }

    public boolean isCanAlwaysEat() {
        return canAlwaysEat;
    }

    public void setCanAlwaysEat(boolean value) {
        canAlwaysEat = value;
    }

    public boolean isMeat() {
        return isMeat;
    }

    public void setMeat(boolean value) {
        isMeat = value;
    }

    public boolean isFastFood() {
        return isFastFood;
    }

    public void setFastFood(boolean value) {
        isFastFood = value;
    }

    public List<FoodEffect> getEffects() {
        return effects;
    }

    public void setEffects(List<FoodEffect> value) {
        effects = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    // Easy-Diet getters/setters

    /**
     * Get the nutrition profile map for Easy-Diet integration.
     * @return unmodifiable view of the nutrition profile
     */
    public Map<NutritionCategory, Float> getNutritionProfile() {
        return java.util.Collections.unmodifiableMap(nutritionProfile);
    }

    /**
     * Get value for a specific nutrition category.
     * @param category the category
     * @return value 0.0-1.0, or 0 if not set
     */
    public float getNutritionValue(NutritionCategory category) {
        return nutritionProfile.getOrDefault(category, 0f);
    }

    /**
     * Set value for a specific nutrition category.
     * @param category the category
     * @param value value 0.0-1.0
     */
    public void setNutritionValue(NutritionCategory category, float value) {
        if (value <= 0f) {
            nutritionProfile.remove(category);
        } else {
            nutritionProfile.put(category, Math.min(1.0f, value));
        }
    }

    /**
     * Set the entire nutrition profile.
     * @param profile map of category to value
     */
    public void setNutritionProfile(Map<NutritionCategory, Float> profile) {
        nutritionProfile.clear();
        if (profile != null) {
            for (Map.Entry<NutritionCategory, Float> entry : profile.entrySet()) {
                if (entry.getValue() > 0f) {
                    nutritionProfile.put(entry.getKey(), Math.min(1.0f, entry.getValue()));
                }
            }
        }
    }

    /**
     * Check if this food has any Easy-Diet nutrition values.
     */
    public boolean hasNutritionProfile() {
        return !nutritionProfile.isEmpty();
    }

    /**
     * Get the primary nutrition category.
     * @return primary category, or null if not set
     */
    @Nullable
    public NutritionCategory getPrimaryCategory() {
        return primaryCategory;
    }

    /**
     * Set the primary nutrition category.
     * @param category the primary category
     */
    public void setPrimaryCategory(@Nullable NutritionCategory category) {
        this.primaryCategory = category;
    }

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
            tag.putString("effectId", Objects.requireNonNull(effectId));
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
            if (!(o instanceof FoodEffect that)) return false;
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

    @Override
    public FoodStats copy() {
        FoodStats copy = new FoodStats();
        copy.setNutrition(this.getNutrition());
        copy.setSaturation(this.getSaturation());
        copy.setConsumptionTime(this.getConsumptionTime());
        copy.setCanAlwaysEat(this.isCanAlwaysEat());
        copy.setMeat(this.isMeat());
        copy.setFastFood(this.isFastFood());
        copy.effects = new ArrayList<>();
        for (FoodEffect effect : this.effects) {
            copy.effects.add(effect.copy());
        }
        // Copy nutrition profile
        copy.nutritionProfile = new EnumMap<>(this.nutritionProfile);
        copy.primaryCategory = this.primaryCategory;
        return copy;
    }

    // ═══════════════════════════════════════════════════════════════
    // NBT SERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
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

        // Save Easy-Diet nutrition profile
        if (!nutritionProfile.isEmpty()) {
            CompoundTag dietTag = new CompoundTag();
            for (Map.Entry<NutritionCategory, Float> entry : nutritionProfile.entrySet()) {
                NutritionCategory category = entry.getKey();
                if (category != null) {
                    dietTag.putFloat(category.getKey(), entry.getValue());
                }
            }
            tag.put("nutritionProfile", dietTag);
        }
        NutritionCategory primary = primaryCategory;
        if (primary != null) {
            tag.putString("primaryCategory", primary.getKey());
        }
    }

    @Override
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

        // Load Easy-Diet nutrition profile
        nutritionProfile.clear();
        if (tag.contains("nutritionProfile", Tag.TAG_COMPOUND)) {
            CompoundTag dietTag = tag.getCompound("nutritionProfile");
            for (NutritionCategory cat : NutritionCategory.ALL) {
                String catKey = cat.getKey();
                if (dietTag.contains(catKey)) {
                    float value = dietTag.getFloat(catKey);
                    if (value > 0f) {
                        nutritionProfile.put(cat, value);
                    }
                }
            }
        }
        if (tag.contains("primaryCategory")) {
            primaryCategory = NutritionCategory.byKey(tag.getString("primaryCategory"));
        } else {
            primaryCategory = null;
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

    /**
     * Check if all values are at defaults (no modifications).
     */
    @Override
    public boolean isDefault() {
        return nutrition == 0
            && Float.compare(saturation, 0.0f) == 0
            && consumptionTime == 32
            && !canAlwaysEat
            && !isMeat
            && !isFastFood
            && effects.isEmpty()
            && nutritionProfile.isEmpty()
            && primaryCategory == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FoodStats that)) return false;
        return nutrition == that.getNutrition() &&
               Float.compare(that.getSaturation(), saturation) == 0 &&
               consumptionTime == that.getConsumptionTime() &&
               canAlwaysEat == that.isCanAlwaysEat() &&
               isMeat == that.isMeat() &&
               isFastFood == that.isFastFood() &&
               Objects.equals(effects, that.effects) &&
               Objects.equals(nutritionProfile, that.nutritionProfile) &&
               primaryCategory == that.primaryCategory;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nutrition, saturation, consumptionTime, canAlwaysEat, isMeat, isFastFood,
                effects, nutritionProfile, primaryCategory);
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
               ", nutritionProfile=" + nutritionProfile.size() +
               ", primaryCategory=" + primaryCategory +
               '}';
    }
}
