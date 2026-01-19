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
import com.devmod.foundry.progression.FoundryPlayerProgress;
import com.devmod.foundry.progression.FoundryProgressAttachment;

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

    // Data slot indices
    private static final int DATA_PROGRESS = 0;
    private static final int DATA_MAX_PROGRESS = 1;
    private static final int DATA_FUEL_TICKS = 2;
    private static final int DATA_FUEL_TICKS_MAX = 3;
    private static final int DATA_FUEL_TEMP = 4;
    private static final int DATA_MOLTEN_AMOUNT = 5;
    private static final int DATA_MOLTEN_CAPACITY = 6;
    private static final int DATA_STRUCTURE_HEAT = 7;
    private static final int DATA_THERMAL_STRESS = 8;
    private static final int DATA_RISK_LEVEL = 9;
    private static final int DATA_PURITY = 10;
    private static final int DATA_STRUCTURE_DAMAGE = 11;
    private static final int DATA_MOLTEN_QUALITY = 12;
    private static final int DATA_ALLOY_PREVIEW_FLUID = 13;
    private static final int DATA_ALLOY_PREVIEW_RATIO = 14;
    private static final int DATA_SLOT_COUNT = 15;

    public FoundryControllerMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new SimpleContainer(CONTAINER_SIZE), ContainerLevelAccess.NULL, new SimpleContainerData(DATA_SLOT_COUNT));
    }

    public FoundryControllerMenu(int containerId, Inventory playerInv, FoundryControllerBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity.getInventory(),
            ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), Objects.requireNonNull(blockEntity.getBlockPos())),
            createContainerData(blockEntity));
        FoundryPlayerProgress progress = FoundryProgressAttachment.get(Objects.requireNonNull(playerInv.player));
        blockEntity.setLastOperator(playerInv.player);
        blockEntity.applyTierLimit(progress.getTier());
    }

    private static ContainerData createContainerData(FoundryControllerBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PROGRESS -> be.getProgress();
                    case DATA_MAX_PROGRESS -> be.getMaxProgress();
                    case DATA_FUEL_TICKS -> be.getFuelTicks();
                    case DATA_FUEL_TICKS_MAX -> be.getFuelTicksMax();
                    case DATA_FUEL_TEMP -> be.getFuelTemperature();
                    case DATA_MOLTEN_AMOUNT -> be.getMoltenAmount();
                    case DATA_MOLTEN_CAPACITY -> be.getMoltenCapacity();
                    case DATA_STRUCTURE_HEAT -> (int) be.getStructureHeat();
                    case DATA_THERMAL_STRESS -> (int) (be.getThermalStressPercent() * 100);
                    case DATA_RISK_LEVEL -> be.getCurrentRiskLevel().ordinal();
                    case DATA_PURITY -> (int) (be.getCurrentPurity() * 100);
                    case DATA_STRUCTURE_DAMAGE -> be.getStructureDamage();
                    case DATA_MOLTEN_QUALITY -> be.getMoltenQualityTier();
                    case DATA_ALLOY_PREVIEW_FLUID -> be.getAlloyPreviewFluidId();
                    case DATA_ALLOY_PREVIEW_RATIO -> be.getAlloyPreviewRatio();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Client-side read only.
            }

            @Override
            public int getCount() {
                return DATA_SLOT_COUNT;
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
        int max = data.get(DATA_MAX_PROGRESS);
        return max > 0 ? (float) data.get(DATA_PROGRESS) / max : 0.0f;
    }

    public float getFuelPercent() {
        int max = data.get(DATA_FUEL_TICKS_MAX);
        return max > 0 ? (float) data.get(DATA_FUEL_TICKS) / max : 0.0f;
    }

    public int getFuelTemperature() {
        return data.get(DATA_FUEL_TEMP);
    }

    public int getMoltenAmount() {
        return data.get(DATA_MOLTEN_AMOUNT);
    }

    public int getMoltenCapacity() {
        int cap = data.get(DATA_MOLTEN_CAPACITY);
        return cap > 0 ? cap : 1;
    }

    public float getMoltenPercent() {
        int cap = data.get(DATA_MOLTEN_CAPACITY);
        return cap > 0 ? (float) data.get(DATA_MOLTEN_AMOUNT) / cap : 0.0f;
    }

    // New system getters

    public int getStructureHeat() {
        return data.get(DATA_STRUCTURE_HEAT);
    }

    public float getThermalStressPercent() {
        return data.get(DATA_THERMAL_STRESS) / 100f;
    }

    public int getRiskLevelOrdinal() {
        return data.get(DATA_RISK_LEVEL);
    }

    public float getPurityPercent() {
        return data.get(DATA_PURITY) / 100f;
    }

    public int getStructureDamage() {
        return data.get(DATA_STRUCTURE_DAMAGE);
    }

    public int getMoltenQualityTier() {
        return data.get(DATA_MOLTEN_QUALITY);
    }

    public int getAlloyPreviewFluidId() {
        return data.get(DATA_ALLOY_PREVIEW_FLUID);
    }

    public int getAlloyPreviewRatio() {
        return data.get(DATA_ALLOY_PREVIEW_RATIO);
    }

    public boolean hasAlloyPreview() {
        return getAlloyPreviewFluidId() >= 0;
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
