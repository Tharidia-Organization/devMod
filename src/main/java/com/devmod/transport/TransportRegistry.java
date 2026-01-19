package com.devmod.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import com.devmod.portal.PortalConfig;
import com.devmod.portal.PortalRegistry;
import com.devmod.transport.TransportData.NetworkSelectionMode;
import com.devmod.transport.TransportData.TemporalPhase;
import com.devmod.transport.block.TransportCoreBlock;
import com.devmod.transport.block.entity.TransportCoreBlockEntity;

/**
 * Central registry for all transport nodes in the world.
 * Unifies PortalRegistry, NexusReturnSavedData, and instance tracking.
 *
 * <p>Thread-safe for concurrent access from multiple dimensions.
 *
 * <p>Features:
 * <ul>
 *   <li>UUID-based node storage</li>
 *   <li>Position index for block lookup</li>
 *   <li>Network index for grouped teleportation</li>
 *   <li>Type index for querying by node type</li>
 *   <li>Player return point tracking</li>
 *   <li>Automatic migration from legacy PortalRegistry</li>
 * </ul>
 */
public class TransportRegistry extends SavedData {
    private static final String DATA_NAME = "devmod_transport";
    private static final String TAG_NODES = "nodes";
    private static final String TAG_RETURN_POINTS = "return_points";
    private static final String TAG_MIGRATED = "migrated_from_portal_registry";

    // Primary storage
    private final Map<UUID, TransportData> nodes = new ConcurrentHashMap<>();

    // Indices for fast lookup
    private final Map<BlockPos, UUID> positionIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> networkIndex = new ConcurrentHashMap<>();
    private final Map<TransportNodeType, Set<UUID>> typeIndex = new ConcurrentHashMap<>();

    // Player return points
    private final Map<UUID, UUID> playerReturnPoints = new ConcurrentHashMap<>();

    // Migration flag
    private boolean migratedFromLegacy = false;

    // Round-robin counters for networks
    private final Map<String, Integer> roundRobinCounters = new ConcurrentHashMap<>();

    public TransportRegistry() {
        super();
    }

    /**
     * Gets the transport registry for the given level.
     * Registry is stored in the overworld for cross-dimensional access.
     */
    @Nonnull
    public static TransportRegistry get(@Nonnull ServerLevel level) {
        return Objects.requireNonNull(level.getServer().overworld().getDataStorage()
            .computeIfAbsent(
                new Factory<>(TransportRegistry::new, TransportRegistry::load),
                DATA_NAME
            ));
    }

    // ============================================================================
    // CRUD OPERATIONS
    // ============================================================================

    /**
     * Registers a new transport node.
     */
    public void register(@Nonnull TransportData node) {
        Objects.requireNonNull(node, "node");

        nodes.put(node.id(), node);

        // Update position index
        node.getPosition().ifPresent(pos -> positionIndex.put(pos, node.id()));

        // Update network index
        node.getNetworkName().ifPresent(network -> {
            networkIndex.computeIfAbsent(network, k -> ConcurrentHashMap.newKeySet()).add(node.id());
        });

        // Update type index
        typeIndex.computeIfAbsent(node.nodeType(), k -> ConcurrentHashMap.newKeySet()).add(node.id());

        setDirty();
    }

    /**
     * Unregisters a transport node.
     * Also unlinks any connected nodes.
     */
    public void unregister(@Nonnull UUID nodeId) {
        TransportData node = nodes.remove(nodeId);
        if (node == null) {
            return;
        }

        // Remove from position index
        node.getPosition().ifPresent(positionIndex::remove);

        // Remove from network index
        node.getNetworkName().ifPresent(network -> {
            Set<UUID> networkNodes = networkIndex.get(network);
            if (networkNodes != null) {
                networkNodes.remove(nodeId);
                if (networkNodes.isEmpty()) {
                    networkIndex.remove(network);
                }
            }
        });

        // Remove from type index
        Set<UUID> typeNodes = typeIndex.get(node.nodeType());
        if (typeNodes != null) {
            typeNodes.remove(nodeId);
        }

        // Unlink connected node if linked
        node.getLinkedNodeId().ifPresent(linkedId -> {
            TransportData linked = nodes.get(linkedId);
            if (linked != null && linked.getLinkedNodeId().orElse(null) != null
                && linked.getLinkedNodeId().get().equals(nodeId)) {
                nodes.put(linkedId, linked.unlink());
            }
        });

        setDirty();
    }

