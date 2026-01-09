package com.devmod.debug.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.debug.block.DebugBlockEntities;
import com.devmod.debug.network.EntityScanDataPayload;

/**
 * Block entity that scans nearby entities and collects debug information.
 * Updates every 20 ticks (1 second) when players are viewing.
 */
public class EntityScannerBlockEntity extends BlockEntity {
    private static final int SCAN_INTERVAL = 20; // ticks
    private static final int[] SCAN_RADII = {8, 16, 32, 64};

    private int scanRadiusIndex = 1; // Default to 16 blocks
    private int tickCounter = 0;
    private final List<ServerPlayer> viewers = new ArrayList<>();

    public EntityScannerBlockEntity(BlockPos pos, BlockState state) {
        super(DebugBlockEntities.ENTITY_SCANNER.get(), pos, state);
    }

    /**
     * Server tick - scan entities periodically.
     */
    public void tick() {
        if (level == null || level.isClientSide) return;

        // Clean up disconnected viewers
        viewers.removeIf(p -> p.isRemoved() || !p.isAlive());

        // Only scan if there are viewers
        if (viewers.isEmpty()) return;

        tickCounter++;
        if (tickCounter >= SCAN_INTERVAL) {
            tickCounter = 0;
            scanAndSendData();
        }
    }

    /**
     * Scan entities and send data to viewers.
     */
    private void scanAndSendData() {
        if (level == null) return;

        int radius = getCurrentScanRadius();
        BlockPos pos = getBlockPos();
        AABB scanBox = new AABB(
            pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
            pos.getX() + radius, pos.getY() + radius, pos.getZ() + radius
        );

        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, scanBox);
        List<EntityScanDataPayload.ScannedEntity> scannedEntities = new ArrayList<>();

        for (LivingEntity living : entities) {
            scannedEntities.add(collectEntityData(living));
        }

        // Send to all viewers
        EntityScanDataPayload payload = new EntityScanDataPayload(pos, radius, scannedEntities);
        for (ServerPlayer viewer : viewers) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    /**
     * Collect debug data from an entity.
     */
    private EntityScanDataPayload.ScannedEntity collectEntityData(LivingEntity entity) {
        String name = entity.getName().getString();
        String type = entity.getType().toString();
        int entityId = entity.getId();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        float health = entity.getHealth();
        float maxHealth = entity.getMaxHealth();

        // Collect attributes
        List<EntityScanDataPayload.AttributeData> attributes = new ArrayList<>();
        for (AttributeInstance instance : entity.getAttributes().getSyncableAttributes()) {
            Attribute attr = instance.getAttribute().value();
            attributes.add(new EntityScanDataPayload.AttributeData(
                attr.getDescriptionId(),
                instance.getBaseValue(),
                instance.getValue()
            ));
        }

        // Collect equipment
        List<EntityScanDataPayload.EquipmentData> equipment = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                equipment.add(new EntityScanDataPayload.EquipmentData(
                    slot.getName(),
                    stack.getDisplayName().getString(),
                    stack.getCount()
                ));
            }
        }

        // Collect AI goals (only for Mob entities)
        List<EntityScanDataPayload.GoalData> goals = new ArrayList<>();
        List<EntityScanDataPayload.GoalData> targetGoals = new ArrayList<>();

        if (entity instanceof Mob mob) {
            collectGoals(mob.goalSelector, goals);
            collectGoals(mob.targetSelector, targetGoals);
        }

        return new EntityScanDataPayload.ScannedEntity(
            entityId, name, type, x, y, z, health, maxHealth,
            attributes, equipment, goals, targetGoals
        );
    }

    /**
     * Collect goals from a GoalSelector.
     */
    private void collectGoals(GoalSelector selector, List<EntityScanDataPayload.GoalData> goalList) {
        Set<WrappedGoal> availableGoals = selector.getAvailableGoals();
        for (WrappedGoal wrapped : availableGoals) {
            Goal goal = wrapped.getGoal();
            goalList.add(new EntityScanDataPayload.GoalData(
                wrapped.getPriority(),
                wrapped.isRunning(),
                goal.getClass().getSimpleName()
            ));
        }
    }

    /**
     * Open the scanner screen for a player.
     */
    public void openScreen(ServerPlayer player) {
        if (!viewers.contains(player)) {
            viewers.add(player);
        }
        // Send initial scan immediately
        scanAndSendData();
        // Notify client to open screen
        PacketDistributor.sendToPlayer(player, new EntityScanDataPayload.OpenScreenPayload(getBlockPos()));
    }

    /**
     * Remove a player from the viewers list.
     */
    public void removeViewer(ServerPlayer player) {
        viewers.remove(player);
    }

    /**
     * Cycle to the next scan radius.
     */
    public void cycleScanRadius() {
        scanRadiusIndex = (scanRadiusIndex + 1) % SCAN_RADII.length;
        setChanged();

        // Notify nearby players of the change
        if (level != null && !level.isClientSide) {
            int newRadius = getCurrentScanRadius();
            level.players().stream()
                .filter(p -> p.distanceToSqr(getBlockPos().getCenter()) < 64 * 64)
                .forEach(p -> p.displayClientMessage(
                    Component.literal("Scan radius: " + newRadius + " blocks"), true));
        }
    }

    /**
     * Get the current scan radius.
     */
    public int getCurrentScanRadius() {
        return SCAN_RADII[scanRadiusIndex];
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("scan_radius_index", scanRadiusIndex);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("scan_radius_index")) {
            scanRadiusIndex = tag.getInt("scan_radius_index");
            if (scanRadiusIndex < 0 || scanRadiusIndex >= SCAN_RADII.length) {
                scanRadiusIndex = 1;
            }
        }
    }
}
