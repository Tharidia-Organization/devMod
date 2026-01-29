package com.devmod.transport.block.entity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.transport.TransportBlockEntities;
import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportData;
import com.devmod.transport.TransportEnhancement;
import com.devmod.transport.TransportMode;
import com.devmod.transport.TransportNodeType;
import com.devmod.transport.TransportRegistry;
import com.devmod.transport.TransportState;
import com.devmod.transport.block.TransportCoreBlock;
import com.devmod.transport.block.TransportFrameBlock;
import com.devmod.transport.block.TransportModuleBlock;
import com.devmod.transport.executor.TransportExecutor;
import com.devmod.transport.network.TransportConfigOpenPayload;

/**
 * Block entity for the Warp Core transport node.
 * Manages transport data, module detection, and player charging.
 *
 * <p>Pattern based on TelepadBlockEntity with extensions for:
 * <ul>
 *   <li>Module detection (adjacent TransportModuleBlock)</li>
 *   <li>Frame segment counting (TransportFrameBlock above)</li>
 *   <li>Multiple transport modes</li>
 *   <li>Network integration via TransportRegistry</li>
 * </ul>
 *
 * <p>Timing (from Bibbia Estetica):
 * <ul>
 *   <li>CHARGE_TIME: 40 ticks (2 seconds)</li>
 *   <li>ARRIVAL_COOLDOWN: 60 ticks (3 seconds)</li>
 * </ul>
 */
public class TransportCoreBlockEntity extends BlockEntity {

    // === Constants (from TelepadBlockEntity pattern) ===
    private static final int DEFAULT_CHARGE_TIME = 40;  // 2 seconds
    private static final int DEFAULT_COOLDOWN_TIME = 60; // 3 seconds

    // === Identity ===
    private UUID nodeId;
    @Nullable
    private UUID creatorId;

    // === Configuration ===
    private TransportMode mode = TransportMode.NETWORK;
    private TransportColor color = TransportColor.CYAN;
    private String networkName = "";
    private String displayName = "";
    private TransportData.NetworkSelectionMode selectionMode = TransportData.NetworkSelectionMode.RANDOM;

    // === Linked Mode ===
    @Nullable
    private UUID linkedNodeId;

    // === Timing ===
    private int chargeTime = DEFAULT_CHARGE_TIME;
    private int cooldownTime = DEFAULT_COOLDOWN_TIME;

    // === Module Detection ===
    private final Set<TransportEnhancement> activeModules = EnumSet.noneOf(TransportEnhancement.class);
    private int frameSegmentCount = 0;

    // === Player Tracking (delegated to ChargeManager) ===
    private final Map<UUID, Long> recentArrivals = new HashMap<>();

    // === State ===
    private TransportState currentState = TransportState.IDLE;
    private boolean registryDirty = false;

    public TransportCoreBlockEntity(BlockPos pos, BlockState state) {
        super(TransportBlockEntities.WARP_CORE.get(), pos, state);
        this.nodeId = UUID.randomUUID();
    }

    // === Getters/Setters ===

    @Nonnull
    public UUID getNodeId() {
        return Objects.requireNonNull(nodeId);
    }

    @Nullable
    public UUID getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(@Nullable UUID creatorId) {
        this.creatorId = creatorId;
        setChanged();
    }

    @Nonnull
    public TransportMode getMode() {
        return Objects.requireNonNull(mode);
    }

    public void setMode(@Nonnull TransportMode mode) {
        this.mode = mode;
        markRegistryDirty();
        setChanged();
    }

    @Nonnull
    public TransportColor getColor() {
        return Objects.requireNonNull(color);
    }

    public void setColor(@Nonnull TransportColor color) {
        this.color = color;
        markRegistryDirty();
        setChanged();
        updateBlockState();
    }

    @Nonnull
    public String getNetworkName() {
        return Objects.requireNonNull(networkName);
    }

    public void setNetworkName(@Nonnull String networkName) {
        this.networkName = networkName != null ? networkName : "";
        markRegistryDirty();
        setChanged();
    }

    @Nonnull
    public String getDisplayName() {
        return Objects.requireNonNull(displayName);
    }

    public void setDisplayName(@Nonnull String displayName) {
        this.displayName = displayName != null ? displayName : "";
        markRegistryDirty();
        setChanged();
    }

    @Nonnull
    public TransportData.NetworkSelectionMode getSelectionMode() {
        return Objects.requireNonNull(selectionMode);
    }