    /**
     * Updates a node's data.
     */
    public void update(@Nonnull TransportData node) {
        TransportData existing = nodes.get(node.id());
        if (existing != null) {
            // Update position index if changed
            existing.getPosition().ifPresent(positionIndex::remove);
            node.getPosition().ifPresent(pos -> positionIndex.put(pos, node.id()));

            // Update network index if changed
            if (!Objects.equals(existing.getNetworkName().orElse(null), node.getNetworkName().orElse(null))) {
                existing.getNetworkName().ifPresent(oldNetwork -> {
                    Set<UUID> oldNetworkNodes = networkIndex.get(oldNetwork);
                    if (oldNetworkNodes != null) {
                        oldNetworkNodes.remove(node.id());
                    }
                });
                node.getNetworkName().ifPresent(newNetwork -> {
                    networkIndex.computeIfAbsent(newNetwork, k -> ConcurrentHashMap.newKeySet()).add(node.id());
                });
            }
        }

        nodes.put(node.id(), node);
        setDirty();
    }

    /**
     * Gets a node by its ID.
     */
    @Nonnull
    public Optional<TransportData> get(@Nonnull UUID nodeId) {
        return Objects.requireNonNull(Optional.ofNullable(nodes.get(nodeId)));
    }

    /**
     * Gets a node by its exact position.
     */
    @Nonnull
    public Optional<TransportData> getByPosition(@Nonnull BlockPos pos) {
        UUID id = positionIndex.get(pos);
        return id != null ? get(id) : Objects.requireNonNull(Optional.empty());
    }

    /**
     * Gets all registered transport nodes.
     */
    @Nonnull
    public java.util.Collection<TransportData> getAll() {
        return Objects.requireNonNull(java.util.Collections.unmodifiableCollection(nodes.values()));
    }

    /**
     * Finds a node that contains the given position.
     * Searches nearby registered nodes within portal bounds.
     */
    @Nonnull
    public Optional<TransportData> findContaining(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull TransportColor color) {
        // First try exact match
        Optional<TransportData> exact = getByPosition(pos);
        if (exact.isPresent() && exact.get().color() == color) {
            return exact;
        }

        // Search all nodes of this color in this dimension
        ResourceLocation dim = level.dimension().location();
        for (TransportData node : nodes.values()) {
            if (node.color() != color) {
                continue;
            }
            if (node.getDimension().isEmpty() || !node.getDimension().get().equals(dim)) {
                continue;
            }
            BlockPos center = node.getPosition().orElse(null);
            if (center == null) {
                continue;
            }

            // Check if pos is within reasonable distance (max portal is 23x23)
            int dx = Math.abs(pos.getX() - center.getX());
            int dy = Math.abs(pos.getY() - center.getY());
            int dz = Math.abs(pos.getZ() - center.getZ());

            if (dx <= 12 && dy <= 12 && dz <= 12) {
                return Objects.requireNonNull(Optional.of(node));
            }
        }

        return Objects.requireNonNull(Optional.empty());
    }

    // ============================================================================
    // LINKING
    // ============================================================================

