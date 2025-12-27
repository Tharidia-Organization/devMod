package com.devmod.mailbox.network.payload;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.mailbox.task.TestTask;

/**
 * Payload for task actions from client to server.
 */
public record TaskActionPayload(
    Action action,
    UUID taskId,
    @Nullable String statusId,
    @Nullable String notes
) implements CustomPacketPayload {

    public enum Action {
        UPDATE_STATUS,
        ADD_NOTES
    }

    public static final Type<TaskActionPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "task_action"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TaskActionPayload> STREAM_CODEC = StreamCodec.of(
        TaskActionPayload::encode,
        TaskActionPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, TaskActionPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeUUID(payload.taskId);
        buf.writeUtf(payload.statusId != null ? payload.statusId : "");
        buf.writeUtf(payload.notes != null ? payload.notes : "");
    }

    private static TaskActionPayload decode(RegistryFriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID taskId = buf.readUUID();
        String statusId = buf.readUtf(64);
        String notes = buf.readUtf(1000);

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
}
