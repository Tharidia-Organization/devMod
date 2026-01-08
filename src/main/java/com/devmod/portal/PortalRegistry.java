package com.devmod.portal;

import com.devmod.portal.block.CustomPortalBlock;

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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<BlockPos, UUID> positionIndex = new ConcurrentHashMap<>();

    public PortalRegistry() {
        super();
    }

    /**
     * Gets the portal registry for the given level.
     */
    @Nonnull
    public static PortalRegistry get(@Nonnull ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
            .computeIfAbsent(
                new Factory<>(PortalRegistry::new, PortalRegistry::load),
                DATA_NAME
            );
    }

    /**
     * Registers a new portal.
     */
    public void register(@Nonnull PortalData portal) {
        Objects.requireNonNull(portal, "portal");
        portals.put(portal.id(), portal);
        portal.position().ifPresent(pos -> positionIndex.put(pos, portal.id()));
        setDirty();
    }

    /**
     * Removes a portal from the registry.
     * Also unlinks any connected portal.
     */
    public void unregister(@Nonnull UUID portalId) {
        PortalData portal = portals.remove(portalId);
        if (portal != null) {
            portal.position().ifPresent(positionIndex::remove);

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
        return Optional.ofNullable(portals.get(portalId));
    }

    /**
     * Gets a portal by its exact center position.
     */
    @Nonnull
    public Optional<PortalData> getByPosition(@Nonnull BlockPos pos) {
        UUID id = positionIndex.get(pos);
        return id != null ? get(id) : Optional.empty();
    }

    /**
     * Finds a portal that contains the given position.
     * Searches by scanning nearby registered portals and checking if the position
     * could be within their bounds.
     *
     * @param level the server level to check
     * @param pos any position within a portal's interior
     * @param color the portal color to match
     * @return the portal containing this position, or empty if none found
     */
    @Nonnull
    public Optional<PortalData> findPortalContaining(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull PortalColor color) {
        // First try exact match (if clicking on center)
        Optional<PortalData> exact = getByPosition(pos);
        if (exact.isPresent() && exact.get().color() == color) {
            return exact;
        }

        // Search all portals of this color in this dimension
        ResourceLocation dim = level.dimension().location();
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
                return Optional.of(portal);
            }
        }

        return Optional.empty();
    }

    /**
     * Updates a portal's data.
     */
    public void update(@Nonnull PortalData portal) {
        PortalData existing = portals.get(portal.id());
        if (existing != null) {
            // Update position index if changed
            existing.position().ifPresent(positionIndex::remove);
            portal.position().ifPresent(pos -> positionIndex.put(pos, portal.id()));
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

        ServerLevel level = server.getLevel(ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION, dimLoc));
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

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CustomPortalBlock)) {
            return;
        }

        // Update the linked state
        if (state.getValue(CustomPortalBlock.LINKED) != linked) {
            level.setBlock(pos, state.setValue(CustomPortalBlock.LINKED, linked), 3);
        }

        // Check adjacent blocks for more portal blocks
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            updatePortalBlocksRecursive(level, pos.relative(dir), linked, visited);
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
            return Optional.empty();
        }

        BlockPos sourcePos = source.position().orElse(null);
        ResourceLocation sourceDim = source.dimension().orElse(null);
        if (sourcePos == null) {
            return Optional.empty();
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

        return Optional.ofNullable(bestCandidate);
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
                PortalData portal = PortalData.load(portalList.getCompound(i));
                registry.portals.put(portal.id(), portal);
                portal.position().ifPresent(pos -> registry.positionIndex.put(pos, portal.id()));
            }
        }

        return registry;
    }
}
