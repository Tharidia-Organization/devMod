package com.devmod.arena.security;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DD31: Command Permissions - modello granulare + audit log
 *
 * Provides granular permission levels for arena commands.
 * 7 permission levels from VIEWER (lowest) to SUPERADMIN (highest).
 */
public class ArenaCommandPermissions {

    private static final ArenaCommandPermissions INSTANCE = new ArenaCommandPermissions();

    /**
     * Permission levels for arena commands (ordered from lowest to highest)
     */
    public enum PermissionLevel {
        /** Can view arena status and basic info */
        VIEWER(0),
        /** Can join/leave arenas */
        PARTICIPANT(1),
        /** Can spectate arenas */
        SPECTATOR(2),
        /** Can create and configure personal arenas */
        CREATOR(3),
        /** Can moderate arenas (kick, mute, etc.) */
        MODERATOR(4),
        /** Can manage all arenas, templates, and settings */
        ADMIN(5),
        /** Full access including destructive operations */
        SUPERADMIN(6);

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
    }

    /**
     * Arena command categories that require specific permissions
     */
    public enum CommandCategory {
        // View operations
        STATUS(PermissionLevel.VIEWER, false),
        LIST(PermissionLevel.VIEWER, false),
        INFO(PermissionLevel.VIEWER, false),

        // Participant operations
        JOIN(PermissionLevel.PARTICIPANT, false),
        LEAVE(PermissionLevel.PARTICIPANT, false),
        READY(PermissionLevel.PARTICIPANT, false),

        // Spectator operations
        SPECTATE(PermissionLevel.SPECTATOR, false),

        // Creator operations (mutating)
        CREATE(PermissionLevel.CREATOR, true),
        CONFIGURE(PermissionLevel.CREATOR, true),
        DELETE_OWN(PermissionLevel.CREATOR, true),

        // Moderator operations (mutating)
        KICK(PermissionLevel.MODERATOR, true),
        MUTE(PermissionLevel.MODERATOR, true),
        PAUSE(PermissionLevel.MODERATOR, true),
        FORCE_START(PermissionLevel.MODERATOR, true),

        // Admin operations (mutating)
        DELETE_ANY(PermissionLevel.ADMIN, true),
        TEMPLATE_MANAGE(PermissionLevel.ADMIN, true),
        CONFIG_GLOBAL(PermissionLevel.ADMIN, true),
        FORCE_TEMPLATE(PermissionLevel.ADMIN, true),

        // Superadmin operations (mutating, destructive)
        RESET_ALL(PermissionLevel.SUPERADMIN, true),
        PURGE(PermissionLevel.SUPERADMIN, true),
        DEBUG_OVERRIDE(PermissionLevel.SUPERADMIN, true),
        AUTOSMOKE_RUN(PermissionLevel.SUPERADMIN, true);

        private final PermissionLevel requiredLevel;
        private final boolean mutating;

        CommandCategory(PermissionLevel requiredLevel, boolean mutating) {
            this.requiredLevel = requiredLevel;
            this.mutating = mutating;
        }

        public PermissionLevel getRequiredLevel() {
            return requiredLevel;
        }

        public boolean isMutating() {
            return mutating;
        }
    }

    /** Per-player permission level overrides */
    private final Map<UUID, PermissionLevel> playerPermissions = new ConcurrentHashMap<>();

    /** Default permission level for new players */
    private PermissionLevel defaultLevel = PermissionLevel.PARTICIPANT;

    /** Op level required for automatic ADMIN permission */
    private int opLevelForAdmin = 3;

    /** Op level required for automatic SUPERADMIN permission */
    private int opLevelForSuperadmin = 4;

    private ArenaCommandPermissions() {}

    public static ArenaCommandPermissions getInstance() {
        return INSTANCE;
    }

    /**
     * Checks if a player has permission to execute a command category
     *
     * @param player The player
     * @param category The command category
     * @return true if permitted
     */
    public boolean hasPermission(ServerPlayer player, CommandCategory category) {
        PermissionLevel playerLevel = getPlayerLevel(player);
        return playerLevel.isAtLeast(category.getRequiredLevel());
    }

    /**
     * Checks if a command source has permission to execute a command category
     *
     * @param source The command source
     * @param category The command category
     * @return true if permitted
     */
    public boolean hasPermission(CommandSourceStack source, CommandCategory category) {
        // Console always has full access
        if (!source.isPlayer()) {
            return true;
        }

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return false;
        }

        return hasPermission(player, category);
    }

    /**
     * Gets the effective permission level for a player
     *
     * @param player The player
     * @return The player's permission level
     */
    public PermissionLevel getPlayerLevel(ServerPlayer player) {
        // Check for explicit override first
        PermissionLevel override = playerPermissions.get(player.getUUID());
        if (override != null) {
            return override;
        }

        // Check op level for automatic promotion
        if (player.hasPermissions(opLevelForSuperadmin)) {
            return PermissionLevel.SUPERADMIN;
        }
        if (player.hasPermissions(opLevelForAdmin)) {
            return PermissionLevel.ADMIN;
        }
        if (player.hasPermissions(2)) {
            return PermissionLevel.MODERATOR;
        }

        return defaultLevel;
    }

    /**
     * Sets an explicit permission level for a player
     *
     * @param playerId The player's UUID
     * @param level The permission level
     */
    public void setPlayerLevel(UUID playerId, PermissionLevel level) {
        playerPermissions.put(playerId, level);
        ArenaCommandAudit.getInstance().logPermissionChange(playerId, level);
    }

    /**
     * Removes an explicit permission level for a player (reverts to default/op-based)
     *
     * @param playerId The player's UUID
     */
    public void clearPlayerLevel(UUID playerId) {
        playerPermissions.remove(playerId);
    }

    /**
     * Gets all mutating command categories
     */
    public Set<CommandCategory> getMutatingCategories() {
        EnumSet<CommandCategory> mutating = EnumSet.noneOf(CommandCategory.class);
        for (CommandCategory cat : CommandCategory.values()) {
            if (cat.isMutating()) {
                mutating.add(cat);
            }
        }
        return mutating;
    }

    /**
     * Gets the default permission level
     */
    public PermissionLevel getDefaultLevel() {
        return defaultLevel;
    }

    /**
     * Sets the default permission level for new players
     *
     * @param level The default level
     */
    public void setDefaultLevel(PermissionLevel level) {
        this.defaultLevel = level;
    }

    /**
     * Gets the op level required for automatic ADMIN permission
     */
    public int getOpLevelForAdmin() {
        return opLevelForAdmin;
    }

    /**
     * Sets the op level required for automatic ADMIN permission
     *
     * @param level The op level (1-4)
     */
    public void setOpLevelForAdmin(int level) {
        this.opLevelForAdmin = Math.max(1, Math.min(4, level));
    }

    /**
     * Gets the op level required for automatic SUPERADMIN permission
     */
    public int getOpLevelForSuperadmin() {
        return opLevelForSuperadmin;
    }

    /**
     * Sets the op level required for automatic SUPERADMIN permission
     *
     * @param level The op level (1-4)
     */
    public void setOpLevelForSuperadmin(int level) {
        this.opLevelForSuperadmin = Math.max(1, Math.min(4, level));
    }

    /**
     * Gets the count of players with explicit permission overrides
     */
    public int getOverrideCount() {
        return playerPermissions.size();
    }
}
