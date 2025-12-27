package com.devmod.mailbox.network;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.mailbox.network.payload.MailboxActionPayload;
import com.devmod.mailbox.network.payload.MailboxNotifyPayload;
import com.devmod.mailbox.network.payload.MailboxSendPayload;
import com.devmod.mailbox.network.payload.MailboxSyncPayload;
import com.devmod.mailbox.network.payload.TaskActionPayload;
import com.devmod.mailbox.network.payload.TaskSyncPayload;
import com.devmod.mailbox.task.TestTask;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for mailbox network payloads.
 */
class MailboxPayloadTest {

    private static final UUID TEST_UUID = UUID.randomUUID();
    private static final UUID RECIPIENT_UUID = UUID.randomUUID();

    @Nested
    @DisplayName("MailboxSendPayload")
    class SendPayloadTests {

        @Test
        @DisplayName("Create send payload with all fields")
        void createWithAllFields() {
            MailboxSendPayload payload = new MailboxSendPayload(
                RECIPIENT_UUID,
                "RecipientName",
                "Test Subject",
                "Test Body",
                null
            );

            assertEquals(RECIPIENT_UUID, payload.recipientUuid());
            assertEquals("Test Subject", payload.subject());
            assertEquals("Test Body", payload.body());
            assertNull(payload.attachmentData());
        }

        @Test
        @DisplayName("Create send payload with attachment")
        void createWithAttachment() {
            String attachmentJson = "{\"type\":\"item\",\"id\":\"minecraft:diamond\",\"count\":5}";
            MailboxSendPayload payload = new MailboxSendPayload(
                RECIPIENT_UUID,
                "RecipientName",
                "Gift",
                "Here are some diamonds!",
                attachmentJson
            );

            assertEquals(attachmentJson, payload.attachmentData());
        }

        @Test
        @DisplayName("Payload type is correct")
        void payloadTypeCorrect() {
            MailboxSendPayload payload = new MailboxSendPayload(
                RECIPIENT_UUID, null, "Subject", "Body", null
            );

            assertNotNull(payload.type());
            assertEquals("devmod", payload.type().id().getNamespace());
            assertEquals("mailbox_send", payload.type().id().getPath());
        }
    }

    @Nested
    @DisplayName("MailboxActionPayload")
    class ActionPayloadTests {

        @Test
        @DisplayName("Create read action")
        void createReadAction() {
            MailboxActionPayload payload = MailboxActionPayload.read(TEST_UUID);

            assertEquals(MailboxActionPayload.Action.READ, payload.action());
            assertEquals(TEST_UUID, payload.messageId());
        }

        @Test
        @DisplayName("Create delete action")
        void createDeleteAction() {
            MailboxActionPayload payload = MailboxActionPayload.delete(TEST_UUID);

            assertEquals(MailboxActionPayload.Action.DELETE, payload.action());
            assertEquals(TEST_UUID, payload.messageId());
        }

        @Test
        @DisplayName("Create claim action")
        void createClaimAction() {
            MailboxActionPayload payload = MailboxActionPayload.claim(TEST_UUID);

            assertEquals(MailboxActionPayload.Action.CLAIM, payload.action());
            assertEquals(TEST_UUID, payload.messageId());
        }

        @Test
        @DisplayName("All actions have payload type")
        void allActionsHaveType() {
            for (MailboxActionPayload.Action action : MailboxActionPayload.Action.values()) {
                MailboxActionPayload payload = new MailboxActionPayload(action, TEST_UUID);
                assertNotNull(payload.type());
            }
        }
    }

    @Nested
    @DisplayName("MailboxNotifyPayload")
    class NotifyPayloadTests {

        @Test
        @DisplayName("Create notification payload")
        void createNotification() {
            MailboxNotifyPayload payload = new MailboxNotifyPayload(
                TEST_UUID,
                "SenderName",
                "New Message Subject",
                0, // PLAYER type
                true,
                5
            );

            assertEquals(TEST_UUID, payload.messageId());
            assertEquals("SenderName", payload.senderName());
            assertEquals("New Message Subject", payload.subject());
            assertEquals(0, payload.messageTypeOrdinal());
            assertTrue(payload.hasAttachment());
            assertEquals(5, payload.totalUnread());
        }

        @Test
        @DisplayName("getDisplaySender returns sender name")
        void getDisplaySenderReturnsSenderName() {
            MailboxNotifyPayload payload = new MailboxNotifyPayload(
                TEST_UUID, "TestSender", "Subject", 0, false, 1
            );

            assertEquals("TestSender", payload.getDisplaySender());
        }
    }

