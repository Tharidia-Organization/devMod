package com.devmod.foundry.tool;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import com.devmod.shared.SharedColorTokens;

/**
 * Pattern item used by the Part Builder.
 * Now supports durability, tiers, and specialization.
 */
public class FoundryPatternItem extends Item {
    private static final String TAG_ROOT = "FoundryPattern";
    private static final String TAG_TIER = "Tier";
    private static final String TAG_MASTERY = "Mastery";
    private static final String TAG_SPECIALIZED = "Specialized";
    private static final String TAG_SPEC_MATERIAL = "SpecMaterial";
    private static final String TAG_USE_COUNT = "UseCount";

    private static final int SPECIALIZATION_THRESHOLD = 100;

    private final FoundryPartType partType;
    private final PatternTier defaultTier;

    public FoundryPatternItem(FoundryPartType partType) {
        this(partType, PatternTier.STANDARD);
    }

    public FoundryPatternItem(FoundryPartType partType, PatternTier defaultTier) {
        super(new Item.Properties().stacksTo(1).durability(defaultTier.getBaseDurability()));
        this.partType = partType;
        this.defaultTier = defaultTier;
    }

    public FoundryPartType getPartType() {
        return partType;
    }

    public PatternTier getDefaultTier() {
        return defaultTier;
    }

    /**
     * Gets the tier of this pattern stack.
     */
    public PatternTier getTier(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (!root.contains(TAG_ROOT)) {
            return defaultTier;
        }
        CompoundTag patternTag = root.getCompound(TAG_ROOT);
        return PatternTier.fromName(patternTag.getString(TAG_TIER));
    }

    /**
     * Sets the tier of this pattern stack.
     */
    public void setTier(ItemStack stack, PatternTier tier) {
        CompoundTag patternTag = getOrCreatePatternTag(stack);
        patternTag.putString(TAG_TIER, tier.getName());
        savePatternTag(stack, patternTag);

        // Update max durability based on tier
        stack.set(DataComponents.MAX_DAMAGE, tier.getBaseDurability());
    }

    /**
     * Gets remaining durability.
     */
    public int getDurability(ItemStack stack) {
        int maxDamage = stack.getMaxDamage();
        int damage = stack.getDamageValue();
        return maxDamage - damage;
    }

    /**
     * Gets the quality bonus this pattern provides.
     * Accounts for tier, wear, and mastery.
     */
    public float getQualityBonus(ItemStack stack) {
        PatternTier tier = getTier(stack);
        float tierBonus = tier.getQualityBonus();

        // Worn patterns produce slightly worse quality
        float wearFactor = (float) getDurability(stack) / stack.getMaxDamage();
        float wearPenalty = 1.0f - ((1.0f - wearFactor) * 0.2f); // Max 20% penalty

        // Master patterns gain mastery bonus
        float masteryBonus = 1.0f;
        if (tier.improveWithUse()) {
            int mastery = getMastery(stack);
            masteryBonus = 1.0f + (mastery / 1000f); // Up to 10% bonus at 100 mastery
        }

        return tierBonus * wearPenalty * masteryBonus;
    }

    /**
     * Gets specialization bonus for a specific material.
     */
    public float getSpecializationBonus(ItemStack stack, ResourceLocation materialId) {
        if (!isSpecialized(stack)) {
            return 1.0f;
        }

        Optional<ResourceLocation> specMaterial = getSpecializedMaterial(stack);
        if (specMaterial.isPresent() && specMaterial.get().equals(materialId)) {
            return 1.1f; // 10% bonus for specialized material
        } else {
            return 0.9f; // 10% penalty for other materials
        }
    }

    /**
     * Uses the pattern with a material.
     * Returns true if pattern is still usable, false if broken.
     */
    public boolean usePattern(ItemStack stack, ResourceLocation materialId, int materialHardness) {
        PatternTier tier = getTier(stack);

        // Ancient patterns don't wear on soft materials
        if (tier.isImmuneToWear(materialHardness <= 2 ? "soft" : "hard")) {
            trackMaterialUse(stack, materialId);
            return true;
        }

        // Calculate wear based on material hardness
        int wear = Math.max(1, materialHardness);

        // Master patterns gain mastery instead of losing durability sometimes
        if (tier.improveWithUse()) {
            incrementMastery(stack);
            // 50% chance to not take damage
            if (Math.random() < 0.5) {
                trackMaterialUse(stack, materialId);
                return true;
            }
        }

        // Apply damage
        int currentDamage = stack.getDamageValue();
        int newDamage = currentDamage + wear;

        if (newDamage >= stack.getMaxDamage()) {
            // Pattern breaks
            return false;
        }

        stack.setDamageValue(newDamage);
        trackMaterialUse(stack, materialId);
        return true;
    }

