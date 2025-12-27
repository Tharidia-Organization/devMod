package com.devmod.mailbox.api.controllers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.devmod.DevMod;
import com.devmod.mailbox.MailboxConfig;
import com.devmod.mailbox.MailboxManager;
import com.devmod.mailbox.MailboxMessage;
import com.devmod.mailbox.MessageType;
import com.devmod.mailbox.api.AuthMiddleware;
import com.devmod.mailbox.moderation.AdminAuditLog;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

/**
 * REST API controller for mailbox messages.
 */
public final class MessageController {

    private MessageController() {}

    /**
     * GET /api/messages
     * Query params: limit (default 50), offset (default 0), recipientUuid (optional)
     */
    public static void listMessages(Context ctx) {
        int limitParam = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        String recipientUuidStr = ctx.queryParam("recipientUuid");

        int limit = Math.min(limitParam, 200);
        boolean applyOffsetInMemory = recipientUuidStr != null && !recipientUuidStr.isBlank();

        CompletableFuture<List<MailboxMessage>> future;
        if (recipientUuidStr != null && !recipientUuidStr.isBlank()) {
            UUID recipientUuid = parseUuid(recipientUuidStr);
            if (recipientUuid == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid recipientUuid"));
                return;
            }
            future = MailboxManager.INSTANCE.getMessages(recipientUuid);
        } else {
            future = MailboxManager.INSTANCE.getAllMessages(limit, offset);
        }

        future.thenAccept(messages -> {
            java.util.stream.Stream<MailboxMessage> stream = messages.stream();
            if (applyOffsetInMemory && offset > 0) {
                stream = stream.skip(offset);
            }
            List<MessageDto> dtos = stream
                .limit(limit)
                .map(MessageDto::from)
                .toList();
            ctx.json(new ListResponse<>(dtos, dtos.size()));
        }).exceptionally(e -> {
            handleException(ctx, e);
            return null;
        });
    }

