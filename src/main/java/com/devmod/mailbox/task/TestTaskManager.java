package com.devmod.mailbox.task;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.persistence.MailboxRepository;

/**
 * Manager for tester task operations.
 * Uses in-memory storage for simplicity; can be backed by DuckDB if persistence is needed.
 */
public class TestTaskManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestTaskManager.class);

    // ============================================================================
    // SINGLETON
    // ============================================================================

    public static final TestTaskManager INSTANCE = new TestTaskManager();

    private TestTaskManager() {}

    // ============================================================================
    // STATE
    // ============================================================================

    /** In-memory task storage */
    private final ConcurrentMap<UUID, TestTask> tasks = new ConcurrentHashMap<>();

    @Nullable
    private volatile MailboxRepository repository;

    // ============================================================================
    // INITIALIZATION
    // ============================================================================

    public void initialize(MailboxRepository repo) {
        this.repository = repo;
        if (repo != null) {
            LOGGER.info("[Tasks] Task repository initialized");
        }
    }

    public void shutdown() {
        this.repository = null;
        tasks.clear();
        LOGGER.info("[Tasks] Task manager shutdown");
    }

    // ============================================================================
    // TASK OPERATIONS
    // ============================================================================

    /**
     * Create a new task.
     */
    public CompletableFuture<Void> createTask(TestTask task) {
        return createTask(task, null, null);
    }

    /**
     * Create a new task with audit tracking.
     */
    public CompletableFuture<Void> createTask(TestTask task, @Nullable UUID actorUuid, @Nullable String actorName) {
        MailboxRepository repo = repository;
        if (repo != null) {
            return repo.saveTask(task).thenCompose(saved -> {
                LOGGER.info("[Tasks] Created task {} for {}", saved.id(), saved.assignedTo());
                TaskAuditEntry audit = TaskAuditEntry.builder()
                    .taskId(saved.id())
                    .action(TaskAuditEntry.Actions.CREATED)
                    .actorUuid(actorUuid)
                    .actorName(actorName != null ? actorName : "System")
                    .newValue(saved.title())
                    .build();
                return repo.saveTaskAudit(audit).thenAccept(a -> {});
            });
        }
        return CompletableFuture.runAsync(() -> {
            tasks.put(task.id(), task);
            LOGGER.info("[Tasks] Created task {} for {}", task.id(), task.assignedTo());
        });
    }

    /**
     * Get a task by ID.
     * @return future containing the task, or null if not found
     */
    public CompletableFuture<TestTask> getTask(UUID taskId) {
        MailboxRepository repo = repository;
        if (repo != null) {
            return repo.getTask(taskId).thenApply(opt -> opt.orElse(null));
        }
        return CompletableFuture.completedFuture(tasks.get(taskId));
    }

    /**
     * Update an existing task.
     */
    public CompletableFuture<Void> updateTask(TestTask task) {
        return updateTask(task, null, null, null);
    }

    /**
     * Update an existing task with audit tracking.
     */
    public CompletableFuture<Void> updateTask(TestTask task, @Nullable TestTask oldTask,
            @Nullable UUID actorUuid, @Nullable String actorName) {
        MailboxRepository repo = repository;
        if (repo != null) {
            return repo.updateTask(task).thenCompose(success -> {
                if (success) {
                    LOGGER.debug("[Tasks] Updated task {}", task.id());
                    return createUpdateAuditEntries(repo, task, oldTask, actorUuid, actorName);
                }
                return CompletableFuture.completedFuture(null);
            });
        }
        return CompletableFuture.runAsync(() -> {
            tasks.put(task.id(), task);
            LOGGER.debug("[Tasks] Updated task {}", task.id());
        });
    }

    private CompletableFuture<Void> createUpdateAuditEntries(MailboxRepository repo, TestTask task,
            @Nullable TestTask oldTask, @Nullable UUID actorUuid, @Nullable String actorName) {
        if (oldTask == null) {
            return CompletableFuture.completedFuture(null);
        }

        String actor = actorName != null ? actorName : "System";
        java.util.List<CompletableFuture<TaskAuditEntry>> futures = new java.util.ArrayList<>();

        // Check status change
        if (oldTask.status() != task.status()) {
            TaskAuditEntry audit = TaskAuditEntry.builder()
                .taskId(task.id())
                .action(TaskAuditEntry.Actions.STATUS_CHANGED)
                .actorUuid(actorUuid)
                .actorName(actor)
                .oldValue(oldTask.status().getId())
                .newValue(task.status().getId())
                .build();
            futures.add(repo.saveTaskAudit(audit));
        }

        // Check priority change
        if (oldTask.priority() != task.priority()) {
            TaskAuditEntry audit = TaskAuditEntry.builder()
                .taskId(task.id())
                .action(TaskAuditEntry.Actions.PRIORITY_CHANGED)
                .actorUuid(actorUuid)
                .actorName(actor)
                .oldValue(String.valueOf(oldTask.priority()))
                .newValue(String.valueOf(task.priority()))
                .build();
            futures.add(repo.saveTaskAudit(audit));
        }

        // Check notes change
        if (!java.util.Objects.equals(oldTask.notes(), task.notes())) {
            TaskAuditEntry audit = TaskAuditEntry.builder()
                .taskId(task.id())
                .action(TaskAuditEntry.Actions.NOTES_UPDATED)
                .actorUuid(actorUuid)
                .actorName(actor)
                .oldValue(oldTask.notes())
                .newValue(task.notes())
                .build();
            futures.add(repo.saveTaskAudit(audit));
        }

        // Check due date change
        if (!java.util.Objects.equals(oldTask.dueAt(), task.dueAt())) {
            TaskAuditEntry audit = TaskAuditEntry.builder()
                .taskId(task.id())
                .action(TaskAuditEntry.Actions.DUE_DATE_CHANGED)
                .actorUuid(actorUuid)
                .actorName(actor)
                .oldValue(oldTask.dueAt() != null ? String.valueOf(oldTask.dueAt()) : null)
                .newValue(task.dueAt() != null ? String.valueOf(task.dueAt()) : null)
                .build();
            futures.add(repo.saveTaskAudit(audit));
        }

        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
    }

    /**
     * Delete a task.
     */
    public CompletableFuture<Boolean> deleteTask(UUID taskId) {
        return deleteTask(taskId, null, null);
    }

    /**
     * Delete a task with audit tracking.
     */
    public CompletableFuture<Boolean> deleteTask(UUID taskId, @Nullable UUID actorUuid, @Nullable String actorName) {
        MailboxRepository repo = repository;
        if (repo != null) {
            // First create audit entry, then delete
            TaskAuditEntry audit = TaskAuditEntry.builder()
                .taskId(taskId)
                .action(TaskAuditEntry.Actions.DELETED)
                .actorUuid(actorUuid)
                .actorName(actorName != null ? actorName : "System")
                .build();
            return repo.saveTaskAudit(audit).thenCompose(a ->
                repo.deleteTask(taskId).thenApply(success -> {
                    if (success) {
                        LOGGER.info("[Tasks] Deleted task {}", taskId);
                    }
                    return success;
                })
            );
        }
        return CompletableFuture.supplyAsync(() -> {
            TestTask removed = tasks.remove(taskId);
            if (removed != null) {
                LOGGER.info("[Tasks] Deleted task {}", taskId);
                return true;
            }
            return false;
        });
    }

    // ============================================================================
    // AUDIT OPERATIONS
    // ============================================================================

    /**
     * Get audit history for a task.
     */
    public CompletableFuture<java.util.List<TaskAuditEntry>> getTaskAuditHistory(UUID taskId) {
        MailboxRepository repo = repository;
        if (repo != null) {
            return repo.getTaskAuditHistory(taskId);
        }
        return CompletableFuture.completedFuture(java.util.List.of());
    }

    /**
     * Get recent audit entries across all tasks.
     */
    public CompletableFuture<java.util.List<TaskAuditEntry>> getRecentAudits(int limit) {
        MailboxRepository repo = repository;
        if (repo != null) {
            return repo.getRecentTaskAudits(limit);
        }
        return CompletableFuture.completedFuture(java.util.List.of());
    }

    /**
     * Get all tasks for a specific user.
     */
    public CompletableFuture<List<TestTask>> getTasksForUser(UUID userId) {
        MailboxRepository repo = repository;
        if (repo != null) {
            return repo.getTasksForUser(userId);
        }
        return CompletableFuture.supplyAsync(() ->
            tasks.values().stream()
                .filter(t -> t.assignedTo().equals(userId))
                .sorted(TestTaskManager::compareTaskOrder)
                .toList()
        );
    }

    /**
     * Get all tasks with pagination.
     */
    public CompletableFuture<List<TestTask>> getAllTasks(int limit, int offset) {
        MailboxRepository repo = repository;
        if (repo != null) {
            return repo.getAllTasks(limit, offset);
        }
        return CompletableFuture.supplyAsync(() ->
            tasks.values().stream()
                .sorted((a, b) -> Long.compare(b.createdAt(), a.createdAt()))
                .skip(offset)
                .limit(limit)
                .toList()
        );
    }

    /**
     * Get all tasks (no pagination, default limit 1000).
     */
    public CompletableFuture<List<TestTask>> getAllTasks() {
        return getAllTasks(Integer.MAX_VALUE, 0);
    }

    private static int compareTaskOrder(TestTask first, TestTask second) {
        // Sort by status (pending first), then priority (high first), then due date.
        int statusCompare = Integer.compare(first.status().ordinal(), second.status().ordinal());
        if (statusCompare != 0) {
            return statusCompare;
        }

        int priorityCompare = Integer.compare(second.priority(), first.priority());
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        Long firstDue = first.dueAt();
        Long secondDue = second.dueAt();
        if (firstDue != null && secondDue != null) {
            return Long.compare(firstDue, secondDue);
        }

        return 0;
    }

    // ============================================================================
    // STATISTICS
    // ============================================================================

    /**
     * Get total task count.
     */
    public CompletableFuture<Integer> getTotalTaskCount() {
        MailboxRepository repo = repository;
        if (repo != null) {
            return repo.getAllTasks(Integer.MAX_VALUE, 0).thenApply(List::size);
        }
        return CompletableFuture.completedFuture(tasks.size());
    }

    /**
     * Get pending task count.
     */
    public CompletableFuture<Integer> getPendingTaskCount() {
        MailboxRepository repo = repository;
        if (repo != null) {
            return repo.getAllTasks(Integer.MAX_VALUE, 0).thenApply(list ->
                (int) list.stream()
                    .filter(t -> t.status() == TestTask.TaskStatus.PENDING ||
                                 t.status() == TestTask.TaskStatus.IN_PROGRESS)
                    .count()
            );
        }
        return CompletableFuture.supplyAsync(() ->
            (int) tasks.values().stream()
                .filter(t -> t.status() == TestTask.TaskStatus.PENDING ||
                             t.status() == TestTask.TaskStatus.IN_PROGRESS)
                .count()
        );
    }

    /**
     * Get completed task count.
     */
    public CompletableFuture<Integer> getCompletedTaskCount() {
        MailboxRepository repo = repository;
        if (repo != null) {
            return repo.getAllTasks(Integer.MAX_VALUE, 0).thenApply(list ->
                (int) list.stream()
                    .filter(t -> t.status() == TestTask.TaskStatus.COMPLETED)
                    .count()
            );
        }
        return CompletableFuture.supplyAsync(() ->
            (int) tasks.values().stream()
                .filter(t -> t.status() == TestTask.TaskStatus.COMPLETED)
                .count()
        );
    }

    /**
     * Clear all tasks (for testing or reset).
     */
    public void clear() {
        tasks.clear();
        LOGGER.info("[Tasks] All tasks cleared");
    }
}
