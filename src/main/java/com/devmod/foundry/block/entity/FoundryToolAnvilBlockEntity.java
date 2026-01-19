package com.devmod.foundry.block.entity;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.menu.FoundryToolAnvilMenu;
import com.devmod.foundry.tool.FoundryToolBuilder;
import com.devmod.foundry.tool.FoundryToolData;
import com.devmod.foundry.tool.FoundryToolDefinition;
import com.devmod.foundry.tool.FoundryToolDefinitionRegistry;
import com.devmod.foundry.tool.modifier.FoundryModifierDefinition;
import com.devmod.foundry.tool.modifier.FoundryModifierRegistry;

/**
 * Block entity for applying modifiers to tools.
 */
public class FoundryToolAnvilBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements MenuProvider {
    private static final String TAG_INVENTORY = "Inventory";

    public static final int SLOT_TOOL = 0;
    public static final int SLOT_MODIFIER = 1;
    public static final int SLOT_OUTPUT = 2;

    private final SimpleContainer inventory = new SimpleContainer(3);

    public FoundryToolAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_TOOL_ANVIL.get()), pos, state);
    }

    public void tickServer() {
        updateOutput();
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public void consumeInputs() {
        ItemStack modifier = inventory.getItem(SLOT_MODIFIER);
        if (!modifier.isEmpty()) {
            modifier.shrink(1);
            if (modifier.isEmpty()) {
                inventory.setItem(SLOT_MODIFIER, ItemStack.EMPTY);
            }
        }
    }

    private void updateOutput() {
        ItemStack toolStack = inventory.getItem(SLOT_TOOL);
        ItemStack modifierStack = inventory.getItem(SLOT_MODIFIER);
        if (toolStack.isEmpty() || modifierStack.isEmpty()) {
            inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
            return;
        }

        FoundryToolData data = FoundryToolData.fromStack(toolStack).orElse(null);
        if (data == null) {
            inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
            return;
        }

        FoundryModifierDefinition modifier = FoundryModifierRegistry.findModifier(modifierStack).orElse(null);
        if (modifier == null) {
            inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
            return;
        }

        int currentLevel = data.modifiers().getOrDefault(modifier.id(), 0);
        if (currentLevel >= modifier.maxLevel()) {
            inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
            return;
        }

        FoundryToolDefinition definition = FoundryToolDefinitionRegistry.get(data.toolId());
        if (definition == null) {
            inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
            return;
        }

        FoundryToolData updated = data.withModifier(modifier.id(), currentLevel + 1);
        FoundryToolBuilder.applyStats(toolStack, definition.kind(),
            FoundryToolBuilder.computeStats(definition, definition.parts(), data.materials(), updated.modifiers()));

        ItemStack output = toolStack.copy();
        updated.writeToStack(output);
        inventory.setItem(SLOT_OUTPUT, output);
    }

    @Override
    @Nonnull
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.devmod.foundry_tool_anvil");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull Player player) {
        return new FoundryToolAnvilMenu(containerId, playerInv, this);
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag invTag = new CompoundTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                invTag.put("Slot" + i, stack.save(registries));
            }
        }
        tag.put(TAG_INVENTORY, invTag);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_INVENTORY)) {
            CompoundTag invTag = tag.getCompound(TAG_INVENTORY);
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                String key = "Slot" + i;
                if (invTag.contains(key)) {
                    inventory.setItem(i, ItemStack.parse(registries, invTag.getCompound(key)).orElse(ItemStack.EMPTY));
                }
            }
        }
    }
}
