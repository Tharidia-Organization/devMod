package com.devmod.mailbox.network.payload;

import javax.annotation.Nullable;

import com.devmod.network.NetworkConstants;

/**
 * Shared limits and helpers for mailbox-related payloads.
 * References {@link NetworkConstants} for standard tiers.
 */
final class MailboxPayloadLimits {
    private MailboxPayloadLimits() {
    }

    // Message limits - referencing NetworkConstants tiers
    static final int MAX_MESSAGES = NetworkConstants.MAX_XLARGE_ARRAY; // 200
    static final int MAX_MESSAGE_SUBJECT_LENGTH = NetworkConstants.MAX_MEDIUM_STRING; // 256
    static final int MAX_MESSAGE_BODY_LENGTH = 2000; // Domain-specific (between LARGE and XLARGE)
    static final int MAX_MESSAGE_NAME_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH; // 64
    static final int MAX_MESSAGE_ATTACHMENT_LENGTH = NetworkConstants.MAX_XLARGE_STRING; // 4096

    // Notification limits
    static final int MAX_NOTIFY_SUBJECT_LENGTH = NetworkConstants.MAX_TITLE_LENGTH; // 128
    static final int MAX_STATUS_MESSAGE_LENGTH = NetworkConstants.MAX_MEDIUM_STRING; // 256

    // News limits
    static final int MAX_NEWS_ARTICLES = NetworkConstants.MAX_LARGE_ARRAY; // 100
    static final int MAX_NEWS_TITLE_LENGTH = NetworkConstants.MAX_MEDIUM_STRING; // 256
    static final int MAX_NEWS_CONTENT_LENGTH = 10000; // Domain-specific (very large content)
    static final int MAX_NEWS_AUTHOR_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH; // 64

    // Task limits
    static final int MAX_TASKS = NetworkConstants.MAX_LARGE_ARRAY; // 100
    static final int MAX_TASK_TITLE_LENGTH = NetworkConstants.MAX_MEDIUM_STRING; // 256
    static final int MAX_TASK_DESC_LENGTH = 2000; // Domain-specific
    static final int MAX_TASK_NOTES_LENGTH = NetworkConstants.MAX_LARGE_STRING; // 1024 (close match)
    static final int MAX_TASK_NAME_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH; // 64
    static final int MAX_TASK_STATUS_ID_LENGTH = NetworkConstants.MAX_SMALL_STRING; // 32

    // Ticket limits
    static final int MAX_TICKETS = NetworkConstants.MAX_LARGE_ARRAY; // 100
    static final int MAX_TICKET_SUBJECT_LENGTH = NetworkConstants.MAX_MEDIUM_STRING; // 256
    static final int MAX_TICKET_DESCRIPTION_LENGTH = NetworkConstants.MAX_XLARGE_STRING; // 4096 (close match)
    static final int MAX_TICKET_STATUS_ID_LENGTH = NetworkConstants.MAX_SMALL_STRING; // 32
    static final int MAX_TICKET_COMMENT_LENGTH = NetworkConstants.MAX_LARGE_STRING; // 1024 (close match)

    // Utility methods - delegate to NetworkConstants
    static int clampCount(int count, int max) {
        return NetworkConstants.clampCount(count, max);
    }

    static String truncate(@Nullable String value, int maxLength) {
        return NetworkConstants.truncate(value, maxLength);
    }

    @Nullable
    static String truncateNullable(@Nullable String value, int maxLength) {
        return NetworkConstants.truncateNullable(value, maxLength);
    }
}
