package com.devmod.mailbox.template;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.devmod.mailbox.MessageType;
import com.devmod.mailbox.template.MessageTemplateRegistry.MessageSound;
import com.devmod.mailbox.template.MessageTemplateRegistry.TemplateCategory;

/**
 * Represents a message template.
 *
 * Templates provide reusable message formats with placeholder support.
 * Placeholders use the {key} syntax and are substituted at send time.
 */
public record MessageTemplate(
    String id,
    TemplateCategory category,
    String subject,
    String body,
    MessageType type,
    @Nullable MessageSound sendSound,
    @Nullable MessageSound receiveSound,
    @Nullable String customSendSound,
    @Nullable String customReceiveSound,
    @Nullable Duration expiresAfter,
    List<String> requiredPlaceholders,
    List<String> optionalPlaceholders,
    @Nullable String description,
    boolean isSystem,
    Instant createdAt,
    @Nullable Instant modifiedAt
) {
    /**
     * Create a new builder for a template.
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Get all placeholders (required + optional).
     */
    public List<String> getAllPlaceholders() {
        List<String> all = new ArrayList<>();
        all.addAll(requiredPlaceholders);
        all.addAll(optionalPlaceholders);
        return all;
    }

    /**
     * Check if this template has a send sound.
     */
    public boolean hasSendSound() {
        return sendSound != null && sendSound != MessageSound.NONE;
    }

    /**
     * Check if this template has a receive sound.
     */
    public boolean hasReceiveSound() {
        return receiveSound != null && receiveSound != MessageSound.NONE;
    }

    /**
     * Get the actual Minecraft sound ID for sending.
     */
    @Nullable
    public String getSendSoundId() {
        if (sendSound == MessageSound.CUSTOM) {
            return customSendSound;
        }
        return sendSound != null ? sendSound.getMinecraftSound() : null;
    }

    /**
     * Get the actual Minecraft sound ID for receiving.
     */
    @Nullable
    public String getReceiveSoundId() {
        if (receiveSound == MessageSound.CUSTOM) {
            return customReceiveSound;
        }
        return receiveSound != null ? receiveSound.getMinecraftSound() : null;
    }

    /**
     * Builder for MessageTemplate.
     */
    public static final class Builder {
        private final String id;
        private TemplateCategory category = TemplateCategory.CUSTOM;
        private String subject = "";
        private String body = "";
        private MessageType type = MessageType.SYSTEM;
        @Nullable
        private MessageSound sendSound;
        @Nullable
        private MessageSound receiveSound;
        @Nullable
        private String customSendSound;
        @Nullable
        private String customReceiveSound;
        @Nullable
        private Duration expiresAfter;
        private List<String> requiredPlaceholders = Collections.emptyList();
        private List<String> optionalPlaceholders = Collections.emptyList();
        @Nullable
        private String description;
        private boolean isSystem = false;

        Builder(String id) {
            this.id = Objects.requireNonNull(id, "Template ID cannot be null");
        }

        public Builder category(TemplateCategory category) {
            this.category = Objects.requireNonNull(category);
            return this;
        }

        public Builder subject(String subject) {
            this.subject = Objects.requireNonNull(subject);
            return this;
        }

        public Builder body(String body) {
            this.body = Objects.requireNonNull(body);
            return this;
        }

        public Builder type(MessageType type) {
            this.type = Objects.requireNonNull(type);
            return this;
        }

        public Builder sendSound(MessageSound sound) {
            this.sendSound = sound;
            return this;
        }

        public Builder receiveSound(MessageSound sound) {
            this.receiveSound = sound;
            return this;
        }

        public Builder customSendSound(String soundId) {
            this.sendSound = MessageSound.CUSTOM;
            this.customSendSound = soundId;
            return this;
        }

        public Builder customReceiveSound(String soundId) {
            this.receiveSound = MessageSound.CUSTOM;
            this.customReceiveSound = soundId;
            return this;
        }

        public Builder expiresAfter(Duration duration) {
            this.expiresAfter = duration;
            return this;
        }

        public Builder requiredPlaceholders(String... placeholders) {
            this.requiredPlaceholders = List.of(placeholders);
            return this;
        }

        public Builder requiredPlaceholders(List<String> placeholders) {
            this.requiredPlaceholders = List.copyOf(placeholders);
            return this;
        }

        public Builder optionalPlaceholders(String... placeholders) {
            this.optionalPlaceholders = List.of(placeholders);
            return this;
        }

        public Builder optionalPlaceholders(List<String> placeholders) {
            this.optionalPlaceholders = List.copyOf(placeholders);
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder system(boolean isSystem) {
            this.isSystem = isSystem;
            return this;
        }

        public MessageTemplate build() {
            // Auto-extract placeholders from subject and body if not specified
            List<String> allPlaceholders = new ArrayList<>();
            allPlaceholders.addAll(MessageTemplateRegistry.extractPlaceholders(subject));
            allPlaceholders.addAll(MessageTemplateRegistry.extractPlaceholders(body));

            // Remove duplicates
            List<String> uniquePlaceholders = allPlaceholders.stream().distinct().toList();

            // If required placeholders not specified, consider all as optional
            List<String> required = requiredPlaceholders.isEmpty()
                ? Collections.emptyList()
                : requiredPlaceholders;

            List<String> optional = optionalPlaceholders.isEmpty()
                ? uniquePlaceholders.stream()
                    .filter(p -> !required.contains(p))
                    .toList()
                : optionalPlaceholders;

            return new MessageTemplate(
                id,
                category,
                subject,
                body,
                type,
                sendSound,
                receiveSound,
                customSendSound,
                customReceiveSound,
                expiresAfter,
                required,
                optional,
                description,
                isSystem,
                Instant.now(),
                null
            );
        }
    }
}
