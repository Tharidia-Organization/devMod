package com.devmod.area.aesthetic;

import java.util.Objects;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * BIBBIA ESTETICA - REGOLA 9: MESSAGGI E FEEDBACK
 *
 * Format messaggi standardizzato per tutte le comunicazioni.
 * Questi messaggi garantiscono coerenza nella comunicazione con l'utente.
 */
public final class AreaBuilderMessages {

    private AreaBuilderMessages() {}

    // ============================================================================
    // PREFIX
    // ============================================================================

    /** Common prefix for all messages */
    public static final String PREFIX = "[Area Builder] ";

    /** Prefix component with formatting */
    public static final Component PREFIX_COMPONENT = Component.literal("[Area Builder] ")
        .withStyle(ChatFormatting.AQUA);

    // ============================================================================
    // FORMAT STRINGS (legacy string format)
    // ============================================================================

    /** Success message format (green) */
    public static final String SUCCESS_FORMAT = PREFIX + "§a%s§r";

    /** Info message format (yellow) */
    public static final String INFO_FORMAT = PREFIX + "§e%s§r";

    /** Error message format (red) */
    public static final String ERROR_FORMAT = PREFIX + "§c%s§r";

    /** Progress message format (cyan + percentage) */
    public static final String PROGRESS_FORMAT = PREFIX + "§b%s§r §7(%d%%)";

    /** Warning message format (gold) */
    public static final String WARNING_FORMAT = PREFIX + "§6%s§r";

    // ============================================================================
    // PREDEFINED MESSAGES (localization keys)
    // ============================================================================

    /** Preview started */
    public static final String MSG_PREVIEW_STARTED = "area.message.preview_started";

    /** Preview stopped */
    public static final String MSG_PREVIEW_STOPPED = "area.message.preview_stopped";

    /** Build started */
    public static final String MSG_BUILD_STARTED = "area.message.build_started";

    /** Build in progress */
    public static final String MSG_BUILD_PROGRESS = "area.message.build_progress";

    /** Build complete */
    public static final String MSG_BUILD_COMPLETE = "area.message.build_complete";

    /** Build cancelled */
    public static final String MSG_BUILD_CANCELLED = "area.message.build_cancelled";

    /** Error: overlap */
    public static final String MSG_ERROR_OVERLAP = "area.message.error_overlap";

    /** Error: bounds */
    public static final String MSG_ERROR_BOUNDS = "area.message.error_bounds";

    /** Error: permission */
    public static final String MSG_ERROR_PERMISSION = "area.message.error_permission";

    /** Error: locked */
    public static final String MSG_ERROR_LOCKED = "area.message.error_locked";

    // ============================================================================
    // BOSSBAR
    // ============================================================================

    /** Bossbar name format */
    public static final String BOSSBAR_NAME_FORMAT = "Building Area: %s";

    /** Bossbar starting color */
    public static final ChatFormatting BOSSBAR_COLOR_BUILDING = ChatFormatting.BLUE;

    /** Bossbar complete color */
    public static final ChatFormatting BOSSBAR_COLOR_COMPLETE = ChatFormatting.GREEN;

    // ============================================================================
    // COMPONENT BUILDERS
    // ============================================================================

    /**
     * Creates a success message component.
     */
    public static MutableComponent success(String message) {
        return PREFIX_COMPONENT.copy()
            .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(message)).withStyle(ChatFormatting.GREEN)));
    }

    /**
     * Creates a success message component from translatable key.
     */
    public static MutableComponent successTranslatable(String key, Object... args) {
        return PREFIX_COMPONENT.copy()
            .append(Objects.requireNonNull(Component.translatable(Objects.requireNonNull(key), Objects.requireNonNull(args)).withStyle(ChatFormatting.GREEN)));
    }

    /**
     * Creates an info message component.
     */
    public static MutableComponent info(String message) {
        return PREFIX_COMPONENT.copy()
            .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(message)).withStyle(ChatFormatting.YELLOW)));
    }

    /**
     * Creates an info message component from translatable key.
     */
    public static MutableComponent infoTranslatable(String key, Object... args) {
        return PREFIX_COMPONENT.copy()
            .append(Objects.requireNonNull(Component.translatable(Objects.requireNonNull(key), Objects.requireNonNull(args)).withStyle(ChatFormatting.YELLOW)));
    }

    /**
     * Creates an error message component.
     */
    public static MutableComponent error(String message) {
        return PREFIX_COMPONENT.copy()
            .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(message)).withStyle(ChatFormatting.RED)));
    }

    /**
     * Creates an error message component from translatable key.
     */
    public static MutableComponent errorTranslatable(String key, Object... args) {
        return PREFIX_COMPONENT.copy()
            .append(Objects.requireNonNull(Component.translatable(Objects.requireNonNull(key), Objects.requireNonNull(args)).withStyle(ChatFormatting.RED)));
    }

    /**
     * Creates a warning message component.
     */
    public static MutableComponent warning(String message) {
        return PREFIX_COMPONENT.copy()
            .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(message)).withStyle(ChatFormatting.GOLD)));
    }

    /**
     * Creates a warning message component from translatable key.
     */
    public static MutableComponent warningTranslatable(String key, Object... args) {
        return PREFIX_COMPONENT.copy()
            .append(Objects.requireNonNull(Component.translatable(Objects.requireNonNull(key), Objects.requireNonNull(args)).withStyle(ChatFormatting.GOLD)));
    }

    /**
     * Creates a progress message component with percentage.
     */
    public static MutableComponent progress(String message, int percent) {
        return PREFIX_COMPONENT.copy()
            .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(message)).withStyle(ChatFormatting.AQUA)))
            .append(Objects.requireNonNull(Component.literal(" (" + percent + "%)").withStyle(ChatFormatting.GRAY)));
    }

    /**
     * Creates a progress message component from translatable key.
     */
    public static MutableComponent progressTranslatable(String key, int percent) {
        return PREFIX_COMPONENT.copy()
            .append(Objects.requireNonNull(Component.translatable(Objects.requireNonNull(key)).withStyle(ChatFormatting.AQUA)))
            .append(Objects.requireNonNull(Component.literal(" (" + percent + "%)").withStyle(ChatFormatting.GRAY)));
    }

    /**
     * Creates a bossbar title component for building.
     */
    public static MutableComponent bossbarTitle(String areaName) {
        return Component.literal("Building Area: ")
            .withStyle(ChatFormatting.WHITE)
            .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(areaName)).withStyle(ChatFormatting.AQUA)));
    }

    // ============================================================================
    // INTERACTION RESULT MESSAGES
    // ============================================================================

    /**
     * Gets the appropriate error message for an interaction result.
     */
    public static MutableComponent getInteractionMessage(AreaBuilderInteraction.InteractionResult result) {
        return switch (result) {
            case SUCCESS -> success("Interaction successful");
            case TOO_FAR -> error("You are too far from the editor");
            case NO_PERMISSION -> errorTranslatable(MSG_ERROR_PERMISSION);
            case LOCKED -> errorTranslatable(MSG_ERROR_LOCKED);
            case COOLDOWN -> warning("Please wait before interacting again");
            case OVERLAP -> errorTranslatable(MSG_ERROR_OVERLAP);
            case OUT_OF_BOUNDS -> errorTranslatable(MSG_ERROR_BOUNDS);
            case ERROR -> error("An unknown error occurred");
        };
    }
}