    public void setSelectionMode(@Nonnull TransportData.NetworkSelectionMode selectionMode) {
        this.selectionMode = selectionMode;
        markRegistryDirty();
        setChanged();
    }

    @Nullable
    public UUID getLinkedNodeId() {
        return linkedNodeId;
    }

    public void setLinkedNodeId(@Nullable UUID linkedNodeId) {
        this.linkedNodeId = linkedNodeId;
        markRegistryDirty();
        setChanged();
    }

    public int getChargeTime() {
        return chargeTime;
    }

    public int getCooldownTime() {
        return cooldownTime;
    }

    @Nonnull
    public TransportState getCurrentState() {
        return Objects.requireNonNull(currentState);
    }

    public void setCurrentState(@Nonnull TransportState state) {
        this.currentState = state;
    }

    @Nonnull
    public Set<TransportEnhancement> getActiveModules() {
        return Objects.requireNonNull(EnumSet.copyOf(activeModules));
    }

    public int getFrameSegmentCount() {
        return frameSegmentCount;
    }

    public int getFrameLevel() {
        return TransportCoreBlock.calculateFrameLevel(frameSegmentCount);
    }

    public boolean hasLinkedNode() {
        return linkedNodeId != null;
    }

    // === Module Scanning ===

    /**
     * Scans adjacent blocks for transport modules and frame segments.
     * Modules are detected on horizontal sides (N/S/E/W).
     * Frame segments are detected above the core.
     */
    public void scanAdjacentModules() {
        if (level == null) {
            return;
        }

        activeModules.clear();
        frameSegmentCount = 0;

        // Scan horizontal directions for modules
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacentPos = Objects.requireNonNull(worldPosition.relative(Objects.requireNonNull(dir)));
            BlockState adjacentState = Objects.requireNonNull(level).getBlockState(adjacentPos);
            Block adjacentBlock = adjacentState.getBlock();

            if (adjacentBlock instanceof TransportModuleBlock moduleBlock) {
                TransportEnhancement moduleType = moduleBlock.getModuleType(adjacentState);
                if (moduleType != null) {
                    activeModules.add(moduleType);
                }
            }
        }

        // Scan above for frame segments (up to 10 blocks high)
        for (int y = 1; y <= 10; y++) {
            BlockPos abovePos = Objects.requireNonNull(worldPosition.above(y));
            BlockState aboveState = Objects.requireNonNull(level).getBlockState(abovePos);

            if (aboveState.getBlock() instanceof TransportFrameBlock) {
                frameSegmentCount++;
            } else if (!aboveState.isAir()) {
                // Stop scanning if we hit a non-frame, non-air block
                break;
            }
        }

        // Update timing based on modules
        updateTimingFromModules();

        // Update block state
        updateBlockState();

