package com.devmod.foundry.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.foundry.quality.MaterialQuality;

/**
 * Serialized data stored on foundry tool stacks.
 */
public record FoundryToolData(
    ResourceLocation toolId,
    List<ResourceLocation> materials,
    Map<ResourceLocation, Integer> modifiers,
    MaterialQuality quality,
    int level,
    int xp,
    int bonusUpgrades,
    int bonusAbilities,
    int repairCount,
    @Nullable ResourceLocation specialization,
    @Nullable ResourceLocation embossment
) {
    private static final String TAG_ROOT = "FoundryTool";
    private static final String TAG_TOOL = "ToolId";
    private static final String TAG_MATERIALS = "Materials";
    private static final String TAG_MODIFIERS = "Modifiers";
    private static final String TAG_LEVEL = "Level";
    private static final String TAG_XP = "Xp";
    private static final String TAG_BONUS_UPGRADES = "BonusUpgrades";
    private static final String TAG_BONUS_ABILITIES = "BonusAbilities";
    private static final String TAG_REPAIR = "RepairCount";
    private static final String TAG_SPECIALIZATION = "Specialization";
    private static final String TAG_EMBOSS = "Embossment";
    private static final String TAG_QUALITY = "Quality";

    public static Optional<FoundryToolData> fromStack(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (!root.contains(TAG_ROOT)) {
            return Optional.empty();
        }
        CompoundTag toolTag = root.getCompound(TAG_ROOT);
        ResourceLocation toolId = ResourceLocation.tryParse(toolTag.getString(TAG_TOOL));
        if (toolId == null) {
            return Optional.empty();
        }

        List<ResourceLocation> materials = new ArrayList<>();
        ListTag materialTag = toolTag.getList(TAG_MATERIALS, ListTag.TAG_STRING);
        for (int i = 0; i < materialTag.size(); i++) {
            ResourceLocation matId = ResourceLocation.tryParse(materialTag.getString(i));
            if (matId != null) {
                materials.add(matId);
            }
        }

        Map<ResourceLocation, Integer> modifiers = new LinkedHashMap<>();
        if (toolTag.contains(TAG_MODIFIERS)) {
            CompoundTag modTag = toolTag.getCompound(TAG_MODIFIERS);
            for (String key : modTag.getAllKeys()) {
                ResourceLocation modId = ResourceLocation.tryParse(key);
                if (modId != null) {
                    modifiers.put(modId, modTag.getInt(key));
                }
            }
        }

        MaterialQuality quality = toolTag.contains(TAG_QUALITY)
            ? MaterialQuality.fromName(toolTag.getString(TAG_QUALITY))
            : MaterialQuality.STANDARD;
        int level = toolTag.contains(TAG_LEVEL) ? toolTag.getInt(TAG_LEVEL) : 1;
        int xp = toolTag.contains(TAG_XP) ? toolTag.getInt(TAG_XP) : 0;
        int bonusUpgrades = toolTag.contains(TAG_BONUS_UPGRADES) ? toolTag.getInt(TAG_BONUS_UPGRADES) : 0;
        int bonusAbilities = toolTag.contains(TAG_BONUS_ABILITIES) ? toolTag.getInt(TAG_BONUS_ABILITIES) : 0;
        int repairCount = toolTag.contains(TAG_REPAIR) ? toolTag.getInt(TAG_REPAIR) : 0;
        ResourceLocation specialization = null;
        if (toolTag.contains(TAG_SPECIALIZATION)) {
            specialization = ResourceLocation.tryParse(toolTag.getString(TAG_SPECIALIZATION));
        }
        ResourceLocation embossment = null;
        if (toolTag.contains(TAG_EMBOSS)) {
            embossment = ResourceLocation.tryParse(toolTag.getString(TAG_EMBOSS));
        }

        return Optional.of(new FoundryToolData(toolId, List.copyOf(materials), Map.copyOf(modifiers),
            quality, level, xp, bonusUpgrades, bonusAbilities, repairCount, specialization, embossment));
    }

    public void writeToStack(ItemStack stack) {
        CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = existing.copyTag();
        CompoundTag toolTag = new CompoundTag();
        toolTag.putString(TAG_TOOL, toolId.toString());

        ListTag materialsTag = new ListTag();
        for (ResourceLocation materialId : materials) {
            materialsTag.add(StringTag.valueOf(materialId.toString()));
        }
        toolTag.put(TAG_MATERIALS, materialsTag);

        CompoundTag modifiersTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : modifiers.entrySet()) {
            modifiersTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        toolTag.put(TAG_MODIFIERS, modifiersTag);
        toolTag.putString(TAG_QUALITY, quality.getSerializedName());
        toolTag.putInt(TAG_LEVEL, level);
        toolTag.putInt(TAG_XP, xp);
        toolTag.putInt(TAG_BONUS_UPGRADES, bonusUpgrades);
        toolTag.putInt(TAG_BONUS_ABILITIES, bonusAbilities);
        toolTag.putInt(TAG_REPAIR, repairCount);
        if (specialization != null) {
            toolTag.putString(TAG_SPECIALIZATION, specialization.toString());
        }
        if (embossment != null) {
            toolTag.putString(TAG_EMBOSS, embossment.toString());
        }

        root.put(TAG_ROOT, toolTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public Map<ResourceLocation, Integer> modifiersWithEmbossment() {
        if (embossment == null) {
            return modifiers;
        }
        Map<ResourceLocation, Integer> combined = new LinkedHashMap<>(modifiers);
        combined.merge(embossment, 1, Integer::sum);
        return Map.copyOf(combined);
    }

    public FoundryToolData withModifier(ResourceLocation id, int level) {
        Map<ResourceLocation, Integer> updated = new LinkedHashMap<>(modifiers);
        updated.put(id, level);
        return new FoundryToolData(toolId, materials, Map.copyOf(updated),
            quality, this.level, xp, bonusUpgrades, bonusAbilities, repairCount, specialization, embossment);
    }

    public FoundryToolData withQuality(MaterialQuality quality) {
        return new FoundryToolData(toolId, materials, modifiers, quality, level, xp, bonusUpgrades, bonusAbilities, repairCount, specialization, embossment);
    }

    public FoundryToolData withXp(int xp) {
        return new FoundryToolData(toolId, materials, modifiers, quality, level, xp, bonusUpgrades, bonusAbilities, repairCount, specialization, embossment);
    }

    public FoundryToolData withLevel(int level) {
        return new FoundryToolData(toolId, materials, modifiers, quality, level, xp, bonusUpgrades, bonusAbilities, repairCount, specialization, embossment);
    }

    public FoundryToolData withBonusUpgrades(int bonusUpgrades) {
        return new FoundryToolData(toolId, materials, modifiers, quality, level, xp, bonusUpgrades, bonusAbilities, repairCount, specialization, embossment);
    }

    public FoundryToolData withBonusAbilities(int bonusAbilities) {
        return new FoundryToolData(toolId, materials, modifiers, quality, level, xp, bonusUpgrades, bonusAbilities, repairCount, specialization, embossment);
    }

    public FoundryToolData withRepairCount(int repairCount) {
        return new FoundryToolData(toolId, materials, modifiers, quality, level, xp, bonusUpgrades, bonusAbilities, repairCount, specialization, embossment);
    }

    public FoundryToolData withEmbossment(@Nullable ResourceLocation embossment) {
        return new FoundryToolData(toolId, materials, modifiers, quality, level, xp, bonusUpgrades, bonusAbilities, repairCount, specialization, embossment);
    }

    public FoundryToolData withSpecialization(@Nullable ResourceLocation specialization) {
        return new FoundryToolData(toolId, materials, modifiers, quality, level, xp, bonusUpgrades, bonusAbilities, repairCount, specialization, embossment);
    }
}