    /**
     * Links two nodes bidirectionally.
     */
    public boolean link(@Nonnull UUID node1Id, @Nonnull UUID node2Id) {
        if (node1Id.equals(node2Id)) {
            return false;
        }

        TransportData node1 = nodes.get(node1Id);
        TransportData node2 = nodes.get(node2Id);

        if (node1 == null || node2 == null) {
            return false;
        }

        if (node1.isLinked() || node2.isLinked()) {
            return false;
        }

        // Require same color for linking
        if (node1.color() != node2.color()) {
            return false;
        }

        // Check private mode
        if (!canLinkPrivate(node1, node2)) {
            return false;
        }

        nodes.put(node1Id, node1.linkTo(node2Id));
        nodes.put(node2Id, node2.linkTo(node1Id));
        setDirty();
        return true;
    }

    /**
     * Links two nodes and updates block states.
     */
    public boolean link(@Nonnull UUID node1Id, @Nonnull UUID node2Id, @Nullable MinecraftServer server) {
        boolean success = link(node1Id, node2Id);
        if (success && server != null) {
            updateNodeBlockState(nodes.get(node1Id), true, server);
            updateNodeBlockState(nodes.get(node2Id), true, server);
        }
        return success;
    }

    /**
     * Unlinks a node.
     */
    public boolean unlink(@Nonnull UUID nodeId) {
        TransportData node = nodes.get(nodeId);
        if (node == null || !node.isLinked()) {
            return false;
        }

        UUID linkedId = node.getLinkedNodeId().orElse(null);
        nodes.put(nodeId, node.unlink());

        if (linkedId != null) {
            TransportData linked = nodes.get(linkedId);
            if (linked != null) {
                nodes.put(linkedId, linked.unlink());
            }
        }

        setDirty();
        return true;
    }

    /**
     * Unlinks a node and updates block states.
     */
    public boolean unlink(@Nonnull UUID nodeId, @Nullable MinecraftServer server) {
        TransportData node = nodes.get(nodeId);
        UUID linkedId = node != null ? node.getLinkedNodeId().orElse(null) : null;

        boolean success = unlink(nodeId);
        if (success && server != null) {
            updateNodeBlockState(nodes.get(nodeId), false, server);
            if (linkedId != null) {
                updateNodeBlockState(nodes.get(linkedId), false, server);
            }
        }
        return success;
    }

    private boolean canLinkPrivate(@Nonnull TransportData source, @Nonnull TransportData target) {
        if (!PortalConfig.isPrivatePortals()) {
            return true;
        }
        if (source.getCreatorId().isEmpty() || target.getCreatorId().isEmpty()) {
            return false;
        }
        return source.getCreatorId().get().equals(target.getCreatorId().get());
    }

    private void updateNodeBlockState(@Nullable TransportData node, boolean linked, @Nonnull MinecraftServer server) {
        if (node == null || node.getPosition().isEmpty() || node.getDimension().isEmpty()) {
            return;
        }

        ResourceLocation dimension = node.getDimension().get();
        ResourceKey<Level> levelKey = Objects.requireNonNull(ResourceKey.create(Objects.requireNonNull(Registries.DIMENSION), Objects.requireNonNull(dimension)));
        ServerLevel level = server.getLevel(Objects.requireNonNull(levelKey));
        if (level == null) {
            return;
        }

        BlockPos pos = Objects.requireNonNull(node.getPosition().get());
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TransportCoreBlockEntity coreBe) {
            coreBe.setLinkedNodeId(linked ? node.getLinkedNodeId().orElse(null) : null);
            coreBe.setChanged();
        }

