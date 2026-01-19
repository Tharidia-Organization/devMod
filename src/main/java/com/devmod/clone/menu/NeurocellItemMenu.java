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
import com.devmod.clone.block.entity.NeurocellItemBlockEntity;

/**
 * Container menu for the Neurocell Item Display block.
 * Single slot for any item + player inventory.
 */
public class NeurocellItemMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerLevelAccess access;

    /**
     * Client-side constructor (called from network).
     */
    @SuppressWarnings("this-escape")
    public NeurocellItemMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new SimpleContainer(1), ContainerLevelAccess.NULL);
    }

    /**
     * Server-side constructor (called when opening menu).
     */
    @SuppressWarnings("this-escape")
    public NeurocellItemMenu(int containerId, Inventory playerInv, NeurocellItemBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity.getInventory(),
             ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()),
                                          Objects.requireNonNull(blockEntity.getBlockPos())));
    }

    /**
     * Main constructor.
     */
    @SuppressWarnings("this-escape")
    private NeurocellItemMenu(int containerId, Inventory playerInv, Container container, ContainerLevelAccess access) {
        super(CloneMenus.NEUROCELL_ITEM.get(), containerId);
        this.container = container;
        this.access = access;

        checkContainerSize(Objects.requireNonNull(container), 1);

        // Display item slot (centered at 80, 35)
        this.addSlot(new Slot(Objects.requireNonNull(container), 0, 80, 35) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(Objects.requireNonNull(playerInv), col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(Objects.requireNonNull(playerInv), col, 8 + col * 18, 142));
        }
    }

    @Override
    @Nonnull
    public ItemStack quickMoveStack(@Nonnull Player player, int index) {
        ItemStack result = Objects.requireNonNull(ItemStack.EMPTY);
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotItem = slot.getItem();
            result = slotItem.copy();

            if (index == 0) {
                // Moving FROM display slot to player inventory
                if (!this.moveItemStackTo(slotItem, 1, 37, true)) {
                    return Objects.requireNonNull(ItemStack.EMPTY);
                }
            } else {
                // Moving TO display slot (any item allowed)
                if (!this.moveItemStackTo(slotItem, 0, 1, false)) {
                    // If display slot is full, move between inventory sections
                    if (index < 28) {
                        // From main inventory to hotbar
                        if (!this.moveItemStackTo(slotItem, 28, 37, false)) {
                            return Objects.requireNonNull(ItemStack.EMPTY);
                        }
                    } else {
                        // From hotbar to main inventory
                        if (!this.moveItemStackTo(slotItem, 1, 28, false)) {
                            return Objects.requireNonNull(ItemStack.EMPTY);
                        }
                    }
                }
            }

            if (slotItem.isEmpty()) {
                slot.setByPlayer(Objects.requireNonNull(ItemStack.EMPTY));
            } else {
                slot.setChanged();
            }

            if (slotItem.getCount() == result.getCount()) {
                return Objects.requireNonNull(ItemStack.EMPTY);
            }

            slot.onTake(player, slotItem);
        }

        return Objects.requireNonNull(result);
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        return stillValid(Objects.requireNonNull(this.access), player, Objects.requireNonNull(CloneBlocks.NEUROCELL_ITEM.get()));
    }

    /**
     * Get the container for external access.
     */
    public Container getContainer() {
        return container;
    }
}