    @Nested
    @DisplayName("MailboxSyncPayload")
    class SyncPayloadTests {

        @Test
        @DisplayName("Create sync payload with messages")
        void createWithMessages() {
            List<MailboxSyncPayload.MailboxMessageData> messages = List.of(
                new MailboxSyncPayload.MailboxMessageData(
                    TEST_UUID, "Sender", "Subject", "Body",
                    0, System.currentTimeMillis(), false,
                    0, false, false, null
                )
            );

            MailboxSyncPayload payload = new MailboxSyncPayload(messages, 1, 100);

            assertEquals(1, payload.messages().size());
            assertEquals(1, payload.unreadCount());
            assertEquals(100, payload.maxMessages());
        }

        @Test
        @DisplayName("Empty sync payload is valid")
        void emptySyncPayloadValid() {
            MailboxSyncPayload payload = new MailboxSyncPayload(List.of(), 0, 100);

            assertTrue(payload.messages().isEmpty());
            assertEquals(0, payload.unreadCount());
        }
    }

    @Nested
    @DisplayName("TaskActionPayload")
    class TaskActionPayloadTests {

        @Test
        @DisplayName("Create update status action")
        void createUpdateStatus() {
            TaskActionPayload payload = TaskActionPayload.updateStatus(
                TEST_UUID, TestTask.TaskStatus.IN_PROGRESS
            );

            assertEquals(TaskActionPayload.Action.UPDATE_STATUS, payload.action());
            assertEquals(TEST_UUID, payload.taskId());
            assertEquals("in_progress", payload.statusId());
            assertNull(payload.notes());
        }

        @Test
        @DisplayName("Create add notes action")
        void createAddNotes() {
            TaskActionPayload payload = TaskActionPayload.addNotes(
                TEST_UUID, "Test notes here"
            );

            assertEquals(TaskActionPayload.Action.ADD_NOTES, payload.action());
            assertEquals(TEST_UUID, payload.taskId());
            assertNull(payload.statusId());
            assertEquals("Test notes here", payload.notes());
        }

        @Test
        @DisplayName("Payload type is correct")
        void payloadTypeCorrect() {
            TaskActionPayload payload = TaskActionPayload.updateStatus(
                TEST_UUID, TestTask.TaskStatus.COMPLETED
            );

            assertNotNull(payload.type());
            assertEquals("devmod", payload.type().id().getNamespace());
            assertEquals("task_action", payload.type().id().getPath());
        }
    }

    @Nested
    @DisplayName("TaskSyncPayload")
    class TaskSyncPayloadTests {

        @Test
        @DisplayName("Create sync payload from tasks")
        void createFromTasks() {
            UUID assigneeId = UUID.randomUUID();
            List<TestTask> tasks = List.of(
                TestTask.builder()
                    .id(TEST_UUID)
                    .title("Test Task 1")
                    .description("Description")
                    .assignedTo(assigneeId)
                    .assignedByName("Admin")
                    .priority(2)
                    .status(TestTask.TaskStatus.PENDING)
                    .createdAt(System.currentTimeMillis())
                    .build()
            );

            TaskSyncPayload payload = TaskSyncPayload.fromTasks(tasks);

            assertNotNull(payload);
            assertEquals(1, payload.tasks().size());
        }

        @Test
        @DisplayName("Convert payload back to tasks")
        void convertBackToTasks() {
            UUID assigneeId = UUID.randomUUID();
            List<TestTask> originalTasks = List.of(
                TestTask.builder()
                    .id(TEST_UUID)
                    .title("Test Task")
                    .description("Description")
                    .assignedTo(assigneeId)
                    .assignedByName("Admin")
                    .priority(2)
                    .status(TestTask.TaskStatus.IN_PROGRESS)
                    .createdAt(System.currentTimeMillis())
                    .build()
            );

            TaskSyncPayload payload = TaskSyncPayload.fromTasks(originalTasks);
            List<TestTask> convertedTasks = payload.toTasks(assigneeId);

            assertEquals(1, convertedTasks.size());
            assertEquals(TEST_UUID, convertedTasks.get(0).id());
            assertEquals("Test Task", convertedTasks.get(0).title());
            assertEquals(TestTask.TaskStatus.IN_PROGRESS, convertedTasks.get(0).status());
        }

        @Test
        @DisplayName("Payload type is correct")
        void payloadTypeCorrect() {
            TaskSyncPayload payload = TaskSyncPayload.fromTasks(List.of());

            assertNotNull(payload.type());
            assertEquals("devmod", payload.type().id().getNamespace());
            assertEquals("task_sync", payload.type().id().getPath());
        }
    }
}
