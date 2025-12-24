package com.devmod.ui.editor.systems;

import com.devmod.ui.editor.ItemEditorDataManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Preset wrapper for ItemEditorDataManager.PresetData
 */
public class DataPreset implements Preset {
    private final ItemEditorDataManager.PresetData data;

    public DataPreset(ItemEditorDataManager.PresetData data) {
        this.data = Objects.requireNonNull(data);
    }

    public ItemEditorDataManager.PresetData getData() { return data; }

    @Override
    public Predicate<ItemStack> scope() {
        return stack -> {
            if (stack == null || stack.isEmpty()) return false;
            if (data == null) return false;
            // GLOBAL scope applies to any item
            if ("GLOBAL".equalsIgnoreCase(data.scope)) return true;
            // If itemType not specified, treat as applicable to any item
            if (data.itemType == null || data.itemType.isBlank()) return true;

            String want = data.itemType.trim();
            try {
                // Support tag syntax: "#namespace:tag"
                if (want.startsWith("#")) {
                    String tagId = want.substring(1);
                    ResourceLocation rl = tagId.contains(":")
                        ? Objects.requireNonNull(ResourceLocation.parse(tagId), "tag id cannot be null")
                        : Objects.requireNonNull(ResourceLocation.parse("minecraft:" + tagId), "tag id cannot be null");
                    TagKey<Item> tag = TagKey.create(Objects.requireNonNull(Registries.ITEM), rl);
                    // Require a real tag check (ItemStack#is)
                    try {
                        java.lang.reflect.Method isMethod = stack.getClass().getMethod("is", TagKey.class);
                        Object res = isMethod.invoke(stack, tag);
                        return res instanceof Boolean && (Boolean) res;
                    } catch (NoSuchMethodException ex) {
                        return false;
                    }
                }

                // Try to match full registry id (namespace:path) or just path
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem(), "item cannot be null"));
                if (key != null) {
                    ResourceLocation wantLoc = want.contains(":")
                        ? Objects.requireNonNull(ResourceLocation.parse(want), "itemType cannot be null")
                        : Objects.requireNonNull(ResourceLocation.parse("minecraft:" + want), "itemType cannot be null");
                    if (key.equals(wantLoc)) return true;
                    if (key.getPath().equalsIgnoreCase(wantLoc.getPath())) return true;
                }

                // If we reach here, no strict match found
                return false;
            } catch (Exception e) {
                return false;
            }
        };
    }
}
