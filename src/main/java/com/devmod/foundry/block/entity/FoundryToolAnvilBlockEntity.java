package com.devmod.foundry.block.entity;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import com.devmod.foundry.tool.FoundryToolRepair;
import com.devmod.foundry.tool.FoundryToolSlots;
import com.devmod.foundry.tool.material.FoundryMaterialDefinition;
import com.devmod.foundry.tool.material.FoundryMaterialRegistry;
import com.devmod.foundry.tool.modifier.FoundryModifierDefinition;
import com.devmod.foundry.tool.modifier.FoundryModifierRegistry;
import com.devmod.foundry.tool.modifier.FoundryModifierSlot;
import com.devmod.foundry.util.FoundryContainers;

/**
 * Block entity for applying modifiers to tools.
 */
public class FoundryToolAnvilBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements MenuProvider {
    private static final String TAG_INVENTORY = "Inventory";

    public static final int SLOT_TOOL = 0;
    public static final int SLOT_MODIFIER = 1;
    public static final int SLOT_OUTPUT = 2;

    private final SimpleContainer inventory;

    public FoundryToolAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_TOOL_ANVIL.get()), pos, state);
        this.inventory = FoundryContainers.tracked(3);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        FoundryContainers.bind(inventory, this);
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

        FoundryToolDefinition definition = FoundryToolDefinitionRegistry.get(data.toolId());
        if (definition == null) {
            inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
            return;
        }

        FoundryModifierDefinition modifier = FoundryModifierRegistry.findModifier(modifierStack).orElse(null);
        if (modifier != null) {
            int currentLevel = data.modifiers().getOrDefault(modifier.id(), 0);
            if (currentLevel >= modifier.maxLevel()) {
                inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
                return;
            }
            FoundryToolSlots.SlotUsage usage = FoundryToolSlots.calculate(definition, data);
            int free = modifier.slotType() == FoundryModifierSlot.ABILITY ? usage.freeAbilities() : usage.freeUpgrades();
            if (modifier.slots() > 0 && modifier.slots() > free) {
                inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
                return;
            }

            FoundryToolData updated = data.withModifier(modifier.id(), currentLevel + 1);
            ItemStack output = toolStack.copy();
            var stats = FoundryToolBuilder.computeStats(
                definition,
                definition.parts(),
                data.materials(),
                updated.modifiersWithEmbossment(),
                data.quality()
            );
            stats = FoundryToolBuilder.applySpecialization(stats, definition.kind(), data.specialization());
            FoundryToolBuilder.applyStats(output, definition.kind(), stats);
            updated.writeToStack(output);
            inventory.setItem(SLOT_OUTPUT, output);
            return;
        }

        FoundryToolData embossData = FoundryToolData.fromStack(modifierStack).orElse(null);
        if (embossData != null && data.embossment() == null) {
            ResourceLocation embossTrait = null;
            if (!embossData.materials().isEmpty()) {
                FoundryMaterialDefinition material = FoundryMaterialRegistry.get(embossData.materials().get(0));
                if (material != null && !material.traits().isEmpty()) {
                    embossTrait = material.traits().get(0);
                }
            }
            if (embossTrait == null) {
                inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
                return;
            }
            FoundryToolData updated = data.withEmbossment(embossTrait);
            ItemStack output = toolStack.copy();
            var stats = FoundryToolBuilder.computeStats(
                definition,
                definition.parts(),
                data.materials(),
                updated.modifiersWithEmbossment(),
                data.quality()
            );
            stats = FoundryToolBuilder.applySpecialization(stats, definition.kind(), data.specialization());
            FoundryToolBuilder.applyStats(output, definition.kind(), stats);
            updated.writeToStack(output);
            inventory.setItem(SLOT_OUTPUT, output);
            return;
        }

        FoundryMaterialDefinition repairMaterial = FoundryMaterialRegistry.findMaterial(modifierStack).orElse(null);
        if (repairMaterial != null && data.materials().contains(repairMaterial.id())) {
            int repairAmount = FoundryToolRepair.getRepairAmount(repairMaterial, data.repairCount());
            ItemStack output = toolStack.copy();
            int damage = output.getOrDefault(net.minecraft.core.component.DataComponents.DAMAGE, 0);
            int newDamage = Math.max(0, damage - repairAmount);
            output.set(net.minecraft.core.component.DataComponents.DAMAGE, newDamage);
            FoundryToolData updated = data.withRepairCount(data.repairCount() + 1);
            updated.writeToStack(output);
            inventory.setItem(SLOT_OUTPUT, output);
            return;
        }

        inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
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
