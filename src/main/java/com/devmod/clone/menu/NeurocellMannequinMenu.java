package com.devmod.clone.menu;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
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
import com.devmod.clone.block.entity.NeurocellMannequinBlockEntity;

/**
 * Container menu for the Neurocell Mannequin block.
 * 6 equipment slots with type validation + player inventory.
 *
 * Slot layout:
 * - 0: HEAD (helmet)
 * - 1: CHEST (chestplate)
 * - 2: LEGS (leggings)
 * - 3: FEET (boots)
 * - 4: MAINHAND (weapon/tool)
 * - 5: OFFHAND (shield/item)
 */
public class NeurocellMannequinMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerLevelAccess access;

    /** Block position for client-side network packets */
    @Nullable
    private final BlockPos blockPos;

    /** Current skin UUID (synced from server) */
    @Nullable
    private UUID skinUUID;

    /**
     * Client-side constructor (called from network).
     */
    @SuppressWarnings("this-escape")
    public NeurocellMannequinMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new SimpleContainer(6), ContainerLevelAccess.NULL,
             buf.readBlockPos(), buf.readBoolean() ? buf.readUUID() : null);
    }

    /**
     * Server-side constructor (called when opening menu).
     */
    @SuppressWarnings("this-escape")
    public NeurocellMannequinMenu(int containerId, Inventory playerInv, NeurocellMannequinBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity.getInventory(),
             ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()),
                                          Objects.requireNonNull(blockEntity.getBlockPos())),
             blockEntity.getBlockPos(), blockEntity.getSkinUUID());
    }

    /**
     * Main constructor.
     */
    @SuppressWarnings("this-escape")
    private NeurocellMannequinMenu(int containerId, Inventory playerInv, Container container,
                                    ContainerLevelAccess access, @Nullable BlockPos blockPos,
                                    @Nullable UUID skinUUID) {
        super(CloneMenus.NEUROCELL_MANNEQUIN.get(), containerId);
        this.container = container;
        this.access = access;
        this.blockPos = blockPos;
        this.skinUUID = skinUUID;

        checkContainerSize(Objects.requireNonNull(container), 6);

        // Equipment slots arranged like armor stand display
        // Left column (armor): HEAD, CHEST, LEGS, FEET
        // Right column (hands): MAINHAND, OFFHAND

        // HEAD slot (helmet) - position 8, 8
        this.addSlot(new EquipmentSlot(Objects.requireNonNull(container), 0, 8, 8));

        // CHEST slot (chestplate) - position 8, 26
        this.addSlot(new EquipmentSlot(Objects.requireNonNull(container), 1, 8, 26));

        // LEGS slot (leggings) - position 8, 44
        this.addSlot(new EquipmentSlot(Objects.requireNonNull(container), 2, 8, 44));

        // FEET slot (boots) - position 8, 62
        this.addSlot(new EquipmentSlot(Objects.requireNonNull(container), 3, 8, 62));

        // MAINHAND slot - position 152, 26
        this.addSlot(new EquipmentSlot(Objects.requireNonNull(container), 4, 152, 26));

        // OFFHAND slot - position 152, 44
        this.addSlot(new EquipmentSlot(Objects.requireNonNull(container), 5, 152, 44));

        // Player inventory (3 rows) - starts at y=84
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(Objects.requireNonNull(playerInv), col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Hotbar - starts at y=142
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

            if (index < 6) {
                // Moving FROM equipment slot to player inventory
                if (!this.moveItemStackTo(slotItem, 6, 42, true)) {
                    return Objects.requireNonNull(ItemStack.EMPTY);
                }
            } else {
                // Moving FROM player inventory TO equipment slots
                // Try to place in appropriate slot based on item type
                boolean moved = false;

                // Check armor slots first (0-3)
                for (int i = 0; i < 4; i++) {
                    if (NeurocellMannequinBlockEntity.isValidForSlot(i, slotItem) && !this.slots.get(i).hasItem()) {
                        if (this.moveItemStackTo(slotItem, i, i + 1, false)) {
                            moved = true;
                            break;
                        }
                    }
                }

                // Then try hand slots (4-5)
                if (!moved) {
                    for (int i = 4; i < 6; i++) {
                        if (!this.slots.get(i).hasItem()) {
                            if (this.moveItemStackTo(slotItem, i, i + 1, false)) {
                                moved = true;
                                break;
                            }
                        }
                    }
                }

                // If still not moved, shift between inventory sections
                if (!moved) {
                    if (index < 33) {
                        // From main inventory to hotbar
                        if (!this.moveItemStackTo(slotItem, 33, 42, false)) {
                            return Objects.requireNonNull(ItemStack.EMPTY);
                        }
                    } else {
                        // From hotbar to main inventory
                        if (!this.moveItemStackTo(slotItem, 6, 33, false)) {
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
        return stillValid(Objects.requireNonNull(this.access), player, Objects.requireNonNull(CloneBlocks.NEUROCELL_MANNEQUIN.get()));
    }

    /**
     * Get the container for external access.
     */
    public Container getContainer() {
        return container;
    }

    /**
     * Get the block position for network packets.
     */
    @Nullable
    public BlockPos getBlockPos() {
        return blockPos;
    }

    /**
     * Get the current skin UUID.
     */
    @Nullable
    public UUID getSkinUUID() {
        return skinUUID;
    }

    /**
     * Set the skin UUID (client-side update after server confirmation).
     */
    public void setSkinUUID(@Nullable UUID uuid) {
        this.skinUUID = uuid;
    }

    /**
     * Write additional data to the network buffer when opening menu.
     * Called from MenuProvider.
     */
    public static void writeExtraData(FriendlyByteBuf buf, NeurocellMannequinBlockEntity blockEntity) {
        buf.writeBlockPos(Objects.requireNonNull(blockEntity.getBlockPos()));
        UUID skin = blockEntity.getSkinUUID();
        buf.writeBoolean(skin != null);
        if (skin != null) {
            buf.writeUUID(skin);
        }
    }

    /**
     * Custom slot class with equipment validation.
     */
    private static class EquipmentSlot extends Slot {

        public EquipmentSlot(Container container, int slot, int x, int y) {
            super(Objects.requireNonNull(container), slot, x, y);
        }

        @Override
        public boolean mayPlace(@Nonnull ItemStack stack) {
            return NeurocellMannequinBlockEntity.isValidForSlot(this.getSlotIndex(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