    /**
     * Tracks material usage for specialization.
     */
    private void trackMaterialUse(ItemStack stack, ResourceLocation materialId) {
        CompoundTag patternTag = getOrCreatePatternTag(stack);

        int useCount = patternTag.getInt(TAG_USE_COUNT) + 1;
        patternTag.putInt(TAG_USE_COUNT, useCount);

        // Check for specialization
        if (!isSpecialized(stack) && useCount >= SPECIALIZATION_THRESHOLD) {
            patternTag.putBoolean(TAG_SPECIALIZED, true);
            patternTag.putString(TAG_SPEC_MATERIAL, materialId.toString());
        }

        savePatternTag(stack, patternTag);
    }

    public boolean isSpecialized(ItemStack stack) {
        CompoundTag patternTag = getPatternTag(stack);
        return patternTag != null && patternTag.getBoolean(TAG_SPECIALIZED);
    }

    public Optional<ResourceLocation> getSpecializedMaterial(ItemStack stack) {
        CompoundTag patternTag = getPatternTag(stack);
        if (patternTag == null || !patternTag.contains(TAG_SPEC_MATERIAL)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(patternTag.getString(TAG_SPEC_MATERIAL)));
    }

    public int getMastery(ItemStack stack) {
        CompoundTag patternTag = getPatternTag(stack);
        return patternTag != null ? patternTag.getInt(TAG_MASTERY) : 0;
    }

    private void incrementMastery(ItemStack stack) {
        CompoundTag patternTag = getOrCreatePatternTag(stack);
        int mastery = patternTag.getInt(TAG_MASTERY);
        patternTag.putInt(TAG_MASTERY, Math.min(100, mastery + 1));
        savePatternTag(stack, patternTag);
    }

    public int getUseCount(ItemStack stack) {
        CompoundTag patternTag = getPatternTag(stack);
        return patternTag != null ? patternTag.getInt(TAG_USE_COUNT) : 0;
    }

    // NBT helpers

    @Nullable
    private CompoundTag getPatternTag(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (!root.contains(TAG_ROOT)) {
            return null;
        }
        return root.getCompound(TAG_ROOT);
    }

    private CompoundTag getOrCreatePatternTag(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (!root.contains(TAG_ROOT)) {
            CompoundTag patternTag = new CompoundTag();
            patternTag.putString(TAG_TIER, defaultTier.getName());
            root.put(TAG_ROOT, patternTag);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
            return patternTag;
        }
        return root.getCompound(TAG_ROOT);
    }

    private void savePatternTag(ItemStack stack, CompoundTag patternTag) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        root.put(TAG_ROOT, patternTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag
    ) {
        PatternTier tier = getTier(stack);
        tooltip.add(tier.getColoredDisplayName());
        tooltip.add(Component.translatable("tooltip.devmod.foundry.pattern.part", partType.id().getPath()));

        // Show durability
        int durability = getDurability(stack);
        int maxDurability = stack.getMaxDamage();
        tooltip.add(Component.translatable("tooltip.devmod.foundry.pattern.durability", durability, maxDurability));

        // Show quality bonus
        float quality = getQualityBonus(stack);
        tooltip.add(Component.translatable("tooltip.devmod.foundry.pattern.quality", String.format("%.0f%%", quality * 100)));

        // Show specialization
        if (isSpecialized(stack)) {
            getSpecializedMaterial(stack).ifPresent(mat ->
                tooltip.add(Component.translatable("tooltip.devmod.foundry.pattern.specialized", mat.getPath())
                    .withColor(SharedColorTokens.Foundry.Pattern.SPECIALIZED))
            );
        } else {
            int progress = (getUseCount(stack) * 100) / SPECIALIZATION_THRESHOLD;
            if (progress > 0) {
                tooltip.add(Component.translatable("tooltip.devmod.foundry.pattern.spec_progress", progress));
            }
        }

        // Show mastery for master patterns
        if (tier.improveWithUse()) {
            int mastery = getMastery(stack);
            tooltip.add(Component.translatable("tooltip.devmod.foundry.pattern.mastery", mastery));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getDurability(stack) < stack.getMaxDamage();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * getDurability(stack) / stack.getMaxDamage());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float ratio = (float) getDurability(stack) / stack.getMaxDamage();
        if (ratio > 0.6f) return SharedColorTokens.Foundry.Pattern.DURABILITY_HIGH;
        if (ratio > 0.3f) return SharedColorTokens.Foundry.Pattern.DURABILITY_MEDIUM;
        return SharedColorTokens.Foundry.Pattern.DURABILITY_LOW;
    }
}
