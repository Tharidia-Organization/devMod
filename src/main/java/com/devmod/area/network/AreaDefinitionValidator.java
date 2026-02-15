package com.devmod.area.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.DevMod;
import com.devmod.area.aesthetic.AreaBuilderMessages;
import com.devmod.area.aesthetic.AreaBuilderNaming;
import com.devmod.area.builder.AreaShapeGenerator;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaPalette;
import com.devmod.area.data.AreaShape;
import com.devmod.area.data.BiomeGenerationConfig;

/**
 * Validates and sanitizes area definitions received from clients.
 * Extracted from AreaNetworkHandler for better modularity.
 */
final class AreaDefinitionValidator {

    private static final int MAX_CUSTOM_SHAPE_POSITIONS = 65_536;
    private static final int MAX_PALETTE_MATERIALS = 128;
    /** H-06 fix: Maximum NBT size for custom shapes (64KB) */
    private static final int MAX_CUSTOM_SHAPE_NBT_SIZE = 65_536;

    // SEC-01 fix: PATH shape limits to prevent DoS
    /** Minimum corridor width for PATH shapes */
    private static final int MIN_PATH_WIDTH = 2;
    /** Maximum corridor width for PATH shapes */
    private static final int MAX_PATH_WIDTH = 32;
    /** Maximum number of waypoints for PATH shapes */
    private static final int MAX_PATH_WAYPOINTS = 100;
    /** SEC-07 fix: Maximum distance from center for any waypoint coordinate */
    private static final int MAX_PATH_WAYPOINT_DISTANCE = 500;
    /** SEC-07 fix: Maximum distance between consecutive waypoints */
    private static final int MAX_PATH_SEGMENT_LENGTH = 200;

    private AreaDefinitionValidator() {}

    /**
     * Sanitizes and validates an AreaDefinition from client input.
     *
     * @param input      The raw definition from the client
     * @param existing   Existing definition (null for new areas)
     * @param player     The player who sent the request
     * @param isNewArea  Whether this is a new area creation
     * @return sanitized definition, or null if validation failed (error message already sent)
     */
    @Nullable
    static AreaDefinition sanitizeDefinition(@Nonnull AreaDefinition input,
                                              @Nullable AreaDefinition existing,
                                              @Nonnull ServerPlayer player,
                                              boolean isNewArea) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(player, "player");

