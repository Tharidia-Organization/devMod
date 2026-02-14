package com.devmod.util;

import java.util.Locale;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;
import org.slf4j.LoggerFactory;

/**
 * Log filter to suppress GeckoLib refmap warnings in dev client runs.
 */
public final class GeckoLibRefmapFilter extends AbstractFilter {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(GeckoLibRefmapFilter.class);
    private static final String REF_MAP_TOKEN = "geckolib.refmap.json";
    private static volatile boolean installed = false;

    private GeckoLibRefmapFilter() {
        super(Result.NEUTRAL, Result.DENY);
    }

    @Override
    public Result filter(LogEvent event) {
        if (event == null || event.getMessage() == null) {
            return Result.NEUTRAL;
        }
        if (!Level.WARN.equals(event.getLevel())) {
            return Result.NEUTRAL;
        }
        return shouldFilter(event.getLoggerName(), event.getMessage().getFormattedMessage())
            ? Result.DENY
            : Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        if (!Level.WARN.equals(level) || msg == null) {
            return Result.NEUTRAL;
        }
        String loggerName = logger != null ? logger.getName() : "";
        return shouldFilter(loggerName, msg.getFormattedMessage())
            ? Result.DENY
            : Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        if (!Level.WARN.equals(level) || msg == null) {
            return Result.NEUTRAL;
        }
        String loggerName = logger != null ? logger.getName() : "";
        return shouldFilter(loggerName, msg)
            ? Result.DENY
            : Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        if (!Level.WARN.equals(level) || msg == null) {
            return Result.NEUTRAL;
        }
        String loggerName = logger != null ? logger.getName() : "";
        return shouldFilter(loggerName, msg.toString())
            ? Result.DENY
            : Result.NEUTRAL;
    }

    private static boolean shouldFilter(String loggerName, String message) {
        if (loggerName == null || message == null) {
            return false;
        }
        if (!loggerName.toLowerCase(Locale.ROOT).contains("mixin")) {
            return false;
        }
        return message.contains(REF_MAP_TOKEN);
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            GeckoLibRefmapFilter filter = new GeckoLibRefmapFilter();

            org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();
            config.getRootLogger().addFilter(filter);
            for (Logger logger : context.getLoggers()) {
                logger.addFilter(filter);
            }

            context.updateLoggers();
            installed = true;
            LOGGER.debug("[DevMod] GeckoLib refmap log filter installed");
        } catch (Exception e) {
            LOGGER.warn("[DevMod] Failed to install GeckoLib refmap filter: {}", e.getMessage());
        }
    }
}
