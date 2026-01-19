package com.devmod.foundry.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Serialized data stored on foundry tool stacks.
 */
public record FoundryToolData(
    ResourceLocation toolId,
    List<ResourceLocation> materials,
    Map<ResourceLocation, Integer> modifiers
) {
    private static final String TAG_ROOT = "FoundryTool";
    private static final String TAG_TOOL = "ToolId";
    private static final String TAG_MATERIALS = "Materials";
    private static final String TAG_MODIFIERS = "Modifiers";

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

        return Optional.of(new FoundryToolData(toolId, List.copyOf(materials), Map.copyOf(modifiers)));
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

        root.put(TAG_ROOT, toolTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public FoundryToolData withModifier(ResourceLocation id, int level) {
        Map<ResourceLocation, Integer> updated = new LinkedHashMap<>(modifiers);
        updated.put(id, level);
        return new FoundryToolData(toolId, materials, Map.copyOf(updated));
    }
}
