package com.devmod.arena.alert;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class LogAlertChannel implements AlertRouter.AlertChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger("arena.alerts");

    private final String id;
    private final boolean critical;

    public LogAlertChannel() {
        this("log", true);
    }

    public LogAlertChannel(String id, boolean critical) {
        this.id = Objects.requireNonNull(id, "id");
        this.critical = critical;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isCritical() {
        return critical;
    }

    @Override
    public boolean deliver(ErrorContext context) {
        if (context == null) {
            return false;
        }

        String message = context.message() != null ? context.message() : "";
        switch (context.severity()) {
            case CRITICAL, ERROR -> LOGGER.error("[{}] {}", context.component(), message, context);
            case WARNING -> LOGGER.warn("[{}] {}", context.component(), message);
            case INFO -> LOGGER.info("[{}] {}", context.component(), message);
            default -> LOGGER.debug("[{}] {}", context.component(), message);
        }
        return true;
    }
}
