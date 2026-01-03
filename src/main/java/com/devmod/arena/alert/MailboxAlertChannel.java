package com.devmod.arena.alert;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.template.MessageTemplateRegistry;

/**
 * Alert channel that sends critical alerts to admin mailboxes.
 *
 * Integrates the arena alert system with the mailbox notification system
 * so admins receive persistent notifications for critical errors.
 */
public class MailboxAlertChannel implements AlertRouter.AlertChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxAlertChannel.class);

    public static final String CHANNEL_ID = "mailbox";

    /** System UUID used for admin notifications */
    private static final UUID ADMIN_SYSTEM_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public String getId() {
        return CHANNEL_ID;
    }

    @Override
    public String getType() {
        return "Mailbox";
    }

    @Override
    public boolean isCritical() {
        // Mailbox alerts are not critical for retry purposes
        // (we don't want to block on mailbox failures)
        return false;
    }

    @Override
    public boolean deliver(ErrorContext context) {
        try {
            String severity = context.severity() != null ? context.severity().name() : "UNKNOWN";
            String errorType = context.errorType() != null ? context.errorType() : "System Alert";
            String message = context.message() != null ? context.message() : "No message provided";

            // Build details string
            StringBuilder details = new StringBuilder();
            details.append("Error Type: ").append(errorType).append("\n");
            details.append("Severity: ").append(severity).append("\n");
            if (context.component() != null) {
                details.append("Component: ").append(context.component()).append("\n");
            }
            // Extract metadata fields if present
            Map<String, Object> metadata = context.metadata();
            if (metadata != null && !metadata.isEmpty()) {
                if (metadata.containsKey("templateId")) {
                    details.append("Template: ").append(metadata.get("templateId")).append("\n");
                }
                if (metadata.containsKey("playerId")) {
                    details.append("Player: ").append(metadata.get("playerId")).append("\n");
                }
                if (metadata.containsKey("arenaId")) {
                    details.append("Arena: ").append(metadata.get("arenaId")).append("\n");
                }
            }
            details.append("\nMessage:\n").append(message);

            // Format stack frames if present
            var stackFrames = context.stackFrames();
            if (stackFrames != null && !stackFrames.isEmpty()) {
                details.append("\n\nStack Trace:\n");
                int frameCount = Math.min(stackFrames.size(), 10); // Limit to 10 frames for mailbox
                for (int i = 0; i < frameCount; i++) {
                    var frame = stackFrames.get(i);
                    details.append("  at ")
                           .append(frame.className())
                           .append(".")
                           .append(frame.methodName())
                           .append("(")
                           .append(frame.fileName() != null ? frame.fileName() : "Unknown")
                           .append(":")
                           .append(frame.lineNumber())
                           .append(")\n");
                }
                if (stackFrames.size() > 10) {
                    details.append("  ... ").append(stackFrames.size() - 10).append(" more\n");
                }
            }

            // Send to admin mailbox using system template
            MessageTemplateRegistry.INSTANCE.sendFromTemplate(
                    "system.admin_alert",
                    ADMIN_SYSTEM_UUID,
                    Map.of(
                            "alert_type", errorType,
                            "severity", severity,
                            "message", message,
                            "details", details.toString()
                    ),
                    null
            ).exceptionally(ex -> {
                LOGGER.warn("[MailboxAlert] Failed to deliver admin alert template", ex);
                return Optional.empty();
            });

            LOGGER.debug("[MailboxAlert] Sent alert to admin mailbox: {}", errorType);
            return true;

        } catch (Exception e) {
            LOGGER.error("[MailboxAlert] Failed to send alert to mailbox", e);
            return false;
        }
    }
}
