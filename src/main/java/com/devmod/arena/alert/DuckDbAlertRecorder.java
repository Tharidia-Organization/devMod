package com.devmod.arena.alert;

import com.devmod.arena.persistence.DuckDbRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists alert delivery history into DuckDB.
 */
public class DuckDbAlertRecorder implements AlertRouter.AlertDeliveryRecorder {

    private static final Logger LOGGER = Logger.getLogger(DuckDbAlertRecorder.class.getName());

    private final DuckDbRepository repository;
    private final Set<UUID> recordedErrors = ConcurrentHashMap.newKeySet();

    public DuckDbAlertRecorder(DuckDbRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public void record(ErrorContext context,
                       AlertRouter.AlertChannel channel,
                       AlertRouter.DeliveryStatus status,
                       int attemptCount,
                       Instant nextRetryAt,
                       String errorMessage) {
        if (context == null || channel == null || status == null) {
            return;
        }
        synchronized (repository) {
            try {
                if (recordedErrors.add(context.errorId())) {
                    repository.recordError(context);
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to record error context for " + context.errorId(), e);
            }

            try {
                UUID alertId = repository.computeAlertId(context.errorId(), channel.getId());
                repository.recordAlertDelivery(
                    alertId,
                    context.errorId(),
                    channel.getId(),
                    channel.getType(),
                    channel.isCritical(),
                    status.name().toLowerCase(),
                    attemptCount,
                    AlertRouter.MAX_RETRY_ATTEMPTS,
                    Instant.now(),
                    nextRetryAt,
                    errorMessage
                );
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to record alert delivery for " + context.errorId(), e);
            }
        }
    }
}
