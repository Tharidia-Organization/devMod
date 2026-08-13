package com.devmod.portal;

import java.util.ArrayList;
import java.util.HashSet;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import com.devmod.portal.block.CustomPortalBlock;

/**
 * Central registry for all custom portals in the world.
 * Handles persistence, linking, and lookup operations.
 *
 * <p>Thread-safe for concurrent access from multiple dimensions.
 */
public class PortalRegistry extends SavedData {
    private static final String DATA_NAME = "devmod_portals";
    private static final String TAG_PORTALS = "portals";

    private final Map<UUID, PortalData> portals = new ConcurrentHashMap<>();
    private final Map<PositionKey, UUID> positionIndex = new ConcurrentHashMap<>();

    /** Position index key. The registry is global, so the dimension is part of the identity. */
    private record PositionKey(ResourceLocation dimension, BlockPos pos) {}

    @Nullable
    private static PositionKey positionKey(@Nonnull PortalData portal) {
        ResourceLocation dimension = portal.dimension().orElse(null);
        BlockPos pos = portal.position().orElse(null);
        return dimension == null || pos == null ? null : new PositionKey(dimension, pos);
    }

    public PortalRegistry() {
        super();
    }

    /**
     * Gets the portal registry for the given level.
     */
    @Nonnull
    public static PortalRegistry get(@Nonnull ServerLevel level) {
        MinecraftServer server = Objects.requireNonNull(level.getServer(), "server");
        return Objects.requireNonNull(server.overworld().getDataStorage()
            .computeIfAbsent(
                new Factory<>(PortalRegistry::new, PortalRegistry::load),
                DATA_NAME
            ));
    }

    /**
     * Registers a new portal.
     */
    public void register(@Nonnull PortalData portal) {
        Objects.requireNonNull(portal, "portal");
        portals.put(portal.id(), portal);
        PositionKey key = positionKey(portal);
        if (key != null) {
            positionIndex.put(key, portal.id());
        }
        setDirty();
    }

    /**
     * Removes a portal from the registry.
     * Also unlinks any connected portal.
     */
    public void unregister(@Nonnull UUID portalId) {
        PortalData portal = portals.remove(portalId);
        if (portal != null) {
            PositionKey key = positionKey(portal);
            if (key != null) {
                positionIndex.remove(key, portalId);
            }

            // Unlink connected portal
            portal.linkedPortalId().ifPresent(linkedId -> {
                PortalData linked = portals.get(linkedId);
                if (linked != null && linked.linkedPortalId().orElse(null) != null
                    && linked.linkedPortalId().get().equals(portalId)) {
                    portals.put(linkedId, linked.unlink());
                }
            });

            setDirty();
        }
    }

    /**
     * Gets a portal by its ID.
     */
    @Nonnull
    public Optional<PortalData> get(@Nonnull UUID portalId) {
        return Objects.requireNonNull(Optional.ofNullable(portals.get(portalId)));
    }

    /**
     * Gets a portal by its exact center position within a dimension.
     */
    @Nonnull
    public Optional<PortalData> getByPosition(@Nonnull ResourceLocation dimension, @Nonnull BlockPos pos) {
        UUID id = positionIndex.get(new PositionKey(dimension, pos));
        return id != null ? get(id) : Objects.requireNonNull(Optional.empty());
    }

    /**
     * Gets a portal by its exact center position in the given level's dimension.
     */
    @Nonnull
    public Optional<PortalData> getByPosition(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
        return getByPosition(Objects.requireNonNull(level.dimension().location()), pos);
    }

    /**
     * Gets all registered portals.
     */
    @Nonnull
    public java.util.Collection<PortalData> getAll() {
        return Objects.requireNonNull(java.util.Collections.unmodifiableCollection(portals.values()));
    }

