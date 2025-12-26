package com.devmod.arena.event;

import java.time.Instant;
import java.util.UUID;

import javax.annotation.Nullable;

import com.devmod.arena.registry.TemplateType;

public sealed interface TemplateEvent
    permits TemplateEvent.TemplateRegistered,
            TemplateEvent.TemplateUnregistered,
            TemplateEvent.BuildStarted,
            TemplateEvent.BuildCompleted,
            TemplateEvent.BuildFailed,
            TemplateEvent.BuildCancelled {

    /**
     * Returns the template ID associated with this event.
     */
    String templateId();

    /**
     * Returns the timestamp when this event occurred.
     */
    Instant timestamp();

    /**
     * Returns the event type discriminator.
     */
    EventType eventType();

    /**
     * Event type discriminator for serialization and filtering.
     */
    enum EventType {
        TEMPLATE_REGISTERED("template.registered"),
        TEMPLATE_UNREGISTERED("template.unregistered"),
        BUILD_STARTED("build.started"),
        BUILD_COMPLETED("build.completed"),
        BUILD_FAILED("build.failed"),
        BUILD_CANCELLED("build.cancelled");

        private final String value;

        EventType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    // ========== Registry Events ==========

    /**
     * DD13: Emitted when a template is registered in the registry.
     * DD1-DD2: Includes template type discriminator for filtering by variant.
     */
    record TemplateRegistered(
        String templateId,
        int version,
        String source,
        @Nullable TemplateType.TypeDiscriminator templateType,
        Instant timestamp
    ) implements TemplateEvent {

        public TemplateRegistered {
            if (templateId == null || templateId.isBlank()) {
                throw new IllegalArgumentException("templateId is required");
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }

        /**
         * Creates event without template type (backwards compatible).
         */
        public static TemplateRegistered now(String templateId, int version, String source) {
            return new TemplateRegistered(templateId, version, source, null, Instant.now());
        }

        /**
         * Creates event with template type discriminator.
         */
        public static TemplateRegistered now(String templateId, int version, String source, TemplateType templateType) {
            TemplateType.TypeDiscriminator discriminator = templateType != null ? templateType.discriminator() : null;
            return new TemplateRegistered(templateId, version, source, discriminator, Instant.now());
        }

        @Override
        public EventType eventType() {
            return EventType.TEMPLATE_REGISTERED;
        }
    }

    /**
     * DD13: Emitted when a template is unregistered from the registry.
     */
    record TemplateUnregistered(
        String templateId,
        String reason,
        Instant timestamp
    ) implements TemplateEvent {

        public TemplateUnregistered {
            if (templateId == null || templateId.isBlank()) {
                throw new IllegalArgumentException("templateId is required");
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }

        public static TemplateUnregistered now(String templateId, String reason) {
            return new TemplateUnregistered(templateId, reason, Instant.now());
        }

        @Override
        public EventType eventType() {
            return EventType.TEMPLATE_UNREGISTERED;
        }
    }

    // ========== Build Events ==========

    /**
     * DD13: Emitted when an arena build starts.
     * DD1-DD2: Includes template type discriminator.
     */
    record BuildStarted(
        String templateId,
        UUID arenaId,
        UUID requestedBy,
        int estimatedBlocks,
        @Nullable TemplateType.TypeDiscriminator templateType,
        Instant timestamp
    ) implements TemplateEvent {

        public BuildStarted {
            if (templateId == null || templateId.isBlank()) {
                throw new IllegalArgumentException("templateId is required");
            }
            if (arenaId == null) {
                throw new IllegalArgumentException("arenaId is required");
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }

        /**
         * Creates event without template type (backwards compatible).
         */
        public static BuildStarted now(String templateId, UUID arenaId, UUID requestedBy, int estimatedBlocks) {
            return new BuildStarted(templateId, arenaId, requestedBy, estimatedBlocks, null, Instant.now());
        }

        /**
         * Creates event with template type discriminator.
         */
        public static BuildStarted now(String templateId, UUID arenaId, UUID requestedBy, int estimatedBlocks, TemplateType templateType) {
            TemplateType.TypeDiscriminator discriminator = templateType != null ? templateType.discriminator() : null;
            return new BuildStarted(templateId, arenaId, requestedBy, estimatedBlocks, discriminator, Instant.now());
        }

        @Override
        public EventType eventType() {
            return EventType.BUILD_STARTED;
        }
    }

    /**
     * DD13: Emitted when an arena build completes successfully.
     */
    record BuildCompleted(
        String templateId,
        UUID arenaId,
        int blocksPlaced,
        int entitiesSpawned,
        long durationMs,
        Instant timestamp
    ) implements TemplateEvent {

        public BuildCompleted {
            if (templateId == null || templateId.isBlank()) {
                throw new IllegalArgumentException("templateId is required");
            }
            if (arenaId == null) {
                throw new IllegalArgumentException("arenaId is required");
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }

        public static BuildCompleted now(String templateId, UUID arenaId, int blocksPlaced, int entitiesSpawned, long durationMs) {
            return new BuildCompleted(templateId, arenaId, blocksPlaced, entitiesSpawned, durationMs, Instant.now());
        }

        @Override
        public EventType eventType() {
            return EventType.BUILD_COMPLETED;
        }
    }

    /**
     * DD13: Emitted when an arena build fails.
     */
    record BuildFailed(
        String templateId,
        UUID arenaId,
        String reason,
        String exceptionType,
        long durationMs,
        boolean rollbackSucceeded,
        Instant timestamp
    ) implements TemplateEvent {

        public BuildFailed {
            if (templateId == null || templateId.isBlank()) {
                throw new IllegalArgumentException("templateId is required");
            }
            if (arenaId == null) {
                throw new IllegalArgumentException("arenaId is required");
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }

        public static BuildFailed now(String templateId, UUID arenaId, String reason, Throwable exception, long durationMs, boolean rollbackSucceeded) {
            String exceptionType = exception != null ? exception.getClass().getSimpleName() : "Unknown";
            return new BuildFailed(templateId, arenaId, reason, exceptionType, durationMs, rollbackSucceeded, Instant.now());
        }

        @Override
        public EventType eventType() {
            return EventType.BUILD_FAILED;
        }
    }

    /**
     * DD13: Emitted when an arena build is cancelled.
     */
    record BuildCancelled(
        String templateId,
        UUID arenaId,
        UUID cancelledBy,
        String reason,
        int blocksPlacedBeforeCancel,
        long durationMs,
        Instant timestamp
    ) implements TemplateEvent {

        public BuildCancelled {
            if (templateId == null || templateId.isBlank()) {
                throw new IllegalArgumentException("templateId is required");
            }
            if (arenaId == null) {
                throw new IllegalArgumentException("arenaId is required");
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }

        public static BuildCancelled now(String templateId, UUID arenaId, UUID cancelledBy, String reason, int blocksPlaced, long durationMs) {
            return new BuildCancelled(templateId, arenaId, cancelledBy, reason, blocksPlaced, durationMs, Instant.now());
        }

        @Override
        public EventType eventType() {
            return EventType.BUILD_CANCELLED;
        }
    }
}
