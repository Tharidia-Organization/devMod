package com.devmod.portal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import com.devmod.DevMod;
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

    /**
     * Flood fill bound for every walk over portal blocks. Taken from the frame detector so a
     * change to the legal frame size cannot leave the fills behind: anything past this bound is
     * not a portal shape the registry can own.
     */
    private static final int MAX_INTERIOR_BLOCKS = PortalFrameDetector.MAX_INTERIOR_BLOCKS;

    /**
     * Upper bound on cached interior positions. Only reachable if entities visit more portal
     * blocks than any real hub holds; the cache is then dropped rather than grown.
     */
    private static final int MAX_INTERIOR_INDEX_SIZE = 8192;

    /**
     * Owner marker for a portal block that belongs to no registered portal. The nil UUID is
     * never produced by {@link UUID#randomUUID()}, so it cannot collide with a portal id.
     */
    private static final UUID NO_OWNER = new UUID(0L, 0L);

    private final Map<UUID, PortalData> portals = new ConcurrentHashMap<>();
    private final Map<PositionKey, UUID> positionIndex = new ConcurrentHashMap<>();

    /**
     * Interior membership: every portal block position of a resolved portal maps to the portal
     * that owns it, or to {@link #NO_OWNER}. Holds ids rather than {@link PortalData} so that
     * changes to a portal's own state can never be read back stale from here.
     */
    private final Map<PositionKey, UUID> interiorIndex = new ConcurrentHashMap<>();

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
        invalidateInteriorIndex();
        setDirty();
    }

    /**
     * Removes a portal from the registry, unlinks its partner and clears the portal
     * blocks from the world.
     *
     * <p>Must not be called while a portal block is being removed: clearing the blocks
     * re-enters {@link CustomPortalBlock#onRemove}. Use {@link #unregister(UUID)} there,
     * where the world already takes care of the blocks.
     */
    public void unregister(@Nonnull UUID portalId, @Nullable MinecraftServer server) {
        PortalData portal = portals.get(portalId);
        if (portal == null) {
            return;
        }

        // Refresh the ex-partner's LINKED state while both entries still exist.
        unlink(portalId, server);
        unregister(portalId);

        if (server != null) {
            clearPortalBlocks(portal, server);
        }
    }

    /**
     * Removes a portal from the registry, leaving its blocks in the world.
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

            invalidateInteriorIndex();
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
     * Finds the portal that actually contains the given position.
     *
     * <p>Membership is the connected run of same-colored portal blocks the position belongs
     * to: a portal owns the position when its recorded center sits in that run. Portals of
     * one color standing a few blocks apart (a hub) therefore resolve to the portal the
     * entity is really standing in, not to whichever center happens to be nearest.
     *
     * <p>Called once per tick for every entity inside every portal block, so the answer is
     * cached per position, negatives included. See {@link #invalidateInteriorIndex()} for
     * what drops the cache.
     *
     * @param level the server level to check
     * @param pos a portal block position
     * @param color the portal color to match
     * @return the portal containing this position, or empty if none does
     */
    @Nonnull
    public Optional<PortalData> findPortalContaining(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull PortalColor color) {
        return findPortalContaining(level, Objects.requireNonNull(level.dimension().location()), pos, color);
    }

    /**
     * Dimension-explicit variant of {@link #findPortalContaining(ServerLevel, BlockPos, PortalColor)}.
     * The registry spans dimensions, so a plain {@link BlockGetter} cannot name its own.
     */
    @Nonnull
    public Optional<PortalData> findPortalContaining(
        @Nonnull BlockGetter level, @Nonnull ResourceLocation dimension,
        @Nonnull BlockPos pos, @Nonnull PortalColor color
    ) {
        PositionKey key = new PositionKey(dimension, Objects.requireNonNull(pos.immutable()));

        UUID owner = interiorIndex.get(key);
        if (owner == null) {
            indexInterior(level, dimension, pos);
            owner = interiorIndex.get(key);
        }

        if (owner == null || owner.equals(NO_OWNER)) {
            return Objects.requireNonNull(Optional.empty());
        }

        PortalData portal = portals.get(owner);
        return Objects.requireNonNull(
            portal != null && portal.color() == color ? Optional.of(portal) : Optional.empty());
    }

    /**
     * Drops the interior membership cache.
     *
     * <p>Must be called whenever the positions a portal owns can change: registration,
     * removal, a position update, and the removal of any portal block from the world
     * (see {@link CustomPortalBlock#onRemove}, which fires for interior blocks too, not
     * only for registered centers). Linking is deliberately not a trigger: the cache holds
     * portal ids, so a link change is already picked up by the next lookup.
     */
    public void invalidateInteriorIndex() {
        interiorIndex.clear();
    }

    /**
     * Resolves the portal blocks connected to {@code start} and records the owning portal for
     * each of them, so that neither the hit nor the miss repeats the flood fill next tick.
     */
    private void indexInterior(@Nonnull BlockGetter level, @Nonnull ResourceLocation dimension, @Nonnull BlockPos start) {
        BlockState state = level.getBlockState(Objects.requireNonNull(start));
        if (!(state.getBlock() instanceof CustomPortalBlock)) {
            // Nothing to index. A position with no portal block can gain one without any
            // registry call (creative placement), so a negative cached here would go stale
            // with nothing to invalidate it.
            return;
        }

        PortalColor blockColor = Objects.requireNonNull(state.getValue(Objects.requireNonNull(CustomPortalBlock.COLOR)));
        Set<BlockPos> interior = collectInterior(level, start, blockColor);

        List<PortalData> owners = new ArrayList<>();
        for (BlockPos pos : interior) {
            UUID id = positionIndex.get(new PositionKey(dimension, pos));
            PortalData portal = id != null ? portals.get(id) : null;
            if (portal != null) {
                owners.add(portal);
            }
        }

        if (interiorIndex.size() + interior.size() > MAX_INTERIOR_INDEX_SIZE) {
            interiorIndex.clear();
        }
        for (BlockPos pos : interior) {
            interiorIndex.put(new PositionKey(dimension, pos), ownerOf(pos, owners));
        }
    }

    /**
     * Picks the owner of a position inside an already-resolved run of portal blocks. More than
     * one registered center in a single run is degenerate; the nearest wins, ties broken by id
     * so the result does not depend on iteration order.
     */
    @Nonnull
    private static UUID ownerOf(@Nonnull BlockPos pos, @Nonnull List<PortalData> owners) {
        UUID best = NO_OWNER;
        double bestDistSq = Double.MAX_VALUE;

        for (PortalData owner : owners) {
            BlockPos center = owner.position().orElse(null);
            if (center == null) {
                continue;
            }
            double distSq = center.distSqr(pos);
            if (distSq < bestDistSq || (distSq == bestDistSq && owner.id().compareTo(best) < 0)) {
                bestDistSq = distSq;
                best = owner.id();
            }
        }

        return best;
    }

    /**
     * Collects the run of portal blocks of the given color connected to {@code start}.
     * Bounded so a creative-built blob of portal blocks cannot walk the whole level, and
     * iterative so a snake-shaped run of the full {@link #MAX_INTERIOR_BLOCKS} cannot overflow
     * the stack.
     *
     * <p>A run that exceeds the bound is truncated rather than half-collected: the caller gets
     * a complete answer for the first {@link #MAX_INTERIOR_BLOCKS} blocks and a warning naming
     * the position, which is all a hand-built or datapack-made oversize portal can be given.
     */
    @Nonnull
    private Set<BlockPos> collectInterior(@Nonnull BlockGetter level, @Nonnull BlockPos start, @Nonnull PortalColor color) {
        Set<BlockPos> interior = new HashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        BlockPos origin = Objects.requireNonNull(start.immutable());
        interior.add(origin);
        pending.add(origin);

        while (!pending.isEmpty()) {
            BlockPos current = Objects.requireNonNull(pending.poll());
            for (Direction dir : Direction.values()) {
                BlockPos next = Objects.requireNonNull(current.relative(Objects.requireNonNull(dir)));
                if (interior.contains(next)) {
                    continue;
                }

                BlockState neighbor = level.getBlockState(next);
                if (!(neighbor.getBlock() instanceof CustomPortalBlock)
                    || neighbor.getValue(Objects.requireNonNull(CustomPortalBlock.COLOR)) != color) {
                    continue;
                }

                // Checked before the add, so a run of exactly the bound is still complete.
                if (interior.size() >= MAX_INTERIOR_BLOCKS) {
                    DevMod.LOGGER.warn("[PortalRegistry] Run of {} portal blocks at {} exceeds the {}-block bound; fill truncated",
                        color, origin, MAX_INTERIOR_BLOCKS);
                    return interior;
                }

                interior.add(next);
                pending.add(next);
            }
        }

        return interior;
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
        invalidateInteriorIndex();
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
        ServerLevel level = levelOf(portal, server);
        if (level == null || portal == null) {
            return;
        }

        for (BlockPos pos : collectPortalBlocks(level, Objects.requireNonNull(portal.position().get()))) {
            BlockState state = level.getBlockState(Objects.requireNonNull(pos));
            if (state.getValue(Objects.requireNonNull(CustomPortalBlock.LINKED)) != linked) {
                level.setBlock(Objects.requireNonNull(pos), Objects.requireNonNull(state.setValue(Objects.requireNonNull(CustomPortalBlock.LINKED), linked)), 3);
            }
        }
    }

    /**
     * Removes all portal blocks belonging to the given portal.
     *
     * <p>Portal blocks are unbreakable and drop nothing, so nothing else in the world can
     * clear them once their registry entry is gone.
     *
     * <p>The fill is resolved in full before the first removal: removing while walking would
     * cut the walk off from the rest of the portal and strand the remainder.
     */
    private void clearPortalBlocks(@Nullable PortalData portal, @Nonnull MinecraftServer server) {
        ServerLevel level = levelOf(portal, server);
        if (level == null || portal == null) {
            return;
        }

        Set<BlockPos> blocks = collectPortalBlocks(level, Objects.requireNonNull(portal.position().get()));
        for (BlockPos pos : blocks) {
            level.removeBlock(Objects.requireNonNull(pos), false);
        }
    }

    /**
     * Resolves the level a portal sits in, or null if it has no recorded position or its
     * dimension is not loaded.
     */
    @Nullable
    private ServerLevel levelOf(@Nullable PortalData portal, @Nonnull MinecraftServer server) {
        if (portal == null || portal.position().isEmpty() || portal.dimension().isEmpty()) {
            return null;
        }

        ResourceLocation dimLoc = portal.dimension().get();
        return server.getLevel(Objects.requireNonNull(ResourceKey.create(
            Objects.requireNonNull(net.minecraft.core.registries.Registries.DIMENSION), Objects.requireNonNull(dimLoc))));
    }

    /**
     * Collects the portal blocks connected to the given center.
     *
     * <p>The same fill the membership index uses, so what gets cleared or restyled is exactly
     * what the registry considers part of the portal. The center's own color scopes the run:
     * a differently colored portal placed against this one is a separate portal.
     */
    @Nonnull
    private Set<BlockPos> collectPortalBlocks(@Nonnull ServerLevel level, @Nonnull BlockPos center) {
        BlockState state = level.getBlockState(Objects.requireNonNull(center));
        if (!(state.getBlock() instanceof CustomPortalBlock)) {
            return Objects.requireNonNull(Set.of());
        }

        PortalColor color = Objects.requireNonNull(state.getValue(Objects.requireNonNull(CustomPortalBlock.COLOR)));
        return collectInterior(level, center, color);
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
