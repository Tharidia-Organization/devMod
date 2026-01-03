package com.devmod.mailbox.api.controllers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.devmod.DevMod;
import com.devmod.mailbox.MailboxManager;
import com.devmod.mailbox.MailboxPermissions;
import com.devmod.mailbox.api.AuthMiddleware;
import com.devmod.mailbox.moderation.AdminAuditLog;
import com.devmod.mailbox.network.MailboxNetworkHandler;

/**
 * REST API controller for user management.
 */
public final class UserController {

    private UserController() {}

    /**
     * GET /api/users
     * List all known users (who have received messages).
     */
    public static void listUsers(Context ctx) {
        MailboxManager.INSTANCE.getKnownUsers().thenAccept(uuids -> {
            Set<UUID> merged = new LinkedHashSet<>(uuids);
            MailboxPermissions perms = MailboxPermissions.INSTANCE;
            merged.addAll(perms.getAdmins());
            merged.addAll(perms.getTesters());
            merged.addAll(perms.getBlockedSenders());
            merged.addAll(perms.getBlockedReceivers());
            List<UserDto> users = merged.stream()
                .map(uuid -> new UserDto(
                    uuid,
                    null,
                    perms.isAdmin(uuid, null),
                    perms.isTester(uuid, null),
                    perms.isSenderBlocked(uuid),
                    perms.isReceiverBlocked(uuid)
                ))
                .toList();
            ctx.json(new ListResponse<>(users, users.size()));
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * GET /api/users/{uuid}
     * Get user info with message stats.
     */
    public static void getUser(Context ctx) {
        String uuidStr = ctx.pathParam("uuid");
        UUID userUuid = parseUuid(uuidStr);
        if (userUuid == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid UUID"));
            return;
        }

        buildUserDetails(userUuid).thenAccept(ctx::json).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * PUT /api/users/{uuid}/access
     * Update user access flags (admin/tester/blocked).
     */
    public static void updateUserAccess(Context ctx) {
        String uuidStr = ctx.pathParam("uuid");
        UUID userUuid = parseUuid(uuidStr);
        if (userUuid == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid UUID"));
            return;
        }

        UpdateUserAccessRequest request = ctx.bodyAsClass(UpdateUserAccessRequest.class);
        if (request == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid payload"));
            return;
        }

        MailboxPermissions perms = MailboxPermissions.INSTANCE;
        boolean wasAdmin = perms.isAdmin(userUuid, null);
        boolean wasTester = perms.isTester(userUuid, null);
        boolean wasBlockedSender = perms.isSenderBlocked(userUuid);
        boolean wasBlockedReceiver = perms.isReceiverBlocked(userUuid);
        List<String> changes = new ArrayList<>();

        Boolean adminFlag = request.admin();
        if (adminFlag != null) {
            if (adminFlag) {
                perms.grantAdmin(userUuid);
            } else {
                perms.revokeAdmin(userUuid);
            }
        }
        Boolean testerFlag = request.tester();
        if (testerFlag != null) {
            if (testerFlag) {
                perms.grantTester(userUuid);
            } else {
                perms.revokeTester(userUuid);
            }
        }
        Boolean blockedSenderFlag = request.blockedSender();
        if (blockedSenderFlag != null) {
            if (blockedSenderFlag) {
                perms.blockSender(userUuid);
            } else {
                perms.unblockSender(userUuid);
            }
        }
        Boolean blockedReceiverFlag = request.blockedReceiver();
        if (blockedReceiverFlag != null) {
            if (blockedReceiverFlag) {
                perms.blockReceiver(userUuid);
            } else {
                perms.unblockReceiver(userUuid);
            }
        }

        boolean isAdmin = perms.isAdmin(userUuid, null);
        boolean isTester = perms.isTester(userUuid, null);
        boolean isBlockedSender = perms.isSenderBlocked(userUuid);
        boolean isBlockedReceiver = perms.isReceiverBlocked(userUuid);

        if (wasAdmin != isAdmin) {
            changes.add("admin: " + wasAdmin + " -> " + isAdmin);
        }
        if (wasTester != isTester) {
            changes.add("tester: " + wasTester + " -> " + isTester);
        }
        if (wasBlockedSender != isBlockedSender) {
            changes.add("blockedSender: " + wasBlockedSender + " -> " + isBlockedSender);
        }
        if (wasBlockedReceiver != isBlockedReceiver) {
            changes.add("blockedReceiver: " + wasBlockedReceiver + " -> " + isBlockedReceiver);
        }

        if (!changes.isEmpty()) {
            AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                    .action(AdminAuditLog.Action.USER_ACCESS_UPDATE)
                    .actorUuid(null)
                    .actorName(resolveActorName(ctx))
                    .targetType("user")
                    .targetId(userUuid.toString())
                    .details(String.join("; ", changes))
                    .build())
                .exceptionally(error -> {
                    DevMod.LOGGER.warn("[Mailbox API] Failed to persist user access audit log", error);
                    return null;
                });
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            var player = server.getPlayerList().getPlayer(userUuid);
            if (player != null) {
                if (isTester) {
                    MailboxNetworkHandler.sendTaskSync(player);
                }
            }
        }

        buildUserDetails(userUuid).thenAccept(ctx::json).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * GET /api/users/{uuid}/inbox
     * Get user's inbox messages.
     * Query params: limit (default 50), includeRead (default true)
     */
    public static void getUserInbox(Context ctx) {
        String uuidStr = ctx.pathParam("uuid");
        UUID userUuid = parseUuid(uuidStr);
        if (userUuid == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid UUID"));
            return;
        }

        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);
        boolean includeRead = ctx.queryParamAsClass("includeRead", Boolean.class).getOrDefault(true);

        limit = Math.min(limit, 200);
        int finalLimit = limit;

        MailboxManager.INSTANCE.getMessages(userUuid).thenAccept(messages -> {
            List<MessageController.MessageDto> dtos = messages.stream()
                .filter(m -> includeRead || !m.isRead())
                .limit(finalLimit)
                .map(MessageController.MessageDto::from)
                .toList();
            ctx.json(new ListResponse<>(dtos, dtos.size()));
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    @Nullable
    private static UUID parseUuid(String str) {
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ErrorResponse errorResponse(@Nullable String message) {
        return new ErrorResponse("error", message != null ? message : "Unknown error");
    }

    private static String resolveActorName(Context ctx) {
        AuthMiddleware.AuthenticatedUser user = AuthMiddleware.getUser(ctx);
        return user != null ? user.username() : "Admin";
    }

    private static java.util.concurrent.CompletableFuture<UserDetailsDto> buildUserDetails(UUID userUuid) {
        MailboxPermissions perms = MailboxPermissions.INSTANCE;
        return MailboxManager.INSTANCE.getMessages(userUuid).thenCombine(
            MailboxManager.INSTANCE.getUnreadCount(userUuid),
            (messages, unreadCount) -> new UserDetailsDto(
                userUuid,
                null,
                messages.size(),
                unreadCount,
                messages.stream().filter(m -> m.hasAttachment() && !m.attachmentClaimed()).count(),
                perms.isAdmin(userUuid, null),
                perms.isTester(userUuid, null),
                perms.isSenderBlocked(userUuid),
                perms.isReceiverBlocked(userUuid)
            )
        );
    }

    // ========================================================================
    // DTOs
    // ========================================================================

    public record ErrorResponse(String status, String message) {}

    public record ListResponse<T>(List<T> items, int count) {}

    public record UserDto(
        UUID uuid,
        @Nullable String name,
        boolean isAdmin,
        boolean isTester,
        boolean blockedSender,
        boolean blockedReceiver
    ) {}

    public record UserDetailsDto(
        UUID uuid,
        @Nullable String name,
        int totalMessages,
        int unreadMessages,
        long unclaimedAttachments,
        boolean isAdmin,
        boolean isTester,
        boolean blockedSender,
        boolean blockedReceiver
    ) {}

    public record UpdateUserAccessRequest(
        @Nullable Boolean admin,
        @Nullable Boolean tester,
        @Nullable Boolean blockedSender,
        @Nullable Boolean blockedReceiver
    ) {}
}
