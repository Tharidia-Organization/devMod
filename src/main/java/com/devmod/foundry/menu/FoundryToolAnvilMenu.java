package com.devmod.foundry.menu;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.devmod.foundry.FoundryBlocks;
import com.devmod.foundry.FoundryItems;
import com.devmod.foundry.FoundryMenus;
import com.devmod.foundry.block.entity.FoundryToolAnvilBlockEntity;
import com.devmod.foundry.progression.FoundryPlayerProgress;
import com.devmod.foundry.progression.FoundryProgressAttachment;
import com.devmod.foundry.tool.FoundryToolData;
import com.devmod.foundry.tool.modifier.FoundryModifierDefinition;
import com.devmod.foundry.tool.modifier.FoundryModifierRegistry;
import com.devmod.foundry.tool.modifier.FoundryModifierSlot;

/**
 * Container menu for the Foundry Tool Anvil.
 */
public class FoundryToolAnvilMenu extends AbstractContainerMenu {
    public static final int SLOT_TOOL = 0;
    public static final int SLOT_MODIFIER = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int CONTAINER_SIZE = 3;

    private final ContainerLevelAccess access;
    @Nullable private final FoundryToolAnvilBlockEntity blockEntity;

    public FoundryToolAnvilMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new SimpleContainer(CONTAINER_SIZE), ContainerLevelAccess.NULL, null);
    }

    public FoundryToolAnvilMenu(int containerId, Inventory playerInv, FoundryToolAnvilBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity.getInventory(),
            ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), Objects.requireNonNull(blockEntity.getBlockPos())),
            blockEntity);
    }

    @SuppressWarnings("this-escape")
    private FoundryToolAnvilMenu(
        int containerId,
        Inventory playerInv,
        Container container,
        ContainerLevelAccess access,
        @Nullable FoundryToolAnvilBlockEntity blockEntity
    ) {
        super(FoundryMenus.FOUNDRY_TOOL_ANVIL.get(), containerId);
        var containerObj = Objects.requireNonNull(container);
        this.access = Objects.requireNonNull(access);
        this.blockEntity = blockEntity;

        checkContainerSize(containerObj, CONTAINER_SIZE);

        this.addSlot(new Slot(containerObj, SLOT_TOOL, 44, 35) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return FoundryToolData.fromStack(stack).isPresent();
            }
        });
        this.addSlot(new Slot(containerObj, SLOT_MODIFIER, 62, 35) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return FoundryModifierRegistry.findModifier(stack).isPresent();
            }
        });
        this.addSlot(new Slot(containerObj, SLOT_OUTPUT, 134, 35) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(@Nonnull Player player) {
                if (FoundryToolAnvilMenu.this.blockEntity == null) {
                    return super.mayPickup(player);
                }
                ItemStack modifierStack = FoundryToolAnvilMenu.this.blockEntity.getInventory().getItem(SLOT_MODIFIER);
                FoundryModifierDefinition modifier = FoundryModifierRegistry.findModifier(modifierStack).orElse(null);
                if (modifier == null || modifier.slotType() == FoundryModifierSlot.TRAIT) {
                    return super.mayPickup(player);
                }
                FoundryPlayerProgress progress = FoundryProgressAttachment.get(player);
                if (!progress.hasSpecialization()) {
                    player.displayClientMessage(
                        Component.translatable("message.devmod.foundry.modifier.needs_specialization"),
                        true
                    );
                    return false;
                }
                if (!progress.isModifierUnlocked(modifier.id()) && !hasUnlockFlux(player)) {
                    player.displayClientMessage(
                        Component.translatable("message.devmod.foundry.modifier.needs_flux"),
                        true
                    );
                    return false;
                }
                return super.mayPickup(player);
            }

            @Override
            public void onTake(@Nonnull Player player, @Nonnull ItemStack stack) {
                super.onTake(player, stack);
                if (FoundryToolAnvilMenu.this.blockEntity != null) {
                    ItemStack modifierStack = FoundryToolAnvilMenu.this.blockEntity.getInventory().getItem(SLOT_MODIFIER);
                    FoundryModifierDefinition modifier = FoundryModifierRegistry.findModifier(modifierStack).orElse(null);
                    if (modifier != null && modifier.slotType() != FoundryModifierSlot.TRAIT) {
                        FoundryPlayerProgress progress = FoundryProgressAttachment.get(player);
                        if (!progress.isModifierUnlocked(modifier.id())) {
                            if (consumeUnlockFlux(player)) {
                                progress.unlockModifier(modifier.id());
                                FoundryProgressAttachment.sync(player);
                            }
                        }
                    }
                    FoundryToolAnvilMenu.this.blockEntity.consumeInputs();
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
                if (!this.moveItemStackTo(slotItem, SLOT_TOOL, SLOT_OUTPUT, false)) {
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
        return stillValid(access, player, Objects.requireNonNull(FoundryBlocks.FOUNDRY_TOOL_ANVIL.get()));
    }

    private static boolean hasUnlockFlux(Player player) {
        return findUnlockFluxSlot(player) >= 0;
    }

    private static boolean consumeUnlockFlux(Player player) {
        int slot = findUnlockFluxSlot(player);
        if (slot < 0) {
            return false;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        stack.shrink(1);
        if (stack.isEmpty()) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        return true;
    }

    private static int findUnlockFluxSlot(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Objects.requireNonNull(FoundryItems.FOUNDRY_FLUX_REFINED.get()))) {
                return i;
            }
        }
        return -1;
    }
}
