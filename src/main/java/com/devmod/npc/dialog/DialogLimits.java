package com.devmod.npc.dialog;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.devmod.network.NetworkConstants;

/**
 * Shared limits for NPC dialog data across UI, network, and persistence.
 * References {@link NetworkConstants} for standard tiers.
 */
public final class DialogLimits {
    private DialogLimits() {
    }

    // ID and name limits - referencing NetworkConstants tiers
    public static final int MAX_DIALOG_ID_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH; // 64
    public static final int MAX_DIALOG_NAME_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH; // 64
    public static final int MAX_NODE_ID_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH; // 64
    public static final int MAX_OPTION_ID_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH; // 64
    public static final int MAX_OPTION_LABEL_LENGTH = NetworkConstants.MAX_TITLE_LENGTH; // 128
    public static final int MAX_OPTION_ICON_LENGTH = NetworkConstants.MAX_SMALL_STRING; // 32

    // Content limits
    public static final int MAX_LINES_PER_NODE = NetworkConstants.MAX_SMALL_ARRAY; // 20
    public static final int MAX_LINE_LENGTH = 512; // Domain-specific (between MEDIUM and LARGE)
    public static final int MAX_OPTIONS_PER_NODE = 10; // Domain-specific
    public static final int MAX_NODES = NetworkConstants.MAX_XLARGE_ARRAY; // 200

    // Action limits
    public static final int MAX_ACTION_TYPE_LENGTH = NetworkConstants.MAX_SMALL_STRING; // 32
    public static final int MAX_ACTION_PARAM_LENGTH = NetworkConstants.MAX_MEDIUM_STRING; // 256
    public static final int MAX_COMMAND_LENGTH = NetworkConstants.MAX_COMMAND_LENGTH; // 256
    public static final int MAX_GUI_ID_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH; // 64
    public static final int MAX_ZONE_ID_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH; // 64
    public static final int MAX_VARIABLE_KEY_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH; // 64
    public static final int MAX_VARIABLE_VALUE_LENGTH = NetworkConstants.MAX_MEDIUM_STRING; // 256
    public static final int MAX_CUSTOM_HANDLER_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH; // 64

    // Condition limits
    public static final int MAX_CONDITION_TYPE_LENGTH = NetworkConstants.MAX_SMALL_STRING; // 32
    public static final int MAX_CONDITION_CHILDREN = 16; // Domain-specific
    public static final int MAX_QUEST_ID_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH; // 64
    public static final int MAX_DATE_LENGTH = 16; // Domain-specific (date format)
    public static final int MAX_SCHEDULE_DAYS = 7; // Domain-specific (days of week)

    // Utility methods - delegate to NetworkConstants
    public static String truncate(@Nullable String value, int maxLength) {
        return NetworkConstants.truncate(value, maxLength);
    }

    public static List<String> clampLines(@Nullable List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(Math.min(lines.size(), MAX_LINES_PER_NODE));
        for (String line : lines) {
            if (result.size() >= MAX_LINES_PER_NODE) {
                break;
            }
            result.add(truncate(line, MAX_LINE_LENGTH));
        }
        return List.copyOf(result);
    }
}
