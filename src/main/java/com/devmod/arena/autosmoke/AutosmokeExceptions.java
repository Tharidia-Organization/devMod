package com.devmod.arena.autosmoke;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AutosmokeExceptions {

    /**
     * Exception categories
     */
    public enum ExceptionCategory {
        /** Skip player count validation */
        PLAYER_COUNT,
        /** Skip duration validation */
        DURATION,
        /** Skip memory validation */
        MEMORY,
        /** Skip all threshold validations */
        ALL_THRESHOLDS,
        /** Allow in production environment (DANGEROUS) */
        PRODUCTION_ALLOWED,
        /** Skip specific assertion by name */
        NAMED_ASSERTION
    }

    /** Whitelist entries: templateId -> set of exceptions */
    private final ConcurrentHashMap<String, Set<ExceptionEntry>> whitelist = new ConcurrentHashMap<>();

    private AutosmokeExceptions() {
        // Initialize with known exceptions (if any)
        initializeDefaultExceptions();
    }

    public static AutosmokeExceptions getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final AutosmokeExceptions INSTANCE = new AutosmokeExceptions();
    }

    /**
     * Initialize default exceptions for known edge cases
     */
    private void initializeDefaultExceptions() {
        // Example: Legacy arena templates that exceed normal thresholds
        // addException("devmod:legacy_large_arena", ExceptionCategory.PLAYER_COUNT, "Legacy 128-player arena");
    }

    /**
     * Checks if a template has an exception for a category
     *
     * @param templateId The template ID
     * @param category The exception category
     * @return true if exception exists
     */
    public boolean hasException(String templateId, ExceptionCategory category) {
        Set<ExceptionEntry> entries = whitelist.get(templateId);
        if (entries == null) {
            return false;
        }

        for (ExceptionEntry entry : entries) {
            if (entry.category == category || entry.category == ExceptionCategory.ALL_THRESHOLDS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a named assertion is whitelisted for a template
     *
     * @param templateId The template ID
     * @param assertionName The assertion name
     * @return true if assertion is whitelisted
     */
    public boolean isAssertionWhitelisted(String templateId, String assertionName) {
        Set<ExceptionEntry> entries = whitelist.get(templateId);
        if (entries == null) {
            return false;
        }

        for (ExceptionEntry entry : entries) {
            if (entry.category == ExceptionCategory.NAMED_ASSERTION &&
                assertionName.equals(entry.assertionName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds an exception to the whitelist
     *
     * @param templateId The template ID
     * @param category The exception category
     * @param reason The reason for the exception (for documentation)
     */
    public void addException(String templateId, ExceptionCategory category, String reason) {
        whitelist.computeIfAbsent(templateId, k -> ConcurrentHashMap.newKeySet())
                .add(new ExceptionEntry(category, null, reason));
    }

    /**
     * Adds a named assertion exception to the whitelist
     *
     * @param templateId The template ID
     * @param assertionName The assertion name to skip
     * @param reason The reason for the exception
     */
    public void addAssertionException(String templateId, String assertionName, String reason) {
        whitelist.computeIfAbsent(templateId, k -> ConcurrentHashMap.newKeySet())
                .add(new ExceptionEntry(ExceptionCategory.NAMED_ASSERTION, assertionName, reason));
    }

    /**
     * Removes all exceptions for a template
     *
     * @param templateId The template ID
     */
    public void removeAllExceptions(String templateId) {
        whitelist.remove(templateId);
    }

    /**
     * Removes a specific exception
     *
     * @param templateId The template ID
     * @param category The exception category
     */
    public void removeException(String templateId, ExceptionCategory category) {
        Set<ExceptionEntry> entries = whitelist.get(templateId);
        if (entries != null) {
            entries.removeIf(e -> e.category == category);
            if (entries.isEmpty()) {
                whitelist.remove(templateId);
            }
        }
    }

    /**
     * Gets all exceptions for a template
     *
     * @param templateId The template ID
     * @return Set of exception entries (unmodifiable)
     */
    public Set<ExceptionEntry> getExceptions(String templateId) {
        Set<ExceptionEntry> entries = whitelist.get(templateId);
        return entries != null ? Collections.unmodifiableSet(entries) : Collections.emptySet();
    }

    /**
     * Gets all whitelisted template IDs
     *
     * @return Set of template IDs with exceptions
     */
    public Set<String> getWhitelistedTemplates() {
        return Collections.unmodifiableSet(whitelist.keySet());
    }

    /**
     * Gets the total count of exceptions
     */
    public int getTotalExceptionCount() {
        return whitelist.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Clears all exceptions (use with caution)
     */
    public void clearAll() {
        whitelist.clear();
    }

    /**
     * Exception entry record
     */
    public record ExceptionEntry(
        ExceptionCategory category,
        String assertionName,
        String reason
    ) {
        @Override
        public String toString() {
            if (category == ExceptionCategory.NAMED_ASSERTION) {
                return String.format("EXCEPTION[%s:%s] - %s", category, assertionName, reason);
            }
            return String.format("EXCEPTION[%s] - %s", category, reason);
        }
    }
}
