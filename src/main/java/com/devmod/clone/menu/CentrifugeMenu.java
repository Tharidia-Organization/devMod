package com.devmod.clone.menu;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.devmod.clone.CloneBlocks;
import com.devmod.clone.CloneMenus;
import com.devmod.clone.block.entity.CentrifugeBlockEntity;

/**
 * Container menu for the Centrifuge block.
 * 3 input slots + 1 output slot with player inventory.
 */
public class CentrifugeMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    // Slot indices
    public static final int INPUT_SLOT_1 = 0;
    public static final int INPUT_SLOT_2 = 1;
    public static final int INPUT_SLOT_3 = 2;
    public static final int OUTPUT_SLOT = 3;
    public static final int CONTAINER_SIZE = 4;

    /**
     * Client-side constructor (called from network).
     */
    @SuppressWarnings("this-escape")
    public CentrifugeMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new SimpleContainer(CONTAINER_SIZE), ContainerLevelAccess.NULL, new SimpleContainerData(2));
    }

    /**
     * Server-side constructor (called when opening menu).
     */
    @SuppressWarnings("this-escape")
    public CentrifugeMenu(int containerId, Inventory playerInv, CentrifugeBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity.getInventory(),
             ContainerLevelAccess.create(
                 Objects.requireNonNull(blockEntity.getLevel()),
                 Objects.requireNonNull(blockEntity.getBlockPos())),
             createContainerData(blockEntity));
    }

    private static ContainerData createContainerData(CentrifugeBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> be.getProgress();
                    case 1 -> be.getMaxProgress();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Read-only on client
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    /**
     * Main constructor.
     */
    @SuppressWarnings("this-escape")
    private CentrifugeMenu(int containerId, Inventory playerInv, Container container, ContainerLevelAccess access, ContainerData data) {
        super(CloneMenus.CENTRIFUGE.get(), containerId);
        var containerObj = Objects.requireNonNull(container);
        this.container = containerObj;
        this.access = Objects.requireNonNull(access);
        this.data = Objects.requireNonNull(data);

        checkContainerSize(containerObj, CONTAINER_SIZE);

        // Input slots (top row: 44, 62, 80 at Y=20)
        this.addSlot(new Slot(containerObj, INPUT_SLOT_1, 44, 20));
        this.addSlot(new Slot(containerObj, INPUT_SLOT_2, 62, 20));
        this.addSlot(new Slot(containerObj, INPUT_SLOT_3, 80, 20));

        // Output slot (right side at 134, 35) - no input allowed
        this.addSlot(new Slot(containerObj, OUTPUT_SLOT, 134, 35) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return false; // Output only
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

        // Track data for progress bar
        addDataSlots(data);
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getMaxProgress() {
        int max = data.get(1);
        return max > 0 ? max : 1;
    }

    public float getProgressPercent() {
        int max = getMaxProgress();
        return max > 0 ? (float) getProgress() / max : 0;
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

            // Index 0-3 are container slots, 4-39 are player inventory
            if (index < CONTAINER_SIZE) {
                // Moving FROM centrifuge to player inventory
                if (!this.moveItemStackTo(slotItem, CONTAINER_SIZE, CONTAINER_SIZE + 36, true)) {
                    return emptyStack;
                }
            } else {
                // Moving TO centrifuge input slots (not output)
                if (!this.moveItemStackTo(slotItem, INPUT_SLOT_1, OUTPUT_SLOT, false)) {
                    // Move between inventory sections
                    if (index < CONTAINER_SIZE + 27) {
                        // From main inventory to hotbar
                        if (!this.moveItemStackTo(slotItem, CONTAINER_SIZE + 27, CONTAINER_SIZE + 36, false)) {
                            return emptyStack;
                        }
                    } else {
                        // From hotbar to main inventory
                        if (!this.moveItemStackTo(slotItem, CONTAINER_SIZE, CONTAINER_SIZE + 27, false)) {
                            return emptyStack;
                        }
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
            Objects.requireNonNull(CloneBlocks.CENTRIFUGE.get()));
    }

    /**
     * Get the container for external access.
     */
    public Container getContainer() {
        return container;
    }
}
