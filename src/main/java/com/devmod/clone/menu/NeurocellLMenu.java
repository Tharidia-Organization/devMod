package com.devmod.clone.menu;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.devmod.clone.CloneBlocks;
import com.devmod.clone.CloneMenus;
import com.devmod.clone.block.entity.NeurocellLBlockEntity;
import com.devmod.clone.item.BioscannerItem;

/**
 * Container menu for the NeurocellL block (2x2x2 large chamber).
 * Single slot for bioscanner item with player inventory.
 */
public class NeurocellLMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerLevelAccess access;

    /**
     * Client-side constructor (called from network).
     */
    @SuppressWarnings("this-escape")
    public NeurocellLMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new SimpleContainer(1), ContainerLevelAccess.NULL);
    }

    /**
     * Server-side constructor (called when opening menu).
     */
    @SuppressWarnings("this-escape")
    public NeurocellLMenu(int containerId, Inventory playerInv, NeurocellLBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity.getInventory(),
             ContainerLevelAccess.create(
                 Objects.requireNonNull(blockEntity.getLevel()),
                 Objects.requireNonNull(blockEntity.getBlockPos())));
    }

    /**
     * Main constructor.
     */
    @SuppressWarnings("this-escape")
    private NeurocellLMenu(int containerId, Inventory playerInv, Container container, ContainerLevelAccess access) {
        super(CloneMenus.NEUROCELL_L.get(), containerId);
        var containerObj = Objects.requireNonNull(container);
        this.container = containerObj;
        this.access = Objects.requireNonNull(access);

        checkContainerSize(containerObj, 1);

        // Bioscanner slot (centered at 80, 35)
        this.addSlot(new Slot(containerObj, 0, 80, 35) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return stack.getItem() instanceof BioscannerItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Player inventory (3 rows)
        var inv = Objects.requireNonNull(playerInv);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    @Override
    @Nonnull
    public ItemStack quickMoveStack(@Nonnull Player player, int index) {
        var emptyStack = Objects.requireNonNull(ItemStack.EMPTY);
        ItemStack result = emptyStack;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotItem = slot.getItem();
            result = slotItem.copy();

            if (index == 0) {
                // Moving FROM neurocell slot to player inventory
                if (!this.moveItemStackTo(slotItem, 1, 37, true)) {
                    return emptyStack;
                }
            } else if (slotItem.getItem() instanceof BioscannerItem) {
                // Moving TO neurocell slot - only BioscannerItem allowed
                if (!this.moveItemStackTo(slotItem, 0, 1, false)) {
                    return emptyStack;
                }
            } else {
                // Not a bioscanner - move between inventory sections
                if (index < 28) {
                    // From main inventory to hotbar
                    if (!this.moveItemStackTo(slotItem, 28, 37, false)) {
                        return emptyStack;
                    }
                } else {
                    // From hotbar to main inventory
                    if (!this.moveItemStackTo(slotItem, 1, 28, false)) {
                        return emptyStack;
                    }
                }
            }

            if (slotItem.isEmpty()) {
                slot.setByPlayer(emptyStack);
            } else {
                slot.setChanged();
            }

            if (slotItem.getCount() == result.getCount()) {
                return emptyStack;
            }

            slot.onTake(player, slotItem);
        }

        return result;
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        return stillValid(
            Objects.requireNonNull(this.access),
            player,
            Objects.requireNonNull(CloneBlocks.NEUROCELL_L.get()));
    }

    /**
     * Clear the bioscanner data in slot 0.
     */
    public void clearBioscanner() {
        ItemStack stack = container.getItem(0);
        if (!stack.isEmpty() && BioscannerItem.hasData(stack)) {
            BioscannerItem.clearData(stack);
            container.setChanged();
        }
    }

    /**
     * Get the container for external access.
     */
    public Container getContainer() {
        return container;
    }
}
