package com.devmod.portal.block;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.network.NetworkHandler;
import com.devmod.portal.PortalColor;
import com.devmod.portal.PortalConfig;
import com.devmod.portal.PortalData;
import com.devmod.portal.PortalRegistry;
import com.devmod.portal.PortalRuneEffects;
import com.devmod.portal.RuneType;
import com.devmod.portal.network.PortalStatePayload;
/**
 * Custom portal block that fills the interior of a portal frame.
 * Supports 16 colors, bidirectional linking, and rune enhancements.
 *
 * <p>Rune blocks placed adjacent to the portal frame affect portal behavior:
 * <ul>
 *   <li>HASTE - Instant teleportation (no delay)</li>
 *   <li>GATE - Cross-dimension linking</li>
 *   <li>ENHANCER - +100 block linking range</li>
 *   <li>STRONG_ENHANCER - +500 block linking range</li>
 *   <li>INFINITY - Unlimited linking range</li>
 * </ul>
 */
public class CustomPortalBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<PortalColor> COLOR = EnumProperty.create("color", PortalColor.class);
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");

    // Thinner shape than nether portal (4 pixels thick)
    private static final VoxelShape X_SHAPE = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    private static final VoxelShape Z_SHAPE = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    // Teleportation cooldown in ticks (5 seconds)
    private static final int TELEPORT_COOLDOWN = 100;

    // Default teleportation delay in ticks (4 seconds)
    private static final int DEFAULT_TELEPORT_DELAY = 80;

    // NBT tag key for tracking portal entry time
    private static final String TAG_PORTAL_TIME = "devmod:portal_time";
    private static final String TAG_PORTAL_POS = "devmod:portal_pos";
    private static final String TAG_GATE_FEEDBACK_SHOWN = "devmod:gate_feedback_shown";

    /** Tag to indicate player is teleporting via custom portal (skip nexus spawn override) */
    public static final String TAG_CUSTOM_PORTAL_TELEPORT = "devmod:custom_portal_teleport";

    /** Cache for rune effects to avoid repeated DFS scans every tick */
    private static final Map<Long, CachedRuneEffects> RUNE_EFFECT_CACHE = new ConcurrentHashMap<>();
    /** Cache TTL in ticks (1 second) */
    private static final int RUNE_CACHE_TTL = 20;

    /** Cached rune effects with timestamp */
    private record CachedRuneEffects(PortalRuneEffects effects, long timestamp) {}

    public CustomPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(AXIS, Direction.Axis.X)
            .setValue(COLOR, PortalColor.BLUE)
            .setValue(LINKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, COLOR, LINKED);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override
    @Nonnull
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    @Override
    @Nonnull
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // Only handle server-side teleportation
        if (level.isClientSide) {
            return;
        }

        // Check if portal is active based on redstone mode
        if (!isActiveForRedstone(level, pos)) {
            resetPortalTime(entity);
            return;
        }

        // Check cooldown (works for all entity types)
        if (entity.isOnPortalCooldown()) {
            resetPortalTime(entity);
            return;
        }

        // Find portal data
        ServerLevel serverLevel = (ServerLevel) level;
        PortalRegistry registry = PortalRegistry.get(serverLevel);
        PortalColor portalColor = state.getValue(COLOR);
        Optional<PortalData> portalOpt = registry.findPortalContaining(serverLevel, pos, portalColor);

        if (portalOpt.isEmpty()) {
            // Only log once per second to avoid spam
            if (level.getGameTime() % 20 == 0) {
                com.devmod.DevMod.LOGGER.debug("[Portal] Entity {} in unregistered portal at {}", entity.getName().getString(), pos);
            }
            resetPortalTime(entity);
            return;
        }

        PortalData portal = portalOpt.get();

        // Check fixed destination first (Nexus zone portals)
        // Fixed destinations have instant teleportation (no delay)
        if (portal.hasFixedDestination()) {
            teleportToFixedDestination(entity, serverLevel, portal);
            resetPortalTime(entity);
            entity.setPortalCooldown(TELEPORT_COOLDOWN);
            return;
        }

        // Then check linked portals
        if (!portal.isLinked()) {
            // Only log once per second to avoid spam
            if (level.getGameTime() % 20 == 0) {
                com.devmod.DevMod.LOGGER.debug("[Portal] Entity {} in unlinked portal {} at {}", entity.getName().getString(), portal.id(), pos);
            }
            resetPortalTime(entity);
            return;
        }

        // Get linked portal
        Optional<PortalData> linkedOpt = registry.get(portal.linkedPortalId().orElse(null));
        if (linkedOpt.isEmpty()) {
            resetPortalTime(entity);
            return;
        }

        PortalData linked = linkedOpt.get();
        Optional<BlockPos> destPosOpt = linked.position();
        if (destPosOpt.isEmpty()) {
            resetPortalTime(entity);
            return;
        }

        // Scan for rune blocks around the portal
        PortalRuneEffects effects = scanForRuneEffects(level, pos);

        // Check cross-dimension requirement
        boolean sameDimension = !portal.isInterDimensional(linked);
        boolean canCrossDimension = PortalConfig.isAlwaysInterdimensional() || effects.crossDimension();
        if (!sameDimension && !canCrossDimension) {
            // Need GATE rune (or alwaysInterdimensional config) for cross-dimension teleportation
            // Show feedback message to player (only once per attempt)
            if (entity instanceof ServerPlayer player) {
                net.minecraft.nbt.CompoundTag data = entity.getPersistentData();
                if (!data.getBoolean(TAG_GATE_FEEDBACK_SHOWN)) {
                    data.putBoolean(TAG_GATE_FEEDBACK_SHOWN, true);
                    player.displayClientMessage(
                        Component.translatable("message.devmod.portal.no_gate_rune"), true);
                }
            }
            resetPortalTime(entity);
            return;
        }

        // Non-player entities cannot teleport cross-dimension (for now)
        if (!sameDimension && !(entity instanceof ServerPlayer)) {
            resetPortalTime(entity);
            return;
        }

        // Track time in portal and check for teleport delay
        int timeInPortal = incrementPortalTime(entity, pos);

        // Play portal entry sound on first tick
        if (timeInPortal == 1) {
            level.playSound(null, pos, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.5F, 1.0F);
            com.devmod.DevMod.LOGGER.info("[Portal] {} entered linked portal, destination: {}",
                entity.getName().getString(), destPosOpt.get());
        }

        // Determine required delay based on HASTE rune and config
        int requiredDelay = calculateRequiredDelay(effects, entity);

        // Sync portal state to client for overlay rendering (rate limited to reduce spam)
        if (entity instanceof ServerPlayer player) {
            net.minecraft.nbt.CompoundTag data = entity.getPersistentData();
            int lastSentTick = data.getInt(TAG_LAST_SENT_TICK);
            // Send on first entry OR every NETWORK_SYNC_INTERVAL ticks
            if (timeInPortal == 1 || (timeInPortal - lastSentTick) >= NETWORK_SYNC_INTERVAL) {
                data.putInt(TAG_LAST_SENT_TICK, timeInPortal);
                NetworkHandler.sendPortalState(player,
                    PortalStatePayload.enterPortal(portalColor, timeInPortal, requiredDelay));
            }
        }

        // Log progress every second
        if (timeInPortal % 20 == 0) {
            com.devmod.DevMod.LOGGER.debug("[Portal] {} waiting: {}/{} ticks",
                entity.getName().getString(), timeInPortal, requiredDelay);
        }

        if (timeInPortal < requiredDelay) {
            // Still waiting - don't teleport yet
            return;
        }

        // Teleport to linked portal
        BlockPos destPos = destPosOpt.get();
        com.devmod.DevMod.LOGGER.info("[Portal] TELEPORTING {} to {}", entity.getName().getString(), destPos);
        teleportEntity(entity, serverLevel, linked, destPos, sameDimension);

        // Reset portal time and set cooldown
        resetPortalTime(entity);
        entity.setPortalCooldown(TELEPORT_COOLDOWN);
    }

    private static final String TAG_LAST_TICK = "devmod:portal_last_tick";
    private static final String TAG_LAST_SENT_TICK = "devmod:portal_last_sent_tick";

    /** Network sync interval in ticks (reduces packet spam by 80%) */
    private static final int NETWORK_SYNC_INTERVAL = 5;

    /**
     * Increments and returns the time an entity has spent in this portal.
     * Only increments once per game tick even if entityInside is called multiple times
     * (which happens when the entity touches multiple portal blocks).
     */
    private int incrementPortalTime(Entity entity, BlockPos currentPos) {
        net.minecraft.nbt.CompoundTag data = entity.getPersistentData();
        long currentTick = entity.level().getGameTime();

        // Only increment once per tick (entityInside is called for each portal block)
        long lastTick = data.getLong(TAG_LAST_TICK);
        if (lastTick == currentTick) {
            // Already processed this tick, just return current time
            return data.getInt(TAG_PORTAL_TIME);
        }

        // New tick - increment time
        data.putLong(TAG_LAST_TICK, currentTick);
        int time = data.getInt(TAG_PORTAL_TIME) + 1;
        data.putInt(TAG_PORTAL_TIME, time);
        data.putLong(TAG_PORTAL_POS, currentPos.asLong()); // Store for reference only
        return time;
    }

    /**
     * Resets the portal time tracking for an entity.
     * Also notifies the client to hide the overlay if the entity is a player.
     */
    private void resetPortalTime(Entity entity) {
        net.minecraft.nbt.CompoundTag data = entity.getPersistentData();
        boolean wasInPortal = data.contains(TAG_PORTAL_TIME);
        data.remove(TAG_PORTAL_TIME);
        data.remove(TAG_PORTAL_POS);
        data.remove(TAG_LAST_TICK);
        data.remove(TAG_LAST_SENT_TICK);
        data.remove(TAG_GATE_FEEDBACK_SHOWN);

        // Notify client to hide overlay
        if (wasInPortal && entity instanceof ServerPlayer player) {
            NetworkHandler.sendPortalState(player, PortalStatePayload.exitPortal());
        }
    }

    /**
     * Calculates the required delay before teleportation based on rune effects and config.
     *
     * <p>Delay is 0 (instant) if any of these conditions are met:
     * <ul>
     *   <li>HASTE rune is present on the portal frame</li>
     *   <li>Config alwaysHaste is TRUE</li>
     *   <li>Config alwaysHaste is CREATIVE_ONLY and entity is a creative mode player</li>
     * </ul>
     *
     * @param effects the calculated rune effects for this portal
     * @param entity the entity attempting to teleport
     * @return 0 for instant teleport, or DEFAULT_TELEPORT_DELAY
     */
    private int calculateRequiredDelay(@Nonnull PortalRuneEffects effects, @Nonnull Entity entity) {
        // HASTE rune always provides instant teleport
        if (effects.instantTeleport()) {
            return 0;
        }

        // Check config-based haste override
        PortalConfig.HasteMode hasteMode = PortalConfig.getAlwaysHasteMode();
        return switch (hasteMode) {
            case TRUE -> 0;
            case CREATIVE_ONLY -> {
                boolean isCreative = entity instanceof ServerPlayer player && player.isCreative();
                yield isCreative ? 0 : DEFAULT_TELEPORT_DELAY;
            }
            case FALSE -> DEFAULT_TELEPORT_DELAY;
        };
    }

    /**
     * Scans for rune blocks around the portal and calculates combined effects.
     * Results are cached for RUNE_CACHE_TTL ticks to avoid repeated DFS scans.
     */
    @Nonnull
    private PortalRuneEffects scanForRuneEffects(Level level, BlockPos portalPos) {
        long gameTime = level.getGameTime();
        long posKey = portalPos.asLong();

        // Check cache first
        CachedRuneEffects cached = RUNE_EFFECT_CACHE.get(posKey);
        if (cached != null && (gameTime - cached.timestamp()) < RUNE_CACHE_TTL) {
            return cached.effects();
        }

        // Cache miss or expired - do the scan
        Set<RuneType> foundRunes = EnumSet.noneOf(RuneType.class);
        Set<BlockPos> visited = new HashSet<>();

        // Scan the portal structure and adjacent blocks for runes
        scanPortalForRunes(level, portalPos, foundRunes, visited, 0);

        PortalRuneEffects effects = PortalRuneEffects.calculate(foundRunes);

        // Cache the result
        RUNE_EFFECT_CACHE.put(posKey, new CachedRuneEffects(effects, gameTime));

        // Periodically clean old entries (every 100 ticks, clean entries older than 5 minutes)
        if (gameTime % 100 == 0) {
            long cutoff = gameTime - 6000; // 5 minutes
            RUNE_EFFECT_CACHE.entrySet().removeIf(e -> e.getValue().timestamp() < cutoff);
        }

        return effects;
    }

    /**
     * Recursively scans portal blocks and their adjacent frame blocks for runes.
     */
    private void scanPortalForRunes(Level level, BlockPos pos, Set<RuneType> foundRunes,
                                     Set<BlockPos> visited, int depth) {
        if (depth > 50 || visited.contains(pos)) {
            return;
        }
        visited.add(pos);

        BlockState state = level.getBlockState(pos);

        // Check if this is a portal block - continue scanning
        if (state.getBlock() instanceof CustomPortalBlock) {
            // Check all 6 directions for more portal blocks or runes
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.relative(dir);
                if (!visited.contains(neighborPos)) {
                    BlockState neighborState = level.getBlockState(neighborPos);

                    // If neighbor is portal, continue scanning
                    if (neighborState.getBlock() instanceof CustomPortalBlock) {
                        scanPortalForRunes(level, neighborPos, foundRunes, visited, depth + 1);
                    }
                    // Check adjacent non-portal blocks for runes
                    else {
                        checkForRune(level, neighborPos, foundRunes, visited);
                    }
                }
            }
        }
    }

    /**
     * Checks if a position contains a rune block and adds it to the found set.
     * Also checks blocks adjacent to frame blocks for runes.
     */
    private void checkForRune(Level level, BlockPos pos, Set<RuneType> foundRunes, Set<BlockPos> visited) {
        if (visited.contains(pos)) {
            return;
        }
        visited.add(pos);

        BlockState state = level.getBlockState(pos);

        // Direct rune block
        if (state.getBlock() instanceof RuneBlock runeBlock) {
            foundRunes.add(runeBlock.getRuneType());
            return;
        }

        // If this is a frame block (solid, not air, not portal), check adjacent for runes
        if (!state.isAir() && !(state.getBlock() instanceof CustomPortalBlock)) {
            for (Direction dir : Direction.values()) {
                BlockPos adjacentPos = pos.relative(dir);
                if (!visited.contains(adjacentPos)) {
                    BlockState adjacentState = level.getBlockState(adjacentPos);
                    if (adjacentState.getBlock() instanceof RuneBlock runeBlock) {
                        visited.add(adjacentPos);
                        foundRunes.add(runeBlock.getRuneType());
                    }
                }
            }
        }
    }

    /**
     * Teleports an entity to the destination portal.
     * Handles both players (cross-dimension capable) and other entities (same-dimension only).
     *
     * @param entity the entity to teleport
     * @param level the current server level
     * @param destPortal the destination portal data
     * @param destPos the destination position
     * @param sameDimension true if source and destination are in the same dimension
     */
    private void teleportEntity(Entity entity, ServerLevel level, PortalData destPortal, BlockPos destPos, boolean sameDimension) {
        double destX = destPos.getX() + 0.5;
        double destY = destPos.getY();
        double destZ = destPos.getZ() + 0.5;

        com.devmod.DevMod.LOGGER.info("[Portal] teleportEntity: sameDim={} destPos={} destX={} destY={} destZ={}",
            sameDimension, destPos, destX, destY, destZ);

        // Play departure sound
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (sameDimension) {
            // Same dimension teleport - works for all entity types
            entity.teleportTo(destX, destY, destZ);
            com.devmod.DevMod.LOGGER.info("[Portal] Same-dimension teleport complete");
        } else if (entity instanceof ServerPlayer player) {
            // Cross-dimension teleport - only for players
            ResourceLocation destDim = destPortal.dimension().orElse(null);
            com.devmod.DevMod.LOGGER.info("[Portal] Cross-dimension teleport to dim={}", destDim);

            ServerLevel destLevel = destDim != null
                ? level.getServer().getLevel(ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, destDim))
                : level;

            if (destLevel == null) {
                destLevel = level;
                com.devmod.DevMod.LOGGER.warn("[Portal] Destination level not found, staying in current level");
            }

            // Set flag to prevent NexusEventHandler from overriding our position
            player.getPersistentData().putBoolean(TAG_CUSTOM_PORTAL_TELEPORT, true);

            // Use DimensionTransition for proper cross-dimension teleport
            // This completely bypasses vanilla portal mechanics
            net.minecraft.world.phys.Vec3 destVec = new net.minecraft.world.phys.Vec3(destX, destY, destZ);
            DimensionTransition transition = new DimensionTransition(
                destLevel,
                destVec,
                net.minecraft.world.phys.Vec3.ZERO, // No momentum
                player.getYRot(),
                player.getXRot(),
                DimensionTransition.DO_NOTHING // No post-teleport action
            );

            com.devmod.DevMod.LOGGER.info("[Portal] Using DimensionTransition to {} at {}",
                destLevel.dimension().location(), destVec);

            // changeDimension returns the player in the new dimension (or same player if same dim)
            Entity teleported = player.changeDimension(transition);

            if (teleported != null) {
                com.devmod.DevMod.LOGGER.info("[Portal] Cross-dimension teleport done. Player now at: {} in {}",
                    teleported.blockPosition(), teleported.level().dimension().location());
            } else {
                com.devmod.DevMod.LOGGER.error("[Portal] changeDimension returned null!");
            }
        }
        // Note: Non-player cross-dimension teleport is blocked in entityInside()

        // Play arrival sound at destination
        level.playSound(null, destPos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * Teleports an entity to a fixed destination (unidirectional portal).
     * Used for Nexus zone portals that always teleport to a specific position.
     *
     * <p>Fixed destination portals:
     * <ul>
     *   <li>Have instant teleportation (no delay)</li>
     *   <li>Support cross-dimension teleportation for players</li>
     *   <li>Support same-dimension teleportation for all entities</li>
     * </ul>
     *
     * @param entity the entity to teleport
     * @param level the current server level
     * @param portal the portal data containing the fixed destination
     */
    private void teleportToFixedDestination(Entity entity, ServerLevel level, PortalData portal) {
        BlockPos destPos = portal.fixedDestination().orElseThrow(
            () -> new IllegalStateException("Portal has no fixed destination"));
        ResourceLocation destDimRL = portal.fixedDestinationDimension().orElse(level.dimension().location());

        double destX = destPos.getX() + 0.5;
        double destY = destPos.getY();
        double destZ = destPos.getZ() + 0.5;

        boolean sameDimension = destDimRL.equals(level.dimension().location());

        com.devmod.DevMod.LOGGER.info("[Portal] Fixed destination teleport: entity={} destPos={} destDim={} sameDim={}",
            entity.getName().getString(), destPos, destDimRL, sameDimension);

        // Play departure sound
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (sameDimension) {
            // Same dimension teleport - works for all entity types
            entity.teleportTo(destX, destY, destZ);
        } else if (entity instanceof ServerPlayer player) {
            // Cross-dimension teleport - only for players
            ServerLevel destLevel = level.getServer().getLevel(
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, destDimRL));

            if (destLevel == null) {
                com.devmod.DevMod.LOGGER.warn("[Portal] Fixed destination level {} not found, aborting", destDimRL);
                return;
            }

            // Set flag to prevent NexusEventHandler from overriding our position
            player.getPersistentData().putBoolean(TAG_CUSTOM_PORTAL_TELEPORT, true);

            // Use DimensionTransition for proper cross-dimension teleport
            net.minecraft.world.phys.Vec3 destVec = new net.minecraft.world.phys.Vec3(destX, destY, destZ);
            DimensionTransition transition = new DimensionTransition(
                destLevel,
                destVec,
                net.minecraft.world.phys.Vec3.ZERO,
                player.getYRot(),
                player.getXRot(),
                DimensionTransition.DO_NOTHING
            );

            player.changeDimension(transition);
        } else {
            // Non-player entities cannot cross dimensions via fixed portals
            com.devmod.DevMod.LOGGER.debug("[Portal] Non-player entity {} cannot use cross-dimension fixed portal",
                entity.getName().getString());
            return;
        }

        // Play arrival sound at destination
        level.playSound(null, destPos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Reduced frequency: 1/1000 chance per block per tick (was 1/100)
        // This prevents sound pool overflow with multi-block portals
        if (random.nextInt(1000) == 0) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.3F,  // Reduced volume
                random.nextFloat() * 0.4F + 0.8F, false);
        }

        // Get portal color for colored particles
        PortalColor portalColor = state.getValue(COLOR);
        org.joml.Vector3f colorVec = new org.joml.Vector3f(
            portalColor.getRed() / 255.0F,
            portalColor.getGreen() / 255.0F,
            portalColor.getBlue() / 255.0F
        );
        DustParticleOptions coloredDust = new DustParticleOptions(colorVec, 1.0F);

        // Portal particles - mix of vanilla and colored
        for (int i = 0; i < 4; ++i) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            double dx = (random.nextDouble() - 0.5D) * 0.5D;
            double dy = (random.nextDouble() - 0.5D) * 0.5D;
            double dz = (random.nextDouble() - 0.5D) * 0.5D;

            // Alternate between colored dust and vanilla portal particles
            if (random.nextBoolean()) {
                level.addParticle(coloredDust, x, y, z, 0, 0.05, 0);
            } else {
                level.addParticle(ParticleTypes.PORTAL, x, y, z, dx, dy, dz);
            }
        }
    }

    @Override
    @Nonnull
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                   LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // Break if frame is destroyed
        Direction.Axis axis = state.getValue(AXIS);
        boolean frameDirection = (axis == Direction.Axis.X)
            ? (direction == Direction.EAST || direction == Direction.WEST)
            : (direction == Direction.NORTH || direction == Direction.SOUTH);

        if (frameDirection || direction == Direction.UP || direction == Direction.DOWN) {
            // Check if neighbor portal block still exists
            if (neighborState.isAir()) {
                // Only break if this would disconnect the portal
                return Blocks.AIR.defaultBlockState();
            }
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) {
                PortalRegistry registry = PortalRegistry.get(serverLevel);
                registry.getByPosition(pos).ifPresent(portal -> {
                    registry.unlink(portal.id(), serverLevel.getServer());
                    registry.unregister(portal.id());
                });
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Checks if the portal is active based on the configured redstone mode.
     *
     * <p>Redstone modes:
     * <ul>
     *   <li>OFF - Portal active when NO redstone signal is present</li>
     *   <li>ON - Portal active when redstone signal IS present</li>
     *   <li>IGNORE - Portal always active (ignores redstone)</li>
     * </ul>
     *
     * @param level the level containing the portal
     * @param pos the position of the portal block
     * @return true if the portal should allow teleportation
     */
    private boolean isActiveForRedstone(@Nonnull Level level, @Nonnull BlockPos pos) {
        PortalConfig.RedstoneMode mode = PortalConfig.getRedstoneMode();

        // IGNORE mode: always active
        if (mode == PortalConfig.RedstoneMode.IGNORE) {
            return true;
        }

        // Check if any adjacent block is providing redstone power
        boolean hasPower = level.hasNeighborSignal(pos);

        return switch (mode) {
            case OFF -> !hasPower;  // Active when no power
            case ON -> hasPower;    // Active when powered
            case IGNORE -> true;    // Already handled above, but for completeness
        };
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 11;
    }
}
