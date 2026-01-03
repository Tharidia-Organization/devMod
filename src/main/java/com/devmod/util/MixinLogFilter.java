package com.devmod.util;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

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
 * Log filter that suppresses client-side mixin warnings on dedicated servers.
 *
 * Many mods include mixins that target client-only classes. On dedicated servers,
 * these mixins cannot be applied because the target classes don't exist.
 * This creates noisy warnings that are expected and harmless.
 *
 * This filter reduces log noise by filtering out these specific warnings.
 */
public final class MixinLogFilter extends AbstractFilter {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MixinLogFilter.class);

    private static boolean installed = false;
    private static volatile MixinLogFilter installedFilter;
    private final AtomicInteger filteredCount = new AtomicInteger();

    // Patterns to match mixin client-side warnings
    private static final Pattern MIXIN_TARGET_PATTERN = Pattern.compile(
        ".*(?:Target class|Mixin target|Cannot find target).*(?:client|Client|render|Render|Screen|Gui|HUD).*",
        Pattern.CASE_INSENSITIVE
    );

    // Known mod prefixes that have client-only mixins
    private static final Set<String> KNOWN_CLIENT_MIXIN_MODS = Set.of(
        "scholar",
        "shield_api",
        "block_factorys_bosses",
        "fdlib",
        "cold_sweat",
        "fetzisdisplays",
        "platform",
        "create",
        "jei",
        "jade",
        "curios"
    );

    // Messages containing these substrings are likely client-side mixin issues
    private static final String[] CLIENT_MIXIN_INDICATORS = {
        "net.minecraft.client",
        "net/minecraft/client",
        "ClientLevel",
        "LocalPlayer",
        "AbstractClientPlayer",
        "GuiGraphics",
        "RenderSystem",
        "PoseStack",
        "BufferBuilder",
        "VertexConsumer",
        "Screen",
        "ItemRenderer",
        "EntityRenderer",
        "LevelRenderer",
        "GameRenderer",
        "Minecraft",
        "KeyMapping"
    };

    private MixinLogFilter() {
        super(Result.NEUTRAL, Result.DENY);
    }

    @Override
    public Result filter(LogEvent event) {
        if (event == null || event.getMessage() == null) {
            return Result.NEUTRAL;
        }

        // Only filter WARN level messages
        if (!Level.WARN.equals(event.getLevel())) {
            return Result.NEUTRAL;
        }

        String loggerName = event.getLoggerName();
        String message = event.getMessage().getFormattedMessage();

        if (shouldFilter(loggerName, message)) {
            filteredCount.incrementAndGet();
            return Result.DENY;
        }

        return Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        if (!Level.WARN.equals(level) || msg == null) {
            return Result.NEUTRAL;
        }

        String loggerName = logger != null ? logger.getName() : "";
        String message = msg.getFormattedMessage();

        if (shouldFilter(loggerName, message)) {
            filteredCount.incrementAndGet();
            return Result.DENY;
        }

        return Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        if (!Level.WARN.equals(level) || msg == null) {
            return Result.NEUTRAL;
        }

        String loggerName = logger != null ? logger.getName() : "";

        if (shouldFilter(loggerName, msg)) {
            filteredCount.incrementAndGet();
            return Result.DENY;
        }

        return Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        if (!Level.WARN.equals(level) || msg == null) {
            return Result.NEUTRAL;
        }

        String loggerName = logger != null ? logger.getName() : "";
        String message = msg.toString();

        if (shouldFilter(loggerName, message)) {
            filteredCount.incrementAndGet();
            return Result.DENY;
        }

        return Result.NEUTRAL;
    }

    /**
     * Check if a log message should be filtered.
     */
    private boolean shouldFilter(String loggerName, String message) {
        if (loggerName == null || message == null) {
            return false;
        }

        // Check if from mixin system
        if (!loggerName.toLowerCase(Locale.ROOT).contains("mixin")
            && !message.toLowerCase(Locale.ROOT).contains("mixin")) {
            return false;
        }

        // Check for known client-side indicators
        for (String indicator : CLIENT_MIXIN_INDICATORS) {
            if (message.contains(indicator)) {
                return true;
            }
        }

        // Check regex pattern
        if (MIXIN_TARGET_PATTERN.matcher(message).matches()) {
            return true;
        }

        // Check for known mods with client mixins
        String lowerMessage = message.toLowerCase(Locale.ROOT);
        for (String mod : KNOWN_CLIENT_MIXIN_MODS) {
            if (lowerMessage.contains(mod) &&
                (lowerMessage.contains("target") || lowerMessage.contains("cannot"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Install the mixin log filter.
     * Should be called early during server startup.
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }

        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            MixinLogFilter filter = new MixinLogFilter();

            // Add filter to root logger configuration
            org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();
            config.getRootLogger().addFilter(filter);

            // Also add to all existing loggers
            for (Logger logger : context.getLoggers()) {
                logger.addFilter(filter);
            }

            context.updateLoggers();

            installedFilter = filter;
            installed = true;
            LOGGER.info("[DevMod] Mixin log filter installed - client-side mixin warnings will be suppressed");

        } catch (Exception e) {
            LOGGER.warn("[DevMod] Failed to install mixin log filter: {}", e.getMessage());
        }
    }

    /**
     * Get count of filtered messages.
     */
    public static int getFilteredCount() {
        MixinLogFilter filter = installedFilter;
        return filter != null ? filter.filteredCount.get() : 0;
    }

    /**
     * Check if filter is installed.
     */
    public static boolean isInstalled() {
        return installed;
    }

    /**
     * Log a summary of filtered messages.
     * Call this periodically or at shutdown.
     */
    public static void logSummary() {
        MixinLogFilter filter = installedFilter;
        if (filter != null) {
            int count = filter.filteredCount.get();
            if (count > 0) {
                LOGGER.info("[DevMod] Mixin log filter suppressed {} client-side mixin warnings", count);
            }
        }
    }
}
