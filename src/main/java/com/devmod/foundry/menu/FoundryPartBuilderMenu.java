package com.devmod.foundry.menu;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
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
import com.devmod.foundry.block.entity.FoundryPartBuilderBlockEntity;
import com.devmod.foundry.progression.FoundryPlayerProgress;
import com.devmod.foundry.progression.FoundryProgressAttachment;
import com.devmod.foundry.tool.FoundryPartItem;
import com.devmod.foundry.tool.FoundryPatternItem;
import com.devmod.foundry.tool.material.FoundryMaterialDefinition;
import com.devmod.foundry.tool.material.FoundryMaterialRegistry;

/**
 * Container menu for the Foundry Part Builder.
 */
public class FoundryPartBuilderMenu extends AbstractContainerMenu {
    public static final int SLOT_PATTERN = 0;
    public static final int SLOT_MATERIAL = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int CONTAINER_SIZE = 3;

    private final ContainerLevelAccess access;
    @Nullable private final FoundryPartBuilderBlockEntity blockEntity;

    public FoundryPartBuilderMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new SimpleContainer(CONTAINER_SIZE), ContainerLevelAccess.NULL, null);
    }

    public FoundryPartBuilderMenu(int containerId, Inventory playerInv, FoundryPartBuilderBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity.getInventory(),
            ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), Objects.requireNonNull(blockEntity.getBlockPos())),
            blockEntity);
    }

    @SuppressWarnings("this-escape")
    private FoundryPartBuilderMenu(
        int containerId,
        Inventory playerInv,
        Container container,
        ContainerLevelAccess access,
        @Nullable FoundryPartBuilderBlockEntity blockEntity
    ) {
        super(FoundryMenus.FOUNDRY_PART_BUILDER.get(), containerId);
        var containerObj = Objects.requireNonNull(container);
        this.access = Objects.requireNonNull(access);
        this.blockEntity = blockEntity;

        checkContainerSize(containerObj, CONTAINER_SIZE);

        this.addSlot(new Slot(containerObj, SLOT_PATTERN, 44, 24) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return stack.getItem() instanceof FoundryPatternItem;
            }
        });
        this.addSlot(new Slot(containerObj, SLOT_MATERIAL, 44, 50) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return FoundryMaterialRegistry.findMaterial(stack).isPresent();
            }
        });
        this.addSlot(new Slot(containerObj, SLOT_OUTPUT, 116, 37) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(@Nonnull Player player) {
                ItemStack output = getItem();
                if (!(output.getItem() instanceof FoundryPartItem partItem)) {
                    return false;
                }
                var materialId = partItem.getMaterialId(output).orElse(null);
                if (materialId == null) {
                    return false;
                }
                FoundryMaterialDefinition material = FoundryMaterialRegistry.get(materialId);
                if (material == null) {
                    return false;
                }
                FoundryPlayerProgress progress = FoundryProgressAttachment.get(player);
                return progress.getTier().getLevel() >= material.tier();
            }

            @Override
            public void onTake(@Nonnull Player player, @Nonnull ItemStack stack) {
                super.onTake(player, stack);
                if (FoundryPartBuilderMenu.this.blockEntity != null) {
                    FoundryPartBuilderMenu.this.blockEntity.consumeInputs();
                }
                if (stack.getItem() instanceof FoundryPartItem partItem) {
                    var materialId = partItem.getMaterialId(stack).orElse(null);
                    if (materialId != null) {
                        FoundryPlayerProgress progress = FoundryProgressAttachment.get(player);
                        progress.unlockMaterial(materialId);
                        progress.addMaterialMastery(materialId, 1);
                        progress.tryAdvanceTier();
                        FoundryProgressAttachment.sync(player);
                    }
                }
            }
        });

        var inv = Objects.requireNonNull(playerInv);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

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

            if (index < CONTAINER_SIZE) {
                if (!this.moveItemStackTo(slotItem, CONTAINER_SIZE, CONTAINER_SIZE + 36, true)) {
                    return emptyStack;
                }
            } else {
                if (!this.moveItemStackTo(slotItem, SLOT_PATTERN, SLOT_OUTPUT, false)) {
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
        return stillValid(access, player, Objects.requireNonNull(FoundryBlocks.FOUNDRY_PART_BUILDER.get()));
    }
}