    /**
     * GET /api/messages/{id}
     */
    public static void getMessage(Context ctx) {
        String idStr = ctx.pathParam("id");
        UUID messageId = parseUuid(idStr);
        if (messageId == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid message ID"));
            return;
        }

        MailboxManager.INSTANCE.getMessage(messageId).thenAccept(optMsg -> {
            if (optMsg.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND).json(errorResponse("Message not found"));
            } else {
                ctx.json(MessageDto.from(optMsg.get()));
            }
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * POST /api/messages
     * Send a message to a specific user.
     */
    public static void createMessage(Context ctx) {
        CreateMessageRequest request = ctx.bodyAsClass(CreateMessageRequest.class);

        if (request.recipientUuid() == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("recipientUuid is required"));
            return;
        }
        if (request.subject() == null || request.subject().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("subject is required"));
            return;
        }
        if (MailboxConfig.INSTANCE.isMaintenanceMode() && !AuthMiddleware.isAdmin(ctx)) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE).json(errorResponse("Mailbox is in maintenance mode"));
            return;
        }

        String senderName = request.senderName() != null ? request.senderName() : "Admin";
        String msgType = request.messageType();
        MessageType type = msgType != null
            ? MessageType.valueOf(msgType.toUpperCase(Locale.ROOT))
            : MessageType.ADMIN;
        Instant expiresAt = request.expiresAtMillis() != null
            ? Instant.ofEpochMilli(request.expiresAtMillis())
            : null;

        CompletableFuture<UUID> future;
        if (type == MessageType.SYSTEM) {
            Duration expiresIn = null;
            if (expiresAt != null && expiresAt.isAfter(Instant.now())) {
                expiresIn = Duration.between(Instant.now(), expiresAt);
            }
            future = MailboxManager.INSTANCE.sendSystemMessage(
                request.recipientUuid(),
                request.subject(),
                request.body(),
                request.attachmentData(),
                expiresIn
            );
        } else {
            future = MailboxManager.INSTANCE.sendAdminMessage(
                senderName,
                request.recipientUuid(),
                request.subject(),
                request.body(),
                request.attachmentData(),
                expiresAt
            );
        }

        future.thenAccept(messageId -> {
            ctx.status(HttpStatus.CREATED).json(new CreateResponse(messageId, "Message sent"));
            AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                    .action(AdminAuditLog.Action.MESSAGE_SEND)
                    .actorUuid(null)
                    .actorName(resolveActorName(ctx))
                    .targetType("message")
                    .targetId(messageId.toString())
                    .details("Recipient=" + request.recipientUuid())
                    .build())
                .exceptionally(error -> {
                    DevMod.LOGGER.warn("[Mailbox API] Failed to persist message send audit log", error);
                    return null;
                });
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * POST /api/messages/broadcast
     * Send a message to all known players (online + offline).
     */
    public static void broadcastMessage(Context ctx) {
        BroadcastRequest request = ctx.bodyAsClass(BroadcastRequest.class);

        if (request.subject() == null || request.subject().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("subject is required"));
            return;
        }
        if (MailboxConfig.INSTANCE.isMaintenanceMode() && !AuthMiddleware.isAdmin(ctx)) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE).json(errorResponse("Mailbox is in maintenance mode"));
            return;
        }

        String senderName = request.senderName() != null ? request.senderName() : "Admin";
        String msgType = request.messageType();
        MessageType type = msgType != null
            ? MessageType.valueOf(msgType.toUpperCase(Locale.ROOT))
            : MessageType.ADMIN;
        Instant expiresAt = request.expiresAtMillis() != null
            ? Instant.ofEpochMilli(request.expiresAtMillis())
            : null;

        MailboxConfig config = MailboxConfig.INSTANCE;
        boolean queueEnabled = config.isBroadcastQueueEnabled();
        int queueThreshold = config.getBroadcastQueueThreshold();

        MailboxManager.INSTANCE.getBroadcastRecipients()
            .thenCompose(recipients -> {
                if (recipients.isEmpty()) {
                    return CompletableFuture.completedFuture(new BroadcastDispatchResult(
                        0,
                        false,
                        null,
                        "No recipients found"
                    ));
                }

                if (queueEnabled && recipients.size() >= queueThreshold) {
                    UUID jobId = com.devmod.mailbox.broadcast.BroadcastQueueWorker.INSTANCE.submitJob(
                        senderName,
                        recipients,
                        request.subject(),
                        request.body(),
                        request.attachmentData(),
                        type,
                        expiresAt
                    );
                    if (jobId == null) {
                        return CompletableFuture.failedFuture(new IllegalStateException("Broadcast queue is full"));
                    }
                    return CompletableFuture.completedFuture(new BroadcastDispatchResult(
                        recipients.size(),
                        true,
                        jobId,
                        "Broadcast queued for " + recipients.size() + " recipients"
                    ));
                }

                return MailboxManager.INSTANCE.sendBroadcast(
                        senderName,
                        recipients,
                        request.subject(),
                        request.body(),
                        request.attachmentData(),
                        type,
                        expiresAt
                    )
                    .thenApply(count -> new BroadcastDispatchResult(
                        count,
                        false,
                        null,
                        "Broadcast sent to " + count + " recipients"
                    ));
            })
            .thenAccept(result -> {
                ctx.json(new BroadcastResponse(
                    result.recipientCount(),
                    result.message(),
                    result.queued(),
                    result.jobId() != null ? result.jobId().toString() : null
                ));
                AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                        .action(AdminAuditLog.Action.BROADCAST)
                        .actorUuid(null)
                        .actorName(resolveActorName(ctx))
                        .targetType("broadcast")
                        .details((result.queued()
                            ? "Queued job=" + result.jobId()
                            : "Recipients=" + result.recipientCount())
                            + "; Subject=" + request.subject())
                        .build())
                    .exceptionally(error -> {
                        DevMod.LOGGER.warn("[Mailbox API] Failed to persist broadcast audit log", error);
                        return null;
                    });
            }).exceptionally(e -> {
                handleException(ctx, e);
                return null;
            });
    }

    /**
     * DELETE /api/messages/{id}
     */
    public static void deleteMessage(Context ctx) {
        String idStr = ctx.pathParam("id");
        UUID messageId = parseUuid(idStr);
        if (messageId == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid message ID"));
            return;
        }

        MailboxManager.INSTANCE.deleteMessage(messageId).thenAccept(success -> {
            if (success) {
                ctx.status(HttpStatus.NO_CONTENT);
                AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                        .action(AdminAuditLog.Action.MESSAGE_DELETE)
                        .actorUuid(null)
                        .actorName(resolveActorName(ctx))
                        .targetType("message")
                        .targetId(messageId.toString())
                        .details("Deleted via admin API")
                        .build())
                    .exceptionally(error -> {
                        DevMod.LOGGER.warn("[Mailbox API] Failed to persist message delete audit log", error);
                        return null;
                    });
            } else {
                ctx.status(HttpStatus.NOT_FOUND).json(errorResponse("Message not found"));
            }
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

    private static void handleException(Context ctx, Throwable error) {
        Throwable root = unwrapCompletion(error);
        String message = root.getMessage();
        if (root instanceof IllegalArgumentException) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse(message));
            return;
        }
        if (root instanceof SecurityException) {
            ctx.status(HttpStatus.FORBIDDEN).json(errorResponse(message));
            return;
        }
        ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(message));
    }

    private static Throwable unwrapCompletion(Throwable error) {
        Throwable current = error;
        while (current instanceof java.util.concurrent.CompletionException
            || current instanceof java.util.concurrent.ExecutionException) {
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        return current;
    }

    // ========================================================================
    // DTOs
    // ========================================================================

    public record ErrorResponse(String status, String message) {}

    public record ListResponse<T>(List<T> items, int count) {}

    public record CreateResponse(UUID id, String message) {}

    public record BroadcastResponse(
        int recipientCount,
        String message,
        boolean queued,
        @Nullable String jobId
    ) {}

    private record BroadcastDispatchResult(
        int recipientCount,
        boolean queued,
        @Nullable UUID jobId,
        String message
    ) {}

    public record MessageDto(
        UUID id,
        @Nullable UUID senderUuid,
        @Nullable String senderName,
        UUID recipientUuid,
        String subject,
        @Nullable String body,
        String messageType,
        long createdAtMillis,
        @Nullable Long readAtMillis,
        @Nullable Long expiresAtMillis,
        boolean hasAttachment,
        boolean attachmentClaimed,
        @Nullable String attachmentData
    ) {
        public static MessageDto from(MailboxMessage msg) {
            Instant readAt = msg.readAt();
            Instant expiresAt = msg.expiresAt();
            return new MessageDto(
                msg.id(),
                msg.senderUuid(),
                msg.senderName(),
                msg.recipientUuid(),
                msg.subject(),
                msg.body(),
                msg.messageType().name(),
                msg.createdAt().toEpochMilli(),
                readAt != null ? readAt.toEpochMilli() : null,
                expiresAt != null ? expiresAt.toEpochMilli() : null,
                msg.hasAttachment(),
                msg.attachmentClaimed(),
                msg.attachmentData()
            );
        }
    }

    public record CreateMessageRequest(
        UUID recipientUuid,
        @Nullable String senderName,
        String subject,
        @Nullable String body,
        @Nullable String messageType,
        @Nullable String attachmentData,
        @Nullable Long expiresAtMillis
    ) {}

    public record BroadcastRequest(
        @Nullable String senderName,
        String subject,
        @Nullable String body,
        @Nullable String messageType,
        @Nullable String attachmentData,
        @Nullable Long expiresAtMillis
    ) {}
}
