package com.devmod.clone.block.entity;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.CloneItems;
import com.devmod.clone.component.CloneComponents;
import com.devmod.clone.data.BioscanData;
import com.devmod.clone.item.BioscannerItem;

/**
 * Block entity for the imprinter.
 * Automatically scans nearby entities and fills empty bioscanners.
 */
public class ImprinterBlockEntity extends BlockEntity {

    private static final int SCAN_RADIUS = 5;
    private static final int SCAN_COOLDOWN = 60; // 3 seconds
    private static final int SCAN_DURATION = 40; // 2 seconds

    private int cooldownTicks = 0;
    private int scanProgress = 0;
    private LivingEntity currentTarget = null;
    private boolean isScanning = false;

    public ImprinterBlockEntity(BlockPos pos, BlockState state) {
        super(CloneBlockEntities.IMPRINTER.get(), pos, state);
    }

    public boolean isScanning() {
        return isScanning;
    }

    public void tick() {
        if (level == null || level.isClientSide) {
            return;
        }

        // Handle cooldown
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        // Handle active scanning
        if (isScanning && currentTarget != null) {
            scanProgress++;

            // Particles
            if (scanProgress % 4 == 0 && level instanceof ServerLevel serverLevel) {
                spawnScanParticles(serverLevel, currentTarget);
            }

            // Complete scan
            if (scanProgress >= SCAN_DURATION) {
                completeScan();
            }
            return;
        }

        // Look for targets if not scanning
        if (!isScanning) {
            findAndStartScan();
        }
    }

    private void findAndStartScan() {
        if (level == null) {
            return;
        }

        // Check if there's an empty bioscanner in adjacent containers
        ItemStack emptyBioscanner = findEmptyBioscanner();
        if (emptyBioscanner.isEmpty()) {
            return;
        }

        // Find a scannable entity
        AABB scanArea = new AABB(worldPosition).inflate(SCAN_RADIUS);
        List<LivingEntity> entities = level.getEntitiesOfClass(
            LivingEntity.class,
            scanArea,
            entity -> !(entity instanceof Player) && entity.isAlive()
        );

        if (!entities.isEmpty()) {
            currentTarget = entities.get(level.random.nextInt(entities.size()));
            isScanning = true;
            scanProgress = 0;

            // Start sound
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5f, 1.5f);
        }
    }

    private void completeScan() {
        if (level == null || currentTarget == null || !currentTarget.isAlive()) {
            resetScan();
            return;
        }

        // Find empty bioscanner and fill it
        ItemStack emptyBioscanner = findEmptyBioscanner();
        if (!emptyBioscanner.isEmpty()) {
            BioscanData data = BioscanData.fromEntity(currentTarget);
            emptyBioscanner.set(CloneComponents.BIOSCAN_DATA.get(), data);

            // Success effects
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.playSound(
                    null, worldPosition,
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS, 0.6f, 1.2f
                );

                // Success particles
                for (int i = 0; i < 30; i++) {
                    double angle = Math.random() * Math.PI * 2.0;
                    double radius = 0.5 + Math.random() * 0.5;
                    serverLevel.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        worldPosition.getX() + 0.5 + Math.cos(angle) * radius,
                        worldPosition.getY() + 1.0,
                        worldPosition.getZ() + 0.5 + Math.sin(angle) * radius,
                        1, 0, 0, 0, 0
                    );
                }
            }
        }

        resetScan();
        cooldownTicks = SCAN_COOLDOWN;
    }

    private void resetScan() {
        isScanning = false;
        scanProgress = 0;
        currentTarget = null;
    }

    /**
     * Manually trigger a scan (from player interaction).
     */
    public void triggerManualScan(@Nonnull Player player) {
        if (level == null || level.isClientSide) {
            return;
        }

        if (isScanning) {
            player.displayClientMessage(
                Component.translatable("message.devmod.imprinter.busy")
                    .withStyle(ChatFormatting.YELLOW),
                true
            );
            return;
        }

        if (cooldownTicks > 0) {
            player.displayClientMessage(
                Component.translatable("message.devmod.imprinter.cooldown")
                    .withStyle(ChatFormatting.YELLOW),
                true
            );
            return;
        }

        // Check for empty bioscanner
        ItemStack emptyBioscanner = findEmptyBioscanner();
        if (emptyBioscanner.isEmpty()) {
            // Check player inventory
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (isEmptyBioscanner(stack)) {
                    emptyBioscanner = stack;
                    break;
                }
            }
        }

        if (emptyBioscanner.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.devmod.imprinter.no_bioscanner")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return;
        }

        // Find target
        AABB scanArea = new AABB(worldPosition).inflate(SCAN_RADIUS);
        List<LivingEntity> entities = level.getEntitiesOfClass(
            LivingEntity.class,
            scanArea,
            entity -> entity != player && entity.isAlive()
        );

        if (entities.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.devmod.imprinter.no_target")
                    .withStyle(ChatFormatting.YELLOW),
                true
            );
            return;
        }

        // Start scan
        currentTarget = entities.get(0);
        isScanning = true;
        scanProgress = 0;

        player.displayClientMessage(
            Component.translatable("message.devmod.imprinter.scanning", currentTarget.getName())
                .withStyle(ChatFormatting.GREEN),
            true
        );

        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5f, 1.5f);
    }

    private ItemStack findEmptyBioscanner() {
        if (level == null) {
            return ItemStack.EMPTY;
        }

        // Check adjacent containers
        for (Direction dir : Direction.values()) {
            BlockPos adjacentPos = worldPosition.relative(dir);
            BlockEntity adjacentBe = level.getBlockEntity(adjacentPos);

            if (adjacentBe instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (isEmptyBioscanner(stack)) {
                        return stack;
                    }
                }
            }
        }

        return ItemStack.EMPTY;
    }

    private boolean isEmptyBioscanner(ItemStack stack) {
        return !stack.isEmpty()
            && stack.is(CloneItems.BIOSCANNER.get())
            && !BioscannerItem.hasData(stack);
    }

    private void spawnScanParticles(ServerLevel serverLevel, LivingEntity target) {
        double tx = target.getX();
        double ty = target.getY() + target.getBbHeight() / 2.0;
        double tz = target.getZ();

        double bx = worldPosition.getX() + 0.5;
        double by = worldPosition.getY() + 0.8;
        double bz = worldPosition.getZ() + 0.5;

        // Line particles from block to entity
        int steps = 5;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            serverLevel.sendParticles(
                ParticleTypes.END_ROD,
                bx + (tx - bx) * t,
                by + (ty - by) * t,
                bz + (tz - bz) * t,
                1, 0, 0, 0, 0
            );
        }

        // Ring around target
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0 + (scanProgress * 0.1);
            serverLevel.sendParticles(
                ParticleTypes.PORTAL,
                tx + Math.cos(angle) * 0.5,
                ty,
                tz + Math.sin(angle) * 0.5,
                1, 0, 0, 0, 0.02
            );
        }
    }
}
