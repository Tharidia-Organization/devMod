package com.devmod.foundry.quality;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Stores quality/purity metadata on item stacks.
 */
public final class FoundryItemQuality {
    private static final String TAG_ROOT = "FoundryQuality";
    private static final String TAG_QUALITY = "Quality";
    private static final String TAG_PURITY = "Purity";

    private FoundryItemQuality() {}

    public static void applyQuality(ItemStack stack, MaterialQuality quality, float purity) {
        if (stack.isEmpty()) {
            return;
        }
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        CompoundTag qualityTag = root.contains(TAG_ROOT) ? root.getCompound(TAG_ROOT) : new CompoundTag();
        qualityTag.putString(TAG_QUALITY, quality.getSerializedName());
        qualityTag.putFloat(TAG_PURITY, clampPurity(purity));
        root.put(TAG_ROOT, qualityTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public static boolean hasQuality(ItemStack stack) {
        CompoundTag root = getRoot(stack);
        return root != null && root.contains(TAG_QUALITY);
    }

    public static MaterialQuality getQuality(ItemStack stack) {
        CompoundTag root = getRoot(stack);
        if (root == null || !root.contains(TAG_QUALITY)) {
            return MaterialQuality.STANDARD;
        }
        return MaterialQuality.fromName(root.getString(TAG_QUALITY));
    }

    public static float getPurity(ItemStack stack) {
        CompoundTag root = getRoot(stack);
        if (root == null || !root.contains(TAG_PURITY)) {
            return 1.0f;
        }
        return clampPurity(root.getFloat(TAG_PURITY));
    }

    private static CompoundTag getRoot(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (!root.contains(TAG_ROOT)) {
            return null;
        }
        return root.getCompound(TAG_ROOT);
    }

    private static float clampPurity(float purity) {
        return Math.max(0.1f, Math.min(1.0f, purity));
    }
}
