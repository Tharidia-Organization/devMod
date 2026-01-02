package com.devmod.mailbox.api.controllers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import com.devmod.mailbox.broadcast.BroadcastQueueWorker;

/**
 * REST API controller for broadcast queue jobs.
 */
public final class BroadcastController {

    private BroadcastController() {}

    /**
     * GET /api/broadcast/jobs/active
     */
    public static void listActiveJobs(Context ctx) {
        List<BroadcastJobDto> dtos = BroadcastQueueWorker.INSTANCE.getActiveJobs().stream()
            .map(BroadcastJobDto::from)
            .toList();
        ctx.json(new ListResponse<>(dtos, dtos.size()));
    }

    /**
     * GET /api/broadcast/jobs/recent
     */
    public static void listRecentJobs(Context ctx) {
        int limitParam = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);
        int limit = Math.min(Math.max(limitParam, 1), 200);
        List<BroadcastJobDto> dtos = BroadcastQueueWorker.INSTANCE.getRecentCompletedJobs(limit).stream()
            .map(BroadcastJobDto::from)
            .toList();
        ctx.json(new ListResponse<>(dtos, dtos.size()));
    }

    /**
     * GET /api/broadcast/jobs/{id}
     */
    public static void getJob(Context ctx) {
        UUID jobId = parseUuid(ctx.pathParam("id"));
        if (jobId == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid job ID"));
            return;
        }
        BroadcastQueueWorker.JobStatus status = BroadcastQueueWorker.INSTANCE.getJobStatus(jobId);
        if (status == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(errorResponse("Job not found"));
            return;
        }
        ctx.json(BroadcastJobDto.from(status));
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

    // ========================================================================
    // DTOs
    // ========================================================================

    public record ErrorResponse(String status, String message) {}

    public record ListResponse<T>(List<T> items, int count) {}

    public record BroadcastJobDto(
        UUID jobId,
        String subject,
        int totalRecipients,
        int sentCount,
        int failedCount,
        String state,
        @Nullable Long startedAtMillis,
        @Nullable Long completedAtMillis,
        @Nullable String errorMessage
    ) {
        public static BroadcastJobDto from(BroadcastQueueWorker.JobStatus status) {
            Instant startedAt = status.startedAt();
            Instant completedAt = status.completedAt();
            return new BroadcastJobDto(
                status.jobId(),
                status.subject(),
                status.totalRecipients(),
                status.sentCount(),
                status.failedCount(),
                status.state().name(),
                startedAt != null ? startedAt.toEpochMilli() : null,
                completedAt != null ? completedAt.toEpochMilli() : null,
                status.errorMessage()
            );
        }
    }
}
