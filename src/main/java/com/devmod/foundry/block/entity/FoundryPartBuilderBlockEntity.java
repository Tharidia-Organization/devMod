package com.devmod.foundry.block.entity;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.menu.FoundryPartBuilderMenu;
import com.devmod.foundry.tool.FoundryPartItem;
import com.devmod.foundry.tool.FoundryPatternItem;
import com.devmod.foundry.tool.FoundryToolItems;
import com.devmod.foundry.tool.material.FoundryMaterialDefinition;
import com.devmod.foundry.tool.material.FoundryMaterialRegistry;

/**
 * Block entity for the Part Builder.
 */
public class FoundryPartBuilderBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements MenuProvider {
    private static final String TAG_INVENTORY = "Inventory";

    public static final int SLOT_PATTERN = 0;
    public static final int SLOT_MATERIAL = 1;
    public static final int SLOT_OUTPUT = 2;

    private final SimpleContainer inventory = new SimpleContainer(3);

    public FoundryPartBuilderBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_PART_BUILDER.get()), pos, state);
    }

    public void tickServer() {
        updateOutput();
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public void consumeInputs() {
        ItemStack pattern = inventory.getItem(SLOT_PATTERN);
        ItemStack material = inventory.getItem(SLOT_MATERIAL);
        if (pattern.isEmpty() || material.isEmpty()) {
            return;
        }
        pattern.shrink(1);
        material.shrink(1);
        if (pattern.isEmpty()) {
            inventory.setItem(SLOT_PATTERN, ItemStack.EMPTY);
        }
        if (material.isEmpty()) {
            inventory.setItem(SLOT_MATERIAL, ItemStack.EMPTY);
        }
    }

    private void updateOutput() {
        ItemStack pattern = inventory.getItem(SLOT_PATTERN);
        ItemStack material = inventory.getItem(SLOT_MATERIAL);
        if (!(pattern.getItem() instanceof FoundryPatternItem patternItem)) {
            inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
            return;
        }
        if (material.isEmpty()) {
            inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
            return;
        }

        FoundryMaterialDefinition materialDef = FoundryMaterialRegistry.findMaterial(material).orElse(null);
        if (materialDef == null) {
            inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
            return;
        }

        FoundryPartItem partItem = FoundryToolItems.getPartItem(patternItem.getPartType());
        if (partItem == null) {
            inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
            return;
        }
        ItemStack output = partItem.createWithMaterial(materialDef.id());
        inventory.setItem(SLOT_OUTPUT, output);
    }

    @Override
    @Nonnull
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.devmod.foundry_part_builder");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull Player player) {
        return new FoundryPartBuilderMenu(containerId, playerInv, this);
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag invTag = new CompoundTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                invTag.put("Slot" + i, stack.save(registries));
            }
        }
        tag.put(TAG_INVENTORY, invTag);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_INVENTORY)) {
            CompoundTag invTag = tag.getCompound(TAG_INVENTORY);
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                String key = "Slot" + i;
                if (invTag.contains(key)) {
                    inventory.setItem(i, ItemStack.parse(registries, invTag.getCompound(key)).orElse(ItemStack.EMPTY));
                }
            }
        }
    }
}
