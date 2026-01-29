package com.devmod.mailbox.network.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.mailbox.task.TestTask;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Payload to sync tasks from server to client.
 */
public record TaskSyncPayload(
    List<TaskData> tasks
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<TaskSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "task_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TaskSyncPayload> STREAM_CODEC = StreamCodec.of(
        TaskSyncPayload::encode,
        TaskSyncPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, TaskSyncPayload payload) {
        int taskCount = MailboxPayloadLimits.clampCount(payload.tasks.size(), MailboxPayloadLimits.MAX_TASKS);
        buf.writeVarInt(taskCount);
        for (int i = 0; i < taskCount; i++) {
            TaskData task = payload.tasks.get(i);
            encodeTask(buf, task);
        }
    }

    private static TaskSyncPayload decode(RegistryFriendlyByteBuf buf) {
        int taskCount = buf.readVarInt();
        taskCount = MailboxPayloadLimits.clampCount(taskCount, MailboxPayloadLimits.MAX_TASKS);

        List<TaskData> tasks = new ArrayList<>(taskCount);
        for (int i = 0; i < taskCount; i++) {
            tasks.add(decodeTask(buf));
        }

        return new TaskSyncPayload(tasks);
    }

    private static void encodeTask(RegistryFriendlyByteBuf buf, TaskData task) {
        buf.writeUUID(Objects.requireNonNull(task.id));
        buf.writeUtf(
            MailboxPayloadLimits.truncate(task.title, MailboxPayloadLimits.MAX_TASK_TITLE_LENGTH),
            MailboxPayloadLimits.MAX_TASK_TITLE_LENGTH
        );
        buf.writeUtf(
            MailboxPayloadLimits.truncate(task.description, MailboxPayloadLimits.MAX_TASK_DESC_LENGTH),
            MailboxPayloadLimits.MAX_TASK_DESC_LENGTH
        );
        buf.writeUtf(
            MailboxPayloadLimits.truncate(task.assignedByName, MailboxPayloadLimits.MAX_TASK_NAME_LENGTH),
            MailboxPayloadLimits.MAX_TASK_NAME_LENGTH
        );
        buf.writeVarInt(task.priority);
        buf.writeUtf(
            MailboxPayloadLimits.truncate(task.statusId, MailboxPayloadLimits.MAX_TASK_STATUS_ID_LENGTH),
            MailboxPayloadLimits.MAX_TASK_STATUS_ID_LENGTH
        );
        buf.writeLong(task.createdAt);
        buf.writeLong(task.dueAt != null ? task.dueAt : 0L);
        buf.writeLong(task.completedAt != null ? task.completedAt : 0L);
        buf.writeUtf(
            MailboxPayloadLimits.truncate(task.notes, MailboxPayloadLimits.MAX_TASK_NOTES_LENGTH),
            MailboxPayloadLimits.MAX_TASK_NOTES_LENGTH
        );
    }

    private static TaskData decodeTask(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String title = buf.readUtf(MailboxPayloadLimits.MAX_TASK_TITLE_LENGTH);
        String description = buf.readUtf(MailboxPayloadLimits.MAX_TASK_DESC_LENGTH);
        String assignedByName = buf.readUtf(MailboxPayloadLimits.MAX_TASK_NAME_LENGTH);
        int priority = buf.readVarInt();
        String statusId = buf.readUtf(MailboxPayloadLimits.MAX_TASK_STATUS_ID_LENGTH);
        long createdAt = buf.readLong();
        long dueAt = buf.readLong();
        long completedAt = buf.readLong();
        String notes = buf.readUtf(MailboxPayloadLimits.MAX_TASK_NOTES_LENGTH);

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

    @Override
    public int estimatedSize() {
        int count = MailboxPayloadLimits.clampCount(tasks.size(), MailboxPayloadLimits.MAX_TASKS);
        int size = PayloadSizeUtil.varIntSize(count);
        for (int i = 0; i < count; i++) {
            TaskData task = tasks.get(i);
            size += estimateTaskSize(task);
        }
        return size;
    }

    private static int estimateTaskSize(TaskData task) {
        int size = 16; // UUID
        size += estimatedUtfSize(task.title, MailboxPayloadLimits.MAX_TASK_TITLE_LENGTH);
        size += estimatedUtfSize(task.description, MailboxPayloadLimits.MAX_TASK_DESC_LENGTH);
        size += estimatedUtfSize(task.assignedByName, MailboxPayloadLimits.MAX_TASK_NAME_LENGTH);
        size += PayloadSizeUtil.varIntSize(task.priority);
        size += estimatedUtfSize(task.statusId, MailboxPayloadLimits.MAX_TASK_STATUS_ID_LENGTH);
        size += 8; // createdAt
        size += 8; // dueAt
        size += 8; // completedAt
        size += estimatedUtfSize(task.notes, MailboxPayloadLimits.MAX_TASK_NOTES_LENGTH);
        return size;
    }

    private static int estimatedUtfSize(@Nullable String value, int maxLength) {
        return PayloadSizeUtil.estimatedUtfSize(MailboxPayloadLimits.truncateNullable(value, maxLength));
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
        @Nonnull UUID id,
        @Nonnull String title,
        @Nullable String description,
        @Nullable String assignedByName,
        int priority,
        @Nonnull String statusId,
        long createdAt,
        @Nullable Long dueAt,
        @Nullable Long completedAt,
        @Nullable String notes
    ) {
        public TaskData {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(statusId, "statusId");
        }
    }
}
