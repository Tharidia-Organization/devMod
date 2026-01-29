package com.devmod.mailbox.network.payload;

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
 * Payload for task actions from client to server.
 */
public record TaskActionPayload(
    @Nonnull Action action,
    @Nonnull UUID taskId,
    @Nullable String statusId,
    @Nullable String notes
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public enum Action {
        UPDATE_STATUS,
        ADD_NOTES
    }

    public TaskActionPayload {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(taskId, "taskId");
    }

    public static final Type<TaskActionPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "task_action"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TaskActionPayload> STREAM_CODEC = StreamCodec.of(
        TaskActionPayload::encode,
        TaskActionPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, TaskActionPayload payload) {
        buf.writeEnum(Objects.requireNonNull(payload.action));
        buf.writeUUID(Objects.requireNonNull(payload.taskId));
        buf.writeUtf(
            MailboxPayloadLimits.truncate(payload.statusId, MailboxPayloadLimits.MAX_TASK_STATUS_ID_LENGTH),
            MailboxPayloadLimits.MAX_TASK_STATUS_ID_LENGTH
        );
        buf.writeUtf(
            MailboxPayloadLimits.truncate(payload.notes, MailboxPayloadLimits.MAX_TASK_NOTES_LENGTH),
            MailboxPayloadLimits.MAX_TASK_NOTES_LENGTH
        );
    }

    private static TaskActionPayload decode(RegistryFriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID taskId = buf.readUUID();
        String statusId = buf.readUtf(MailboxPayloadLimits.MAX_TASK_STATUS_ID_LENGTH);
        String notes = buf.readUtf(MailboxPayloadLimits.MAX_TASK_NOTES_LENGTH);

        return new TaskActionPayload(
            action,
            taskId,
            statusId.isEmpty() ? null : statusId,
            notes.isEmpty() ? null : notes
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Create a payload to update task status.
     */
    public static TaskActionPayload updateStatus(UUID taskId, TestTask.TaskStatus status) {
        return new TaskActionPayload(Action.UPDATE_STATUS, taskId, status.getId(), null);
    }

    /**
     * Create a payload to add notes to a task.
     */
    public static TaskActionPayload addNotes(UUID taskId, String notes) {
        return new TaskActionPayload(Action.ADD_NOTES, taskId, null, notes);
    }

    @Override
    public int estimatedSize() {
        int size = 1; // action enum
        size += 16; // UUID
        size += PayloadSizeUtil.estimatedUtfSize(
            MailboxPayloadLimits.truncateNullable(statusId, MailboxPayloadLimits.MAX_TASK_STATUS_ID_LENGTH)
        );
        size += PayloadSizeUtil.estimatedUtfSize(
            MailboxPayloadLimits.truncateNullable(notes, MailboxPayloadLimits.MAX_TASK_NOTES_LENGTH)
        );
        return size;
    }
}
