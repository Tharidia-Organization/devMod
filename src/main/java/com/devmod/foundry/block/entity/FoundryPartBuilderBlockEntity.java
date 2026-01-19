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
import com.devmod.foundry.quality.FoundryItemQuality;
import com.devmod.foundry.quality.MaterialQuality;
import com.devmod.foundry.quality.QualityCalculator;
import com.devmod.foundry.tool.FoundryPartItem;
import com.devmod.foundry.tool.FoundryPatternItem;
import com.devmod.foundry.tool.FoundryToolItems;
import com.devmod.foundry.tool.material.FoundryMaterialDefinition;
import com.devmod.foundry.tool.material.FoundryMaterialRegistry;
import com.devmod.foundry.tool.material.FoundryMaterialStats;

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
        if (material.isEmpty() || !(pattern.getItem() instanceof FoundryPatternItem patternItem)) {
            return;
        }
        FoundryMaterialDefinition materialDef = FoundryMaterialRegistry.findMaterial(material).orElse(null);
        if (materialDef == null) {
            return;
        }
        int cost = Math.max(1, patternItem.getPartType().materialCost());
        material.shrink(cost);
        if (material.isEmpty()) {
            inventory.setItem(SLOT_MATERIAL, Objects.requireNonNull(ItemStack.EMPTY));
        }
        FoundryMaterialStats stats = materialDef.getStats(patternItem.getPartType().statKey());
        int hardness = Math.max(1, stats.miningLevel());
        boolean stillUsable = patternItem.usePattern(pattern, materialDef.id(), hardness);
        if (!stillUsable) {
            inventory.setItem(SLOT_PATTERN, Objects.requireNonNull(ItemStack.EMPTY));
        }
        setChanged();
    }

    private void updateOutput() {
        ItemStack pattern = inventory.getItem(SLOT_PATTERN);
        ItemStack material = inventory.getItem(SLOT_MATERIAL);
        if (!(pattern.getItem() instanceof FoundryPatternItem patternItem)) {
            inventory.setItem(SLOT_OUTPUT, Objects.requireNonNull(ItemStack.EMPTY));
            return;
        }
        if (material.isEmpty()) {
            inventory.setItem(SLOT_OUTPUT, Objects.requireNonNull(ItemStack.EMPTY));
            return;
        }
        int cost = Math.max(1, patternItem.getPartType().materialCost());
        if (material.getCount() < cost) {
            inventory.setItem(SLOT_OUTPUT, Objects.requireNonNull(ItemStack.EMPTY));
            return;
        }

        FoundryMaterialDefinition materialDef = FoundryMaterialRegistry.findMaterial(material).orElse(null);
        if (materialDef == null) {
            inventory.setItem(SLOT_OUTPUT, Objects.requireNonNull(ItemStack.EMPTY));
            return;
        }

        FoundryPartItem partItem = FoundryToolItems.getPartItem(patternItem.getPartType());
        if (partItem == null) {
            inventory.setItem(SLOT_OUTPUT, Objects.requireNonNull(ItemStack.EMPTY));
            return;
        }
        float patternQuality = patternItem.getQualityBonus(pattern);
        float specBonus = patternItem.getSpecializationBonus(pattern, materialDef.id());
        float effectivePatternQuality = Math.max(0.8f, Math.min(1.3f, patternQuality * specBonus));
        MaterialQuality materialQuality = FoundryItemQuality.getQuality(material);
        MaterialQuality partQuality = QualityCalculator.calculateCastingQuality(
            materialQuality,
            effectivePatternQuality,
            1.0f
        );
        ItemStack output = partItem.createWithMaterial(materialDef.id(), partQuality);
        inventory.setItem(SLOT_OUTPUT, Objects.requireNonNull(output));
    }

    @Override
    @Nonnull
    public net.minecraft.network.chat.Component getDisplayName() {
        return Objects.requireNonNull(net.minecraft.network.chat.Component.translatable("block.devmod.foundry_part_builder"));
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
                invTag.put("Slot" + i, Objects.requireNonNull(stack.save(registries)));
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
                    inventory.setItem(i, Objects.requireNonNull(ItemStack.parse(registries, Objects.requireNonNull(invTag.getCompound(key))).orElse(ItemStack.EMPTY)));
                }
            }
        }
    }
}
