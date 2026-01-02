package com.devmod.security;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

/**
 * P1-011: Permission escalation protection.
 *
 * Prevents unauthorized privilege escalation through:
 * <ul>
 *   <li>Role-based access control (RBAC) with hierarchy</li>
 *   <li>Permission level validation before operations</li>
 *   <li>Temporal permission grants with expiry</li>
 *   <li>Audit logging of permission changes</li>
 *   <li>Anti-escalation checks (can't grant higher than own level)</li>
 * </ul>
 *
 * <p>Permission Hierarchy (lowest to highest):
 * <pre>
 * PLAYER (0) < TRUSTED (1) < MODERATOR (2) < ADMIN (3) < OWNER (4)
 * </pre>
 */
public final class PermissionGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionGuard.class);

    // ============================================================================
    // PERMISSION LEVELS
    // ============================================================================

    /**
     * Permission levels in ascending order of privilege.
     */
    public enum PermissionLevel {
        /** Default player with no special permissions */
        PLAYER(0),
        /** Trusted player with limited additional access */
        TRUSTED(1),
        /** Moderator with moderation tools */
        MODERATOR(2),
        /** Admin with configuration access */
        ADMIN(3),
        /** Server owner with full access */
        OWNER(4);

        private final int level;

        PermissionLevel(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }

        public boolean isAtLeast(PermissionLevel required) {
            return this.level >= required.level;
        }

        public boolean canGrant(PermissionLevel target) {
            // Can only grant permissions lower than own level
            return this.level > target.level;
        }

        public boolean canRevoke(PermissionLevel target) {
            // Can only revoke permissions lower than or equal to own level minus 1
            return this.level > target.level;
        }
    }

    /**
     * Specific permissions that can be granted.
     */
    public enum Permission {
        // Player permissions
        USE_RADIAL_MENU(PermissionLevel.PLAYER),
        JOIN_QUEST(PermissionLevel.PLAYER),
        CREATE_PARTY(PermissionLevel.PLAYER),
        SEND_MAIL(PermissionLevel.PLAYER),

        // Trusted permissions
        CREATE_TICKET(PermissionLevel.TRUSTED),
        VIEW_OWN_TICKETS(PermissionLevel.TRUSTED),

        // Moderator permissions
        VIEW_ALL_TICKETS(PermissionLevel.MODERATOR),
        RESOLVE_TICKETS(PermissionLevel.MODERATOR),
        KICK_PLAYER(PermissionLevel.MODERATOR),
        MUTE_PLAYER(PermissionLevel.MODERATOR),
        VIEW_PLAYER_STATS(PermissionLevel.MODERATOR),

        // Admin permissions
        MANAGE_ARENAS(PermissionLevel.ADMIN),
        MODIFY_CONFIG(PermissionLevel.ADMIN),
        GRANT_PERMISSIONS(PermissionLevel.ADMIN),
        REVOKE_PERMISSIONS(PermissionLevel.ADMIN),
        VIEW_TELEMETRY(PermissionLevel.ADMIN),
        MANAGE_TEMPLATES(PermissionLevel.ADMIN),

        // Owner permissions
        MANAGE_ADMINS(PermissionLevel.OWNER),
        SERVER_SHUTDOWN(PermissionLevel.OWNER),
        DATA_EXPORT(PermissionLevel.OWNER);

        private final PermissionLevel requiredLevel;

        Permission(PermissionLevel requiredLevel) {
            this.requiredLevel = requiredLevel;
        }

        public PermissionLevel getRequiredLevel() {
            return requiredLevel;
        }
    }

    // ============================================================================
    // STATE
    // ============================================================================

    private static final Map<UUID, PermissionLevel> PERMISSION_LEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<Permission>> EXPLICIT_GRANTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<Permission>> EXPLICIT_DENIES = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Permission, Long>> TEMPORARY_GRANTS = new ConcurrentHashMap<>();

    // ============================================================================
    // PERMISSION CHECKS
    // ============================================================================

    /**
     * Check if a player has a specific permission.
     *
     * @param player The player to check
     * @param permission The permission to check
     * @return true if the player has the permission
     */
    public static boolean hasPermission(ServerPlayer player, Permission permission) {
        if (player == null) return false;
        return hasPermission(player.getUUID(), permission);
    }

    /**
     * Check if a player UUID has a specific permission.
     */
    public static boolean hasPermission(UUID playerId, Permission permission) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(permission, "permission");

        // Check explicit denies first (highest priority)
        Set<Permission> denies = EXPLICIT_DENIES.get(playerId);
        if (denies != null && denies.contains(permission)) {
            return false;
        }

        // Check temporary grants
        Map<Permission, Long> tempGrants = TEMPORARY_GRANTS.get(playerId);
        if (tempGrants != null) {
            Long expiry = tempGrants.get(permission);
            if (expiry != null) {
                if (System.currentTimeMillis() < expiry) {
                    return true;
                } else {
                    tempGrants.remove(permission); // Clean up expired
                }
            }
        }

        // Check explicit grants
        Set<Permission> grants = EXPLICIT_GRANTS.get(playerId);
        if (grants != null && grants.contains(permission)) {
            return true;
        }

        // Fall back to level-based check
        PermissionLevel level = getPermissionLevel(playerId);
        return level.isAtLeast(permission.getRequiredLevel());
    }

    /**
     * Require a permission, throwing if not granted.
     *
     * @throws SecurityException if permission is not granted
     */
    public static void requirePermission(ServerPlayer player, Permission permission) {
        if (!hasPermission(player, permission)) {
            LOGGER.warn("[Security] Permission denied: {} requires {} for {}",
                player.getName().getString(), permission, player.getUUID());
            throw new SecurityException("Permission denied: " + permission);
        }
    }

    /**
     * Get the permission level of a player.
     */
    public static PermissionLevel getPermissionLevel(UUID playerId) {
        return PERMISSION_LEVELS.getOrDefault(playerId, PermissionLevel.PLAYER);
    }

    /**
     * Get the permission level of a player.
     */
    public static PermissionLevel getPermissionLevel(ServerPlayer player) {
        return getPermissionLevel(player.getUUID());
    }

    // ============================================================================
    // PERMISSION MANAGEMENT
    // ============================================================================

    /**
     * Set a player's permission level.
     *
     * @param grantor The player granting the permission
     * @param targetId The player receiving the permission
     * @param newLevel The new permission level
     * @return true if successful
     */
    public static boolean setPermissionLevel(ServerPlayer grantor, UUID targetId, PermissionLevel newLevel) {
        PermissionLevel grantorLevel = getPermissionLevel(grantor);

        // Anti-escalation check: can't grant equal or higher than own level
        if (!grantorLevel.canGrant(newLevel)) {
            LOGGER.warn("[Security] Escalation blocked: {} ({}) tried to grant {} to {}",
                grantor.getName().getString(), grantorLevel, newLevel, targetId);
            return false;
        }

        // Can't modify someone with equal or higher level
        PermissionLevel targetCurrentLevel = getPermissionLevel(targetId);
        if (!grantorLevel.canGrant(targetCurrentLevel) && targetCurrentLevel != PermissionLevel.PLAYER) {
            LOGGER.warn("[Security] Escalation blocked: {} ({}) tried to modify {} ({})",
                grantor.getName().getString(), grantorLevel, targetId, targetCurrentLevel);
            return false;
        }

        PERMISSION_LEVELS.put(targetId, newLevel);
        LOGGER.info("[Security] {} set {} permission level to {}",
            grantor.getName().getString(), targetId, newLevel);

        return true;
    }

    /**
     * Grant a specific permission to a player.
     */
    public static boolean grantPermission(ServerPlayer grantor, UUID targetId, Permission permission) {
        PermissionLevel grantorLevel = getPermissionLevel(grantor);

        // Must have the permission yourself or be above its required level
        if (!grantorLevel.isAtLeast(permission.getRequiredLevel())) {
            LOGGER.warn("[Security] Grant blocked: {} ({}) doesn't have {} to grant",
                grantor.getName().getString(), grantorLevel, permission);
            return false;
        }

        // Must have GRANT_PERMISSIONS permission
        if (!hasPermission(grantor, Permission.GRANT_PERMISSIONS)) {
            LOGGER.warn("[Security] Grant blocked: {} lacks GRANT_PERMISSIONS",
                grantor.getName().getString());
            return false;
        }

        EXPLICIT_GRANTS.computeIfAbsent(targetId, k -> EnumSet.noneOf(Permission.class))
            .add(permission);

        LOGGER.info("[Security] {} granted {} to {}",
            grantor.getName().getString(), permission, targetId);

        return true;
    }

    /**
     * Grant a temporary permission that expires.
     */
    public static boolean grantTemporaryPermission(
            ServerPlayer grantor, UUID targetId, Permission permission, long duration, TimeUnit unit) {

        if (!hasPermission(grantor, Permission.GRANT_PERMISSIONS)) {
            return false;
        }

        PermissionLevel grantorLevel = getPermissionLevel(grantor);
        if (!grantorLevel.isAtLeast(permission.getRequiredLevel())) {
            return false;
        }

        long expiry = System.currentTimeMillis() + unit.toMillis(duration);
        TEMPORARY_GRANTS.computeIfAbsent(targetId, k -> new ConcurrentHashMap<>())
            .put(permission, expiry);

        LOGGER.info("[Security] {} granted temporary {} to {} (expires in {} {})",
            grantor.getName().getString(), permission, targetId, duration, unit);

        return true;
    }

    /**
     * Revoke a specific permission from a player.
     */
    public static boolean revokePermission(ServerPlayer revoker, UUID targetId, Permission permission) {
        PermissionLevel revokerLevel = getPermissionLevel(revoker);

        // Must have REVOKE_PERMISSIONS permission
        if (!hasPermission(revoker, Permission.REVOKE_PERMISSIONS)) {
            LOGGER.warn("[Security] Revoke blocked: {} lacks REVOKE_PERMISSIONS",
                revoker.getName().getString());
            return false;
        }

        // Can't revoke from someone with equal or higher level
        PermissionLevel targetLevel = getPermissionLevel(targetId);
        if (!revokerLevel.canRevoke(targetLevel)) {
            LOGGER.warn("[Security] Revoke blocked: {} ({}) can't revoke from {} ({})",
                revoker.getName().getString(), revokerLevel, targetId, targetLevel);
            return false;
        }

        Set<Permission> grants = EXPLICIT_GRANTS.get(targetId);
        if (grants != null) {
            grants.remove(permission);
        }

        Map<Permission, Long> tempGrants = TEMPORARY_GRANTS.get(targetId);
        if (tempGrants != null) {
            tempGrants.remove(permission);
        }

        LOGGER.info("[Security] {} revoked {} from {}",
            revoker.getName().getString(), permission, targetId);

        return true;
    }

    /**
     * Explicitly deny a permission (overrides level-based grants).
     */
    public static boolean denyPermission(ServerPlayer denier, UUID targetId, Permission permission) {
        if (!hasPermission(denier, Permission.REVOKE_PERMISSIONS)) {
            return false;
        }

        PermissionLevel denierLevel = getPermissionLevel(denier);
        PermissionLevel targetLevel = getPermissionLevel(targetId);

        if (!denierLevel.canRevoke(targetLevel)) {
            return false;
        }

        EXPLICIT_DENIES.computeIfAbsent(targetId, k -> EnumSet.noneOf(Permission.class))
            .add(permission);

        LOGGER.info("[Security] {} denied {} for {}",
            denier.getName().getString(), permission, targetId);

        return true;
    }

    // ============================================================================
    // VALIDATION RESULT
    // ============================================================================

    /**
     * Result of a permission check with details.
     */
    public record PermissionCheckResult(
        boolean allowed,
        @Nullable String reason,
        PermissionLevel playerLevel,
        PermissionLevel requiredLevel
    ) {
        public static PermissionCheckResult allowed(PermissionLevel playerLevel, PermissionLevel requiredLevel) {
            return new PermissionCheckResult(true, null, playerLevel, requiredLevel);
        }

        public static PermissionCheckResult denied(String reason, PermissionLevel playerLevel, PermissionLevel requiredLevel) {
            return new PermissionCheckResult(false, reason, playerLevel, requiredLevel);
        }
    }

    /**
     * Check permission with detailed result.
     */
    public static PermissionCheckResult checkPermissionDetailed(UUID playerId, Permission permission) {
        PermissionLevel playerLevel = getPermissionLevel(playerId);
        PermissionLevel requiredLevel = permission.getRequiredLevel();

        Set<Permission> denies = EXPLICIT_DENIES.get(playerId);
        if (denies != null && denies.contains(permission)) {
            return PermissionCheckResult.denied("Explicitly denied", playerLevel, requiredLevel);
        }

        if (hasPermission(playerId, permission)) {
            return PermissionCheckResult.allowed(playerLevel, requiredLevel);
        }

        return PermissionCheckResult.denied(
            "Insufficient permission level (" + playerLevel + " < " + requiredLevel + ")",
            playerLevel, requiredLevel);
    }

    // ============================================================================
    // SYSTEM OPERATIONS
    // ============================================================================

    /**
     * Initialize with owner UUID (called once during setup).
     */
    public static void setOwner(UUID ownerUuid) {
        PERMISSION_LEVELS.put(ownerUuid, PermissionLevel.OWNER);
        LOGGER.info("[Security] Owner initialized: {}", ownerUuid);
    }

    /**
     * Clear all permission state (for testing or server reset).
     */
    public static void reset() {
        PERMISSION_LEVELS.clear();
        EXPLICIT_GRANTS.clear();
        EXPLICIT_DENIES.clear();
        TEMPORARY_GRANTS.clear();
        LOGGER.info("[Security] Permission state reset");
    }

    /**
     * Clean up expired temporary permissions.
     */
    public static void cleanupExpired() {
        long now = System.currentTimeMillis();
        int cleaned = 0;

        for (Map<Permission, Long> grants : TEMPORARY_GRANTS.values()) {
            cleaned += grants.entrySet().removeIf(e -> e.getValue() < now) ? 1 : 0;
        }

        if (cleaned > 0) {
            LOGGER.debug("[Security] Cleaned up {} expired temporary permissions", cleaned);
        }
    }

    private PermissionGuard() {} // Static utility class
}
