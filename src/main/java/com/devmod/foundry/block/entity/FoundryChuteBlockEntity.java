package com.devmod.foundry.block.entity;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.block.FoundryChuteBlock;
import com.devmod.foundry.menu.FoundryControllerMenu;

/**
 * Chute block entity for pushing items into the foundry controller.
 * Supports item filtering - set filter by shift+right-clicking with an item.
 */
public class FoundryChuteBlockEntity extends FoundryComponentBlockEntity implements WorldlyContainer {
    private static final String TAG_ITEM = "Item";
    private static final String TAG_FILTER = "Filter";
    private static final int[] SLOTS = new int[] {0};

    private final SimpleContainer inventory = new SimpleContainer(1);
    private ItemStack filterStack = ItemStack.EMPTY;

    public FoundryChuteBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_CHUTE.get()), pos, state);
    }

    @Override
    public void setControllerPos(@Nullable BlockPos pos) {
        super.setControllerPos(pos);
        updateInStructureState(pos != null);
    }

    private void updateInStructureState(boolean inStructure) {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(FoundryChuteBlock.IN_STRUCTURE) && state.getValue(FoundryChuteBlock.IN_STRUCTURE) != inStructure) {
            level.setBlock(worldPosition, state.setValue(FoundryChuteBlock.IN_STRUCTURE, inStructure), 3);
        }
    }

    public void tickServer() {
        Level level = Objects.requireNonNull(getLevel());
        if (level.isClientSide) {
            return;
        }
        ItemStack stack = inventory.getItem(0);
        if (stack.isEmpty()) {
            return;
        }
        FoundryControllerBlockEntity controller = getController(level);
        if (controller == null || !controller.isFormed()) {
            return;
        }
        ItemStack remaining = insertIntoController(controller, stack);
        if (remaining.getCount() != stack.getCount()) {
            inventory.setItem(0, remaining);
            sync();
        }
    }

    public ItemStack insertManual(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        // Check filter
        if (!matchesFilter(stack)) {
            return stack;
        }
        ItemStack slot = inventory.getItem(0);
        if (slot.isEmpty()) {
            inventory.setItem(0, Objects.requireNonNull(stack.copy()));
            sync();
            return Objects.requireNonNull(ItemStack.EMPTY);
        }
        if (!ItemStack.isSameItemSameComponents(slot, stack)) {
            return stack;
        }
        int max = slot.getMaxStackSize();
        if (slot.getCount() >= max) {
            return stack;
        }
        int move = Math.min(stack.getCount(), max - slot.getCount());
        slot.grow(move);
        ItemStack remaining = stack.copy();
        remaining.shrink(move);
        inventory.setItem(0, slot);
        sync();
        return remaining;
    }

    /**
     * Sets the filter item. Only items matching this filter will be accepted.
     * @param filter the filter item (empty = no filter, accepts all)
     */
    public void setFilter(ItemStack filter) {
        this.filterStack = filter.isEmpty() ? ItemStack.EMPTY : filter.copyWithCount(1);
        sync();
    }

    /**
     * Gets the current filter item.
     */
    public ItemStack getFilter() {
        return filterStack;
    }

    /**
     * Clears the filter.
     */
    public void clearFilter() {
        this.filterStack = ItemStack.EMPTY;
        sync();
    }

    /**
     * Checks if the given stack matches the filter.
     */
    public boolean matchesFilter(@Nonnull ItemStack stack) {
        if (filterStack.isEmpty()) {
            return true; // No filter = accept all
        }
        return ItemStack.isSameItem(Objects.requireNonNull(filterStack), Objects.requireNonNull(stack));
    }

    @Nonnull
    public ItemStack extractManual() {
        ItemStack slot = inventory.getItem(0);
        if (slot.isEmpty()) {
            return Objects.requireNonNull(ItemStack.EMPTY);
        }
        inventory.setItem(0, Objects.requireNonNull(ItemStack.EMPTY));
        sync();
        return slot;
    }

    @Nonnull
    private ItemStack insertIntoController(FoundryControllerBlockEntity controller, @Nonnull ItemStack stack) {
        for (int i = 0; i < FoundryControllerMenu.SLOT_INPUT_COUNT; i++) {
            ItemStack slot = controller.getInventory().getItem(i);
            if (slot.isEmpty()) {
                controller.getInventory().setItem(i, Objects.requireNonNull(stack.copy()));
                controller.setChanged();
                return Objects.requireNonNull(ItemStack.EMPTY);
            }
            if (!ItemStack.isSameItemSameComponents(slot, Objects.requireNonNull(stack))) {
                continue;
            }
            int max = slot.getMaxStackSize();
            if (slot.getCount() >= max) {
                continue;
            }
            int move = Math.min(stack.getCount(), max - slot.getCount());
            slot.grow(move);
            controller.getInventory().setItem(i, slot);
            controller.setChanged();
            ItemStack remaining = stack.copy();
            remaining.shrink(move);
            return remaining;
        }
        return stack;
    }

    @Override
    public int getContainerSize() {
        return inventory.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return inventory.isEmpty();
    }

    @Override
    @Nonnull
    public ItemStack getItem(int slot) {
        return Objects.requireNonNull(inventory.getItem(slot));
    }

    @Override
    @Nonnull
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = Objects.requireNonNull(inventory.removeItem(slot, amount));
        if (!result.isEmpty()) {
            sync();
        }
        return result;
    }

    @Override
    @Nonnull
    public ItemStack removeItemNoUpdate(int slot) {
        return Objects.requireNonNull(inventory.removeItemNoUpdate(slot));
    }

    @Override
    public void setItem(int slot, @Nonnull ItemStack stack) {
        inventory.setItem(slot, stack);
        sync();
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        inventory.clearContent();
        sync();
    }

    @Override
    public int[] getSlotsForFace(@Nonnull Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, @Nonnull ItemStack stack, @Nullable Direction side) {
        return true;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, @Nonnull ItemStack stack, @Nonnull Direction side) {
        return true;
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ItemStack stack = inventory.getItem(0);
        if (!stack.isEmpty()) {
            tag.put(TAG_ITEM, Objects.requireNonNull(stack.save(registries)));
        }
        if (!filterStack.isEmpty()) {
            tag.put(TAG_FILTER, Objects.requireNonNull(filterStack.save(registries)));
        }
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_ITEM)) {
            inventory.setItem(0, Objects.requireNonNull(ItemStack.parseOptional(registries, Objects.requireNonNull(tag.getCompound(TAG_ITEM)))));
        } else {
            inventory.setItem(0, Objects.requireNonNull(ItemStack.EMPTY));
        }
        if (tag.contains(TAG_FILTER)) {
            filterStack = ItemStack.parseOptional(registries, Objects.requireNonNull(tag.getCompound(TAG_FILTER)));
        } else {
            filterStack = ItemStack.EMPTY;
        }
    }

    private void sync() {
        setChanged();
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            BlockPos pos = Objects.requireNonNull(worldPosition);
            BlockState state = Objects.requireNonNull(getBlockState());
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }
}
