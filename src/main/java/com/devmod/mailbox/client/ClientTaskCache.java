package com.devmod.mailbox.client;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.mailbox.task.TestTask;

/**
 * Client-side cache for tester tasks.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientTaskCache {

    @Nullable
    private static volatile List<TestTask> cachedTasks = null;
    private static volatile long lastSyncTime = 0;

    public static final long CACHE_STALE_MS = 60000; // 1 minute

    private ClientTaskCache() {}

    /**
     * Update the cache with tasks from server.
     */
    public static void update(List<TestTask> tasks) {
        cachedTasks = List.copyOf(tasks);
        lastSyncTime = System.currentTimeMillis();
    }

    /**
     * Update a single task's status locally (optimistic update).
     */
    public static void updateTaskStatus(UUID taskId, TestTask.TaskStatus newStatus) {
        List<TestTask> tasks = cachedTasks;
        if (tasks == null) return;

        cachedTasks = tasks.stream()
            .map(task -> {
                if (task.id().equals(taskId)) {
                    return task.withStatus(newStatus);
                }
                return task;
            })
            .toList();
    }

    /**
     * Update a single task's notes locally (optimistic update).
     */
    public static void updateTaskNotes(UUID taskId, String newNotes) {
        List<TestTask> tasks = cachedTasks;
        if (tasks == null) return;

        cachedTasks = tasks.stream()
            .map(task -> {
                if (task.id().equals(taskId)) {
                    return task.withNotes(newNotes);
                }
                return task;
            })
            .toList();
    }

    /**
     * Clear all cached data.
     */
    public static void clear() {
        cachedTasks = null;
        lastSyncTime = 0;
    }

    // ========== GETTERS ==========

    /**
     * Check if we have any cached data.
     */
    public static boolean hasCachedData() {
        return cachedTasks != null;
    }

    /**
     * Check if cache is stale.
     */
    public static boolean isStale() {
        return System.currentTimeMillis() - lastSyncTime > CACHE_STALE_MS;
    }

    /**
     * Get all tasks.
     */
    public static List<TestTask> getTasks() {
        List<TestTask> tasks = cachedTasks;
        return tasks != null ? tasks : List.of();
    }

    /**
     * Get a specific task by ID.
     */
    @Nullable
    public static TestTask getTask(UUID taskId) {
        List<TestTask> tasks = cachedTasks;
        if (tasks == null) return null;

        return tasks.stream()
            .filter(t -> t.id().equals(taskId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get pending task count.
     */
    public static int getPendingCount() {
        List<TestTask> tasks = cachedTasks;
        if (tasks == null) return 0;

        return (int) tasks.stream()
            .filter(t -> t.status() == TestTask.TaskStatus.PENDING ||
                         t.status() == TestTask.TaskStatus.IN_PROGRESS)
            .count();
    }

    /**
     * Get total task count.
     */
    public static int getTaskCount() {
        List<TestTask> tasks = cachedTasks;
        return tasks != null ? tasks.size() : 0;
    }

    /**
     * Check if there are pending tasks.
     */
    public static boolean hasPendingTasks() {
        return getPendingCount() > 0;
    }

    /**
     * Get tasks by status.
     */
    public static List<TestTask> getTasksByStatus(TestTask.TaskStatus status) {
        List<TestTask> tasks = cachedTasks;
        if (tasks == null) return List.of();

        return tasks.stream()
            .filter(t -> t.status() == status)
            .toList();
    }
}
