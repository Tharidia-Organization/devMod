package com.devmod.actions;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P0-003: Command injection prevention.
 *
 * Provides validation and sanitization for command parameters to prevent
 * command injection attacks when building commands dynamically.
 *
 * Usage:
 * Instead of:
 *   context.executeCommand("arena create " + templateId);
 *
 * Use:
 *   context.executeCommand(CommandSanitizer.buildCommand("arena create", templateId));
 *
 * Or for validation:
 *   if (CommandSanitizer.isValidIdentifier(templateId)) {
 *       context.executeCommand("arena create " + templateId);
 *   }
 */
public final class CommandSanitizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandSanitizer.class);

    /**
     * Pattern for valid identifiers (alphanumeric, underscores, hyphens).
     * No spaces, no special characters that could be interpreted by command parser.
     */
    private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[a-zA-Z0-9_\\-:]+$");

    /**
     * Pattern for valid template IDs (lowercase alphanumeric, underscores, hyphens).
     * More restrictive than general identifiers.
     */
    private static final Pattern VALID_TEMPLATE_ID = Pattern.compile("^[a-z0-9_\\-]+$");

    /**
     * Pattern for valid player names (Minecraft-style).
     */
    private static final Pattern VALID_PLAYER_NAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    /**
     * Maximum length for any command parameter.
     */
    private static final int MAX_PARAM_LENGTH = 64;

    /**
     * Dangerous command prefixes that should never be allowed.
     */
    private static final Set<String> DANGEROUS_COMMANDS = Set.of(
        "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip",
        "kick", "stop", "save-all", "whitelist", "reload",
        "give", "enchant", "effect", "execute", "function",
        "data", "datapack", "forceload"
    );

    /**
     * Validates that a string is a safe identifier.
     *
     * @param value The value to validate
     * @return true if the value is a safe identifier
     */
    public static boolean isValidIdentifier(@Nullable String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_PARAM_LENGTH) {
            return false;
        }
        return VALID_IDENTIFIER.matcher(value).matches();
    }

    /**
     * Validates that a string is a valid template ID.
     *
     * @param templateId The template ID to validate
     * @return true if the template ID is valid
     */
    public static boolean isValidTemplateId(@Nullable String templateId) {
        if (templateId == null || templateId.isEmpty() || templateId.length() > 32) {
            return false;
        }
        return VALID_TEMPLATE_ID.matcher(templateId).matches();
    }

    /**
     * Validates that a string is a valid player name.
     *
     * @param playerName The player name to validate
     * @return true if the player name is valid
     */
    public static boolean isValidPlayerName(@Nullable String playerName) {
        if (playerName == null) {
            return false;
        }
        return VALID_PLAYER_NAME.matcher(playerName).matches();
    }

    /**
     * Validates that a value is a safe integer within bounds.
     *
     * @param value The string value to validate
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return true if the value is a valid integer within bounds
     */
    public static boolean isValidInteger(@Nullable String value, int min, int max) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            int intValue = Integer.parseInt(value);
            return intValue >= min && intValue <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Builds a command with validated parameters.
     * Throws IllegalArgumentException if any parameter is invalid.
     *
     * @param baseCommand The base command (e.g., "arena create")
     * @param params Parameters to append, each validated as identifier
     * @return The complete command string
     * @throws IllegalArgumentException if any parameter fails validation
     */
    public static String buildCommand(String baseCommand, String... params) {
        if (baseCommand == null || baseCommand.isEmpty()) {
            throw new IllegalArgumentException("Base command cannot be null or empty");
        }

        // Check for dangerous command prefixes
        String firstWord = extractFirstWord(baseCommand).toLowerCase(Locale.ROOT);
        if (DANGEROUS_COMMANDS.contains(firstWord)) {
            LOGGER.warn("[SECURITY] Attempted to build dangerous command: {}", baseCommand);
            throw new IllegalArgumentException("Command not allowed: " + firstWord);
        }

        StringBuilder sb = new StringBuilder(baseCommand);
        for (String param : params) {
            if (!isValidIdentifier(param)) {
                LOGGER.warn("[SECURITY] Invalid command parameter rejected: {}", param);
                throw new IllegalArgumentException("Invalid parameter: " + sanitizeForLog(param));
            }
            sb.append(' ').append(param);
        }
        return sb.toString();
    }

    /**
     * Builds a command with a validated template ID.
     *
     * @param baseCommand The base command (e.g., "arena create")
     * @param templateId The template ID to append
     * @return The complete command string
     * @throws IllegalArgumentException if the template ID is invalid
     */
    public static String buildTemplateCommand(String baseCommand, String templateId) {
        if (!isValidTemplateId(templateId)) {
            LOGGER.warn("[SECURITY] Invalid template ID rejected: {}", sanitizeForLog(templateId));
            throw new IllegalArgumentException("Invalid template ID: " + sanitizeForLog(templateId));
        }
        return baseCommand + " " + templateId;
    }

    /**
     * Builds a command with a validated template ID and additional safe integer.
     *
     * @param baseCommand The base command
     * @param templateId The template ID
     * @param intValue An integer value to append
     * @return The complete command string
     */
    public static String buildTemplateCommandWithInt(String baseCommand, String templateId, int intValue) {
        if (!isValidTemplateId(templateId)) {
            LOGGER.warn("[SECURITY] Invalid template ID rejected: {}", sanitizeForLog(templateId));
            throw new IllegalArgumentException("Invalid template ID: " + sanitizeForLog(templateId));
        }
        return baseCommand + " " + templateId + " " + intValue;
    }

    /**
     * Sanitizes a string for safe logging (prevents log injection).
     *
     * @param value The value to sanitize
     * @return A safe string for logging
     */
    public static String sanitizeForLog(@Nullable String value) {
        if (value == null) {
            return "<null>";
        }
        if (value.length() > 50) {
            value = value.substring(0, 50) + "...";
        }
        // Remove newlines and control characters
        return value.replaceAll("[\\r\\n\\t\\x00-\\x1F]", "?");
    }

    /**
     * Validates a complete command before execution.
     * Returns false if the command appears malicious.
     *
     * @param command The complete command to validate
     * @return true if the command appears safe
     */
    public static boolean isCommandSafe(@Nullable String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }

        // Check for command chaining attempts
        if (command.contains("&&") || command.contains("||") || command.contains(";")) {
            LOGGER.warn("[SECURITY] Command chaining attempt detected: {}", sanitizeForLog(command));
            return false;
        }

        // Check for redirection attempts
        if (command.contains(">") || command.contains("<") || command.contains("|")) {
            LOGGER.warn("[SECURITY] Redirection attempt detected: {}", sanitizeForLog(command));
            return false;
        }

        // Check command length
        if (command.length() > 256) {
            LOGGER.warn("[SECURITY] Command too long: {} chars", command.length());
            return false;
        }

        // Check for dangerous commands
        String firstWord = extractFirstWord(command).toLowerCase(Locale.ROOT);
        if (DANGEROUS_COMMANDS.contains(firstWord)) {
            LOGGER.warn("[SECURITY] Dangerous command blocked: {}", firstWord);
            return false;
        }

        return true;
    }

    private static String extractFirstWord(String command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        int length = command.length();
        int start = 0;
        while (start < length && Character.isWhitespace(command.charAt(start))) {
            start++;
        }
        if (start >= length) {
            return "";
        }
        int end = start;
        while (end < length && !Character.isWhitespace(command.charAt(end))) {
            end++;
        }
        return command.substring(start, end);
    }

    private CommandSanitizer() {} // Prevent instantiation
}
