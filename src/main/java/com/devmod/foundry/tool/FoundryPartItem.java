package com.devmod.foundry.tool;

import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.foundry.quality.MaterialQuality;
import com.devmod.foundry.tool.material.IMaterial;
import com.devmod.foundry.tool.material.MaterialVariantId;
import com.devmod.foundry.tool.part.IMaterialItem;

/**
 * Item representing a materialized tool part.
 */
public class FoundryPartItem extends Item implements IMaterialItem {
    private static final String TAG_ROOT = "FoundryPart";
    private static final String TAG_MATERIAL = "Material";
    private static final String TAG_QUALITY = "Quality";
    private static final String TAG_PURITY = "Purity";

    private final FoundryPartType partType;

    public FoundryPartItem(FoundryPartType partType) {
        super(new Item.Properties().stacksTo(1));
        this.partType = partType;
    }

    public FoundryPartType getPartType() {
        return partType;
    }

    public ItemStack createWithMaterial(ResourceLocation materialId) {
        ItemStack stack = new ItemStack(this);
        setMaterial(stack, materialId);
        setQuality(stack, MaterialQuality.STANDARD);
        setPurity(stack, 1.0f);
        return stack;
    }

    public ItemStack createWithMaterial(ResourceLocation materialId, MaterialQuality quality) {
        ItemStack stack = new ItemStack(this);
        setMaterial(stack, materialId);
        setQuality(stack, quality);
        setPurity(stack, 1.0f);
        return stack;
    }

    public void setMaterial(ItemStack stack, ResourceLocation materialId) {
        CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = existing.copyTag();
        CompoundTag partTag = root.contains(TAG_ROOT) ? root.getCompound(TAG_ROOT) : new CompoundTag();
        partTag.putString(TAG_MATERIAL, materialId.toString());
        root.put(TAG_ROOT, partTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public Optional<ResourceLocation> getMaterialId(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (!root.contains(TAG_ROOT)) {
            return Optional.empty();
        }
        CompoundTag partTag = root.getCompound(TAG_ROOT);
        return Optional.ofNullable(ResourceLocation.tryParse(partTag.getString(TAG_MATERIAL)));
    }

    @Override
    public MaterialVariantId getMaterial(ItemStack stack) {
        return getMaterialId(stack)
            .map(id -> MaterialVariantId.create(id, ""))
            .orElse(IMaterial.UNKNOWN_ID);
    }

    public void setQuality(ItemStack stack, MaterialQuality quality) {
        CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = existing.copyTag();
        CompoundTag partTag = root.contains(TAG_ROOT) ? root.getCompound(TAG_ROOT) : new CompoundTag();
        partTag.putString(TAG_QUALITY, quality.getSerializedName());
        root.put(TAG_ROOT, partTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public MaterialQuality getQuality(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (!root.contains(TAG_ROOT)) {
            return MaterialQuality.STANDARD;
        }
        CompoundTag partTag = root.getCompound(TAG_ROOT);
        if (!partTag.contains(TAG_QUALITY)) {
            return MaterialQuality.STANDARD;
        }
        return MaterialQuality.fromName(partTag.getString(TAG_QUALITY));
    }

    public void setPurity(ItemStack stack, float purity) {
        CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = existing.copyTag();
        CompoundTag partTag = root.contains(TAG_ROOT) ? root.getCompound(TAG_ROOT) : new CompoundTag();
        partTag.putFloat(TAG_PURITY, clampPurity(purity));
        root.put(TAG_ROOT, partTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public float getPurity(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (!root.contains(TAG_ROOT)) {
            return 1.0f;
        }
        CompoundTag partTag = root.getCompound(TAG_ROOT);
        if (!partTag.contains(TAG_PURITY)) {
            return 1.0f;
        }
        return clampPurity(partTag.getFloat(TAG_PURITY));
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull java.util.List<Component> tooltip,
        @Nonnull net.minecraft.world.item.TooltipFlag flag
    ) {
        getMaterialId(stack).ifPresent(id ->
            tooltip.add(Component.translatable("tooltip.devmod.foundry.material", id.getPath()))
        );
        MaterialQuality quality = getQuality(stack);
        tooltip.add(quality.getColoredDisplayName());
        float purity = getPurity(stack);
        tooltip.add(Component.translatable("tooltip.devmod.foundry.purity", String.format("%.0f%%", purity * 100)));
    }

    private static float clampPurity(float purity) {
        return Math.max(0.1f, Math.min(1.0f, purity));
    }
}
