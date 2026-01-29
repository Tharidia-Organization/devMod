package com.devmod.mailbox.network.payload;

import javax.annotation.Nullable;

/**
 * Shared limits and helpers for mailbox-related payloads.
 */
final class MailboxPayloadLimits {
    private MailboxPayloadLimits() {
    }

    static final int MAX_MESSAGES = 200;
    static final int MAX_MESSAGE_SUBJECT_LENGTH = 256;
    static final int MAX_MESSAGE_BODY_LENGTH = 2000;
    static final int MAX_MESSAGE_NAME_LENGTH = 64;
    static final int MAX_MESSAGE_ATTACHMENT_LENGTH = 4096;

    static final int MAX_NOTIFY_SUBJECT_LENGTH = 128;
    static final int MAX_STATUS_MESSAGE_LENGTH = 256;

    static final int MAX_NEWS_ARTICLES = 100;
    static final int MAX_NEWS_TITLE_LENGTH = 256;
    static final int MAX_NEWS_CONTENT_LENGTH = 10000;
    static final int MAX_NEWS_AUTHOR_LENGTH = 64;

    static final int MAX_TASKS = 100;
    static final int MAX_TASK_TITLE_LENGTH = 256;
    static final int MAX_TASK_DESC_LENGTH = 2000;
    static final int MAX_TASK_NOTES_LENGTH = 1000;
    static final int MAX_TASK_NAME_LENGTH = 64;
    static final int MAX_TASK_STATUS_ID_LENGTH = 32;

    static final int MAX_TICKETS = 100;
    static final int MAX_TICKET_SUBJECT_LENGTH = 256;
    static final int MAX_TICKET_DESCRIPTION_LENGTH = 4000;
    static final int MAX_TICKET_STATUS_ID_LENGTH = 32;
    static final int MAX_TICKET_COMMENT_LENGTH = 1000;

    static int clampCount(int count, int max) {
        if (count <= 0) {
            return 0;
        }
        return Math.min(count, max);
    }

    static String truncate(@Nullable String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @Nullable
    static String truncateNullable(@Nullable String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