        String trimmedName = input.name() != null ? input.name().trim() : "";
        Component nameError = AreaBuilderNaming.validateDisplayName(trimmedName);
        if (nameError != null) {
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("area.builder.error.invalid_name", nameError)), true);
            return null;
        }

        AreaDimensions dims = input.dimensions();
        if (dims == null ||
            dims.width() < AreaDimensions.MIN_SIZE || dims.width() > AreaDimensions.MAX_SIZE ||
            dims.length() < AreaDimensions.MIN_SIZE || dims.length() > AreaDimensions.MAX_SIZE ||
            dims.height() < AreaDimensions.MIN_HEIGHT || dims.height() > AreaDimensions.MAX_HEIGHT) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_dimensions")), true);
            return null;
        }

        if (input.palette() == null ||
            input.palette().materials() == null ||
            input.palette().materials().size() > MAX_PALETTE_MATERIALS ||
            // M-14 fix: Require minimum palette entries to prevent empty palette errors during build
            input.palette().materials().size() < AreaPalette.MIN_REQUIRED_MATERIALS) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_palette")), true);
            return null;
        }
        if (input.options() == null || input.centerPosition() == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_definition")), true);
            return null;
        }

        // CRIT-02 fix: Validate shape and generationType before using them
        if (input.shape() == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_definition")), true);
            return null;
        }
        if (input.generationType() == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_definition")), true);
            return null;
        }
        // CRIT-02 fix: Validate wallStyle within options
        if (input.options().wallStyle() == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_definition")), true);
            return null;
        }

        ResourceLocation dimensionId = input.dimensionId();
        if (dimensionId == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_dimension")), true);
            return null;
        }

        var server = player.getServer();
        if (server == null) {
            return null;
        }

        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey =
            net.minecraft.resources.ResourceKey.create(
                Objects.requireNonNull(net.minecraft.core.registries.Registries.DIMENSION), dimensionId);
        ServerLevel targetLevel = server.getLevel(Objects.requireNonNull(levelKey));
        if (targetLevel == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_dimension")), true);
            return null;
        }

        if (isNewArea && !player.level().dimension().location().equals(dimensionId)) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_dimension")), true);
            return null;
        }
        if (!isNewArea && existing != null && !existing.dimensionId().equals(dimensionId)) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_dimension")), true);
            return null;
        }

        int minY = targetLevel.getMinBuildHeight();
        int maxY = targetLevel.getMaxBuildHeight();
        int topY = dims.floorY() + dims.height();
        if (dims.floorY() < minY || topY >= maxY) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_BOUNDS)), true);
            return null;
        }

        if (input.generationType() == com.devmod.area.data.AreaGenerationType.BIOME && input.biomeConfig() == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_definition")), true);
            return null;
        }

        if (!validateShape(input, dims, player)) {
            return null;
        }

        // HIGH-06 fix: Validate zone ID format before checking existence
        String linkedZoneId = input.linkedZoneId();
        if (linkedZoneId != null) {
            if (linkedZoneId.isBlank()) {
                linkedZoneId = null;
            } else if (linkedZoneId.length() > 64) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_zone_id_too_long")), true);
                return null;
            }
        }

        long now = System.currentTimeMillis();
        UUID finalId;
        UUID creatorUUID;
        long createdAt;
        int revision;
        if (isNewArea) {
            finalId = UUID.randomUUID();
            creatorUUID = player.getUUID();
            createdAt = now;
            revision = 0;
        } else {
            AreaDefinition ex = Objects.requireNonNull(existing);
            finalId = ex.id();
            creatorUUID = ex.creatorUUID();
            createdAt = ex.createdAt();
            revision = ex.revision() + 1;
        }
        var biomeConfig = input.generationType() == com.devmod.area.data.AreaGenerationType.BIOME
            ? ensureStableSeed(input.biomeConfig()) : null;
        // SEC-01 fix: Also preserve customShapeNbt for PATH shapes (contains waypoints)
        AreaShape shape = input.shape();
        var customShapeNbt = (shape == AreaShape.CUSTOM_NBT || shape == AreaShape.PATH)
            ? input.customShapeNbt() : null;

        return new AreaDefinition(
            finalId,
            trimmedName,
            input.generationType(),
            shape,
            dims,
            input.centerPosition(),
            dimensionId,
            input.palette(),
            biomeConfig,
            input.options(),
            creatorUUID,
            createdAt,
            now,
            revision,
            input.isMainHub(),
            linkedZoneId,
            customShapeNbt
        );
    }

    /**
     * Validates shape-specific constraints (CUSTOM_NBT, PATH).
     *
     * @return true if valid, false if validation failed (error already sent)
     */
    private static boolean validateShape(@Nonnull AreaDefinition input,
                                          @Nonnull AreaDimensions dims,
                                          @Nonnull ServerPlayer player) {
        AreaShape shape = input.shape();

        if (shape == AreaShape.CUSTOM_NBT) {
            // H-06 fix: Validate NBT size to prevent oversized payloads
            var customNbt = input.customShapeNbt();
            if (customNbt != null) {
                int nbtSize = customNbt.sizeInBytes();
                if (nbtSize > MAX_CUSTOM_SHAPE_NBT_SIZE) {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_custom_shape_too_large")), true);
                    DevMod.LOGGER.warn("Player {} submitted custom shape with NBT size {} (max: {})",
                        player.getName().getString(), nbtSize, MAX_CUSTOM_SHAPE_NBT_SIZE);
                    return false;
                }
            }
            int count = AreaShapeGenerator.generateCustomFloor(
                Objects.requireNonNull(input.centerPosition()),
                Objects.requireNonNull(dims),
                customNbt
            ).size();
            if (count <= 0 || count > MAX_CUSTOM_SHAPE_POSITIONS) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_custom_shape")), true);
                return false;
            }

            // HIGH-02 fix: Validate that custom positions are within declared dimensions
            if (!AreaShapeGenerator.validateCustomNbtBounds(
                    Objects.requireNonNull(input.centerPosition()), dims, customNbt)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_custom_shape_out_of_bounds")), true);
                DevMod.LOGGER.warn("Player {} submitted custom shape with positions outside declared bounds",
                    player.getName().getString());
                return false;
            }
        } else if (shape == AreaShape.PATH) {
            return validatePathShape(input, dims, player);
        }

        return true;
    }

    /**
     * Validates PATH shape-specific constraints.
     */
    private static boolean validatePathShape(@Nonnull AreaDefinition input,
                                              @Nonnull AreaDimensions dims,
                                              @Nonnull ServerPlayer player) {
        // SEC-01 fix: Validate PATH shape to prevent DoS via large pathWidth or waypoint count
        var pathNbt = input.customShapeNbt();
        if (pathNbt == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_path_min_waypoints")), true);
            DevMod.LOGGER.warn("Player {} submitted PATH shape without waypoint data",
                player.getName().getString());
            return false;
        }

        // Validate pathWidth bounds
        if (pathNbt.contains(AreaShapeGenerator.NBT_PATH_WIDTH)) {
            int pathWidth = pathNbt.getInt(AreaShapeGenerator.NBT_PATH_WIDTH);
            if (pathWidth < MIN_PATH_WIDTH || pathWidth > MAX_PATH_WIDTH) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_path_width")), true);
                DevMod.LOGGER.warn("Player {} submitted PATH shape with invalid width: {} (allowed: {}-{})",
                    player.getName().getString(), pathWidth, MIN_PATH_WIDTH, MAX_PATH_WIDTH);
                return false;
            }
        }

        // Validate waypoint count
        java.util.List<BlockPos> waypoints = AreaShapeGenerator.parsePathWaypoints(
            Objects.requireNonNull(input.centerPosition()), pathNbt);
        if (waypoints.size() < 2) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_path_min_waypoints")), true);
            return false;
        }
        if (waypoints.size() > MAX_PATH_WAYPOINTS) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_path_too_many_waypoints")), true);
            DevMod.LOGGER.warn("Player {} submitted PATH shape with {} waypoints (max: {})",
                player.getName().getString(), waypoints.size(), MAX_PATH_WAYPOINTS);
            return false;
        }

        // SEC-07 fix: Validate waypoint coordinates and segment lengths BEFORE generateFloor
        if (!validateWaypointCoordinates(input, waypoints, player)) {
            return false;
        }

        // Validate that generated floor size is reasonable
        int floorSize = AreaShapeGenerator.generateFloor(
            input.shape(), Objects.requireNonNull(input.centerPosition()), dims, pathNbt).size();
        if (floorSize <= 0 || floorSize > MAX_CUSTOM_SHAPE_POSITIONS) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_path_too_large")), true);
            DevMod.LOGGER.warn("Player {} submitted PATH shape generating {} positions (max: {})",
                player.getName().getString(), floorSize, MAX_CUSTOM_SHAPE_POSITIONS);
            return false;
        }

        return true;
    }

    /**
     * SEC-07 fix: Validates waypoint coordinates and segment lengths.
     */
    private static boolean validateWaypointCoordinates(@Nonnull AreaDefinition input,
                                                        @Nonnull java.util.List<BlockPos> waypoints,
                                                        @Nonnull ServerPlayer player) {
        BlockPos center = Objects.requireNonNull(input.centerPosition());
        for (int i = 0; i < waypoints.size(); i++) {
            BlockPos wp = waypoints.get(i);
            // Check distance from center
            int distX = Math.abs(wp.getX() - center.getX());
            int distZ = Math.abs(wp.getZ() - center.getZ());
            if (distX > MAX_PATH_WAYPOINT_DISTANCE || distZ > MAX_PATH_WAYPOINT_DISTANCE) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_path_waypoint_too_far")), true);
                DevMod.LOGGER.warn("Player {} submitted PATH with waypoint {} at distance ({}, {}) from center (max: {})",
                    player.getName().getString(), i, distX, distZ, MAX_PATH_WAYPOINT_DISTANCE);
                return false;
            }
            // Check segment length to next waypoint
            if (i < waypoints.size() - 1) {
                BlockPos next = waypoints.get(i + 1);
                int segX = Math.abs(next.getX() - wp.getX());
                int segZ = Math.abs(next.getZ() - wp.getZ());
                int segLength = Math.max(segX, segZ); // Manhattan-ish for corridor
                if (segLength > MAX_PATH_SEGMENT_LENGTH) {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_path_segment_too_long")), true);
                    DevMod.LOGGER.warn("Player {} submitted PATH with segment {}->{} length {} (max: {})",
                        player.getName().getString(), i, i + 1, segLength, MAX_PATH_SEGMENT_LENGTH);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Ensures biome config has a stable seed for consistent terrain generation.
     * If seed is 0, generates one from current nanoTime at creation time.
     * This prevents multi-tick builds from using different seeds per call.
     */
    @Nullable
    static BiomeGenerationConfig ensureStableSeed(@Nullable BiomeGenerationConfig config) {
        if (config == null) {
            return null;
        }
        if (config.seed() != 0L) {
            return config;
        }
        // Generate stable seed from current time at creation
        return config.withSeed(System.nanoTime());
    }

    /**
     * M-05 fix: Validates that an editor position is within valid world bounds.
     * Prevents invalid BlockPos from being registered which could cause issues.
     *
     * @param pos   The position to validate
     * @param level The level to check bounds against
     * @return true if position is valid
     */
    static boolean isValidEditorPosition(@Nonnull BlockPos pos, @Nonnull net.minecraft.world.level.Level level) {
        Objects.requireNonNull(pos);
        Objects.requireNonNull(level);

        // Check horizontal bounds (Minecraft world coordinate limits)
        int x = pos.getX();
        int z = pos.getZ();
        if (x < -30_000_000 || x >= 30_000_000 || z < -30_000_000 || z >= 30_000_000) {
            DevMod.LOGGER.warn("[Area] Rejected invalid editor position: {} (out of world bounds)", pos);
            return false;
        }

        // Check vertical bounds against level limits
        int y = pos.getY();
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            DevMod.LOGGER.warn("[Area] Rejected invalid editor position: {} (Y out of bounds for level)", pos);
            return false;
        }

        return true;
    }
}
