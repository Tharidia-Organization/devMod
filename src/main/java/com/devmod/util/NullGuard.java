package com.devmod.util;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FIX AUDIT #6: Utility class for null checks with diagnostic logging.
 *
 * <p>Provides alternatives to {@link java.util.Objects#requireNonNull} that
 * log contextual information when null is encountered, making debugging easier.
 *
 * <h2>FIX AUDIT #10: Usage Policy - NullGuard vs Objects.requireNonNull</h2>
 *
 * <p><b>Use {@code NullGuard.require()} when:</b>
 * <ul>
 *   <li>Null indicates a <b>bug</b> that needs debugging context</li>
 *   <li>The null comes from <b>external data</b> (network, config, user input)</li>
 *   <li>The null occurs in <b>complex flows</b> where stack trace alone is insufficient</li>
 *   <li>You need to know <b>which field</b> was null and in <b>what context</b></li>
 * </ul>
 *
 * <p><b>Use {@code Objects.requireNonNull()} when:</b>
 * <ul>
 *   <li>Null check is a <b>defensive guard</b> that should never trigger</li>
 *   <li>Satisfying <b>IDE static analysis</b> warnings</li>
 *   <li>The context is <b>obvious</b> from the stack trace (e.g., constructor args)</li>
 *   <li><b>Performance-critical</b> hot paths where logging overhead matters</li>
 * </ul>
 *
 * <p><b>Use {@code NullGuard.orDefault()} when:</b>
 * <ul>
 *   <li>Null is <b>recoverable</b> but should be logged for investigation</li>
 *   <li>You want to <b>track</b> how often fallbacks occur</li>
 * </ul>
 *
 * <p><b>Use {@code NullGuard.orDefaultSilent()} when:</b>
 * <ul>
 *   <li>Null is a <b>valid/expected</b> case (not a bug)</li>
 *   <li>Equivalent to {@code value != null ? value : defaultValue}</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * // Instead of: Objects.requireNonNull(value)
 * // Use: NullGuard.require(value, "PlayerSession", "memberId")
 * </pre>
 *
 * <h2>FIX AUDIT2 #5: Enforcement Policy</h2>
 * <p>This policy is enforced through <b>code review</b>, not automated tooling.
 * Reviewers should verify:
 * <ul>
 *   <li>Network payload parsing uses {@code NullGuard.require()} for contextual debugging</li>
 *   <li>Hot paths (render loops, tick handlers) use {@code Objects.requireNonNull()}</li>
 *   <li>Optional fallbacks use {@code orDefault()} with logging for investigation</li>
 *   <li>Expected null cases use {@code orDefaultSilent()} without logging</li>
 * </ul>
 *
 * @author DevMod Team
 */
public final class NullGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(NullGuard.class);

    private NullGuard() {
        // Utility class
    }

    /**
     * Require non-null with context logging.
     *
     * @param value the value to check
     * @param context what operation was happening (e.g., "PartyStatsSync")
     * @param fieldName the name of the field/variable that was null
     * @param <T> the type of the value
     * @return the value if non-null
     * @throws NullPointerException if value is null (after logging)
     */
    public static <T> T require(@Nullable T value, String context, String fieldName) {
        if (value == null) {
            String msg = String.format("[NullGuard] Unexpected null in %s: %s was null", context, fieldName);
            LOGGER.error(msg);
            // Log stack trace at debug level for more context
            LOGGER.debug("[NullGuard] Stack trace for null {}:", fieldName, new Exception("Null trace"));
            throw new NullPointerException(msg);
        }
        return value;
    }

    /**
     * Require non-null with context and additional info.
     *
     * @param value the value to check
     * @param context what operation was happening
     * @param fieldName the name of the field
     * @param extraInfo additional diagnostic info (e.g., player name, wave number)
     * @param <T> the type of the value
     * @return the value if non-null
     * @throws NullPointerException if value is null
     */
    public static <T> T require(@Nullable T value, String context, String fieldName, String extraInfo) {
        if (value == null) {
            String msg = String.format("[NullGuard] Unexpected null in %s: %s was null [%s]",
                context, fieldName, extraInfo);
            LOGGER.error(msg);
            LOGGER.debug("[NullGuard] Stack trace for null {}:", fieldName, new Exception("Null trace"));
            throw new NullPointerException(msg);
        }
        return value;
    }

    /**
     * Check for null and return default if null, with warning log.
     *
     * @param value the value to check
     * @param defaultValue the default to return if null
     * @param context what operation was happening
     * @param fieldName the name of the field
     * @param <T> the type of the value
     * @return the value if non-null, otherwise defaultValue
     */
    public static <T> T orDefault(@Nullable T value, T defaultValue, String context, String fieldName) {
        if (value == null) {
            LOGGER.warn("[NullGuard] Null fallback in {}: {} was null, using default",
                context, fieldName);
            return defaultValue;
        }
        return value;
    }

    /**
     * Check for null silently and return default (no logging).
     * Use when null is a valid/expected case.
     *
     * <p><b>FIX AUDIT2 #9: Why not Objects.requireNonNullElse?</b>
     * <ul>
     *   <li>{@code requireNonNullElse} throws NPE if defaultValue is null</li>
     *   <li>{@code orDefaultSilent} allows nullable defaultValue</li>
     *   <li>Use this when the default itself could be computed/nullable</li>
     * </ul>
     *
     * @param value the value to check
     * @param defaultValue the default to return if null (may itself be null)
     * @param <T> the type of the value
     * @return the value if non-null, otherwise defaultValue
     */
    public static <T> T orDefaultSilent(@Nullable T value, @Nullable T defaultValue) {
        return value != null ? value : defaultValue;
    }
}
