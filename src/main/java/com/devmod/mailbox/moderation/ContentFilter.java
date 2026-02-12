package com.devmod.mailbox.moderation;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Content filter for mailbox messages.
 * Checks messages for prohibited words and patterns.
 */
public final class ContentFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentFilter.class);

    public static final ContentFilter INSTANCE = new ContentFilter();

    /** Set of prohibited words (case-insensitive matching). */
    private final Set<String> prohibitedWords = new CopyOnWriteArraySet<>();

    /** Compiled patterns for more complex filtering. */
    private final Set<Pattern> prohibitedPatterns = new CopyOnWriteArraySet<>();

    /** Whether filtering is enabled. */
    private volatile boolean enabled = true;

    /** Action to take when content is blocked. */
    private volatile FilterAction action = FilterAction.BLOCK;

    private ContentFilter() {
        // Initialize with some default prohibited patterns
        initializeDefaults();
    }

    private void initializeDefaults() {
        // Add default prohibited patterns (can be customized via config)
        // These are just examples - actual list should be configurable
        prohibitedPatterns.add(Pattern.compile(
            "(https?://|www\\.)[^\\s]+", Pattern.CASE_INSENSITIVE));  // URLs (optional)
    }

    // ============================================================================
    // CONFIGURATION
    // ============================================================================

    /**
     * Enable or disable the content filter.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("[ContentFilter] Filter {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Check if the filter is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set the action to take when content is blocked.
     */
    public void setAction(@Nullable FilterAction action) {
        this.action = action != null ? action : FilterAction.BLOCK;
    }

    /**
     * Get the current filter action.
     */
    public FilterAction getAction() {
        return action;
    }

    /**
     * Add a prohibited word.
     */
    public void addProhibitedWord(String word) {
        String normalized = normalizeWord(word);
        if (normalized != null) {
            prohibitedWords.add(normalized);
            LOGGER.debug("[ContentFilter] Added prohibited word");
        }
    }

    /**
     * Remove a prohibited word.
     */
    public void removeProhibitedWord(String word) {
        String normalized = normalizeWord(word);
        if (normalized != null) {
            prohibitedWords.remove(normalized);
        }
    }

    /**
     * Set the list of prohibited words (replaces existing).
     */
    public void setProhibitedWords(List<String> words) {
        prohibitedWords.clear();
        if (words != null) {
            words.stream()
                .map(ContentFilter::normalizeWord)
                .filter(java.util.Objects::nonNull)
                .forEach(prohibitedWords::add);
        }
        LOGGER.info("[ContentFilter] Updated prohibited words list ({} words)", prohibitedWords.size());
    }

    /**
     * Get the list of prohibited words.
     */
    public Set<String> getProhibitedWords() {
        return Set.copyOf(prohibitedWords);
    }

    /**
     * Add a prohibited regex pattern.
     */
    public void addProhibitedPattern(@Nullable String regex) {
        if (regex == null || regex.isBlank()) {
            return;
        }
        try {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            prohibitedPatterns.add(pattern);
            LOGGER.debug("[ContentFilter] Added prohibited pattern: {}", regex);
        } catch (Exception e) {
            LOGGER.warn("[ContentFilter] Invalid regex pattern: {}", regex, e);
        }
    }

    /**
     * Clear all custom patterns (keeps defaults).
     */
    public void clearPatterns() {
        prohibitedPatterns.clear();
        initializeDefaults();
    }

    /**
     * Set the list of prohibited regex patterns (replaces existing, keeps defaults).
     */
    public void setProhibitedPatterns(List<String> patterns) {
        clearPatterns();
        if (patterns == null) {
            return;
        }
        for (String pattern : patterns) {
            addProhibitedPattern(pattern);
        }
    }

    // ============================================================================
    // FILTERING
    // ============================================================================

    /**
     * Check if content passes the filter.
     *
     * @param content the content to check
     * @return result containing whether content is allowed and reason if blocked
     */
    public FilterResult check(@Nullable String content) {
        if (!enabled || content == null || content.isBlank()) {
            return FilterResult.pass();
        }

        String lowerContent = content.toLowerCase(Locale.ROOT);

        // Check prohibited words
        for (String word : prohibitedWords) {
            if (containsWord(lowerContent, word)) {
                LOGGER.debug("[ContentFilter] Content blocked: prohibited word");
                return FilterResult.block("Content contains prohibited word");
            }
        }

        // Check prohibited patterns
        for (Pattern pattern : prohibitedPatterns) {
            if (pattern.matcher(content).find()) {
                LOGGER.debug("[ContentFilter] Content blocked: prohibited pattern");
                return FilterResult.block("Content matches prohibited pattern");
            }
        }

        return FilterResult.pass();
    }

    /**
     * Check if content contains a word (word boundary matching).
     */
    private boolean containsWord(String content, String word) {
        // Simple word boundary check
        int index = content.indexOf(word);
        while (index >= 0) {
            boolean startOk = index == 0 || !Character.isLetterOrDigit(content.charAt(index - 1));
            boolean endOk = index + word.length() >= content.length()
                || !Character.isLetterOrDigit(content.charAt(index + word.length()));

            if (startOk && endOk) {
                return true;
            }

            index = content.indexOf(word, index + 1);
        }
        return false;
    }

    @Nullable
    private static String normalizeWord(@Nullable String word) {
        if (word == null) {
            return null;
        }
        String normalized = word.toLowerCase(Locale.ROOT).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Check both subject and body of a message.
     *
     * @param subject the message subject
     * @param body the message body
     * @return combined filter result
     */
    public FilterResult checkMessage(@Nullable String subject, @Nullable String body) {
        FilterResult subjectResult = check(subject);
        if (!subjectResult.isAllowed()) {
            return subjectResult;
        }

        return check(body);
    }

    /**
     * Censor content by replacing prohibited words and patterns.
     */
    public String censor(String content) {
        if (content.isBlank()) {
            return content;
        }
        String result = content;
        for (String word : prohibitedWords) {
            if (word == null || word.isBlank()) {
                continue;
            }
            String mask = "*".repeat(Math.min(word.length(), 64));
            String regex = "\\b" + java.util.regex.Pattern.quote(word) + "\\b";
            result = result.replaceAll("(?i)" + regex, mask);
        }
        for (Pattern pattern : prohibitedPatterns) {
            result = pattern.matcher(result).replaceAll("[censored]");
        }
        return result;
    }

    /**
     * Parse a filter action from string (defaults to BLOCK).
     */
    public static FilterAction parseAction(@Nullable String action) {
        if (action == null || action.isBlank()) {
            return FilterAction.BLOCK;
        }
        try {
            return FilterAction.valueOf(action.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FilterAction.BLOCK;
        }
    }

    // ============================================================================
    // RESULT TYPES
    // ============================================================================

    /**
     * Filter action when content is blocked.
     */
    public enum FilterAction {
        /** Block the message entirely. */
        BLOCK,
        /** Allow but flag for moderation. */
        FLAG,
        /** Allow but replace prohibited content with asterisks. */
        CENSOR
    }

    /**
     * Result of content filtering.
     */
    public record FilterResult(boolean isAllowed, @Nullable String reason) {

        public static FilterResult pass() {
            return new FilterResult(true, null);
        }

        public static FilterResult block(String reason) {
            return new FilterResult(false, reason);
        }
    }
}
