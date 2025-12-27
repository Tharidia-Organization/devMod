package com.devmod.mailbox.api.controllers;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

import com.devmod.DevMod;
import com.devmod.mailbox.api.AuthMiddleware;
import com.devmod.mailbox.moderation.AdminAuditLog;
import com.devmod.mailbox.task.TaskAuditEntry;
import com.devmod.mailbox.task.TestTask;
import com.devmod.mailbox.task.TestTaskManager;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

/**
 * REST API controller for tester tasks.
 */
public final class TaskController {

    private TaskController() {}

    /**
     * GET /api/tasks
     * Query params: assignedTo (optional UUID), status (optional)
     */
    public static void listTasks(Context ctx) {
        String assignedToStr = ctx.queryParam("assignedTo");
        String statusStr = ctx.queryParam("status");

        if (assignedToStr != null) {
            UUID assignedTo = parseUuid(assignedToStr);
            if (assignedTo == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid assignedTo UUID"));
                return;
            }

            TestTaskManager.INSTANCE.getTasksForUser(assignedTo).thenAccept(tasks -> {
                List<TaskDto> dtos = filterByStatus(tasks, statusStr).stream()
                    .map(TaskDto::from)
                    .toList();
                ctx.json(new ListResponse<>(dtos, dtos.size()));
            }).exceptionally(e -> {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
                return null;
            });
        } else {
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(100);
            int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
            TestTaskManager.INSTANCE.getAllTasks(limit, offset).thenAccept(tasks -> {
                List<TaskDto> dtos = filterByStatus(tasks, statusStr).stream()
                    .map(TaskDto::from)
                    .toList();
                ctx.json(new ListResponse<>(dtos, dtos.size()));
            }).exceptionally(e -> {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
                return null;
            });
        }
    }