        BlockState state = Objects.requireNonNull(level.getBlockState(Objects.requireNonNull(pos)));
        if (state.getBlock() instanceof TransportCoreBlock && state.hasProperty(Objects.requireNonNull(TransportCoreBlock.ACTIVE))) {
            BlockState updated = state.setValue(Objects.requireNonNull(TransportCoreBlock.ACTIVE), linked);
            if (!state.equals(updated)) {
                level.setBlock(Objects.requireNonNull(pos), Objects.requireNonNull(updated), Block.UPDATE_ALL);
            }
        }
    }

    // ============================================================================
    // NETWORK OPERATIONS
    // ============================================================================

    /**
     * Gets all nodes in a named network.
     */
    @Nonnull
    public List<TransportData> getByNetwork(@Nonnull String networkName) {
        Objects.requireNonNull(networkName, "networkName");
        List<TransportData> result = new ArrayList<>();

        Set<UUID> networkNodes = networkIndex.get(networkName);
        if (networkNodes != null) {
            for (UUID id : networkNodes) {
                TransportData node = nodes.get(id);
                if (node != null) {
                    result.add(node);
                }
            }
        }

        return result;
    }

    /**
     * Gets a destination from a network based on selection mode.
     */
    @Nonnull
    public Optional<TransportData> getNetworkDestination(
        @Nonnull String networkName,
        @Nonnull UUID excludeId,
        @Nullable NetworkSelectionMode mode
    ) {
        Objects.requireNonNull(networkName, "networkName");
        Objects.requireNonNull(excludeId, "excludeId");

        List<TransportData> candidates = new ArrayList<>();
        Set<UUID> networkNodes = networkIndex.get(networkName);

        if (networkNodes == null || networkNodes.isEmpty()) {
            return Objects.requireNonNull(Optional.empty());
        }

        for (UUID id : networkNodes) {
            if (id.equals(excludeId)) {
                continue;
            }
            TransportData node = nodes.get(id);
            if (node != null && node.hasPosition()) {
                candidates.add(node);
            }
        }

        if (candidates.isEmpty()) {
            return Objects.requireNonNull(Optional.empty());
        }

        NetworkSelectionMode selectionMode = mode != null ? mode : NetworkSelectionMode.RANDOM;

        return Objects.requireNonNull(switch (selectionMode) {
            case RANDOM -> {
                int index = ThreadLocalRandom.current().nextInt(candidates.size());
                yield Optional.of(candidates.get(index));
            }
            case ROUND_ROBIN -> {
                int counter = roundRobinCounters.compute(networkName, (k, v) -> v == null ? 0 : (v + 1) % candidates.size());
                yield Optional.of(candidates.get(counter));
            }
            case NEAREST -> Optional.of(candidates.get(0)); // Would need source position for actual nearest
            case MANUAL -> Optional.empty(); // Requires GUI selection
        });
    }

    /**
     * Adds a node to a network.
     */
    public boolean addToNetwork(@Nonnull UUID nodeId, @Nonnull String networkName) {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(networkName, "networkName");

        TransportData node = nodes.get(nodeId);
        if (node == null) {
            return false;
        }

        // Remove from old network
        node.getNetworkName().ifPresent(oldNetwork -> {
            Set<UUID> oldNetworkNodes = networkIndex.get(oldNetwork);
            if (oldNetworkNodes != null) {
                oldNetworkNodes.remove(nodeId);
            }
        });

        // Update node with new network
        TransportData updated = node.withNetwork(networkName, NetworkSelectionMode.RANDOM);
        nodes.put(nodeId, updated);

        // Add to new network index
        networkIndex.computeIfAbsent(networkName, k -> ConcurrentHashMap.newKeySet()).add(nodeId);

        setDirty();
        return true;
    }

    /**
     * Removes a node from its network.
     */
    public boolean removeFromNetwork(@Nonnull UUID nodeId) {
        Objects.requireNonNull(nodeId, "nodeId");

        TransportData node = nodes.get(nodeId);
        if (node == null || node.getNetworkName().isEmpty()) {
            return false;
        }

        String network = node.getNetworkName().get();
        Set<UUID> networkNodes = networkIndex.get(network);
        if (networkNodes != null) {
            networkNodes.remove(nodeId);
            if (networkNodes.isEmpty()) {
                networkIndex.remove(network);
            }
        }

        nodes.put(nodeId, node.withNetwork(null, null));
        setDirty();
        return true;
    }

    /**
     * Gets the size of a network.
     */
    public int getNetworkSize(@Nonnull String networkName) {
        Set<UUID> networkNodes = networkIndex.get(networkName);
        return networkNodes != null ? networkNodes.size() : 0;
    }

    // ============================================================================
    // TYPE QUERIES
    // ============================================================================

    /**
     * Gets all nodes of a specific type.
     */
    @Nonnull
    public List<TransportData> getByType(@Nonnull TransportNodeType type) {
        List<TransportData> result = new ArrayList<>();
        Set<UUID> typeNodes = typeIndex.get(type);

        if (typeNodes != null) {
            for (UUID id : typeNodes) {
                TransportData node = nodes.get(id);
                if (node != null) {
                    result.add(node);
                }
            }
        }

        return result;
    }

    /**
     * Gets all nodes of a specific color.
     */
    @Nonnull
    public List<TransportData> getByColor(@Nonnull TransportColor color) {
        List<TransportData> result = new ArrayList<>();
        for (TransportData node : nodes.values()) {
            if (node.color() == color) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Gets all nodes in a specific dimension.
     */
    @Nonnull
    public List<TransportData> getByDimension(@Nonnull ResourceLocation dimension) {
        List<TransportData> result = new ArrayList<>();
        for (TransportData node : nodes.values()) {
            if (node.getDimension().orElse(null) != null
                && node.getDimension().get().equals(dimension)) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Gets all unlinked nodes of a specific color.
     */
    @Nonnull
    public List<TransportData> getUnlinkedByColor(@Nonnull TransportColor color) {
        List<TransportData> result = new ArrayList<>();
        for (TransportData node : nodes.values()) {
            if (node.color() == color && !node.isLinked()) {
                result.add(node);
            }
        }
        return result;
    }

    // ============================================================================
    // TEMPORAL OPERATIONS
    // ============================================================================

    /**
     * Gets all temporary nodes.
     */
    @Nonnull
    public List<TransportData> getTemporaryNodes() {
        List<TransportData> result = new ArrayList<>();
        for (TransportData node : nodes.values()) {
            if (node.isTemporary()) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Gets all expired nodes.
     */
    @Nonnull
    public List<TransportData> getExpiredNodes(long currentTick) {
        List<TransportData> result = new ArrayList<>();
        for (TransportData node : nodes.values()) {
            Long expTick = node.expirationTick();
            if (node.isTemporary() && expTick != null && currentTick >= expTick) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Updates a node's temporal phase.
     */
    public void updatePhase(@Nonnull UUID nodeId, @Nonnull TemporalPhase phase) {
        TransportData node = nodes.get(nodeId);
        if (node != null && node.isTemporary()) {
            nodes.put(nodeId, node.withPhase(phase));
            setDirty();
        }
    }

    /**
     * Processes expirations for all temporal nodes.
     */
    public void processExpirations(@Nonnull ServerLevel level, long currentTick) {
        List<UUID> toRemove = new ArrayList<>();

        for (TransportData node : nodes.values()) {
            if (!node.isTemporary() || node.expirationTick() == null) {
                continue;
            }

            Long expiration = node.expirationTick();
            if (expiration != null && currentTick >= expiration) {
                toRemove.add(node.id());
            }
        }

        for (UUID id : toRemove) {
            unregister(Objects.requireNonNull(id));
        }
    }

    // ============================================================================
    // RETURN POINT OPERATIONS
    // ============================================================================

    /**
     * Records a return point for a player.
     */
    public void recordReturnPoint(@Nonnull UUID playerId, @Nonnull TransportData returnNode) {
        // Unregister old return point if exists
        UUID oldReturnId = playerReturnPoints.get(playerId);
        if (oldReturnId != null) {
            unregister(oldReturnId);
        }

        // Register new return point
        register(returnNode);
        playerReturnPoints.put(playerId, returnNode.id());
        setDirty();
    }

    /**
     * Gets the return point for a player.
     */
    @Nonnull
    public Optional<TransportData> getReturnPoint(@Nonnull UUID playerId) {
        UUID returnId = playerReturnPoints.get(playerId);
        return returnId != null ? get(returnId) : Objects.requireNonNull(Optional.empty());
    }

    /**
     * Clears the return point for a player.
     */
    public void clearReturnPoint(@Nonnull UUID playerId) {
        UUID returnId = playerReturnPoints.remove(playerId);
        if (returnId != null) {
            unregister(returnId);
            setDirty();
        }
    }

    // ============================================================================
    // UTILITY
    // ============================================================================

    /**
     * Returns the total number of registered nodes.
     */
    public int size() {
        return nodes.size();
    }

    /**
     * Returns whether migration from legacy has occurred.
     */
    public boolean hasMigratedFromLegacy() {
        return migratedFromLegacy;
    }

    // ============================================================================
    // MIGRATION
    // ============================================================================

    /**
     * Migrates data from legacy PortalRegistry.
     *
     * @return number of nodes migrated
     */
    public int migrateFromPortalRegistry(@Nonnull PortalRegistry legacy) {
        if (migratedFromLegacy) {
            return 0;
        }

        int migrated = 0;
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.WHITE)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.ORANGE)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.MAGENTA)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.LIGHT_BLUE)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.YELLOW)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.LIME)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.PINK)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.GRAY)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.LIGHT_GRAY)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.CYAN)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.PURPLE)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.BLUE)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.BROWN)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.GREEN)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.RED)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }
        for (var portal : legacy.getByColor(com.devmod.portal.PortalColor.BLACK)) {
            register(TransportData.fromPortalData(Objects.requireNonNull(portal)));
            migrated++;
        }

        migratedFromLegacy = true;
        setDirty();
        return migrated;
    }

    // ============================================================================
    // PERSISTENCE
    // ============================================================================

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        // Save nodes
        ListTag nodeList = new ListTag();
        for (TransportData node : nodes.values()) {
            nodeList.add(node.save());
        }
        tag.put(TAG_NODES, nodeList);

        // Save return points
        CompoundTag returnPointsTag = new CompoundTag();
        for (Map.Entry<UUID, UUID> entry : playerReturnPoints.entrySet()) {
            returnPointsTag.putUUID(Objects.requireNonNull(entry.getKey().toString()), Objects.requireNonNull(entry.getValue()));
        }
        tag.put(TAG_RETURN_POINTS, returnPointsTag);

        // Save migration flag
        tag.putBoolean(TAG_MIGRATED, migratedFromLegacy);

        return tag;
    }

    @Nonnull
    public static TransportRegistry load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        TransportRegistry registry = new TransportRegistry();

        // Load nodes
        if (tag.contains(TAG_NODES, Tag.TAG_LIST)) {
            ListTag nodeList = tag.getList(TAG_NODES, Tag.TAG_COMPOUND);
            for (int i = 0; i < nodeList.size(); i++) {
                TransportData node = TransportData.load(Objects.requireNonNull(nodeList.getCompound(i)));

                registry.nodes.put(node.id(), node);

                // Rebuild indices
                node.getPosition().ifPresent(pos -> registry.positionIndex.put(pos, node.id()));
                node.getNetworkName().ifPresent(network -> {
                    registry.networkIndex.computeIfAbsent(network, k -> ConcurrentHashMap.newKeySet()).add(node.id());
                });
                registry.typeIndex.computeIfAbsent(node.nodeType(), k -> ConcurrentHashMap.newKeySet()).add(node.id());
            }
        }

        // Load return points
        if (tag.contains(TAG_RETURN_POINTS, Tag.TAG_COMPOUND)) {
            CompoundTag returnPointsTag = tag.getCompound(TAG_RETURN_POINTS);
            for (String key : returnPointsTag.getAllKeys()) {
                UUID playerId = UUID.fromString(Objects.requireNonNull(key));
                UUID returnId = returnPointsTag.getUUID(key);
                registry.playerReturnPoints.put(playerId, returnId);
            }
        }

        // Load migration flag
        registry.migratedFromLegacy = tag.getBoolean(TAG_MIGRATED);

        return registry;
    }
}
