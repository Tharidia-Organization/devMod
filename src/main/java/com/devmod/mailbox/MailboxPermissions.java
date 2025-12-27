package com.devmod.mailbox;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

/**
 * Permission system for the mailbox feature.
 * Integrates with Minecraft's permission levels and provides
 * mailbox-specific permission checks.
 */
public final class MailboxPermissions {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxPermissions.class);

    public static final MailboxPermissions INSTANCE = new MailboxPermissions();

    /** Minimum op level required for admin actions. */
    public static final int ADMIN_PERMISSION_LEVEL = 2;

    /** Minimum op level required for tester role. */
    public static final int TESTER_PERMISSION_LEVEL = 1;

    // ============================================================================
    // PERMISSION TYPES
    // ============================================================================

    public enum Permission {
        /** Can send messages to other players. */
        SEND_MESSAGES,
        /** Can send messages with attachments. */
        SEND_ATTACHMENTS,
        /** Can receive messages. */
        RECEIVE_MESSAGES,
        /** Can use the mailbox UI. */
        USE_MAILBOX,
        /** Can view news. */
        VIEW_NEWS,
        /** Is a tester (can see/complete tasks). */
        TESTER,
        /** Is an admin (full access). */
        ADMIN,
        /** Can broadcast messages to all players. */
        BROADCAST,
        /** Can moderate other players' mailboxes. */
        MODERATE,
        /** Can manage news articles. */
        MANAGE_NEWS,
        /** Can manage tasks. */
        MANAGE_TASKS,
        /** Can configure the mailbox system. */
        CONFIGURE
    }

    // ============================================================================
    // STATE
    // ============================================================================

    /** Players who are blocked from sending messages. */
    private final Set<UUID> blockedSenders = ConcurrentHashMap.newKeySet();

    /** Players who are blocked from receiving messages. */
    private final Set<UUID> blockedReceivers = ConcurrentHashMap.newKeySet();

    /** Players explicitly granted tester role. */
    private final Set<UUID> testers = ConcurrentHashMap.newKeySet();

    /** Players explicitly granted admin role. */
    private final Set<UUID> admins = ConcurrentHashMap.newKeySet();

    /** Per-player permission overrides. */
    private final ConcurrentHashMap<UUID, Set<Permission>> grantedPermissions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<Permission>> deniedPermissions = new ConcurrentHashMap<>();

    private MailboxPermissions() {}

    // ============================================================================
    // PERMISSION CHECKS
    // ============================================================================

    /**
     * Check if a player has a specific permission.
     */
    public boolean hasPermission(ServerPlayer player, Permission permission) {
        if (player == null) {
            return false;
        }
        return hasPermission(player.getUUID(), player, permission);
    }

    /**
     * Check if a player has a specific permission.
     */
    public boolean hasPermission(UUID playerUuid, @Nullable ServerPlayer player, Permission permission) {
        if (playerUuid == null) {
            return false;
        }

        // Check explicit denials first
        Set<Permission> denied = deniedPermissions.get(playerUuid);
        if (denied != null && denied.contains(permission)) {
            return false;
        }

        // Check explicit grants
        Set<Permission> granted = grantedPermissions.get(playerUuid);
        if (granted != null && granted.contains(permission)) {
            return true;
        }

        // Check role-based permissions
        return switch (permission) {
            case SEND_MESSAGES -> canSendMessages(playerUuid, player);
            case SEND_ATTACHMENTS -> canSendAttachments(playerUuid, player);
            case RECEIVE_MESSAGES -> canReceiveMessages(playerUuid);
            case USE_MAILBOX -> true; // All players can use mailbox by default
            case VIEW_NEWS -> true; // All players can view news
            case TESTER -> isTester(playerUuid, player);
            case ADMIN -> isAdmin(playerUuid, player);
            case BROADCAST -> isAdmin(playerUuid, player);
            case MODERATE -> isAdmin(playerUuid, player);
            case MANAGE_NEWS -> isAdmin(playerUuid, player);
            case MANAGE_TASKS -> isAdmin(playerUuid, player);
            case CONFIGURE -> isAdmin(playerUuid, player);
        };
    }

    /**
     * Check if player can send messages.
     */
    private boolean canSendMessages(UUID playerUuid, @Nullable ServerPlayer player) {
        // Check if blocked
        if (blockedSenders.contains(playerUuid)) {
            return false;
        }

        // Check if player-to-player is enabled
        if (!MailboxConfig.INSTANCE.isPlayerToPlayerEnabled()) {
            // Only admins can send if p2p is disabled
            return isAdmin(playerUuid, player);
        }

        // Check min level requirement
        int minLevel = MailboxConfig.INSTANCE.getMinLevelToSend();
        if (minLevel > 0 && player != null) {
            if (player.experienceLevel < minLevel) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if player can send attachments.
     */
    private boolean canSendAttachments(UUID playerUuid, @Nullable ServerPlayer player) {
        // Must be able to send messages first
        if (!canSendMessages(playerUuid, player)) {
            return false;
        }

        // Admins/testers can always send attachments
        if (isTester(playerUuid, player) || isAdmin(playerUuid, player)) {
            return true;
        }

        MailboxConfig config = MailboxConfig.INSTANCE;
        return config.isItemAttachmentsEnabled() || config.isCurrencyAttachmentsEnabled();
    }

    /**
     * Check if player can receive messages.
     */
    private boolean canReceiveMessages(UUID playerUuid) {
        return !blockedReceivers.contains(playerUuid);
    }

    /**
     * Check if player is a tester.
     */
    public boolean isTester(UUID playerUuid, @Nullable ServerPlayer player) {
        // Explicit tester grant
        if (testers.contains(playerUuid)) {
            return true;
        }

        // Check op level
        if (player != null && player.hasPermissions(TESTER_PERMISSION_LEVEL)) {
            return true;
        }

        // Admins are also testers
        return isAdmin(playerUuid, player);
    }

    /**
     * Check if player is an admin.
     */
    public boolean isAdmin(UUID playerUuid, @Nullable ServerPlayer player) {
        // Explicit admin grant
        if (admins.contains(playerUuid)) {
            return true;
        }

        // Check op level
        if (player != null && player.hasPermissions(ADMIN_PERMISSION_LEVEL)) {
            return true;
        }

        return false;
    }

    // ============================================================================
    // PERSISTENCE
    // ============================================================================

    /**
     * Load roles/blocks from config.
     */
    public void loadFromConfig(MailboxConfig config) {
        if (config == null) {
            return;
        }
        clear();
        addUuidList(admins, config.getAdminUuids());
        addUuidList(testers, config.getTesterUuids());
        addUuidList(blockedSenders, config.getBlockedSenderUuids());
        addUuidList(blockedReceivers, config.getBlockedReceiverUuids());
        LOGGER.info("[MailboxPermissions] Loaded roles/blocks from config");
    }

    private void persistToConfig() {
        MailboxConfig config = MailboxConfig.INSTANCE;
        config.setAdminUuids(admins.stream().map(UUID::toString).toList());
        config.setTesterUuids(testers.stream().map(UUID::toString).toList());
        config.setBlockedSenderUuids(blockedSenders.stream().map(UUID::toString).toList());
        config.setBlockedReceiverUuids(blockedReceivers.stream().map(UUID::toString).toList());
        config.save();
    }

    private static void addUuidList(Set<UUID> target, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                target.add(UUID.fromString(value.trim()));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[MailboxPermissions] Skipping invalid UUID in config: {}", value);
            }
        }
    }

    // ============================================================================
    // ROLE MANAGEMENT
    // ============================================================================

    /**
     * Block a player from sending messages.
     */
    public void blockSender(UUID playerUuid) {
        blockedSenders.add(playerUuid);
        LOGGER.info("[MailboxPermissions] Blocked sender: {}", playerUuid);
        persistToConfig();
    }

    /**
     * Unblock a player from sending messages.
     */
    public void unblockSender(UUID playerUuid) {
        blockedSenders.remove(playerUuid);
        LOGGER.info("[MailboxPermissions] Unblocked sender: {}", playerUuid);
        persistToConfig();
    }

    /**
     * Check if a player is blocked from sending.
     */
    public boolean isSenderBlocked(UUID playerUuid) {
        return blockedSenders.contains(playerUuid);
    }

    /**
     * Block a player from receiving messages.
     */
    public void blockReceiver(UUID playerUuid) {
        blockedReceivers.add(playerUuid);
        LOGGER.info("[MailboxPermissions] Blocked receiver: {}", playerUuid);
        persistToConfig();
    }

    /**
     * Unblock a player from receiving messages.
     */
    public void unblockReceiver(UUID playerUuid) {
        blockedReceivers.remove(playerUuid);
        LOGGER.info("[MailboxPermissions] Unblocked receiver: {}", playerUuid);
        persistToConfig();
    }

    /**
     * Check if a player is blocked from receiving.
     */
    public boolean isReceiverBlocked(UUID playerUuid) {
        return blockedReceivers.contains(playerUuid);
    }

    /**
     * Grant tester role to a player.
     */
    public void grantTester(UUID playerUuid) {
        testers.add(playerUuid);
        LOGGER.info("[MailboxPermissions] Granted tester role: {}", playerUuid);
        persistToConfig();
    }

    /**
     * Revoke tester role from a player.
     */
    public void revokeTester(UUID playerUuid) {
        testers.remove(playerUuid);
        LOGGER.info("[MailboxPermissions] Revoked tester role: {}", playerUuid);
        persistToConfig();
    }

    /**
     * Grant admin role to a player.
     */
    public void grantAdmin(UUID playerUuid) {
        admins.add(playerUuid);
        LOGGER.info("[MailboxPermissions] Granted admin role: {}", playerUuid);
        persistToConfig();
    }

    /**
     * Revoke admin role from a player.
     */
    public void revokeAdmin(UUID playerUuid) {
        admins.remove(playerUuid);
        LOGGER.info("[MailboxPermissions] Revoked admin role: {}", playerUuid);
        persistToConfig();
    }

    // ============================================================================
    // PERMISSION OVERRIDES
    // ============================================================================

    /**
     * Grant a specific permission to a player.
     */
    public void grantPermission(UUID playerUuid, Permission permission) {
        grantedPermissions.computeIfAbsent(playerUuid, k -> new CopyOnWriteArraySet<>()).add(permission);
        deniedPermissions.getOrDefault(playerUuid, Set.of()).remove(permission);
        LOGGER.info("[MailboxPermissions] Granted {} to {}", permission, playerUuid);
    }

    /**
     * Deny a specific permission to a player.
     */
    public void denyPermission(UUID playerUuid, Permission permission) {
        deniedPermissions.computeIfAbsent(playerUuid, k -> new CopyOnWriteArraySet<>()).add(permission);
        grantedPermissions.getOrDefault(playerUuid, Set.of()).remove(permission);
        LOGGER.info("[MailboxPermissions] Denied {} to {}", permission, playerUuid);
    }

    /**
     * Clear permission overrides for a player.
     */
    public void clearPermissionOverrides(UUID playerUuid) {
        grantedPermissions.remove(playerUuid);
        deniedPermissions.remove(playerUuid);
    }

    // ============================================================================
    // QUERY
    // ============================================================================

    /**
     * Get all blocked senders.
     */
    public Set<UUID> getBlockedSenders() {
        return Set.copyOf(blockedSenders);
    }

    /**
     * Get all blocked receivers.
     */
    public Set<UUID> getBlockedReceivers() {
        return Set.copyOf(blockedReceivers);
    }

    /**
     * Get all testers.
     */
    public Set<UUID> getTesters() {
        return Set.copyOf(testers);
    }

    /**
     * Get all admins.
     */
    public Set<UUID> getAdmins() {
        return Set.copyOf(admins);
    }

    /**
     * Clear all permission data.
     */
    public void clear() {
        blockedSenders.clear();
        blockedReceivers.clear();
        testers.clear();
        admins.clear();
        grantedPermissions.clear();
        deniedPermissions.clear();
    }
}
