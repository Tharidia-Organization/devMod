package com.devmod.mailbox.task;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Immutable record representing a test task for QA testers.
 */
public record TestTask(
    UUID id,
    String title,
    @Nullable String description,
    UUID assignedTo,
    @Nullable String assignedByName,
    int priority,
    TaskStatus status,
    long createdAt,
    @Nullable Long dueAt,
    @Nullable Long completedAt,
    @Nullable String notes
) {

    public TestTask {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(assignedTo, "assignedTo must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * Task status enum.
     */
    public enum TaskStatus {
        PENDING("pending"),
        IN_PROGRESS("in_progress"),
        COMPLETED("completed");

        private final String id;

        TaskStatus(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static TaskStatus fromId(@Nullable String id) {
            if (id == null) {
                return PENDING;
            }
            for (TaskStatus status : values()) {
                if (status.id.equalsIgnoreCase(id)) {
                    return status;
                }
            }
            return PENDING;
        }
    }

    /**
     * Check if task is overdue.
     */
    public boolean isOverdue() {
        Long due = dueAt;
        return due != null && status != TaskStatus.COMPLETED && System.currentTimeMillis() > due;
    }

    /**
     * Create a copy with updated status.
     */
    public TestTask withStatus(TaskStatus newStatus) {
        Long newCompletedAt = completedAt;
        if (newStatus == TaskStatus.COMPLETED && status != TaskStatus.COMPLETED) {
            newCompletedAt = System.currentTimeMillis();
        }
        return new TestTask(id, title, description, assignedTo, assignedByName,
            priority, newStatus, createdAt, dueAt, newCompletedAt, notes);
    }

    /**
     * Create a copy with updated notes.
     */
    public TestTask withNotes(String newNotes) {
        return new TestTask(id, title, description, assignedTo, assignedByName,
            priority, status, createdAt, dueAt, completedAt, newNotes);
    }

    /**
     * Builder for creating TestTask instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private @Nullable UUID id;
        private @Nullable String title;
        private @Nullable String description;
        private @Nullable UUID assignedTo;
        private @Nullable String assignedByName;
        private int priority = 1;
        private TaskStatus status = TaskStatus.PENDING;
        private long createdAt;
        private @Nullable Long dueAt;
        private @Nullable Long completedAt;
        private @Nullable String notes;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public Builder assignedTo(UUID assignedTo) {
            this.assignedTo = assignedTo;
            return this;
        }

        public Builder assignedByName(@Nullable String assignedByName) {
            this.assignedByName = assignedByName;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder status(TaskStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder dueAt(@Nullable Long dueAt) {
            this.dueAt = dueAt;
            return this;
        }

        public Builder completedAt(@Nullable Long completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder notes(@Nullable String notes) {
            this.notes = notes;
            return this;
        }

        public TestTask build() {
            UUID resolvedId = id != null ? id : UUID.randomUUID();
            long resolvedCreatedAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
            String resolvedTitle = Objects.requireNonNull(title, "title");
            UUID resolvedAssignedTo = Objects.requireNonNull(assignedTo, "assignedTo");

            return new TestTask(
                resolvedId, resolvedTitle, description, resolvedAssignedTo, assignedByName,
                priority, status, resolvedCreatedAt, dueAt, completedAt, notes
            );
        }
    }
}
