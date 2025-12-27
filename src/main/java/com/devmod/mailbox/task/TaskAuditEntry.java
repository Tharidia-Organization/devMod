package com.devmod.mailbox.task;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Immutable record representing an audit entry for task changes.
 */
public record TaskAuditEntry(
    UUID id,
    UUID taskId,
    String action,
    @Nullable UUID actorUuid,
    @Nullable String actorName,
    @Nullable String oldValue,
    @Nullable String newValue,
    long timestamp
) {

    public TaskAuditEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(action, "action must not be null");
    }

    /**
     * Audit action types.
     */
    public static final class Actions {
        public static final String CREATED = "created";
        public static final String STATUS_CHANGED = "status_changed";
        public static final String PRIORITY_CHANGED = "priority_changed";
        public static final String ASSIGNED = "assigned";
        public static final String NOTES_UPDATED = "notes_updated";
        public static final String DUE_DATE_CHANGED = "due_date_changed";
        public static final String DELETED = "deleted";

        private Actions() {}
    }

    /**
     * Builder for creating TaskAuditEntry instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private @Nullable UUID id;
        private @Nullable UUID taskId;
        private @Nullable String action;
        private @Nullable UUID actorUuid;
        private @Nullable String actorName;
        private @Nullable String oldValue;
        private @Nullable String newValue;
        private long timestamp;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder taskId(UUID taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder actorUuid(@Nullable UUID actorUuid) {
            this.actorUuid = actorUuid;
            return this;
        }

        public Builder actorName(@Nullable String actorName) {
            this.actorName = actorName;
            return this;
        }

        public Builder oldValue(@Nullable String oldValue) {
            this.oldValue = oldValue;
            return this;
        }

        public Builder newValue(@Nullable String newValue) {
            this.newValue = newValue;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public TaskAuditEntry build() {
            UUID resolvedId = id != null ? id : UUID.randomUUID();
            long resolvedTimestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
            UUID resolvedTaskId = Objects.requireNonNull(taskId, "taskId");
            String resolvedAction = Objects.requireNonNull(action, "action");

            return new TaskAuditEntry(
                resolvedId, resolvedTaskId, resolvedAction,
                actorUuid, actorName, oldValue, newValue, resolvedTimestamp
            );
        }
    }
}
