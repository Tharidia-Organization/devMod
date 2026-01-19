package com.devmod.foundry.tool;

import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.tooltip.TooltipContext;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

/**
 * Item representing a materialized tool part.
 */
public class FoundryPartItem extends Item {
    private static final String TAG_ROOT = "FoundryPart";
    private static final String TAG_MATERIAL = "Material";

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
        return stack;
    }

    public void setMaterial(ItemStack stack, ResourceLocation materialId) {
        CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = existing.copyTag();
        CompoundTag partTag = new CompoundTag();
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
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull java.util.List<Component> tooltip,
        @Nonnull net.minecraft.world.item.TooltipFlag flag
    ) {
        getMaterialId(stack).ifPresent(id ->
            tooltip.add(Component.translatable("tooltip.devmod.foundry.material", id.getPath()))
        );
    }
}