    /**
     * Finds a portal that contains the given position.
     * Searches by scanning nearby registered portals and checking if the position
     * could be within their bounds.
     *
     * <p>Several portals of the same color can sit inside the search cube (a portal
     * hub); the nearest center wins so the result does not depend on map iteration
     * order.
     *
     * @param level the server level to check
     * @param pos any position within a portal's interior
     * @param color the portal color to match
     * @return the portal containing this position, or empty if none found
     */
    @Nonnull
    public Optional<PortalData> findPortalContaining(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull PortalColor color) {
        ResourceLocation dim = level.dimension().location();

        // First try exact match (if clicking on center)
        Optional<PortalData> exact = getByPosition(dim, pos);
        if (exact.isPresent() && exact.get().color() == color) {
            return exact;
        }

        // Search all portals of this color in this dimension
        PortalData nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (PortalData portal : portals.values()) {
            if (portal.color() != color) {
                continue;
            }
            if (portal.dimension().isEmpty() || !portal.dimension().get().equals(dim)) {
                continue;
            }
            BlockPos center = portal.position().orElse(null);
            if (center == null) {
                continue;
            }

            // Check if pos is within reasonable distance of center (max portal is 23x23)
            int dx = Math.abs(pos.getX() - center.getX());
            int dy = Math.abs(pos.getY() - center.getY());
            int dz = Math.abs(pos.getZ() - center.getZ());

            // Portal interior is at most 11 blocks from center in any direction
            if (dx <= 12 && dy <= 12 && dz <= 12) {
                double distSq = center.distSqr(pos);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = portal;
                }
            }
        }

