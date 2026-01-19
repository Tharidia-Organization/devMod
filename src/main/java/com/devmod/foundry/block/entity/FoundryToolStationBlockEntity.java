package com.devmod.foundry.block.entity;

import java.util.ArrayList;
import java.util.List;
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
import com.devmod.foundry.menu.FoundryToolStationMenu;
import com.devmod.foundry.tool.FoundryPartItem;
import com.devmod.foundry.tool.FoundryPartType;
import com.devmod.foundry.tool.FoundryToolBuilder;
import com.devmod.foundry.tool.FoundryToolDefinition;
import com.devmod.foundry.tool.FoundryToolDefinitionRegistry;

/**
 * Block entity for the Tool Station.
 */
public class FoundryToolStationBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements MenuProvider {
    private static final String TAG_INVENTORY = "Inventory";

    public static final int SLOT_PART_START = 0;
    public static final int SLOT_PART_COUNT = 3;
    public static final int SLOT_OUTPUT = 3;

    private final SimpleContainer inventory = new SimpleContainer(4);

    public FoundryToolStationBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_TOOL_STATION.get()), pos, state);
    }

    public void tickServer() {
        updateOutput();
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public void consumeInputs() {
        for (int i = 0; i < SLOT_PART_COUNT; i++) {
            ItemStack part = inventory.getItem(i);
            if (!part.isEmpty()) {
                part.shrink(1);
                if (part.isEmpty()) {
                    inventory.setItem(i, ItemStack.EMPTY);
                }
            }
        }
    }

    private void updateOutput() {
        List<ItemStack> parts = new ArrayList<>();
        List<FoundryPartType> types = new ArrayList<>();
        for (int i = 0; i < SLOT_PART_COUNT; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof FoundryPartItem partItem)) {
                inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
                return;
            }
            parts.add(stack);
            types.add(partItem.getPartType());
        }

        FoundryToolDefinition definition = FoundryToolDefinitionRegistry.findMatch(types).orElse(null);
        if (definition == null) {
            inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
            return;
        }

        List<ItemStack> orderedParts = new ArrayList<>();
        boolean[] used = new boolean[parts.size()];
        for (FoundryPartType required : definition.parts()) {
            int matchIndex = -1;
            for (int i = 0; i < types.size(); i++) {
                if (!used[i] && Objects.equals(types.get(i), required)) {
                    matchIndex = i;
                    break;
                }
            }
            if (matchIndex < 0) {
                inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
                return;
            }
            used[matchIndex] = true;
            orderedParts.add(parts.get(matchIndex));
        }

        ItemStack output = FoundryToolBuilder.buildTool(definition, orderedParts, java.util.Map.of());
        inventory.setItem(SLOT_OUTPUT, output);
    }

    @Override
    @Nonnull
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.devmod.foundry_tool_station");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull Player player) {
        return new FoundryToolStationMenu(containerId, playerInv, this);
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
