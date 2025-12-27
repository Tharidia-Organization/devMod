package com.devmod.mailbox.network.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.mailbox.task.TestTask;

/**
 * Payload to sync tasks from server to client.
 */
public record TaskSyncPayload(
    List<TaskData> tasks
) implements CustomPacketPayload {

    private static final int MAX_TASKS = 100;
    private static final int MAX_TITLE_LENGTH = 256;
    private static final int MAX_DESC_LENGTH = 2000;
    private static final int MAX_NOTES_LENGTH = 1000;
    private static final int MAX_NAME_LENGTH = 64;

    public static final Type<TaskSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "task_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TaskSyncPayload> STREAM_CODEC = StreamCodec.of(
        TaskSyncPayload::encode,
        TaskSyncPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, TaskSyncPayload payload) {
        buf.writeVarInt(payload.tasks.size());
        for (TaskData task : payload.tasks) {
            encodeTask(buf, task);
        }
    }

    private static TaskSyncPayload decode(RegistryFriendlyByteBuf buf) {
        int taskCount = buf.readVarInt();
        if (taskCount < 0 || taskCount > MAX_TASKS) {
            taskCount = 0;
        }

        List<TaskData> tasks = new ArrayList<>(taskCount);
        for (int i = 0; i < taskCount; i++) {
            tasks.add(decodeTask(buf));
        }

        return new TaskSyncPayload(tasks);
    }

    private static void encodeTask(RegistryFriendlyByteBuf buf, TaskData task) {
        buf.writeUUID(Objects.requireNonNull(task.id));
        buf.writeUtf(Objects.requireNonNull(task.title));
        buf.writeUtf(task.description != null ? task.description : "");
        buf.writeUtf(task.assignedByName != null ? task.assignedByName : "");
        buf.writeVarInt(task.priority);
        buf.writeUtf(task.statusId);
        buf.writeLong(task.createdAt);
        buf.writeLong(task.dueAt != null ? task.dueAt : 0L);
        buf.writeLong(task.completedAt != null ? task.completedAt : 0L);
        buf.writeUtf(task.notes != null ? task.notes : "");
    }

    private static TaskData decodeTask(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String title = buf.readUtf(MAX_TITLE_LENGTH);
        String description = buf.readUtf(MAX_DESC_LENGTH);
        String assignedByName = buf.readUtf(MAX_NAME_LENGTH);
        int priority = buf.readVarInt();
        String statusId = buf.readUtf(32);
        long createdAt = buf.readLong();
        long dueAt = buf.readLong();
        long completedAt = buf.readLong();
        String notes = buf.readUtf(MAX_NOTES_LENGTH);

        return new TaskData(
            id,
            title,
            description.isEmpty() ? null : description,
            assignedByName.isEmpty() ? null : assignedByName,
            priority,
            statusId,
            createdAt,
            dueAt == 0 ? null : dueAt,
            completedAt == 0 ? null : completedAt,
            notes.isEmpty() ? null : notes
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Create an empty sync payload.
     */
    public static TaskSyncPayload empty() {
        return new TaskSyncPayload(List.of());
    }

    /**
     * Create a sync payload from TestTask list.
     */
    public static TaskSyncPayload fromTasks(List<TestTask> tasks) {
        List<TaskData> data = tasks.stream()
            .map(task -> new TaskData(
                task.id(),
                task.title(),
                task.description(),
                task.assignedByName(),
                task.priority(),
                task.status().getId(),
                task.createdAt(),
                task.dueAt(),
                task.completedAt(),
                task.notes()
            ))
            .toList();
        return new TaskSyncPayload(data);
    }

    /**
     * Convert to TestTask list.
     */
    public List<TestTask> toTasks(UUID assignedTo) {
        return tasks.stream()
            .map(data -> TestTask.builder()
                .id(data.id)
                .title(data.title)
                .description(data.description)
                .assignedTo(assignedTo)
                .assignedByName(data.assignedByName)
                .priority(data.priority)
                .status(TestTask.TaskStatus.fromId(data.statusId))
                .createdAt(data.createdAt)
                .dueAt(data.dueAt)
                .completedAt(data.completedAt)
                .notes(data.notes)
                .build())
            .toList();
    }

    /**
     * Data structure for a single task in the sync.
     */
    public record TaskData(
        UUID id,
        String title,
        @Nullable String description,
        @Nullable String assignedByName,
        int priority,
        String statusId,
        long createdAt,
        @Nullable Long dueAt,
        @Nullable Long completedAt,
        @Nullable String notes
    ) {}
}