        return Objects.requireNonNull(Optional.ofNullable(nearest));
    }

    /**
     * Updates a portal's data.
     */
    public void update(@Nonnull PortalData portal) {
        PortalData existing = portals.get(portal.id());
        if (existing != null) {
            // Update position index if changed
            PositionKey oldKey = positionKey(existing);
            if (oldKey != null) {
                positionIndex.remove(oldKey, portal.id());
            }
            PositionKey newKey = positionKey(portal);
            if (newKey != null) {
                positionIndex.put(newKey, portal.id());
            }
        }
        portals.put(portal.id(), portal);
        setDirty();
    }

    /**
     * Links two portals bidirectionally.
     * Returns true if successful.
     */
    public boolean link(@Nonnull UUID portal1Id, @Nonnull UUID portal2Id) {
        if (portal1Id.equals(portal2Id)) {
            return false;
        }

        PortalData portal1 = portals.get(portal1Id);
        PortalData portal2 = portals.get(portal2Id);

        if (portal1 == null || portal2 == null) {
            return false;
        }

        if (portal1.isLinked() || portal2.isLinked()) {
            return false;
        }

        // Require same color for linking
        if (portal1.color() != portal2.color()) {
            return false;
        }

        // Check private portals mode
        if (!canLinkPrivate(portal1, portal2)) {
            return false;
        }

        portals.put(portal1Id, portal1.linkTo(portal2Id));
        portals.put(portal2Id, portal2.linkTo(portal1Id));
        setDirty();
        return true;
    }

    /**
     * Links two portals and updates block states.
     */
    public boolean link(@Nonnull UUID portal1Id, @Nonnull UUID portal2Id, @Nullable MinecraftServer server) {
        boolean success = link(portal1Id, portal2Id);
        if (success && server != null) {
            updatePortalBlockState(portals.get(portal1Id), true, server);
            updatePortalBlockState(portals.get(portal2Id), true, server);
        }
        return success;
    }

    /**
     * Checks if two portals can link based on private portals config.
     * If privatePortals is disabled, always returns true.
     * If enabled, both portals must have the same creator.
     */
    private boolean canLinkPrivate(@Nonnull PortalData source, @Nonnull PortalData target) {
        if (!PortalConfig.isPrivatePortals()) {
            return true;
        }
        // Both portals must have a creator and they must match
        if (source.creator().isEmpty() || target.creator().isEmpty()) {
            return false;
        }
        return source.creator().get().equals(target.creator().get());
    }

    /**
     * Unlinks a portal (and its connected portal).
     */
    public boolean unlink(@Nonnull UUID portalId) {
        PortalData portal = portals.get(portalId);
        if (portal == null || !portal.isLinked()) {
            return false;
        }

        UUID linkedId = portal.linkedPortalId().orElse(null);
        portals.put(portalId, portal.unlink());

        if (linkedId != null) {
            PortalData linked = portals.get(linkedId);
            if (linked != null) {
                portals.put(linkedId, linked.unlink());
            }
        }

        setDirty();
        return true;
    }

    /**
     * Unlinks a portal and updates block states.
     */
    public boolean unlink(@Nonnull UUID portalId, @Nullable MinecraftServer server) {
        PortalData portal = portals.get(portalId);
        UUID linkedId = portal != null ? portal.linkedPortalId().orElse(null) : null;

        boolean success = unlink(portalId);
        if (success && server != null) {
            updatePortalBlockState(portals.get(portalId), false, server);
            if (linkedId != null) {
                updatePortalBlockState(portals.get(linkedId), false, server);
            }
        }
        return success;
    }

    /**
     * Updates the LINKED block state property for all portal blocks at the given portal's position.
     */
    private void updatePortalBlockState(@Nullable PortalData portal, boolean linked, @Nonnull MinecraftServer server) {
        if (portal == null || portal.position().isEmpty() || portal.dimension().isEmpty()) {
            return;
        }

        BlockPos center = portal.position().get();
        ResourceLocation dimLoc = portal.dimension().get();

        ServerLevel level = server.getLevel(Objects.requireNonNull(ResourceKey.create(
            Objects.requireNonNull(net.minecraft.core.registries.Registries.DIMENSION), Objects.requireNonNull(dimLoc))));
        if (level == null) {
            return;
        }

        // Update block state at center and scan for connected portal blocks
        updatePortalBlocksRecursive(level, center, linked, new HashSet<>());
    }

    /**
     * Recursively updates all connected portal blocks.
     */
    private void updatePortalBlocksRecursive(ServerLevel level, BlockPos pos, boolean linked, Set<BlockPos> visited) {
        if (visited.contains(pos) || visited.size() > 100) {
            return;
        }
        visited.add(pos);

        BlockState state = level.getBlockState(Objects.requireNonNull(pos));
        if (!(state.getBlock() instanceof CustomPortalBlock)) {
            return;
        }

        // Update the linked state
        if (state.getValue(Objects.requireNonNull(CustomPortalBlock.LINKED)) != linked) {
            level.setBlock(Objects.requireNonNull(pos), Objects.requireNonNull(state.setValue(Objects.requireNonNull(CustomPortalBlock.LINKED), linked)), 3);
        }

        // Check adjacent blocks for more portal blocks
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            updatePortalBlocksRecursive(level, pos.relative(Objects.requireNonNull(dir)), linked, visited);
        }
    }

    /**
     * Gets all portals of a specific color.
     */
    @Nonnull
    public List<PortalData> getByColor(@Nonnull PortalColor color) {
        List<PortalData> result = new ArrayList<>();
        for (PortalData portal : portals.values()) {
            if (portal.color() == color) {
                result.add(portal);
            }
        }
        return result;
    }

    /**
     * Gets all portals in a specific dimension.
     */
    @Nonnull
    public List<PortalData> getByDimension(@Nonnull ResourceLocation dimension) {
        List<PortalData> result = new ArrayList<>();
        for (PortalData portal : portals.values()) {
            if (portal.dimension().orElse(null) != null
                && portal.dimension().get().equals(dimension)) {
                result.add(portal);
            }
        }
        return result;
    }

    /**
     * Gets all unlinked portals of a specific color.
     */
    @Nonnull
    public List<PortalData> getUnlinkedByColor(@Nonnull PortalColor color) {
        List<PortalData> result = new ArrayList<>();
        for (PortalData portal : portals.values()) {
            if (portal.color() == color && !portal.isLinked()) {
                result.add(portal);
            }
        }
        return result;
    }

    /**
     * Gets all portals in a named network.
     *
     * @param networkName the network name to search for
     * @return list of portals in the network, empty if none found
     */
    @Nonnull
    public List<PortalData> getByNetwork(@Nonnull String networkName) {
        Objects.requireNonNull(networkName, "networkName");
        List<PortalData> result = new ArrayList<>();
        for (PortalData portal : portals.values()) {
            if (portal.hasNetwork() && networkName.equals(portal.networkName())) {
                result.add(portal);
            }
        }
        return result;
    }

    /**
     * Gets a random portal from a named network, excluding the source portal.
     * Used for network teleportation where the destination is random.
     *
     * @param networkName the network name to search
     * @param excludeId the portal ID to exclude (typically the source portal)
     * @return a random destination portal, or empty if no valid destinations
     */
    @Nonnull
    public Optional<PortalData> getRandomNetworkDestination(@Nonnull String networkName, @Nonnull UUID excludeId) {
        Objects.requireNonNull(networkName, "networkName");
        Objects.requireNonNull(excludeId, "excludeId");

        List<PortalData> candidates = new ArrayList<>();
        for (PortalData portal : portals.values()) {
            if (portal.hasNetwork()
                && networkName.equals(portal.networkName())
                && !portal.id().equals(excludeId)
                && portal.position().isPresent()) {
                candidates.add(portal);
            }
        }

        if (candidates.isEmpty()) {
            return Objects.requireNonNull(Optional.empty());
        }

        // Pick random destination
        int index = ThreadLocalRandom.current().nextInt(candidates.size());
        return Objects.requireNonNull(Optional.of(candidates.get(index)));
    }

    /**
     * Adds a portal to a named network.
     * Note: This clears any existing link or fixed destination on the portal.
     *
     * @param portalId the portal to add to the network
     * @param networkName the network name
     * @return true if successful, false if portal not found
     */
    public boolean addToNetwork(@Nonnull UUID portalId, @Nonnull String networkName) {
        Objects.requireNonNull(portalId, "portalId");
        Objects.requireNonNull(networkName, "networkName");

        PortalData portal = portals.get(portalId);
        if (portal == null) {
            return false;
        }

        portals.put(portalId, portal.withNetwork(networkName));
        setDirty();
        return true;
    }

    /**
     * Removes a portal from its network.
     *
     * @param portalId the portal to remove from its network
     * @return true if successful, false if portal not found or not in a network
     */
    public boolean removeFromNetwork(@Nonnull UUID portalId) {
        Objects.requireNonNull(portalId, "portalId");

        PortalData portal = portals.get(portalId);
        if (portal == null || !portal.hasNetwork()) {
            return false;
        }

        portals.put(portalId, portal.withNetwork(null));
        setDirty();
        return true;
    }

    /**
     * Returns the total number of registered portals.
     */
    public int size() {
        return portals.size();
    }

    // ========================================================================
    // Auto-Linking
    // ========================================================================

    /**
     * Attempts to automatically link a portal to the nearest compatible portal.
     * Uses rune effects to determine linking range and cross-dimension capability.
     *
     * @param portalId the portal to try auto-linking
     * @param effects the rune effects affecting this portal
     * @return true if successfully linked, false otherwise
     */
    public boolean tryAutoLink(@Nonnull UUID portalId, @Nonnull PortalRuneEffects effects) {
        Optional<PortalData> candidateOpt = findLinkCandidate(portalId, effects);
        if (candidateOpt.isEmpty()) {
            return false;
        }
        return link(portalId, candidateOpt.get().id());
    }

    /**
     * Finds the best portal to link to, based on color, distance, and rune effects.
     * Returns the closest unlinked portal of the same color within range.
     *
     * @param portalId the portal looking for a link
     * @param effects the rune effects affecting the source portal
     * @return the best candidate portal, or empty if none found
     */
    @Nonnull
    public Optional<PortalData> findLinkCandidate(@Nonnull UUID portalId, @Nonnull PortalRuneEffects effects) {
        PortalData source = portals.get(portalId);
        if (source == null || source.isLinked()) {
            return Objects.requireNonNull(Optional.empty());
        }

        BlockPos sourcePos = source.position().orElse(null);
        ResourceLocation sourceDim = source.dimension().orElse(null);
        if (sourcePos == null) {
            return Objects.requireNonNull(Optional.empty());
        }

        List<PortalData> candidates = getUnlinkedByColor(source.color());
        PortalData bestCandidate = null;
        double bestDistance = Double.MAX_VALUE;

        for (PortalData candidate : candidates) {
            // Skip self
            if (candidate.id().equals(portalId)) {
                continue;
            }

            BlockPos candidatePos = candidate.position().orElse(null);
            if (candidatePos == null) {
                continue;
            }

            // Check dimension compatibility
            ResourceLocation candidateDim = candidate.dimension().orElse(null);
            boolean sameDimension = (sourceDim == null && candidateDim == null)
                || (sourceDim != null && sourceDim.equals(candidateDim));

            if (!effects.canLinkToDimension(sameDimension)) {
                continue;
            }

            // Check private portals mode
            if (!canLinkPrivate(source, candidate)) {
                continue;
            }

            // Calculate distance (for cross-dimension, use Nether scaling if applicable)
            double distance = calculateLinkDistance(sourcePos, candidatePos, sourceDim, candidateDim);

            // Check range
            if (!effects.canLinkAtDistance(distance)) {
                continue;
            }

            // Track closest candidate
            if (distance < bestDistance) {
                bestDistance = distance;
                bestCandidate = candidate;
            }
        }

        return Objects.requireNonNull(Optional.ofNullable(bestCandidate));
    }

    /**
     * Calculates the effective distance between two portal positions.
     * Accounts for Nether coordinate scaling (8:1 ratio) when portals are in different dimensions.
     *
     * @param pos1 first position
     * @param pos2 second position
     * @param dim1 first dimension
     * @param dim2 second dimension
     * @return the effective distance in blocks
     */
    private double calculateLinkDistance(
        @Nonnull BlockPos pos1, @Nonnull BlockPos pos2,
        @Nullable ResourceLocation dim1, @Nullable ResourceLocation dim2
    ) {
        // Same dimension or unknown dimensions: use direct distance
        if (dim1 == null || dim2 == null || dim1.equals(dim2)) {
            return Math.sqrt(pos1.distSqr(pos2));
        }

        // Cross-dimension: apply Nether scaling
        // Nether coordinates are 1/8 of Overworld
        ResourceLocation nether = ResourceLocation.parse("minecraft:the_nether");
        ResourceLocation overworld = ResourceLocation.parse("minecraft:overworld");

        double x1 = pos1.getX();
        double z1 = pos1.getZ();
        double x2 = pos2.getX();
        double z2 = pos2.getZ();

        // Scale Nether coords to Overworld scale for comparison
        if (dim1.equals(nether) && dim2.equals(overworld)) {
            x1 *= 8;
            z1 *= 8;
        } else if (dim1.equals(overworld) && dim2.equals(nether)) {
            x2 *= 8;
            z2 *= 8;
        }

        double dx = x2 - x1;
        double dy = pos2.getY() - pos1.getY();
        double dz = z2 - z1;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ========================================================================
    // Persistence
    // ========================================================================

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        ListTag portalList = new ListTag();
        for (PortalData portal : portals.values()) {
            portalList.add(portal.save());
        }
        tag.put(TAG_PORTALS, portalList);
        return tag;
    }

    @Nonnull
    public static PortalRegistry load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        PortalRegistry registry = new PortalRegistry();

        if (tag.contains(TAG_PORTALS, Tag.TAG_LIST)) {
            ListTag portalList = tag.getList(TAG_PORTALS, Tag.TAG_COMPOUND);
            for (int i = 0; i < portalList.size(); i++) {
                PortalData portal = PortalData.load(Objects.requireNonNull(portalList.getCompound(i)));
                registry.portals.put(portal.id(), portal);
                PositionKey key = positionKey(portal);
                if (key != null) {
                    registry.positionIndex.put(key, portal.id());
                }
            }
        }

        return registry;
    }
}
