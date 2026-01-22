package com.devmod.foundry.block.entity;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.resources.ResourceLocation;

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.menu.FoundryStencilTableMenu;
import com.devmod.foundry.tool.FoundryBlankStencilItem;
import com.devmod.foundry.tool.FoundryPartType;
import com.devmod.foundry.tool.FoundryPartTypes;
import com.devmod.foundry.tool.FoundryPatternItem;
import com.devmod.foundry.tool.FoundryToolItems;
import com.devmod.foundry.util.FoundryContainers;

/**
 * Block entity for the Stencil Table.
 * Handles converting blank stencils into specific patterns.
 */
public class FoundryStencilTableBlockEntity extends BlockEntity implements MenuProvider {
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_SELECTED_PATTERN = "SelectedPattern";

    public static final int SLOT_STENCIL = 0;
    public static final int SLOT_OUTPUT = 1;

    private final SimpleContainer inventory;
    @Nullable
    private FoundryPartType selectedPattern = null;

    public FoundryStencilTableBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_STENCIL_TABLE.get()), pos, state);
        this.inventory = FoundryContainers.tracked(2);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        FoundryContainers.bind(inventory, this);
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    @Nullable
    public FoundryPartType getSelectedPattern() {
        return selectedPattern;
    }

    /**
     * Sets the selected pattern type and updates the output.
     */
    public void setSelectedPattern(@Nullable FoundryPartType pattern) {
        this.selectedPattern = pattern;
        updateOutput();
        setChanged();
    }

    /**
     * Consumes one blank stencil and produces the selected pattern.
     */
    public void consumeStencil() {
        ItemStack stencil = inventory.getItem(SLOT_STENCIL);
        if (stencil.isEmpty() || !(stencil.getItem() instanceof FoundryBlankStencilItem)) {
            return;
        }
        if (selectedPattern == null) {
            return;
        }
        stencil.shrink(1);
        if (stencil.isEmpty()) {
            inventory.setItem(SLOT_STENCIL, Objects.requireNonNull(ItemStack.EMPTY));
        }
        setChanged();
    }

    /**
     * Updates the output slot based on the stencil input and selected pattern.
     */
    public void updateOutput() {
        ItemStack stencil = inventory.getItem(SLOT_STENCIL);

        if (stencil.isEmpty() || !(stencil.getItem() instanceof FoundryBlankStencilItem)) {
            inventory.setItem(SLOT_OUTPUT, Objects.requireNonNull(ItemStack.EMPTY));
            return;
        }

        if (selectedPattern == null) {
            inventory.setItem(SLOT_OUTPUT, Objects.requireNonNull(ItemStack.EMPTY));
            return;
        }

        // Find the pattern item for the selected pattern type
        FoundryPatternItem patternItem = findPatternItem(selectedPattern);
        if (patternItem == null) {
            inventory.setItem(SLOT_OUTPUT, Objects.requireNonNull(ItemStack.EMPTY));
            return;
        }

        // Create a new pattern item
        ItemStack output = new ItemStack(patternItem);
        inventory.setItem(SLOT_OUTPUT, Objects.requireNonNull(output));
    }

    @Nullable
    private FoundryPatternItem findPatternItem(FoundryPartType partType) {
        for (FoundryPatternItem pattern : FoundryToolItems.getPatternItems()) {
            if (pattern.getPartType().equals(partType)) {
                return pattern;
            }
        }
        return null;
    }

    @Override
    @Nonnull
    public Component getDisplayName() {
        return Objects.requireNonNull(Component.translatable("block.devmod.foundry_stencil_table"));
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull Player player) {
        return new FoundryStencilTableMenu(containerId, playerInv, this);
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
        if (selectedPattern != null) {
            tag.putString(TAG_SELECTED_PATTERN, selectedPattern.id().toString());
        }
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_INVENTORY)) {
            CompoundTag invTag = tag.getCompound(TAG_INVENTORY);
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                String key = "Slot" + i;
                if (invTag.contains(key)) {
                    inventory.setItem(i, Objects.requireNonNull(
                        ItemStack.parse(registries, Objects.requireNonNull(invTag.getCompound(key)))
                            .orElse(ItemStack.EMPTY)
                    ));
                }
            }
        }
        if (tag.contains(TAG_SELECTED_PATTERN)) {
            String patternIdStr = tag.getString(TAG_SELECTED_PATTERN);
            ResourceLocation patternId = ResourceLocation.tryParse(patternIdStr);
            if (patternId != null && FoundryPartTypes.all().containsKey(patternId)) {
                selectedPattern = FoundryPartTypes.get(patternId);
            }
        }
    }
}