    /**
     * GET /api/tasks/{id}
     */
    public static void getTask(Context ctx) {
        String idStr = ctx.pathParam("id");
        UUID taskId = parseUuid(idStr);
        if (taskId == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid task ID"));
            return;
        }

        TestTaskManager.INSTANCE.getTask(taskId).thenAccept(task -> {
            if (task == null) {
                ctx.status(HttpStatus.NOT_FOUND).json(errorResponse("Task not found"));
            } else {
                ctx.json(TaskDto.from(task));
            }
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * POST /api/tasks
     * Create a new task.
     */
    public static void createTask(Context ctx) {
        CreateTaskRequest request = ctx.bodyAsClass(CreateTaskRequest.class);

        if (request.title() == null || request.title().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("title is required"));
            return;
        }
        if (request.assignedTo() == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("assignedTo is required"));
            return;
        }

        Integer priority = request.priority();
        TestTask task = TestTask.builder()
            .id(UUID.randomUUID())
            .title(request.title())
            .description(request.description())
            .assignedTo(request.assignedTo())
            .assignedByName(request.assignedByName() != null ? request.assignedByName() : "Admin")
            .priority(priority != null ? priority : 1)
            .status(TestTask.TaskStatus.PENDING)
            .createdAt(System.currentTimeMillis())
            .dueAt(request.dueAtMillis())
            .build();

        TestTaskManager.INSTANCE.createTask(task).thenAccept(v -> {
            ctx.status(HttpStatus.CREATED).json(TaskDto.from(task));
            AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                    .action(AdminAuditLog.Action.TASK_CREATE)
                    .actorUuid(null)
                    .actorName(resolveActorName(ctx))
                    .targetType("task")
                    .targetId(task.id().toString())
                    .details("AssignedTo=" + task.assignedTo())
                    .build())
                .exceptionally(error -> {
                    DevMod.LOGGER.warn("[Mailbox API] Failed to persist task create audit log", error);
                    return null;
                });
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * PUT /api/tasks/{id}
     * Update an existing task.
     */
    public static void updateTask(Context ctx) {
        String idStr = ctx.pathParam("id");
        UUID taskId = parseUuid(idStr);
        if (taskId == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid task ID"));
            return;
        }

        UpdateTaskRequest request = ctx.bodyAsClass(UpdateTaskRequest.class);

        TestTaskManager.INSTANCE.getTask(taskId).thenCompose(existing -> {
            if (existing == null) {
                ctx.status(HttpStatus.NOT_FOUND).json(errorResponse("Task not found"));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            TestTask.TaskStatus status = existing.status();
            if (request.status() != null) {
                status = TestTask.TaskStatus.fromId(request.status());
            }

            Integer priority = request.priority();
            Long dueAtMillis = request.dueAtMillis();
            Long existingCompletedAt = existing.completedAt();
            Long newCompletedAt = (status == TestTask.TaskStatus.COMPLETED && existingCompletedAt == null)
                ? Long.valueOf(System.currentTimeMillis())
                : existingCompletedAt;
            TestTask updated = TestTask.builder()
                .id(existing.id())
                .title(request.title() != null ? request.title() : existing.title())
                .description(request.description() != null ? request.description() : existing.description())
                .assignedTo(existing.assignedTo())
                .assignedByName(existing.assignedByName())
                .priority(priority != null ? priority : existing.priority())
                .status(status)
                .createdAt(existing.createdAt())
                .dueAt(dueAtMillis != null ? dueAtMillis : existing.dueAt())
                .completedAt(newCompletedAt)
                .notes(request.notes() != null ? request.notes() : existing.notes())
                .build();

            return TestTaskManager.INSTANCE.updateTask(updated).thenApply(v -> updated);
        }).thenAccept(result -> {
            if (result != null) {
                ctx.json(TaskDto.from(result));
                AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                        .action(AdminAuditLog.Action.TASK_UPDATE)
                        .actorUuid(null)
                        .actorName(resolveActorName(ctx))
                        .targetType("task")
                        .targetId(result.id().toString())
                        .details("Status=" + result.status().getId())
                        .build())
                    .exceptionally(error -> {
                        DevMod.LOGGER.warn("[Mailbox API] Failed to persist task update audit log", error);
                        return null;
                    });
            }
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * DELETE /api/tasks/{id}
     */
    public static void deleteTask(Context ctx) {
        String idStr = ctx.pathParam("id");
        UUID taskId = parseUuid(idStr);
        if (taskId == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid task ID"));
            return;
        }

        TestTaskManager.INSTANCE.deleteTask(taskId).thenAccept(success -> {
            if (success) {
                ctx.status(HttpStatus.NO_CONTENT);
                AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                        .action(AdminAuditLog.Action.TASK_DELETE)
                        .actorUuid(null)
                        .actorName(resolveActorName(ctx))
                        .targetType("task")
                        .targetId(taskId.toString())
                        .details("Deleted via admin API")
                        .build())
                    .exceptionally(error -> {
                        DevMod.LOGGER.warn("[Mailbox API] Failed to persist task delete audit log", error);
                        return null;
                    });
            } else {
                ctx.status(HttpStatus.NOT_FOUND).json(errorResponse("Task not found"));
            }
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * GET /api/tasks/{id}/audit
     * Get audit history for a task.
     */
    public static void getTaskAudit(Context ctx) {
        String idStr = ctx.pathParam("id");
        UUID taskId = parseUuid(idStr);
        if (taskId == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid task ID"));
            return;
        }

        TestTaskManager.INSTANCE.getTaskAuditHistory(taskId).thenAccept(entries -> {
            List<AuditEntryDto> dtos = entries.stream()
                .map(AuditEntryDto::from)
                .toList();
            ctx.json(new ListResponse<>(dtos, dtos.size()));
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * GET /api/tasks/audit/recent
     * Get recent audit entries across all tasks.
     */
    public static void getRecentAudits(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);

        TestTaskManager.INSTANCE.getRecentAudits(Math.min(limit, 200)).thenAccept(entries -> {
            List<AuditEntryDto> dtos = entries.stream()
                .map(AuditEntryDto::from)
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

    private static List<TestTask> filterByStatus(List<TestTask> tasks, @Nullable String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return tasks;
        }
        TestTask.TaskStatus status = TestTask.TaskStatus.fromId(statusStr.toLowerCase(Locale.ROOT));
        return tasks.stream()
            .filter(t -> t.status() == status)
            .toList();
    }

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

    // ========================================================================
    // DTOs
    // ========================================================================

    public record ErrorResponse(String status, String message) {}

    public record ListResponse<T>(List<T> items, int count) {}

    public record TaskDto(
        UUID id,
        String title,
        @Nullable String description,
        UUID assignedTo,
        @Nullable String assignedByName,
        int priority,
        String status,
        long createdAtMillis,
        @Nullable Long dueAtMillis,
        @Nullable Long completedAtMillis,
        @Nullable String notes,
        boolean overdue
    ) {
        public static TaskDto from(TestTask task) {
            return new TaskDto(
                task.id(),
                task.title(),
                task.description(),
                task.assignedTo(),
                task.assignedByName(),
                task.priority(),
                task.status().getId(),
                task.createdAt(),
                task.dueAt(),
                task.completedAt(),
                task.notes(),
                task.isOverdue()
            );
        }
    }

    public record CreateTaskRequest(
        String title,
        @Nullable String description,
        UUID assignedTo,
        @Nullable String assignedByName,
        @Nullable Integer priority,
        @Nullable Long dueAtMillis
    ) {}

    public record UpdateTaskRequest(
        @Nullable String title,
        @Nullable String description,
        @Nullable Integer priority,
        @Nullable String status,
        @Nullable Long dueAtMillis,
        @Nullable String notes
    ) {}

    public record AuditEntryDto(
        UUID id,
        UUID taskId,
        String action,
        @Nullable UUID actorUuid,
        @Nullable String actorName,
        @Nullable String oldValue,
        @Nullable String newValue,
        long timestampMillis
    ) {
        public static AuditEntryDto from(TaskAuditEntry entry) {
            return new AuditEntryDto(
                entry.id(),
                entry.taskId(),
                entry.action(),
                entry.actorUuid(),
                entry.actorName(),
                entry.oldValue(),
                entry.newValue(),
                entry.timestamp()
            );
        }
    }
}
