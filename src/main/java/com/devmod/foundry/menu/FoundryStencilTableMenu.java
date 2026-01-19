package com.devmod.foundry.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.devmod.foundry.FoundryBlocks;
import com.devmod.foundry.FoundryMenus;
import com.devmod.foundry.block.entity.FoundryStencilTableBlockEntity;
import com.devmod.foundry.tool.FoundryBlankStencilItem;
import com.devmod.foundry.tool.FoundryPartType;
import com.devmod.foundry.tool.FoundryPartTypes;

/**
 * Container menu for the Foundry Stencil Table.
 * Allows players to convert blank stencils into specific patterns.
 */
public class FoundryStencilTableMenu extends AbstractContainerMenu {
    public static final int SLOT_STENCIL = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int CONTAINER_SIZE = 2;

    private final ContainerLevelAccess access;
    @Nullable
    private final FoundryStencilTableBlockEntity blockEntity;
    private final List<FoundryPartType> availablePatterns;
    private int selectedPatternIndex = -1;

    public FoundryStencilTableMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new SimpleContainer(CONTAINER_SIZE), ContainerLevelAccess.NULL, null);
    }

    public FoundryStencilTableMenu(int containerId, Inventory playerInv, FoundryStencilTableBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity.getInventory(),
            ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), Objects.requireNonNull(blockEntity.getBlockPos())),
            blockEntity);
    }

    @SuppressWarnings("this-escape")
    private FoundryStencilTableMenu(
        int containerId,
        Inventory playerInv,
        Container container,
        ContainerLevelAccess access,
        @Nullable FoundryStencilTableBlockEntity blockEntity
    ) {
        super(FoundryMenus.FOUNDRY_STENCIL_TABLE.get(), containerId);
        var containerObj = Objects.requireNonNull(container);
        this.access = Objects.requireNonNull(access);
        this.blockEntity = blockEntity;
        this.availablePatterns = new ArrayList<>(FoundryPartTypes.all().values());

        checkContainerSize(containerObj, CONTAINER_SIZE);

        // Stencil input slot
        this.addSlot(new Slot(containerObj, SLOT_STENCIL, 33, 35) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return stack.getItem() instanceof FoundryBlankStencilItem;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (FoundryStencilTableMenu.this.blockEntity != null) {
                    FoundryStencilTableMenu.this.blockEntity.updateOutput();
                }
            }
        });

        // Output slot
        this.addSlot(new Slot(containerObj, SLOT_OUTPUT, 143, 35) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(@Nonnull Player player, @Nonnull ItemStack stack) {
                super.onTake(player, stack);
                if (FoundryStencilTableMenu.this.blockEntity != null) {
                    FoundryStencilTableMenu.this.blockEntity.consumeStencil();
                }
            }
        });

        // Player inventory
        var inv = Objects.requireNonNull(playerInv);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }

        // Initialize selected pattern from block entity
        if (blockEntity != null && blockEntity.getSelectedPattern() != null) {
            FoundryPartType selected = blockEntity.getSelectedPattern();
            for (int i = 0; i < availablePatterns.size(); i++) {
                if (availablePatterns.get(i).equals(selected)) {
                    selectedPatternIndex = i;
                    break;
                }
            }
        }
    }

    /**
     * Gets the list of available patterns.
     */
    public List<FoundryPartType> getAvailablePatterns() {
        return availablePatterns;
    }

    /**
     * Gets the currently selected pattern index.
     */
    public int getSelectedPatternIndex() {
        return selectedPatternIndex;
    }

    /**
     * Gets the currently selected pattern type.
     */
    @Nullable
    public FoundryPartType getSelectedPattern() {
        if (selectedPatternIndex >= 0 && selectedPatternIndex < availablePatterns.size()) {
            return availablePatterns.get(selectedPatternIndex);
        }
        return null;
    }

    /**
     * Called when a pattern is selected in the UI.
     */
    public void selectPattern(int index) {
        if (index < 0 || index >= availablePatterns.size()) {
            selectedPatternIndex = -1;
            if (blockEntity != null) {
                blockEntity.setSelectedPattern(null);
            }
        } else {
            selectedPatternIndex = index;
            if (blockEntity != null) {
                blockEntity.setSelectedPattern(availablePatterns.get(index));
            }
        }
    }

    /**
     * Selects a pattern by its ResourceLocation ID.
     */
    public void selectPatternById(ResourceLocation id) {
        for (int i = 0; i < availablePatterns.size(); i++) {
            if (availablePatterns.get(i).id().equals(id)) {
                selectPattern(i);
                return;
            }
        }
        selectPattern(-1);
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
                // Moving from container to player inventory
                if (!this.moveItemStackTo(slotItem, CONTAINER_SIZE, CONTAINER_SIZE + 36, true)) {
                    return emptyStack;
                }
            } else {
                // Moving from player inventory to container
                if (slotItem.getItem() instanceof FoundryBlankStencilItem) {
                    if (!this.moveItemStackTo(slotItem, SLOT_STENCIL, SLOT_STENCIL + 1, false)) {
                        return emptyStack;
                    }
                } else if (index < CONTAINER_SIZE + 27) {
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
        return stillValid(access, player, Objects.requireNonNull(FoundryBlocks.FOUNDRY_STENCIL_TABLE.get()));
    }
}