        // Mark registry dirty
        markRegistryDirty();
        setChanged();
    }

    /**
     * Updates charge/cooldown times based on active modules.
     */
    private void updateTimingFromModules() {
        chargeTime = DEFAULT_CHARGE_TIME;
        cooldownTime = DEFAULT_COOLDOWN_TIME;

        // FLUX_CAPACITOR: Instant charging
        if (activeModules.contains(TransportEnhancement.FLUX_CAPACITOR)) {
            chargeTime = 0;
        }
        // HASTE rune: Also instant
        else if (activeModules.contains(TransportEnhancement.HASTE)) {
            chargeTime = 0;
        }
    }

    /**
     * Updates the block state to reflect current configuration.
     */
    private void updateBlockState() {
        var currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }

        BlockState currentBlockState = getBlockState();
        BlockState newState = currentBlockState
            .setValue(Objects.requireNonNull(TransportCoreBlock.COLOR), Objects.requireNonNull(color))
            .setValue(Objects.requireNonNull(TransportCoreBlock.ACTIVE), hasValidDestination())
            .setValue(Objects.requireNonNull(TransportCoreBlock.FRAME_LEVEL), getFrameLevel());

        if (!currentBlockState.equals(newState)) {
            currentLevel.setBlock(Objects.requireNonNull(worldPosition), Objects.requireNonNull(newState), Block.UPDATE_ALL);
        }
    }

    /**
     * Checks if this node has a valid destination.
     */
    private boolean hasValidDestination() {
        return switch (mode) {
            case LINKED -> linkedNodeId != null;
            case NETWORK -> !networkName.isEmpty();
            case FIXED -> true; // Has fixed destination
            default -> false;
        };
    }

    // === Registry Integration ===

    private void markRegistryDirty() {
        registryDirty = true;
    }

    /**
     * Updates the TransportRegistry with current node data.
     */
    public void updateRegistry() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        TransportRegistry registry = TransportRegistry.get(serverLevel);

        // Build TransportData from current state
        TransportData data = TransportData.createTelepad(
            Objects.requireNonNull(nodeId),
            Objects.requireNonNull(color),
            Objects.requireNonNull(serverLevel.dimension().location()),
            Objects.requireNonNull(worldPosition),
            Objects.requireNonNull(networkName),
            Objects.requireNonNull(selectionMode)
        );

        // Apply mode-specific configuration
        data = data.withMode(mode);

        // Apply linked node if in LINKED mode
        if (mode == TransportMode.LINKED && linkedNodeId != null) {
            data = data.linkTo(linkedNodeId);
        }

        // Apply display name
        if (!displayName.isEmpty()) {
            data = data.withDisplayName(displayName);
        }

        // Apply enhancements from modules
        for (TransportEnhancement enhancement : activeModules) {
            data = data.withEnhancement(enhancement);
        }

        // Apply frame count
        data = data.withFrameCount(frameSegmentCount);

        // Apply timing
        data = data.withChargeTime(chargeTime).withCooldownTime(cooldownTime);

        // Apply creator
        if (creatorId != null) {
            data = data.withCreatorId(creatorId);
        }

        registry.register(Objects.requireNonNull(data));
        registryDirty = false;
    }

    /**
     * Removes this node from the registry (on block destruction).
     */
    public void removeFromRegistry() {
        if (level instanceof ServerLevel serverLevel) {
            TransportRegistry registry = TransportRegistry.get(serverLevel);
            registry.unregister(Objects.requireNonNull(nodeId));
        }
    }

    // === Player Standing (Charging) ===

    /**
     * Called when a player is standing on the core.
     * Delegates to TransportExecutor for charging management.
     */
    public void onPlayerStanding(ServerPlayer player) {
        var currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }

        // Check arrival cooldown
        UUID playerId = player.getUUID();
        if (recentArrivals.containsKey(playerId)) {
            long arrivedAt = recentArrivals.get(playerId);
            long currentTime = currentLevel.getGameTime();
            if (currentTime - arrivedAt < cooldownTime) {
                return;
            }
        }

        // Get transport data
        if (!(currentLevel instanceof ServerLevel serverLevel)) {
            return;
        }

        TransportRegistry registry = TransportRegistry.get(serverLevel);
        Optional<TransportData> nodeData = registry.get(Objects.requireNonNull(nodeId));

        if (nodeData.isEmpty()) {
            // Not registered yet, update registry
            updateRegistry();
            nodeData = registry.get(Objects.requireNonNull(nodeId));
        }

        if (nodeData.isPresent()) {
            TransportExecutor.INSTANCE.startCharging(player, nodeData.get());
        }
    }

    /**
     * Marks a player as having recently arrived (to prevent immediate re-teleport).
     */
    public void markPlayerArrival(UUID playerId) {
        if (level != null) {
            recentArrivals.put(playerId, level.getGameTime());
        }
    }

    // === Configuration GUI ===

    /**
     * Opens the transport configuration GUI for the given player.
     */
    public void openConfigGui(ServerPlayer player) {
        TransportConfigOpenPayload payload = new TransportConfigOpenPayload(
            Objects.requireNonNull(worldPosition),
            TransportNodeType.TELEPAD.ordinal(),
            Objects.requireNonNull(mode).ordinal(),
            Objects.requireNonNull(color).getIndex(),
            Objects.requireNonNull(networkName),
            Objects.requireNonNull(displayName),
            chargeTime,
            cooldownTime,
            hasLinkedNode(),
            frameSegmentCount
        );

        PacketDistributor.sendToPlayer(Objects.requireNonNull(player), payload);
    }

    // === Tick ===

    /**
     * Server tick - handles state updates and cleanup.
     */
    public void tick() {
        var currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }

        long currentTime = currentLevel.getGameTime();

        // Clean up old arrivals every second
        if (currentTime % 20L == 0L) {
            recentArrivals.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > cooldownTime);
        }

        // Update registry if dirty
        if (registryDirty && currentTime % 10L == 0L) {
            updateRegistry();
        }
    }

    /**
     * Returns whether this block entity needs ticking.
     */
    public boolean needsTicking() {
        return !recentArrivals.isEmpty() || registryDirty;
    }

    // === NBT Persistence ===

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        // Identity
        tag.putUUID("NodeId", Objects.requireNonNull(nodeId));
        if (creatorId != null) {
            tag.putUUID("CreatorId", creatorId);
        }

        // Configuration
        tag.putInt("Mode", Objects.requireNonNull(mode).ordinal());
        tag.putInt("Color", Objects.requireNonNull(color).getIndex());
        tag.putString("NetworkName", Objects.requireNonNull(networkName));
        tag.putString("DisplayName", Objects.requireNonNull(displayName));
        tag.putInt("SelectionMode", Objects.requireNonNull(selectionMode).ordinal());

        // Linked
        if (linkedNodeId != null) {
            tag.putUUID("LinkedNodeId", linkedNodeId);
        }

        // Timing
        tag.putInt("ChargeTime", chargeTime);
        tag.putInt("CooldownTime", cooldownTime);

        // Modules
        ListTag modulesList = new ListTag();
        for (TransportEnhancement enhancement : activeModules) {
            modulesList.add(StringTag.valueOf(Objects.requireNonNull(enhancement).getSerializedName()));
        }
        tag.put("ActiveModules", modulesList);

        // Frame
        tag.putInt("FrameSegmentCount", frameSegmentCount);

        // State
        tag.putInt("CurrentState", Objects.requireNonNull(currentState).ordinal());
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // Identity
        nodeId = tag.hasUUID("NodeId") ? tag.getUUID("NodeId") : UUID.randomUUID();
        creatorId = tag.hasUUID("CreatorId") ? tag.getUUID("CreatorId") : null;

        // Configuration
        mode = Objects.requireNonNull(TransportMode.byIndex(tag.getInt("Mode")));
        color = Objects.requireNonNull(TransportColor.byIndex(tag.getInt("Color")));
        networkName = Objects.requireNonNull(tag.getString("NetworkName"));
        displayName = Objects.requireNonNull(tag.getString("DisplayName"));
        selectionMode = Objects.requireNonNull(TransportData.NetworkSelectionMode.byIndex(tag.getInt("SelectionMode")));

        // Linked
        linkedNodeId = tag.hasUUID("LinkedNodeId") ? tag.getUUID("LinkedNodeId") : null;

        // Timing
        chargeTime = tag.contains("ChargeTime") ? tag.getInt("ChargeTime") : DEFAULT_CHARGE_TIME;
        cooldownTime = tag.contains("CooldownTime") ? tag.getInt("CooldownTime") : DEFAULT_COOLDOWN_TIME;

        // Modules
        activeModules.clear();
        if (tag.contains("ActiveModules", Tag.TAG_LIST)) {
            ListTag modulesList = tag.getList("ActiveModules", Tag.TAG_STRING);
            for (int i = 0; i < modulesList.size(); i++) {
                String name = modulesList.getString(i);
                TransportEnhancement enhancement = TransportEnhancement.byName(name);
                if (enhancement != null) {
                    activeModules.add(enhancement);
                }
            }
        }

        // Frame
        frameSegmentCount = tag.getInt("FrameSegmentCount");

        // State
        TransportState[] states = TransportState.values();
        int stateIndex = tag.getInt("CurrentState");
        if (stateIndex >= 0 && stateIndex < states.length) {
            currentState = Objects.requireNonNull(states[stateIndex]);
        }
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Color", Objects.requireNonNull(color).getIndex());
        tag.putString("NetworkName", Objects.requireNonNull(networkName));
        tag.putString("DisplayName", Objects.requireNonNull(displayName));
        tag.putInt("FrameSegmentCount", frameSegmentCount);
        tag.putInt("CurrentState", Objects.requireNonNull(currentState).ordinal());
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // === Configuration Update (from GUI) ===

    /**
     * Applies configuration from the save payload.
     */
    public void applyConfiguration(
        @Nonnull TransportMode newMode,
        @Nonnull TransportColor newColor,
        @Nonnull String newNetworkName,
        @Nonnull TransportData.NetworkSelectionMode newSelectionMode,
        @Nonnull String newDisplayName
    ) {
        this.mode = newMode;
        this.color = newColor;
        this.networkName = newNetworkName;
        this.selectionMode = newSelectionMode;
        this.displayName = newDisplayName;

        updateBlockState();
        updateRegistry();
        setChanged();

        var currentLevel = level;
        if (currentLevel != null && !currentLevel.isClientSide) {
            BlockState state = Objects.requireNonNull(getBlockState());
            currentLevel.sendBlockUpdated(Objects.requireNonNull(worldPosition), state, state, Block.UPDATE_ALL);
        }
    }
}
