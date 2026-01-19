package com.devmod.foundry.menu;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
import com.devmod.foundry.block.entity.FoundryToolStationBlockEntity;
import com.devmod.foundry.progression.FoundryPlayerProgress;
import com.devmod.foundry.progression.FoundryProgressAttachment;
import com.devmod.foundry.quality.MaterialQuality;
import com.devmod.foundry.quality.QualityCalculator;
import com.devmod.foundry.tool.FoundryPartItem;
import com.devmod.foundry.tool.FoundryToolBuilder;
import com.devmod.foundry.tool.FoundryToolData;
import com.devmod.foundry.tool.FoundryToolDefinition;
import com.devmod.foundry.tool.FoundryToolDefinitionRegistry;
import com.devmod.foundry.tool.FoundryToolStats;
import com.devmod.foundry.tool.material.FoundryMaterialDefinition;
import com.devmod.foundry.tool.material.FoundryMaterialRegistry;

/**
 * Container menu for the Foundry Tool Station.
 */
public class FoundryToolStationMenu extends AbstractContainerMenu {
    public static final int SLOT_PART_START = 0;
    public static final int SLOT_PART_COUNT = 3;
    public static final int SLOT_OUTPUT = 3;
    public static final int CONTAINER_SIZE = 4;

    private final ContainerLevelAccess access;
    @Nullable private final FoundryToolStationBlockEntity blockEntity;

    public FoundryToolStationMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new SimpleContainer(CONTAINER_SIZE), ContainerLevelAccess.NULL, null);
    }

    public FoundryToolStationMenu(int containerId, Inventory playerInv, FoundryToolStationBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity.getInventory(),
            ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), Objects.requireNonNull(blockEntity.getBlockPos())),
            blockEntity);
    }

    @SuppressWarnings("this-escape")
    private FoundryToolStationMenu(
        int containerId,
        Inventory playerInv,
        Container container,
        ContainerLevelAccess access,
        @Nullable FoundryToolStationBlockEntity blockEntity
    ) {
        super(FoundryMenus.FOUNDRY_TOOL_STATION.get(), containerId);
        var containerObj = Objects.requireNonNull(container);
        this.access = Objects.requireNonNull(access);
        this.blockEntity = blockEntity;

        checkContainerSize(containerObj, CONTAINER_SIZE);

        for (int i = 0; i < SLOT_PART_COUNT; i++) {
            int x = 44 + i * 18;
            this.addSlot(new Slot(containerObj, SLOT_PART_START + i, x, 24) {
                @Override
                public boolean mayPlace(@Nonnull ItemStack stack) {
                    return stack.getItem() instanceof FoundryPartItem;
                }
            });
        }

        this.addSlot(new Slot(containerObj, SLOT_OUTPUT, 134, 35) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(@Nonnull Player player) {
                ItemStack output = getItem();
                FoundryToolData data = FoundryToolData.fromStack(output).orElse(null);
                if (data == null) {
                    return false;
                }
                int requiredTier = 0;
                for (var materialId : data.materials()) {
                    FoundryMaterialDefinition material = FoundryMaterialRegistry.get(materialId);
                    if (material != null) {
                        requiredTier = Math.max(requiredTier, material.tier());
                    }
                }
                FoundryPlayerProgress progress = FoundryProgressAttachment.get(player);
                if (progress.getTier().getLevel() < requiredTier) {
                    return false;
                }
                for (var materialId : data.materials()) {
                    if (!progress.isMaterialUnlocked(materialId)) {
                        player.displayClientMessage(
                            Component.translatable("message.devmod.foundry.material.locked"),
                            true
                        );
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void onTake(@Nonnull Player player, @Nonnull ItemStack stack) {
                super.onTake(player, stack);
                FoundryPlayerProgress progress = FoundryProgressAttachment.get(player);
                MaterialQuality assemblyQuality = null;
                if (FoundryToolStationMenu.this.blockEntity != null) {
                    assemblyQuality = calculateAssemblyQuality(progress, FoundryToolStationMenu.this.blockEntity);
                    FoundryToolStationMenu.this.blockEntity.consumeInputs();
                }
                FoundryToolData data = FoundryToolData.fromStack(stack).orElse(null);
                if (data != null) {
                    progress.recordToolCrafted();
                    for (var materialId : data.materials()) {
                        progress.unlockMaterial(materialId);
                        progress.addMaterialMastery(materialId, 2);
                    }
                    progress.tryAdvanceTier();
                    FoundryProgressAttachment.sync(player);
                    ResourceLocation specializationId = data.specialization();
                    if (specializationId == null) {
                        specializationId = progress.getSpecialization();
                    }
                    FoundryToolDefinition definition = FoundryToolDefinitionRegistry.get(data.toolId());
                    if (definition != null) {
                        FoundryToolData updated = data;
                        if (assemblyQuality != null) {
                            updated = updated.withQuality(assemblyQuality);
                        }
                        updated = updated.withSpecialization(specializationId);
                        FoundryToolStats stats = FoundryToolBuilder.computeStats(
                            definition,
                            definition.parts(),
                            updated.materials(),
                            updated.modifiersWithEmbossment(),
                            updated.quality()
                        );
                        stats = FoundryToolBuilder.applySpecialization(stats, definition.kind(), specializationId);
                        updated.writeToStack(stack);
                        FoundryToolBuilder.applyStats(stack, definition.kind(), stats);
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
                if (!this.moveItemStackTo(slotItem, SLOT_PART_START, SLOT_OUTPUT, false)) {
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
        return stillValid(access, player, Objects.requireNonNull(FoundryBlocks.FOUNDRY_TOOL_STATION.get()));
    }

    @Nullable
    private static MaterialQuality calculateAssemblyQuality(FoundryPlayerProgress progress, FoundryToolStationBlockEntity blockEntity) {
        MaterialQuality[] qualities = new MaterialQuality[SLOT_PART_COUNT];
        int qualityCount = 0;
        int masteryTotal = 0;
        int masteryCount = 0;

        for (int i = 0; i < SLOT_PART_COUNT; i++) {
            ItemStack part = blockEntity.getInventory().getItem(i);
            if (!(part.getItem() instanceof FoundryPartItem partItem)) {
                continue;
            }
            qualities[qualityCount++] = partItem.getQuality(part);
            var materialId = partItem.getMaterialId(part);
            if (materialId.isPresent()) {
                masteryTotal += progress.getMaterialMastery(materialId.get());
                masteryCount++;
            }
        }

        if (qualityCount == 0) {
            return null;
        }

        float assemblySkill = 0.0f;
        if (masteryCount > 0) {
            assemblySkill = Math.min(1.0f, masteryTotal / (100.0f * masteryCount));
        }

        MaterialQuality[] trimmed = new MaterialQuality[qualityCount];
        System.arraycopy(qualities, 0, trimmed, 0, qualityCount);
        return QualityCalculator.calculateToolQuality(trimmed, assemblySkill);
    }
}
