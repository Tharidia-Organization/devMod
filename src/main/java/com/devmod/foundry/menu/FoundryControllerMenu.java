package com.devmod.foundry.menu;

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

import com.devmod.foundry.FoundryBlocks;
import com.devmod.foundry.FoundryMenus;
import com.devmod.foundry.block.entity.FoundryControllerBlockEntity;

/**
 * Container menu for the Foundry Controller.
 * 4 input slots + 1 fuel slot.
 */
public class FoundryControllerMenu extends AbstractContainerMenu {
    public static final int SLOT_INPUT_START = 0;
    public static final int SLOT_INPUT_COUNT = 4;
    public static final int SLOT_FUEL = 4;
    public static final int CONTAINER_SIZE = 5;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    public FoundryControllerMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new SimpleContainer(CONTAINER_SIZE), ContainerLevelAccess.NULL, new SimpleContainerData(5));
    }

    public FoundryControllerMenu(int containerId, Inventory playerInv, FoundryControllerBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity.getInventory(),
            ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), Objects.requireNonNull(blockEntity.getBlockPos())),
            createContainerData(blockEntity));
    }

    private static ContainerData createContainerData(FoundryControllerBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> be.getProgress();
                    case 1 -> be.getMaxProgress();
                    case 2 -> be.getFuelTicks();
                    case 3 -> be.getFuelTicksMax();
                    case 4 -> be.getFuelTemperature();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Client-side read only.
            }

            @Override
            public int getCount() {
                return 5;
            }
        };
    }

    @SuppressWarnings("this-escape")
    private FoundryControllerMenu(int containerId, Inventory playerInv, Container container, ContainerLevelAccess access, ContainerData data) {
        super(FoundryMenus.FOUNDRY_CONTROLLER.get(), containerId);
        var containerObj = Objects.requireNonNull(container);
        this.access = Objects.requireNonNull(access);
        this.data = Objects.requireNonNull(data);

        checkContainerSize(containerObj, CONTAINER_SIZE);

        // Input slots: 2x2 grid
        this.addSlot(new Slot(containerObj, 0, 44, 21));
        this.addSlot(new Slot(containerObj, 1, 62, 21));
        this.addSlot(new Slot(containerObj, 2, 44, 39));
        this.addSlot(new Slot(containerObj, 3, 62, 39));

        // Fuel slot
        this.addSlot(new Slot(containerObj, SLOT_FUEL, 116, 39));

        // Player inventory
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

        addDataSlots(data);
    }

    public float getProgressPercent() {
        int max = data.get(1);
        return max > 0 ? (float) data.get(0) / max : 0.0f;
    }

    public float getFuelPercent() {
        int max = data.get(3);
        return max > 0 ? (float) data.get(2) / max : 0.0f;
    }

    public int getFuelTemperature() {
        return data.get(4);
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

            if (index < CONTAINER_SIZE) {
                if (!this.moveItemStackTo(slotItem, CONTAINER_SIZE, CONTAINER_SIZE + 36, true)) {
                    return emptyStack;
                }
            } else {
                if (!this.moveItemStackTo(slotItem, SLOT_INPUT_START, SLOT_FUEL + 1, false)) {
                    if (index < CONTAINER_SIZE + 27) {
                        if (!this.moveItemStackTo(slotItem, CONTAINER_SIZE + 27, CONTAINER_SIZE + 36, false)) {
                            return emptyStack;
                        }
                    } else if (!this.moveItemStackTo(slotItem, CONTAINER_SIZE, CONTAINER_SIZE + 27, false)) {
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
        return stillValid(access, player, Objects.requireNonNull(FoundryBlocks.FOUNDRY_CONTROLLER.get()));
    }
}
