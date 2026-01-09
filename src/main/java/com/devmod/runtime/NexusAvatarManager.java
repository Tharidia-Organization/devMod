package com.devmod.runtime;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import com.devmod.config.Config;
import com.devmod.entity.ModEntities;
import com.devmod.entity.NexaEntity;

/**
 * Spawns and maintains the Nexus AI avatar (NexaEntity).
 *
 * <p>This manager handles the lifecycle of the Nexa entity in the Nexus hub:
 * <ul>
 *   <li>Spawning when the hub is built</li>
 *   <li>Cleanup on server shutdown</li>
 *   <li>Finding the avatar for interaction handling</li>
 * </ul>
 *
 * <p>The NexaEntity handles its own animation and interaction - this manager
 * just handles spawn/remove lifecycle.
 */
public final class NexusAvatarManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusAvatarManager.class);

    /**
     * Tag used to identify avatar entities.
     * @deprecated Use {@link NexaEntity#NEXA_TAG} instead. Kept for backwards compatibility cleanup.
     */
    @Deprecated
    public static final String AVATAR_TAG = "devmod_nexus_avatar";

    private static final int BASE_Y_OFFSET = 2;
    private static final int CLEANUP_RADIUS = 40;

    private NexusAvatarManager() {}

    /**
     * Spawn the Nexa avatar at the hub origin.
     *
     * @param level The server level (Nexus dimension)
     * @param hubOrigin The hub center position
     */
    public static void spawn(@Nullable ServerLevel level, @Nullable BlockPos hubOrigin) {
        if (level == null || hubOrigin == null) {
            return;
        }
        if (!Config.NEXUS_AVATAR_ENABLED.get()) {
            remove(level, hubOrigin);
            return;
        }

        // Remove any existing avatar first
        remove(level, hubOrigin);

        // Calculate spawn position
        BlockPos spawnPos = resolveSpawnPos(hubOrigin);
        String name = resolveName();
        String skinName = resolveSkinName();

        // Create the NexaEntity
        NexaEntity nexa = new NexaEntity(ModEntities.NEXA.get(), level);
        nexa.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        nexa.resetBaseY(spawnPos.getY());

        // Configure the entity
        nexa.setCustomName(Component.literal(name));
        nexa.setCustomNameVisible(true);
        nexa.setGlowingTag(Config.NEXUS_AVATAR_GLOW.get());

        // Set skin if specified
        if (!skinName.isEmpty()) {
            nexa.setSkinName(skinName);
        }

        // Spawn the entity
        if (level.addFreshEntity(nexa)) {
            LOGGER.info("[NexusAvatar] Spawned NexaEntity '{}' at {}", name, spawnPos);
        } else {
            LOGGER.warn("[NexusAvatar] Failed to spawn NexaEntity at {}", spawnPos);
        }
    }

    /**
     * Remove any existing avatar from the level.
     *
     * @param level The server level
     * @param hubOrigin The hub center position
     */
    public static void remove(@Nullable ServerLevel level, @Nullable BlockPos hubOrigin) {
        if (level == null) {
            return;
        }

        AABB bounds = resolveCleanupBounds(hubOrigin);

        // Remove NexaEntity instances
        List<NexaEntity> nexaEntities = level.getEntitiesOfClass(NexaEntity.class, bounds);
        for (NexaEntity nexa : nexaEntities) {
            nexa.discard();
        }

        // Also clean up legacy entities (for migration from old system)
        cleanupLegacyEntities(level, bounds);

        if (!nexaEntities.isEmpty()) {
            LOGGER.debug("[NexusAvatar] Removed {} NexaEntity instance(s)", nexaEntities.size());
        }
    }

    /**
     * Clean up legacy avatar entities from the old EasyNPC/fallback system.
     */
    private static void cleanupLegacyEntities(@Nonnull ServerLevel level, @Nonnull AABB bounds) {
        // Remove entities with old avatar tags
        List<Entity> legacyEntities = level.getEntities((Entity) null, bounds,
            entity -> entity.getTags().contains(AVATAR_TAG) ||
                      entity.getTags().contains(NexaEntity.NEXA_TAG));

        int legacyCount = 0;
        for (Entity entity : legacyEntities) {
            if (!(entity instanceof NexaEntity)) {
                entity.discard();
                legacyCount++;
            }
        }

        if (legacyCount > 0) {
            LOGGER.debug("[NexusAvatar] Cleaned up {} legacy avatar entities", legacyCount);
        }
    }

    /**
     * Check if an avatar exists in the level.
     *
     * @param level The server level
     * @param hubOrigin The hub center position
     * @return true if an avatar exists
     */
    public static boolean hasAvatar(@Nullable ServerLevel level, @Nullable BlockPos hubOrigin) {
        if (level == null) {
            return false;
        }
        AABB bounds = resolveCleanupBounds(hubOrigin);
        return !level.getEntitiesOfClass(NexaEntity.class, bounds).isEmpty();
    }

    /**
     * Find the avatar entity in the level.
     *
     * @param level The server level
     * @param hubOrigin The hub center position
     * @return The NexaEntity, or null if not found
     */
    @Nullable
    public static NexaEntity findAvatar(@Nullable ServerLevel level, @Nullable BlockPos hubOrigin) {
        if (level == null) {
            return null;
        }
        AABB bounds = resolveCleanupBounds(hubOrigin);
        List<NexaEntity> avatars = level.getEntitiesOfClass(NexaEntity.class, bounds);
        return avatars.isEmpty() ? null : avatars.get(0);
    }

    /**
     * Called every server tick.
     * NexaEntity handles its own animation, so this is just for respawn checks.
     *
     * @param level The server level
     * @param hubOrigin The hub center position
     */
    public static void tick(@Nullable ServerLevel level, @Nullable BlockPos hubOrigin) {
        if (level == null || !Config.NEXUS_AVATAR_ENABLED.get()) {
            return;
        }

        // Check if avatar needs to be respawned
        if (!hasAvatar(level, hubOrigin)) {
            LOGGER.debug("[NexusAvatar] Avatar not found, respawning...");
            spawn(level, hubOrigin);
        }
    }

    /**
     * Cleanup resources when shutting down.
     * Called from NexusDimensionManager.shutdown().
     *
     * @param level The server level
     * @param hubOrigin The hub center position
     */
    public static void cleanup(@Nullable ServerLevel level, @Nullable BlockPos hubOrigin) {
        remove(level, hubOrigin);
        LOGGER.debug("[NexusAvatar] Cleaned up avatar");
    }

    /**
     * Handle player interaction with the avatar.
     * Note: NexaEntity handles interaction directly via mobInteract(),
     * so this method is only needed for legacy entity support.
     *
     * @param player The interacting player
     * @param avatar The avatar entity
     * @deprecated NexaEntity handles interaction directly
     */
    @Deprecated
    public static void handleInteraction(net.minecraft.server.level.ServerPlayer player, Entity avatar) {
        if (player == null || avatar == null) {
            return;
        }

        // NexaEntity handles this in mobInteract()
        if (avatar instanceof NexaEntity) {
            return;
        }

        // Legacy support for old avatar entities
        if (!avatar.getTags().contains(AVATAR_TAG) && !avatar.getTags().contains(NexaEntity.NEXA_TAG)) {
            return;
        }

        LOGGER.debug("[NexusAvatar] Legacy interaction handler for player {}", player.getName().getString());
        com.devmod.runtime.network.NexusNetworkHandler.openGreetingDialog(player);
    }

    // === Helper Methods ===

    @Nonnull
    private static BlockPos resolveSpawnPos(@Nonnull BlockPos hubOrigin) {
        BlockPos anchor = resolveAnchor(hubOrigin);
        int y = anchor.getY() + BASE_Y_OFFSET + Config.NEXUS_AVATAR_FLOAT_OFFSET.get();
        return new BlockPos(anchor.getX(), y, anchor.getZ());
    }

    @Nonnull
    private static BlockPos resolveAnchor(@Nonnull BlockPos hubOrigin) {
        BlockPos offset = Objects.requireNonNull(NexusSpawnManager.getDefaultSpawnOffset(), "spawn offset");
        return Objects.requireNonNull(hubOrigin.offset(offset), "anchor");
    }

    @Nonnull
    private static AABB resolveCleanupBounds(@Nullable BlockPos hubOrigin) {
        BlockPos anchor = hubOrigin == null ? BlockPos.ZERO : resolveAnchor(hubOrigin);
        int minX = anchor.getX() - CLEANUP_RADIUS;
        int maxX = anchor.getX() + CLEANUP_RADIUS;
        int minZ = anchor.getZ() - CLEANUP_RADIUS;
        int maxZ = anchor.getZ() + CLEANUP_RADIUS;
        int minY = anchor.getY() - 8;
        int maxY = anchor.getY() + 40;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Nonnull
    private static String resolveName() {
        String name = Config.NEXUS_AVATAR_NAME.get();
        if (name == null || name.isBlank()) {
            return "NEXA";
        }
        return Objects.requireNonNull(name.trim(), "name");
    }

    @Nonnull
    private static String resolveSkinName() {
        String skin = Config.NEXUS_AVATAR_SKIN.get();
        if (skin == null || skin.isBlank()) {
            return "";
        }

        String trimmed = skin.trim();
        if ("none".equalsIgnoreCase(trimmed) || "off".equalsIgnoreCase(trimmed)) {
            return "";
        }

        // Extract player name from various formats
        if (trimmed.startsWith("player:")) {
            return Objects.requireNonNull(trimmed.substring("player:".length()).trim(), "skinName");
        }

        // For texture: or base64: formats, return empty (not supported in NexaEntity skin system yet)
        if (trimmed.startsWith("texture:") || trimmed.startsWith("base64:")) {
            return "";
        }

        // Assume it's a player name
        return trimmed;
    }
}
